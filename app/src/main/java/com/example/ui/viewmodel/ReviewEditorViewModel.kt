package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewEditorUiState(
    val selectedLineId: Long? = null,
    val isCoverOverlayActive: Boolean = true,
    val isRetranslating: Boolean = false,
    val userMessage: String? = null
)

class ReviewEditorViewModel(
    private val projectId: Long,
    private val projectRepository: ProjectRepository,
    private val subtitleRepository: SubtitleRepository,
    private val settingsRepository: SettingsRepository,
    private val translator: Translator
) : ViewModel() {

    val project: StateFlow<Project?> = projectRepository.observeProjectById(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val subtitleLines: StateFlow<List<SubtitleLine>> = subtitleRepository.getLines(projectId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private val _uiState = MutableStateFlow(ReviewEditorUiState())
    val uiState: StateFlow<ReviewEditorUiState> = _uiState.asStateFlow()

    init {
        // Initialize cover overlay setting from user preferences
        viewModelScope.launch {
            val appSettings = settingsRepository.settings.first()
            _uiState.value = _uiState.value.copy(
                isCoverOverlayActive = appSettings.coverOriginalOverlay
            )
        }
    }

    fun selectLine(lineId: Long?) {
        _uiState.value = _uiState.value.copy(selectedLineId = lineId)
    }

    fun toggleCoverOverlay(active: Boolean) {
        _uiState.value = _uiState.value.copy(isCoverOverlayActive = active)
    }

    fun clearUserMessage() {
        _uiState.value = _uiState.value.copy(userMessage = null)
    }

    fun updateLineText(lineId: Long, original: String, translated: String) {
        viewModelScope.launch {
            subtitleRepository.updateLineText(lineId, original.trim(), translated.trim())
        }
    }

    fun updateLineTimestamps(lineId: Long, startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val rawDuration = project.value?.durationMs ?: 0L
            val validStart = startMs.coerceIn(0L, Long.MAX_VALUE - 2_000L).let { start ->
                if (rawDuration > 0L) start.coerceAtMost(rawDuration) else start
            }
            val minimumEnd = validStart + 100L
            val requestedEnd = endMs.coerceAtLeast(minimumEnd)
            val validEnd = if (rawDuration > 0L) {
                requestedEnd.coerceAtMost(rawDuration.coerceAtLeast(minimumEnd))
            } else {
                requestedEnd
            }

            subtitleRepository.updateLineTimestamps(lineId, validStart, validEnd)
        }
    }

    fun deleteLine(lineId: Long) {
        viewModelScope.launch {
            subtitleRepository.deleteLine(lineId)
            if (_uiState.value.selectedLineId == lineId) {
                _uiState.value = _uiState.value.copy(selectedLineId = null)
            }
            reindexLines()
        }
    }

    fun mergeWithNext(lineId: Long) {
        viewModelScope.launch {
            val lines = subtitleLines.value
            val currentIndex = lines.indexOfFirst { it.id == lineId }
            if (currentIndex >= 0 && currentIndex < lines.size - 1) {
                val current = lines[currentIndex]
                val next = lines[currentIndex + 1]

                val merged = current.copy(
                    endTimeMs = next.endTimeMs,
                    originalText = "${current.originalText} ${next.originalText}".trim(),
                    translatedText = "${current.translatedText} ${next.translatedText}".trim()
                )
                subtitleRepository.updateLine(merged)
                subtitleRepository.deleteLine(next.id)
                reindexLines()
            }
        }
    }

    fun splitLine(lineId: Long, splitTimeMs: Long) {
        viewModelScope.launch {
            val lines = subtitleLines.value
            val current = lines.firstOrNull { it.id == lineId } ?: return@launch
            if (splitTimeMs <= current.startTimeMs || splitTimeMs >= current.endTimeMs) {
                _uiState.value = _uiState.value.copy(userMessage = "O tempo de divisão deve estar entre o início e o fim da fala.")
                return@launch
            }

            val totalDuration = (current.endTimeMs - current.startTimeMs).toFloat()
            val firstRatio = ((splitTimeMs - current.startTimeMs) / totalDuration).coerceIn(0.1f, 0.9f)

            // Split original and translated text proportionately by words
            val (origPart1, origPart2) = splitTextByRatio(current.originalText, firstRatio)
            val (transPart1, transPart2) = splitTextByRatio(current.translatedText, firstRatio)

            val firstHalf = current.copy(
                endTimeMs = splitTimeMs,
                originalText = origPart1,
                translatedText = transPart1
            )
            val secondHalf = SubtitleLine(
                id = 0,
                projectId = current.projectId,
                index = current.index + 1,
                startTimeMs = splitTimeMs,
                endTimeMs = current.endTimeMs,
                originalText = origPart2,
                translatedText = transPart2,
                confidence = current.confidence,
                isFlagged = current.isFlagged
            )

            subtitleRepository.updateLine(firstHalf)
            subtitleRepository.saveLine(secondHalf)
            reindexLines()
        }
    }

    fun retranslateLine(lineId: Long) {
        val proj = project.value ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRetranslating = true)
            try {
                val lines = subtitleLines.value
                val current = lines.firstOrNull { it.id == lineId }
                if (current != null) {
                    val currentSettings = settings.value
                    val newTranslation = translator.translate(
                        text = current.originalText,
                        sourceLanguage = proj.sourceLanguage,
                        targetLanguage = proj.targetLanguage,
                        glossary = currentSettings.glossaryTerms,
                        bypassCache = true
                    )
                    subtitleRepository.updateLine(
                        current.copy(translatedText = newTranslation, isFlagged = false)
                    )
                    _uiState.value = _uiState.value.copy(userMessage = "Fala retraduzida com sucesso!")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(userMessage = "Erro ao retraduzir: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isRetranslating = false)
            }
        }
    }

    fun addManualLine(startTimeMs: Long) {
        viewModelScope.launch {
            val lines = subtitleLines.value
            val rawDuration = project.value?.durationMs ?: 0L
            val safeStart = startTimeMs.coerceIn(0L, Long.MAX_VALUE - 2_000L).let { start ->
                if (rawDuration > 0L) start.coerceAtMost(rawDuration) else start
            }
            val minimumEnd = safeStart + 500L
            val preferredEnd = safeStart + 2000L
            val safeEnd = if (rawDuration > 0L) {
                preferredEnd.coerceAtMost(rawDuration.coerceAtLeast(minimumEnd)).coerceAtLeast(minimumEnd)
            } else {
                preferredEnd.coerceAtLeast(minimumEnd)
            }

            val newLine = SubtitleLine(
                id = 0,
                projectId = projectId,
                index = lines.size + 1,
                startTimeMs = safeStart,
                endTimeMs = safeEnd,
                originalText = "Novo diálogo",
                translatedText = "Novo diálogo",
                confidence = 1.0f,
                isFlagged = false
            )
            val newId = subtitleRepository.saveLine(newLine)
            _uiState.value = _uiState.value.copy(selectedLineId = newId)
            reindexLines()
        }
    }

    private suspend fun reindexLines() {
        val currentLines = subtitleRepository.getLinesList(projectId)
        val sorted = currentLines.sortedBy { it.startTimeMs }
        val reindexed = sorted.mapIndexed { idx, line ->
            if (line.index == idx + 1) line else line.copy(index = idx + 1)
        }
        if (reindexed != sorted) subtitleRepository.updateLines(reindexed)
    }

    companion object {
        fun splitTextByRatio(text: String, ratio: Float): Pair<String, String> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return Pair("", "")
            val words = trimmed.split("\\s+".toRegex())
            if (words.size > 1) {
                val splitPoint = (words.size * ratio).toInt().coerceIn(1, words.size - 1)
                return Pair(
                    words.subList(0, splitPoint).joinToString(" "),
                    words.subList(splitPoint, words.size).joinToString(" ")
                )
            }

            val codePointCount = trimmed.codePointCount(0, trimmed.length)
            if (codePointCount <= 1) return Pair(trimmed, "")
            val splitAtCodePoint = (codePointCount * ratio).toInt().coerceIn(1, codePointCount - 1)
            val splitOffset = trimmed.offsetByCodePoints(0, splitAtCodePoint)
            return Pair(trimmed.substring(0, splitOffset), trimmed.substring(splitOffset))
        }
    }

    class Factory(
        private val projectId: Long,
        private val projectRepository: ProjectRepository,
        private val subtitleRepository: SubtitleRepository,
        private val settingsRepository: SettingsRepository,
        private val translator: Translator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReviewEditorViewModel(
                projectId,
                projectRepository,
                subtitleRepository,
                settingsRepository,
                translator
            ) as T
        }
    }
}
