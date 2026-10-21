package com.example.di

import android.content.Context
import com.example.data.engine.MlKitOcrEngine
import com.example.data.engine.MlKitTranslatorImpl
import com.example.data.local.SubTranslateDatabase
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SubtitleRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.Translator

class AppContainer(val context: Context) {

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

    val ocrEngine: OcrEngine by lazy {
        MlKitOcrEngine()
    }

    val translator: Translator by lazy {
        MlKitTranslatorImpl(subtitleRepository)
    }

    val extractionPipeline: SubtitleExtractionPipeline by lazy {
        SubtitleExtractionPipeline(
            context = context,
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = ocrEngine,
            translator = translator
        )
    }
}
