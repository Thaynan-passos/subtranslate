package com.example.data.engine

import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.Translator
import com.example.domain.util.LanguageHelper
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslatorImpl(
    private val repository: SubtitleRepository
) : Translator {

    override val name: String = "ML Kit Translate (Offline)"

    private val activeClients = ConcurrentHashMap<String, com.google.mlkit.nl.translate.Translator>()

    private fun getClient(sourceLang: String, targetLang: String): com.google.mlkit.nl.translate.Translator {
        val src = LanguageHelper.toMlKitTranslateLanguage(sourceLang)
        val tgt = LanguageHelper.toMlKitTranslateLanguage(targetLang)
        val key = "$src->$tgt"

        return activeClients.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(src)
                .setTargetLanguage(tgt)
                .build()
            Translation.getClient(options)
        }
    }

    override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val src = LanguageHelper.toMlKitTranslateLanguage(sourceLanguage)
            val tgt = LanguageHelper.toMlKitTranslateLanguage(targetLanguage)

            val modelManager = RemoteModelManager.getInstance()
            modelManager.getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    val downloadedCodes = models.map { it.language }
                    val hasSource = downloadedCodes.contains(src)
                    val hasTarget = downloadedCodes.contains(tgt)
                    continuation.resume(hasSource && hasTarget)
                }
                .addOnFailureListener {
                    continuation.resume(false)
                }
        }
    }

    override suspend fun downloadModelIfNeeded(
        sourceLanguage: String,
        targetLanguage: String,
        requireWifi: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val client = getClient(sourceLanguage, targetLanguage)
                val builder = DownloadConditions.Builder()
                if (requireWifi) {
                    builder.requireWifi()
                }
                onProgress(0.1f)
                client.downloadModelIfNeeded(builder.build())
                    .addOnSuccessListener {
                        onProgress(1.0f)
                        continuation.resume(true)
                    }
                    .addOnFailureListener {
                        continuation.resume(false)
                    }
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }
    }

    data class GlossaryPlaceholder(
        val placeholder: String,
        val sourceTerm: String,
        val targetTerm: String
    )

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        contextLines: List<String>,
        glossary: Map<String, String>,
        bypassCache: Boolean
    ): String {
        if (text.isBlank()) return text

        // Check local Room cache first if not bypassing
        if (!bypassCache) {
            val cached = repository.getCachedTranslation(text, sourceLanguage, targetLanguage, name)
            if (cached != null) {
                return applyGlossary(cached, glossary)
            }
        }

        val (preparedText, placeholders) = preparePlaceholders(text, glossary)

        // Perform ML Kit Translation
        val rawTranslatedWithPlaceholders = suspendCancellableCoroutine<String> { continuation ->
            val client = getClient(sourceLanguage, targetLanguage)
            val conditions = DownloadConditions.Builder().build()

            client.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    client.translate(preparedText)
                        .addOnSuccessListener { result ->
                            continuation.resume(result)
                        }
                        .addOnFailureListener { ex ->
                            continuation.resumeWithException(ex)
                        }
                }
                .addOnFailureListener { ex ->
                    continuation.resumeWithException(ex)
                }
        }

        // Cache the raw translation WITHOUT glossary (restore placeholders with source terms)
        val withoutGlossary = restoreSourceTerms(rawTranslatedWithPlaceholders, placeholders)
        if (withoutGlossary.isNotBlank()) {
            repository.cacheTranslation(text, sourceLanguage, targetLanguage, name, withoutGlossary)
        }

        // Restore placeholders with target terms (or source term if target is blank)
        return restorePlaceholders(rawTranslatedWithPlaceholders, placeholders, glossary)
    }

    override fun close() {
        for (client in activeClients.values) {
            client.close()
        }
        activeClients.clear()
    }

    companion object {
        fun applyGlossary(text: String, glossary: Map<String, String>): String {
            if (text.isBlank() || glossary.isEmpty()) return text
            var processed = text

            for ((sourceTerm, targetTerm) in glossary) {
                if (sourceTerm.isBlank()) continue
                try {
                    val escapedSource = Regex.escape(sourceTerm.trim())
                    val replacement = if (targetTerm.isNotBlank()) targetTerm.trim() else sourceTerm.trim()
                    val escapedReplacement = Matcher.quoteReplacement(replacement)

                    // Safe word boundary with lookarounds supporting CJK, symbols and alphanumeric
                    val pattern = "(?i)(?<![\\p{L}\\p{N}])$escapedSource(?![\\p{L}\\p{N}])"
                    processed = processed.replace(Regex(pattern), escapedReplacement)
                } catch (_: Exception) {
                    // Fail safely so invalid regex or term does not crash the pipeline
                }
            }
            return processed
        }

        fun preparePlaceholders(text: String, glossary: Map<String, String>): Pair<String, List<GlossaryPlaceholder>> {
            val activeGlossary = glossary.filterKeys { it.isNotBlank() }
            if (activeGlossary.isEmpty() || text.isBlank()) return Pair(text, emptyList())

            val placeholders = mutableListOf<GlossaryPlaceholder>()
            var prepared = text
            val sortedEntries = activeGlossary.entries.sortedByDescending { it.key.length }
            var idx = 0
            for ((src, tgt) in sortedEntries) {
                val escapedSource = Regex.escape(src.trim())
                val pattern = "(?i)(?<![\\p{L}\\p{N}])$escapedSource(?![\\p{L}\\p{N}])"
                val regex = Regex(pattern)
                if (regex.containsMatchIn(prepared)) {
                    var placeholder: String
                    do {
                        placeholder = "⟦G${idx++}⟧"
                    } while (prepared.contains(placeholder))
                    prepared = prepared.replace(regex, placeholder)
                    placeholders.add(GlossaryPlaceholder(placeholder, src.trim(), tgt.trim()))
                }
            }
            return Pair(prepared, placeholders)
        }

        fun restorePlaceholders(
            translatedText: String,
            placeholders: List<GlossaryPlaceholder>,
            glossary: Map<String, String>
        ): String {
            var result = translatedText
            var missing = false
            for (item in placeholders) {
                val rep = if (item.targetTerm.isNotBlank()) item.targetTerm else item.sourceTerm
                if (result.contains(item.placeholder)) {
                    result = result.replace(item.placeholder, rep)
                } else {
                    missing = true
                }
            }
            if (missing) {
                result = applyGlossary(result, glossary)
            }
            return result
        }

        private fun restoreSourceTerms(
            translatedText: String,
            placeholders: List<GlossaryPlaceholder>
        ): String {
            var result = translatedText
            for (item in placeholders) {
                result = result.replace(item.placeholder, item.sourceTerm)
            }
            return result
        }
    }
}
