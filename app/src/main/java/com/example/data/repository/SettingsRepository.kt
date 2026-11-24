package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.AndroidKeyStoreApiKeyCipher
import com.example.data.security.ApiKeyCipher
import com.example.domain.model.AppSettings
import com.example.domain.util.WatermarkFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtranslate_settings")

class SettingsRepository(
    private val context: Context,
    private val apiKeyCipher: ApiKeyCipher = AndroidKeyStoreApiKeyCipher()
) {

    private object Keys {
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val MIN_SPEECH_DURATION = intPreferencesKey("min_speech_duration")
        val SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val MAX_CHARS_PER_LINE = intPreferencesKey("max_chars_per_line")
        val COVER_ORIGINAL_OVERLAY = booleanPreferencesKey("cover_original_overlay")
        val OVERLAY_COLOR_HEX = stringPreferencesKey("overlay_color_hex")
        val SUBTITLE_FONT_SIZE = intPreferencesKey("subtitle_font_size")
        val SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val GLOSSARY_RAW = stringPreferencesKey("glossary_raw")
        val FILTER_WATERMARKS = booleanPreferencesKey("filter_watermarks")
        val IGNORED_TERMS_RAW = stringPreferencesKey("ignored_terms_raw")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val AUTO_FALLBACK_TO_MLKIT = booleanPreferencesKey("auto_fallback_to_mlkit")
        val ENABLE_HIGH_CONTRAST_OCR = booleanPreferencesKey("enable_high_contrast_ocr")
        val ENABLE_OTSU_OCR = booleanPreferencesKey("enable_otsu_ocr")
        val ENABLE_FULL_FRAME_FALLBACK_OCR = booleanPreferencesKey("enable_full_frame_fallback_ocr")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val glossaryString = prefs[Keys.GLOSSARY_RAW] ?: ""
        val glossaryMap = parseGlossary(glossaryString)
        val ignoredTermsString = prefs[Keys.IGNORED_TERMS_RAW] ?: ""
        val parsedIgnoredTerms = WatermarkFilter.parseIgnoredTerms(ignoredTermsString)

        AppSettings(
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 2,
            minSpeechDurationMs = (prefs[Keys.MIN_SPEECH_DURATION] ?: 300).toLong(),
            textSimilarityThreshold = prefs[Keys.SIMILARITY_THRESHOLD] ?: AppSettings.DEFAULT_SIMILARITY_THRESHOLD,
            maxCharsPerLine = prefs[Keys.MAX_CHARS_PER_LINE] ?: 42,
            coverOriginalOverlay = prefs[Keys.COVER_ORIGINAL_OVERLAY] ?: true,
            overlayColorHex = prefs[Keys.OVERLAY_COLOR_HEX] ?: "#E6000000",
            subtitleFontSizeSp = prefs[Keys.SUBTITLE_FONT_SIZE] ?: 18,
            subtitleTextColorHex = prefs[Keys.SUBTITLE_TEXT_COLOR] ?: "#FFFFFFFF",
            glossaryRaw = glossaryString,
            glossaryTerms = glossaryMap,
            filterWatermarks = prefs[Keys.FILTER_WATERMARKS] ?: true,
            ignoredTermsRaw = ignoredTermsString,
            ignoredTerms = parsedIgnoredTerms,
            translationEngine = prefs[Keys.TRANSLATION_ENGINE] ?: "gemini",
            geminiApiKey = apiKeyCipher.decryptIfEncrypted(prefs[Keys.GEMINI_API_KEY] ?: ""),
            geminiModel = prefs[Keys.GEMINI_MODEL] ?: AppSettings.DEFAULT_GEMINI_MODEL,
            autoFallbackToMlKit = prefs[Keys.AUTO_FALLBACK_TO_MLKIT] ?: true,
            enableHighContrastOcr = prefs[Keys.ENABLE_HIGH_CONTRAST_OCR] ?: true,
            enableOtsuOcr = prefs[Keys.ENABLE_OTSU_OCR] ?: true,
            enableFullFrameFallbackOcr = prefs[Keys.ENABLE_FULL_FRAME_FALLBACK_OCR] ?: true
        )
    }

    suspend fun updateFps(fps: Int) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_FPS] = fps }
    }

    suspend fun updateCoverOriginal(cover: Boolean) {
        context.settingsDataStore.edit { it[Keys.COVER_ORIGINAL_OVERLAY] = cover }
    }

    suspend fun updateFontSize(sizeSp: Int) {
        context.settingsDataStore.edit { it[Keys.SUBTITLE_FONT_SIZE] = sizeSp }
    }

    suspend fun updateMaxCharsPerLine(maxChars: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_CHARS_PER_LINE] = maxChars.coerceIn(20, 80) }
    }

    suspend fun updateGlossary(rawText: String) {
        context.settingsDataStore.edit { it[Keys.GLOSSARY_RAW] = rawText }
    }

    suspend fun updateFilterWatermarks(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FILTER_WATERMARKS] = enabled }
    }

    suspend fun updateIgnoredTerms(rawText: String) {
        context.settingsDataStore.edit { it[Keys.IGNORED_TERMS_RAW] = rawText }
    }

    suspend fun updateMinSpeechDuration(durationMs: Long) {
        context.settingsDataStore.edit { it[Keys.MIN_SPEECH_DURATION] = durationMs.coerceIn(100L, 5000L).toInt() }
    }

    suspend fun updateSimilarityThreshold(threshold: Float) {
        context.settingsDataStore.edit { it[Keys.SIMILARITY_THRESHOLD] = threshold.coerceIn(0.50f, 0.98f) }
    }

    suspend fun updateTranslationEngine(engine: String) {
        context.settingsDataStore.edit { it[Keys.TRANSLATION_ENGINE] = engine }
    }

    suspend fun updateGeminiApiKey(apiKey: String) {
        val cleanKey = apiKey.trim()
        val stored = if (cleanKey.isBlank()) "" else apiKeyCipher.encrypt(cleanKey)
        context.settingsDataStore.edit { it[Keys.GEMINI_API_KEY] = stored }
    }

    suspend fun updateGeminiModel(model: String) {
        context.settingsDataStore.edit { it[Keys.GEMINI_MODEL] = model.trim() }
    }

    suspend fun updateAutoFallbackToMlKit(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_FALLBACK_TO_MLKIT] = enabled }
    }

    suspend fun updateEnableHighContrastOcr(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ENABLE_HIGH_CONTRAST_OCR] = enabled }
    }

    suspend fun updateEnableOtsuOcr(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ENABLE_OTSU_OCR] = enabled }
    }

    suspend fun updateEnableFullFrameFallbackOcr(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ENABLE_FULL_FRAME_FALLBACK_OCR] = enabled }
    }

    companion object {
        fun parseGlossary(raw: String): Map<String, String> {
            if (raw.isBlank()) return emptyMap()
            val result = mutableMapOf<String, String>()
            val lines = raw.lines()

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                // Check for "->" first, then "="
                val separatorIndex = when {
                    trimmed.contains("->") -> trimmed.indexOf("->")
                    trimmed.contains("=") -> trimmed.indexOf("=")
                    else -> -1
                }

                if (separatorIndex != -1) {
                    val sepLength = if (trimmed.substring(separatorIndex).startsWith("->")) 2 else 1
                    val source = trimmed.substring(0, separatorIndex).trim()
                    val target = trimmed.substring(separatorIndex + sepLength).trim()
                    if (source.isNotEmpty()) {
                        result[source] = target
                    }
                }
            }
            return result
        }
    }
}
