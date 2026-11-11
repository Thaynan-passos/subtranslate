package com.example.di

import android.content.Context
import com.example.data.engine.GeminiAudioTranscriber
import com.example.data.engine.GeminiTranslatorImpl
import com.example.data.engine.MlKitOcrEngine
import com.example.data.engine.MlKitTranslatorImpl
import com.example.data.engine.SmartHybridTranslator
import com.example.data.local.SubTranslateDatabase
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.SpeechTranscriber
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppContainer(val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SubTranslateDatabase by lazy {
        SubTranslateDatabase.getDatabase(context)
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.subtitleLineDao())
    }

    val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepository(database.subtitleLineDao(), database.translationCacheDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context)
    }

    private val _currentSettings = MutableStateFlow(AppSettings())
    val currentSettings = _currentSettings.asStateFlow()

    init {
        applicationScope.launch {
            settingsRepository.settings.collect { settings ->
                _currentSettings.value = settings
            }
        }
    }

    private val ocrEngineInstance = lazy {
        MlKitOcrEngine()
    }

    val ocrEngine: OcrEngine by ocrEngineInstance

    private val translatorInstance = lazy {
        val mlKit = MlKitTranslatorImpl(subtitleRepository)
        val gemini = GeminiTranslatorImpl(
            repository = subtitleRepository,
            apiKeyProvider = { _currentSettings.value.geminiApiKey },
            modelProvider = { _currentSettings.value.geminiModel }
        )
        SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = gemini,
            settingsProvider = { _currentSettings.value }
        )
    }

    val translator: Translator by translatorInstance

    val speechTranscriber: SpeechTranscriber by lazy {
        GeminiAudioTranscriber(
            context = context,
            apiKeyProvider = { _currentSettings.value.geminiApiKey },
            modelProvider = { _currentSettings.value.geminiModel }
        )
    }

    fun closeEngines() {
        if (ocrEngineInstance.isInitialized()) ocrEngine.close()
        if (translatorInstance.isInitialized()) translator.close()
    }

    val extractionPipeline: SubtitleExtractionPipeline by lazy {
        SubtitleExtractionPipeline(
            context = context,
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = ocrEngine,
            translator = translator,
            speechTranscriber = speechTranscriber
        )
    }
}
