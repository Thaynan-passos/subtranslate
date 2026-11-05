package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SubtitleRepository
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.domain.util.ManualTranslationFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ManualTranslationUiState(
    val isImporting: Boolean = false,
    val userMessage: String? = null
)

/**
 * Backs the "Extrair Texto" result screen: exports the OCR text as an editable file and imports
 * the user's translated file back into the project's subtitle lines.
 */
class ManualTranslationViewModel(
    private val projectId: Long,
    projectRepository: ProjectRepository,
    private val subtitleRepository: SubtitleRepository
) : ViewModel() {

    val project: StateFlow<Project?> = projectRepository.observeProjectById(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val subtitleLines: StateFlow<List<SubtitleLine>> = subtitleRepository.getLines(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(ManualTranslationUiState())
    val uiState: StateFlow<ManualTranslationUiState> = _uiState.asStateFlow()

    /** Builds the editable file content from the current lines. */
    fun buildExportContent(): String {
        val proj = project.value
        return ManualTranslationFile.export(
            lines = subtitleLines.value,
            title = proj?.title ?: "legendas",
            sourceLanguage = proj?.sourceLanguage ?: "",
            targetLanguage = proj?.targetLanguage ?: ""
        )
    }

    /** Parses [content] (txt with markers, srt/vtt or plain lines) and saves the translations. */
    fun importTranslation(content: String) {
        if (_uiState.value.isImporting) return
        _uiState.value = _uiState.value.copy(isImporting = true)
        viewModelScope.launch {
            val message = try {
                val lines = subtitleRepository.getLinesList(projectId)
                val parsed = ManualTranslationFile.parse(content)
                if (parsed.translations.isEmpty()) {
                    "Nenhuma tradução encontrada no arquivo. Verifique se os marcadores [1], [2]... foram mantidos."
                } else {
                    val result = ManualTranslationFile.apply(lines, parsed.translations)
                    val changed = result.updatedLines.filterIndexed { i, line -> line != lines[i] }
                    if (changed.isNotEmpty()) subtitleRepository.updateLines(changed)

                    buildString {
                        append("${result.appliedCount} de ${lines.size} frases importadas.")
                        if (result.missingCount > 0) {
                            append(" ${result.missingCount} sem tradução no arquivo.")
                        }
                        if (result.ignoredCount > 0) {
                            append(" ${result.ignoredCount} números não correspondem a nenhuma frase.")
                        }
                        if (parsed.format == ManualTranslationFile.ImportFormat.PLAIN_LINES) {
                            append(" (Sem marcadores: frases associadas pela ordem das linhas.)")
                        }
                    }
                }
            } catch (e: Exception) {
                "Erro ao importar tradução: ${e.localizedMessage ?: "formato inválido"}"
            }
            _uiState.value = _uiState.value.copy(isImporting = false, userMessage = message)
        }
    }

    fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(userMessage = message)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    class Factory(
        private val projectId: Long,
        private val projectRepository: ProjectRepository,
        private val subtitleRepository: SubtitleRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ManualTranslationViewModel(projectId, projectRepository, subtitleRepository) as T
        }
    }
}
