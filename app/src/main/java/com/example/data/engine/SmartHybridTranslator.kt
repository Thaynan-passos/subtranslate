package com.example.data.engine

import android.util.Log
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings

/**
 * Tradutor híbrido inteligente que seleciona o melhor motor de tradução com base nas configurações
 * do usuário e na conectividade, com fallback automático do Gemini para o ML Kit offline.
 */
class SmartHybridTranslator(
    private val mlKitTranslator: Translator,
    private val geminiTranslator: Translator,
    private val settingsProvider: () -> AppSettings
) : Translator {

    override val name: String
        get() {
            val settings = settingsProvider()
            return if (settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()) {
                "Google Gemini (IA) com Fallback Offline"
            } else {
                mlKitTranslator.name
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
        if (settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()) {
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
        val preferGemini = settings.translationEngine == "gemini" && settings.geminiApiKey.isNotBlank()

        if (preferGemini) {
            try {
                return geminiTranslator.translate(
                    text = text,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            } catch (e: Exception) {
                try {
                    Log.w("SmartHybridTranslator", "Falha na tradução via Gemini, executando fallback para ML Kit: ${e.message}")
                } catch (_: Throwable) {
                    // Fallback para stdout em ambientes de teste unitário onde android.util.Log não está mockado
                    println("[SmartHybridTranslator] Falha na tradução via Gemini, executando fallback para ML Kit: ${e.message}")
                }
                // Fallback automático para o motor offline caso haja erro de rede ou chave
                return mlKitTranslator.translate(
                    text = text,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    glossary = glossary,
                    bypassCache = bypassCache
                )
            }
        }

        return mlKitTranslator.translate(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            glossary = glossary,
            bypassCache = bypassCache
        )
    }

    override fun close() {
        geminiTranslator.close()
        mlKitTranslator.close()
    }
}
