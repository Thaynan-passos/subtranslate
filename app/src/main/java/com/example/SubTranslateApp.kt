package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import com.example.data.service.SubtitleExtractionService
import com.example.di.AppContainer

class SubTranslateApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE &&
            SubtitleExtractionService.activeProjectId == null
        ) {
            container.closeEngines()
        }
    }
}
