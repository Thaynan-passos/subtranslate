package com.example.domain.engine

interface Translator {
    val name: String

    /**
     * Translates text from source to target language.
     * When [bypassCache] is true, ignores Room cache and performs a fresh translation.
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String> = emptyMap(),
        bypassCache: Boolean = false
    ): String

    /**
     * Traduz um lote de frases preservando a ordem de entrada.
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String> = emptyMap(),
        bypassCache: Boolean = false
    ): List<String> = texts.map { translate(it, sourceLanguage, targetLanguage, glossary, bypassCache) }

    /**
     * Checks if the required language model is downloaded.
     */
    suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean

    /**
     * Downloads offline model if needed.
     */
    suspend fun downloadModelIfNeeded(
        sourceLanguage: String,
        targetLanguage: String,
        requireWifi: Boolean = false,
        onProgress: (Float) -> Unit = {}
    ): Boolean

    fun close()
}
