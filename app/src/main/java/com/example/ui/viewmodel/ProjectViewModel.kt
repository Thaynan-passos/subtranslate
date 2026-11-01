package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.data.service.SubtitleExtractionService
import com.example.domain.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectViewModel(
    private val context: Context? = null,
    private val projectRepository: ProjectRepository,
    private val extractionPipeline: SubtitleExtractionPipeline? = null
) : ViewModel() {

    val projects: StateFlow<List<Project>> = projectRepository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(id)
            if (SubtitleExtractionService.activeProjectId == id ||
                extractionPipeline?.getPipelineState(id)?.value?.isRunning == true
            ) {
                extractionPipeline?.cancel(id)
            }
            projectRepository.deleteProject(id)

            // Release persistable URI permission if no other project references this video URI
            if (context != null && project != null && project.videoUri.isNotBlank()) {
                val stillUsed = projects.value.any { it.id != id && it.videoUri == project.videoUri }
                if (!stillUsed) {
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(project.videoUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                        // Ignore if permission was not persisted or already released
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: ProjectRepository,
        private val context: Context? = null,
        private val extractionPipeline: SubtitleExtractionPipeline? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectViewModel(
                context = context?.applicationContext,
                projectRepository = repository,
                extractionPipeline = extractionPipeline
            ) as T
        }
    }
}
