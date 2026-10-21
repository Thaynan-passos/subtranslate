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
import com.example.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtranslate_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val OCR_ENGINE = stringPreferencesKey("ocr_engine")
        val TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        val DEFAULT_FPS = intPreferencesKey("default_fps")
        val MIN_SPEECH_DURATION = intPreferencesKey("min_speech_duration")
        val SIMILARITY_THRESHOLD = floatPreferencesKey("similarity_threshold")
        val MAX_CHARS_PER_LINE = intPreferencesKey("max_chars_per_line")
        val COVER_ORIGINAL_OVERLAY = booleanPreferencesKey("cover_original_overlay")
        val OVERLAY_COLOR_HEX = stringPreferencesKey("overlay_color_hex")
        val SUBTITLE_FONT_SIZE = intPreferencesKey("subtitle_font_size")
        val SUBTITLE_TEXT_COLOR = stringPreferencesKey("subtitle_text_color")
        val GLOSSARY_RAW = stringPreferencesKey("glossary_raw")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val glossaryString = prefs[Keys.GLOSSARY_RAW] ?: ""
        val glossaryMap = parseGlossary(glossaryString)

        AppSettings(
            ocrEngine = prefs[Keys.OCR_ENGINE] ?: "ML_KIT",
            translationEngine = prefs[Keys.TRANSLATION_ENGINE] ?: "ML_KIT",
            defaultFps = prefs[Keys.DEFAULT_FPS] ?: 3,
            minSpeechDurationMs = (prefs[Keys.MIN_SPEECH_DURATION] ?: 300).toLong(),
            textSimilarityThreshold = prefs[Keys.SIMILARITY_THRESHOLD] ?: 0.85f,
            maxCharsPerLine = prefs[Keys.MAX_CHARS_PER_LINE] ?: 42,
            coverOriginalOverlay = prefs[Keys.COVER_ORIGINAL_OVERLAY] ?: true,
            overlayColorHex = prefs[Keys.OVERLAY_COLOR_HEX] ?: "#E6000000",
            subtitleFontSizeSp = prefs[Keys.SUBTITLE_FONT_SIZE] ?: 18,
            subtitleTextColorHex = prefs[Keys.SUBTITLE_TEXT_COLOR] ?: "#FFFFFFFF",
            glossaryRaw = glossaryString,
            glossaryTerms = glossaryMap
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

    suspend fun updateGlossary(rawText: String) {
        context.settingsDataStore.edit { it[Keys.GLOSSARY_RAW] = rawText }
    }

    suspend fun updateEngine(ocr: String, translator: String) {
        context.settingsDataStore.edit {
            it[Keys.OCR_ENGINE] = ocr
            it[Keys.TRANSLATION_ENGINE] = translator
        }
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
