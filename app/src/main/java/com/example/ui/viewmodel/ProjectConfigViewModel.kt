package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.domain.engine.OcrEngine
import com.example.domain.model.OutputMode
import com.example.domain.model.Project
import com.example.domain.model.RegionOfInterest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProjectConfigUiState(
    val videoUri: Uri? = null,
    val videoName: String = "",
    val durationMs: Long = 0,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val previewFrame: Bitmap? = null,
    val roi: RegionOfInterest = RegionOfInterest(left = 0.05f, top = 0.72f, right = 0.95f, bottom = 0.95f),
    val sourceLang: String = "en",
    val targetLang: String = "pt",
    val samplingFps: Int = 2,
    val outputMode: OutputMode = OutputMode.SRT_ONLY,
    val isAutoDetectingRoi: Boolean = false,
    val isCreatingProject: Boolean = false,
    val error: String? = null
)

class ProjectConfigViewModel(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val ocrEngine: OcrEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectConfigUiState())
    val uiState: StateFlow<ProjectConfigUiState> = _uiState.asStateFlow()
    private var videoLoadJob: Job? = null
    private var roiDetectionJob: Job? = null
    @Volatile
    private var videoLoadGeneration = 0L

    fun resetState() {
        videoLoadGeneration++
        videoLoadJob?.cancel()
        roiDetectionJob?.cancel()
        _uiState.value.previewFrame?.takeUnless { it.isRecycled }?.recycle()
        _uiState.value = ProjectConfigUiState()
    }

    fun setVideo(uri: Uri, name: String) {
        val requestGeneration = ++videoLoadGeneration
        videoLoadJob?.cancel()
        roiDetectionJob?.cancel()
        _uiState.value.previewFrame?.takeUnless { it.isRecycled }?.recycle()
        _uiState.value = _uiState.value.copy(
            videoUri = null,
            videoName = "",
            durationMs = 0L,
            previewFrame = null,
            isAutoDetectingRoi = false,
            error = null
        )
        videoLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)

                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (duration <= 0L) {
                    _uiState.update { state ->
                        if (requestGeneration == videoLoadGeneration) {
                            state.copy(
                                videoUri = null,
                                videoName = "",
                                previewFrame = null,
                                durationMs = 0L,
                                error = "O vídeo selecionado possui duração inválida (0s). Escolha outro arquivo de vídeo."
                            )
                        } else {
                            state
                        }
                    }
                    return@launch
                }

                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
                val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080

                val width = if (rotation == 90 || rotation == 270) rawH else rawW
                val height = if (rotation == 90 || rotation == 270) rawW else rawH

                // Sample a representative frame at 15% of duration
                val sampleTime = (duration * 0.15f * 1000).toLong().coerceAtLeast(1_000_000L)
                val frame = retriever.getFrameAtTime(sampleTime, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)

                if (!isActive || requestGeneration != videoLoadGeneration) {
                    frame?.takeUnless { it.isRecycled }?.recycle()
                    return@launch
                }
                var applied = false
                _uiState.update { state ->
                    if (requestGeneration == videoLoadGeneration) {
                        applied = true
                        state.copy(
                            videoUri = uri,
                            videoName = name,
                            durationMs = duration,
                            videoWidth = width,
                            videoHeight = height,
                            previewFrame = frame,
                            error = null
                        )
                    } else {
                        state
                    }
                }
                if (!applied) {
                    frame?.takeUnless { it.isRecycled }?.recycle()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isActive) {
                    _uiState.update { state ->
                        if (requestGeneration == videoLoadGeneration) {
                            state.copy(
                                videoUri = null,
                                videoName = "",
                                durationMs = 0L,
                                previewFrame = null,
                                error = "Não foi possível carregar o vídeo selecionado: ${e.message}"
                            )
                        } else {
                            state
                        }
                    }
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun updateRoi(left: Float, top: Float, right: Float, bottom: Float) {
        _uiState.value = _uiState.value.copy(
            roi = normalizeRoi(left, top, right, bottom, _uiState.value.roi)
        )
    }

    fun autoDetectRoi() {
        if (_uiState.value.isAutoDetectingRoi) return
        val uri = _uiState.value.videoUri ?: return
        val duration = _uiState.value.durationMs
        val sourceLanguage = _uiState.value.sourceLang
        _uiState.value = _uiState.value.copy(error = null)
        _uiState.value = _uiState.value.copy(isAutoDetectingRoi = true)

        roiDetectionJob = viewModelScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)

                var detectedTopSum = 0f
                var detectedBottomSum = 0f
                var sampleCount = 0

                val samplePoints = listOf(0.15f, 0.30f, 0.45f, 0.60f, 0.75f)
                for (ratio in samplePoints) {
                    val timeUs = (duration * ratio.toDouble() * 1000.0).toLong()
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (frame != null) {
                        try {
                            val ocrRes = try {
                                ocrEngine.recognizeText(frame, sourceLanguage)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }
                            if (ocrRes == null) continue
                            val candidateBoxes = ocrRes.blocks.mapNotNull { it.boundingBox }

                            if (candidateBoxes.isNotEmpty()) {
                                val frameH = frame.height.toFloat()
                                val minTop = candidateBoxes.minOf { it.top } / frameH
                                val maxBottom = candidateBoxes.maxOf { it.bottom } / frameH

                                if (maxBottom > minTop && minTop > 0.40f) {
                                    detectedTopSum += minTop
                                    detectedBottomSum += maxBottom
                                    sampleCount++
                                }
                            }
                        } finally {
                            if (!frame.isRecycled) frame.recycle()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = if (sampleCount > 0) {
                        _uiState.value.copy(
                            roi = calculateAutoDetectedRoi(detectedTopSum, detectedBottomSum, sampleCount),
                            isAutoDetectingRoi = false,
                            error = null
                        )
                    } else {
                        _uiState.value.copy(
                            isAutoDetectingRoi = false,
                            error = "Não foi possível detectar legendas nas amostras. A região atual foi mantida."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isAutoDetectingRoi = false,
                        error = "Não foi possível detectar a região da legenda: ${e.message}"
                    )
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun setLanguages(source: String, target: String) {
        if (source != target) {
            _uiState.value = _uiState.value.copy(sourceLang = source, targetLang = target)
        }
    }

    fun setSamplingFps(fps: Int) {
        _uiState.value = _uiState.value.copy(samplingFps = fps)
    }

    fun setOutputMode(mode: OutputMode) {
        _uiState.value = _uiState.value.copy(outputMode = mode)
    }

    suspend fun createProject(): Long? {
        if (_uiState.value.isCreatingProject) return null
        val state = _uiState.value
        if (state.videoUri == null || state.durationMs <= 0L) {
            _uiState.value = state.copy(error = "Vídeo inválido ou sem duração detectada.")
            return null
        }
        _uiState.value = state.copy(isCreatingProject = true, error = null)
        return try {
            val project = Project(
                id = 0,
                title = state.videoName.ifBlank { "Vídeo Legendas" },
                videoUri = state.videoUri.toString(),
                durationMs = state.durationMs,
                videoWidth = state.videoWidth,
                videoHeight = state.videoHeight,
                sourceLanguage = state.sourceLang,
                targetLanguage = state.targetLang,
                samplingFps = state.samplingFps,
                outputMode = state.outputMode,
                roiLeft = state.roi.left,
                roiTop = state.roi.top,
                roiRight = state.roi.right,
                roiBottom = state.roi.bottom
            )
            projectRepository.saveProject(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "Não foi possível criar o projeto: ${e.localizedMessage ?: "erro desconhecido"}"
            )
            null
        } finally {
            _uiState.value = _uiState.value.copy(isCreatingProject = false)
        }
    }

    companion object {
        internal fun normalizeRoi(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            currentRoi: RegionOfInterest
        ): RegionOfInterest {
            val coercedLeft = (left.takeIf { it.isFinite() } ?: currentRoi.left).coerceIn(0.0f, 0.85f)
            val coercedTop = (top.takeIf { it.isFinite() } ?: currentRoi.top).coerceIn(0.0f, 0.85f)
            val coercedRight = (right.takeIf { it.isFinite() } ?: currentRoi.right)
                .coerceIn(coercedLeft + 0.05f, 1.0f)
            val coercedBottom = (bottom.takeIf { it.isFinite() } ?: currentRoi.bottom)
                .coerceIn(coercedTop + 0.05f, 1.0f)

            return RegionOfInterest(
                left = coercedLeft,
                top = coercedTop,
                right = coercedRight,
                bottom = coercedBottom
            )
        }

        fun calculateAutoDetectedRoi(
            detectedTopSum: Float,
            detectedBottomSum: Float,
            sampleCount: Int
        ): RegionOfInterest {
            if (sampleCount <= 0) {
                return RegionOfInterest(left = 0.05f, top = 0.70f, right = 0.95f, bottom = 0.95f)
            }
            val avgTop = (detectedTopSum / sampleCount) - 0.04f
            val avgBottom = (detectedBottomSum / sampleCount) + 0.04f
            val top = avgTop.coerceIn(0.1f, 0.85f)
            val bottom = avgBottom.coerceIn(top + 0.08f, 0.98f)

            return if (bottom > top + 0.05f) {
                RegionOfInterest(left = 0.05f, top = top, right = 0.95f, bottom = bottom)
            } else {
                RegionOfInterest(left = 0.05f, top = 0.70f, right = 0.95f, bottom = 0.95f)
            }
        }
    }

    class Factory(
        private val context: Context,
        private val repository: ProjectRepository,
        private val ocrEngine: OcrEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ProjectConfigViewModel(context.applicationContext, repository, ocrEngine) as T
        }
    }

    override fun onCleared() {
        videoLoadJob?.cancel()
        roiDetectionJob?.cancel()
        _uiState.value.previewFrame?.takeUnless { it.isRecycled }?.recycle()
        super.onCleared()
    }
}
