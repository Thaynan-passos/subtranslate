package com.example.domain.util

import com.google.mlkit.nl.translate.TranslateLanguage

data class LanguageOption(
    val code: String,
    val displayName: String
)

object LanguageHelper {

    /**
     * Source languages supported by both ML Kit OCR (Latin, CJK, Devanagari) and Translation.
     * Note: Russian/Cyrillic is excluded from source languages because the standard ML Kit
     * OCR bundled doesn't include Cyrillic recognition, avoiding recognition failures.
     */
    val sourceLanguages: List<LanguageOption> = listOf(
        LanguageOption("en", "English"),
        LanguageOption("pt", "Português"),
        LanguageOption("es", "Español"),
        LanguageOption("ja", "Japanese (日本語)"),
        LanguageOption("zh", "Chinese (中文)"),
        LanguageOption("ko", "Korean (한국어)"),
        LanguageOption("fr", "Français"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("it", "Italiano"),
        LanguageOption("hi", "Hindi (हिन्दी)")
    )

    /**
     * Target languages for translation. Russian is supported as a translation target.
     */
    val targetLanguages: List<LanguageOption> = listOf(
        LanguageOption("pt", "Português"),
        LanguageOption("en", "English"),
        LanguageOption("es", "Español"),
        LanguageOption("ja", "Japanese (日本語)"),
        LanguageOption("zh", "Chinese (中文)"),
        LanguageOption("ko", "Korean (한국어)"),
        LanguageOption("fr", "Français"),
        LanguageOption("de", "Deutsch"),
        LanguageOption("it", "Italiano"),
        LanguageOption("ru", "Russian (Русский)")
    )

    fun toMlKitTranslateLanguage(code: String): String {
        return when (code.lowercase()) {
            "pt", "pt-br", "portuguese" -> TranslateLanguage.PORTUGUESE
            "en", "english" -> TranslateLanguage.ENGLISH
            "es", "spanish" -> TranslateLanguage.SPANISH
            "ja", "japanese" -> TranslateLanguage.JAPANESE
            "zh", "chinese" -> TranslateLanguage.CHINESE
            "ko", "korean" -> TranslateLanguage.KOREAN
            "fr", "french" -> TranslateLanguage.FRENCH
            "de", "german" -> TranslateLanguage.GERMAN
            "it", "italian" -> TranslateLanguage.ITALIAN
            "ru", "russian" -> TranslateLanguage.RUSSIAN
            "hi", "hindi", "devanagari" -> TranslateLanguage.HINDI
            else -> throw IllegalArgumentException("Idioma não suportado pelo tradutor: $code")
        }
    }
}
