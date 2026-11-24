package com.example.data.engine

import android.util.Log
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tradutor híbrido inteligente que seleciona o melhor motor de tradução com base nas configurações
 * do usuário e na conectividade, com fallback automático do Gemini para o ML Kit offline.
 */
class SmartHybridTranslator(
    private val mlKitTranslator: Translator,
    private val geminiTranslator: Translator,
    private val settingsProvider: () -> AppSettings,
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Translator {

    @Volatile
    private var geminiCircuitTripped: Boolean = false

    @Volatile
    private var lastGeminiConfigKey: String = ""

    override val name: String
        get() {
            val settings = settingsProvider()
            return if (settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()) {
                if (settings.autoFallbackToMlKit) {
                    "Google Gemini (IA) com Fallback Offline"
                } else {
                    "Google Gemini (IA)"
                }
            } else {
                mlKitTranslator.name
            }
        }

    private fun syncCircuitBreaker(settings: AppSettings) {
        val currentConfigKey = "${settings.geminiApiKey.trim()}|${settings.geminiModel.trim()}"
        if (currentConfigKey != lastGeminiConfigKey) {
            lastGeminiConfigKey = currentConfigKey
            geminiCircuitTripped = false
        }
    }

    override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean {
        val settings = settingsProvider()
        return if (settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()) {
            true
        } else {
            mlKitTranslator.isModelAvailable(sourceLanguage, targetLanguage)
        }
    }

    override suspend fun downloadModelIfNeeded(
        sourceLanguage: String,
        targetLanguage: String,
        requireWifi: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        val settings = settingsProvider()
        val preferGemini = settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()
        if (preferGemini) {
            if (settings.autoFallbackToMlKit) {
                // Pré-carrega o modelo de fallback em segundo plano sem bloquear o pipeline principal.
                backgroundScope.launch {
                    try {
                        mlKitTranslator.downloadModelIfNeeded(sourceLanguage, targetLanguage, requireWifi = false) { }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Falha no pré-carregamento do fallback offline não bloqueia o uso primário do Gemini
                    }
                }
            }
            onProgress(1.0f)
            return true
        }
        return mlKitTranslator.downloadModelIfNeeded(sourceLanguage, targetLanguage, requireWifi, onProgress)
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>,
        bypassCache: Boolean
    ): String {
        val settings = settingsProvider()
        syncCircuitBreaker(settings)
        val preferGemini = !geminiCircuitTripped &&
            settings.translationEngine == "gemini" &&
            settings.geminiApiKey.isNotBlank()

        if (preferGemini) {
            try {
                return geminiTranslator.translate(
                    text = text,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val safeMsg = handleGeminiFailure(e, settings)
                if (!settings.autoFallbackToMlKit) {
                    throw IllegalStateException(safeMsg.ifBlank { "Falha na tradução via Google Gemini." })
                }
                return mlKitTranslator.translate(
                    text = text,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            }
        }

        if (settings.translationEngine == "gemini" &&
            settings.geminiApiKey.isNotBlank() &&
            geminiCircuitTripped &&
            !settings.autoFallbackToMlKit
        ) {
            throw IllegalStateException("A API do Google Gemini falhou anteriormente nesta sessão e o fallback automático está desativado.")
        }

        return mlKitTranslator.translate(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            glossary = glossary,
            bypassCache = bypassCache
        )
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>,
        bypassCache: Boolean
    ): List<String> {
        val settings = settingsProvider()
        syncCircuitBreaker(settings)
        val preferGemini = !geminiCircuitTripped &&
            settings.translationEngine == "gemini" &&
            settings.geminiApiKey.isNotBlank()

        if (preferGemini) {
            try {
                return geminiTranslator.translateBatch(
                    texts = texts,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val safeMsg = handleGeminiFailure(e, settings)
                if (!settings.autoFallbackToMlKit) {
                    throw IllegalStateException(safeMsg.ifBlank { "Falha na tradução em lote via Google Gemini." })
                }
                return mlKitTranslator.translateBatch(
                    texts = texts,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            }
        }

        if (settings.translationEngine == "gemini" &&
            settings.geminiApiKey.isNotBlank() &&
            geminiCircuitTripped &&
            !settings.autoFallbackToMlKit
        ) {
            throw IllegalStateException("A API do Google Gemini falhou anteriormente nesta sessão e o fallback automático está desativado.")
        }

        return mlKitTranslator.translateBatch(
            texts = texts,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            glossary = glossary,
            bypassCache = bypassCache
        )
    }

    private fun handleGeminiFailure(e: Exception, settings: AppSettings): String {
        val errorMsg = e.message.orEmpty()
        if (errorMsg.contains("cota") ||
            errorMsg.contains("429") ||
            errorMsg.contains("401") ||
            errorMsg.contains("403") ||
            errorMsg.contains("inválida") ||
            errorMsg.contains("permissões")
        ) {
            geminiCircuitTripped = true
            logWarning("Circuit breaker ativado para o Gemini nesta sessão.")
        }
        val safeMsg = GeminiTranslatorImpl.redactApiKey(e.message, settings.geminiApiKey)
        logWarning("Falha na tradução via Gemini: $safeMsg")
        return safeMsg
    }

    private fun logWarning(message: String) {
        try {
            Log.w("SmartHybridTranslator", message)
        } catch (_: Throwable) {
            println("[SmartHybridTranslator] $message")
        }
    }

    override fun close() {
        geminiCircuitTripped = false
        lastGeminiConfigKey = ""
        geminiTranslator.close()
        mlKitTranslator.close()
    }
}
