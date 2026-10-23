package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.pipeline.PipelineState
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.example.data.service.SubtitleExtractionService
import kotlinx.coroutines.launch

class ProcessingViewModel(
    private val projectId: Long,
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val pipeline: SubtitleExtractionPipeline
) : ViewModel() {

    val pipelineState: StateFlow<PipelineState> = pipeline.getPipelineState(projectId)

    val project: StateFlow<Project?> = projectRepository.observeProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun startProcessing(appSettings: AppSettings = AppSettings()) {
        viewModelScope.launch {
            val proj = projectRepository.getProjectById(projectId) ?: return@launch
            SubtitleExtractionService.start(context, proj.id, proj.title)
        }
    }

    fun pause() {
        pipeline.pause(projectId)
    }

    fun resume() {
        pipeline.resume(projectId)
    }

    suspend fun cancel() {
        pipeline.cancel(projectId)
    }

    class Factory(
        private val projectId: Long,
        private val context: Context,
        private val projectRepository: ProjectRepository,
        private val pipeline: SubtitleExtractionPipeline
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProcessingViewModel(
                projectId,
                context.applicationContext,
                projectRepository,
                pipeline
            ) as T
        }
    }
}
