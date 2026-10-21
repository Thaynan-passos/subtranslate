package com.example.data.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SubtitleRepository
import com.example.data.service.SubtitleExtractionService
import com.example.data.util.NotificationHelper
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.domain.model.RawFrameDetection
import com.example.domain.model.SubtitleLine
import com.example.domain.util.ImagePreprocessor
import com.example.domain.util.TemporalSpeechGrouper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class PipelineState(
    val step: String = "",
    val progress: Float = 0f,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val extractedLinesCount: Int = 0,
    val currentSampleTimeMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val errorMessage: String? = null,
    val failedTranslationsCount: Int = 0
)

class SubtitleExtractionPipeline(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val subtitleRepository: SubtitleRepository,
    private val ocrEngine: OcrEngine,
    private val translator: Translator
) {

    // Separate state flows for active observers
    private val stateFlows = ConcurrentHashMap<Long, MutableStateFlow<PipelineState>>()

    // Project mutexes to prevent concurrent execution races
    private val projectMutexes = ConcurrentHashMap<Long, Mutex>()

    private val cancelledProjects = ConcurrentHashMap.newKeySet<Long>()
    private val pausedProjects = ConcurrentHashMap.newKeySet<Long>()

    init {
        NotificationHelper.createNotificationChannel(context)
    }

    fun getPipelineState(projectId: Long): StateFlow<PipelineState> {
        return stateFlows.getOrPut(projectId) {
            MutableStateFlow(PipelineState())
        }.asStateFlow()
    }

    private fun updateState(projectId: Long, update: (PipelineState) -> PipelineState) {
        val flow = stateFlows.getOrPut(projectId) { MutableStateFlow(PipelineState()) }
        val newState = update(flow.value)
        flow.value = newState
    }

    fun pause(projectId: Long) {
        pausedProjects.add(projectId)
        updateState(projectId) { it.copy(isPaused = true) }
    }

    fun resume(projectId: Long) {
        pausedProjects.remove(projectId)
        updateState(projectId) { it.copy(isPaused = false) }
    }

    fun cancel(projectId: Long) {
        cancelledProjects.add(projectId)
        updateState(projectId) { it.copy(isRunning = false) }
        NotificationHelper.cancelNotification(context)
        SubtitleExtractionService.stop(context)
    }

    suspend fun execute(
        project: Project,
        appSettings: AppSettings = AppSettings()
    ): Result<List<SubtitleLine>> = withContext(Dispatchers.Default) {
        val mutex = projectMutexes.getOrPut(project.id) { Mutex() }

        if (!mutex.tryLock()) {
            return@withContext Result.failure(IllegalStateException("Projeto já está em processamento."))
        }

        try {
            cancelledProjects.remove(project.id)
            pausedProjects.remove(project.id)

            updateState(project.id) {
                PipelineState(
                    step = "Verificando modelo de tradução offline...",
                    progress = 0.02f,
                    isRunning = true,
                    totalDurationMs = project.durationMs
                )
            }
            projectRepository.updateProgress(
                project.id,
                ProjectStatus.PROCESSING.name,
                0.02f,
                "Verificando modelo de tradução"
            )
            val downloadSuccess = try {
                translator.downloadModelIfNeeded(
                    sourceLanguage = project.sourceLanguage,
                    targetLanguage = project.targetLanguage,
                    requireWifi = false,
                    onProgress = { downloadProgress ->
                        updateState(project.id) { it.copy(progress = 0.02f + (0.03f * downloadProgress)) }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!downloadSuccess) {
                val downloadError = "Não foi possível baixar o modelo de tradução. Verifique a conexão e tente novamente."
                projectRepository.updateProgress(project.id, ProjectStatus.ERROR.name, 0f, downloadError)
                updateState(project.id) {
                    PipelineState(
                        step = "Erro ao baixar modelo de tradução",
                        progress = 0f,
                        isRunning = false,
                        errorMessage = downloadError
                    )
                }
                NotificationHelper.cancelNotification(context)
                return@withContext Result.failure(IllegalStateException(downloadError))
            }

            val retriever = MediaMetadataRetriever()
            try {
                val videoUri = Uri.parse(project.videoUri)
                retriever.setDataSource(context, videoUri)

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: project.durationMs
                if (durationMs <= 0L) {
                    throw IllegalStateException("O vídeo selecionado possui duração inválida (0s).")
                }

                updateState(project.id) {
                    PipelineState(
                        step = "Preparando vídeo e otimização...",
                        progress = 0.05f,
                        isRunning = true,
                        totalDurationMs = durationMs
                    )
                }
                projectRepository.updateProgress(project.id, ProjectStatus.PROCESSING.name, 0.05f, "Inicializando")
                NotificationHelper.updateProgressNotification(context, project.title, "Preparando extração...", 5)

                val fps = project.samplingFps.coerceIn(1, 10)
                val intervalMs = (1000L / fps).coerceAtLeast(100L)

                // Step 1: Frame Extraction & OCR with 64x16 thumbnail MAE diffing and emptiness detection
                val rawDetections = mutableListOf<RawFrameDetection>()
                var lastThumb: IntArray? = null
                var lastOcrText = ""
                var lastConfidence = 0f

                var currentTimeMs = 0L
                val totalSteps = (durationMs / intervalMs).coerceAtLeast(1L)
                var stepIndex = 0L

                updateState(project.id) {
                    it.copy(
                        step = "Extraindo frames e detectando legendas (OCR)...",
                        progress = 0.1f
                    )
                }

                while (currentTimeMs < durationMs) {
                    if (cancelledProjects.contains(project.id)) {
                        throw CancellationException("Operação cancelada pelo usuário")
                    }
                    while (pausedProjects.contains(project.id)) {
                        kotlinx.coroutines.delay(200)
                        if (cancelledProjects.contains(project.id)) {
                            throw CancellationException("Operação cancelada pelo usuário")
                        }
                    }

                    val frameMicros = currentTimeMs * 1000L
                    val rawFrame = retriever.getFrameAtTime(frameMicros, MediaMetadataRetriever.OPTION_CLOSEST)

                    if (rawFrame != null) {
                        var croppedBitmap: Bitmap? = null
                        var enhancedBitmap: Bitmap? = null
                        try {
                            val frameW = rawFrame.width
                            val frameH = rawFrame.height

                            val cropLeft = (project.roiLeft * frameW).toInt().coerceIn(0, frameW - 1)
                            val cropTop = (project.roiTop * frameH).toInt().coerceIn(0, frameH - 1)
                            val cropRight = (project.roiRight * frameW).toInt().coerceIn(cropLeft + 1, frameW)
                            val cropBottom = (project.roiBottom * frameH).toInt().coerceIn(cropTop + 1, frameH)
                            val cropW = (cropRight - cropLeft).coerceAtLeast(1)
                            val cropH = (cropBottom - cropTop).coerceAtLeast(1)

                            val crop = Bitmap.createBitmap(rawFrame, cropLeft, cropTop, cropW, cropH)
                            croppedBitmap = crop
                            val currentThumb = ImagePreprocessor.createThumbnailGrayscale(crop, 64, 16)
                            val stdDev = ImagePreprocessor.computeStandardDeviation(currentThumb)

                            val detectedText: String
                            val detectedConfidence: Float

                            if (stdDev < 6.0f) {
                                detectedText = ""
                                detectedConfidence = 0f
                                lastThumb = currentThumb
                                lastOcrText = ""
                                lastConfidence = 0f
                            } else {
                                val isSimilarToLast = lastThumb != null &&
                                        ImagePreprocessor.isFrameSimilar(lastThumb!!, currentThumb, maeThreshold = 6.0f)

                                if (isSimilarToLast) {
                                    detectedText = lastOcrText
                                    detectedConfidence = lastConfidence
                                } else {
                                    val enhanced = ImagePreprocessor.enhanceForOcr(crop)
                                    enhancedBitmap = enhanced
                                    val ocrRes = ocrEngine.recognizeText(enhanced, project.sourceLanguage)
                                    detectedText = ocrRes.fullText
                                    detectedConfidence = ocrRes.confidence
                                    lastThumb = currentThumb
                                    lastOcrText = detectedText
                                    lastConfidence = detectedConfidence
                                }
                            }

                            if (detectedText.isNotBlank()) {
                                rawDetections.add(
                                    RawFrameDetection(
                                        timestampMs = currentTimeMs,
                                        text = detectedText,
                                        confidence = detectedConfidence
                                    )
                                )
                            }
                        } finally {
                            if (enhancedBitmap != null && enhancedBitmap !== croppedBitmap && !enhancedBitmap.isRecycled) {
                                enhancedBitmap.recycle()
                            }
                            if (croppedBitmap != null && croppedBitmap !== rawFrame && !croppedBitmap.isRecycled) {
                                croppedBitmap.recycle()
                            }
                            if (!rawFrame.isRecycled) rawFrame.recycle()
                        }
                    }

                    stepIndex++
                    currentTimeMs += intervalMs

                    val frameProgress = 0.1f + (0.50f * (stepIndex.toFloat() / totalSteps.toFloat()))
                    if (stepIndex % 5 == 0L || currentTimeMs >= durationMs) {
                        updateState(project.id) {
                            it.copy(
                                progress = frameProgress,
                                currentSampleTimeMs = currentTimeMs,
                                extractedLinesCount = rawDetections.size
                            )
                        }
                        val percent = ((currentTimeMs * 100) / durationMs).toInt()
                        projectRepository.updateProgress(
                            project.id,
                            ProjectStatus.PROCESSING.name,
                            frameProgress,
                            "OCR: $percent%"
                        )
                        NotificationHelper.updateProgressNotification(context, project.title, "OCR: $percent%", (frameProgress * 100).toInt())
                    }
                }

                // Step 2: Temporal Speech Grouping
                updateState(project.id) {
                    it.copy(
                        step = "Agrupando falas e refinando linha do tempo...",
                        progress = 0.65f
                    )
                }
                projectRepository.updateProgress(project.id, ProjectStatus.PROCESSING.name, 0.65f, "Agrupamento temporal")
                NotificationHelper.updateProgressNotification(context, project.title, "Agrupando falas...", 65)

                val grouper = TemporalSpeechGrouper(
                    similarityThreshold = appSettings.textSimilarityThreshold,
                    minDurationMs = appSettings.minSpeechDurationMs,
                    sampleIntervalMs = intervalMs,
                    minConsecutiveFrames = 2
                )
                val groupedLines = grouper.groupDetections(project.id, rawDetections)

                // Clear previous lines before inserting fresh batch (Item 1: Avoid duplication!)
                subtitleRepository.clearLines(project.id)

                // Step 4: Batch Translation with Checkpoints (Item 1 & 11)
                updateState(project.id) {
                    it.copy(
                        step = "Traduzindo falas para ${project.targetLanguage.uppercase()}...",
                        progress = 0.75f
                    )
                }
                projectRepository.updateProgress(project.id, ProjectStatus.PROCESSING.name, 0.75f, "Tradução")

                val finalLines = mutableListOf<SubtitleLine>()
                val batchToSave = mutableListOf<SubtitleLine>()
                val totalLines = groupedLines.size
                var failedCount = 0

                for ((idx, line) in groupedLines.withIndex()) {
                    if (cancelledProjects.contains(project.id)) {
                        throw CancellationException("Operação cancelada pelo usuário")
                    }

                    val contextList = mutableListOf<String>()
                    if (idx > 0) contextList.add(groupedLines[idx - 1].originalText)
                    if (idx < totalLines - 1) contextList.add(groupedLines[idx + 1].originalText)

                    var translatedText = ""
                    var isLineFlagged = line.isFlagged

                    try {
                        translatedText = translator.translate(
                            text = line.originalText,
                            sourceLanguage = project.sourceLanguage,
                            targetLanguage = project.targetLanguage,
                            contextLines = contextList,
                            glossary = appSettings.glossaryTerms,
                            bypassCache = false
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Translation failed: flag line and retain original text
                        translatedText = line.originalText
                        isLineFlagged = true
                        failedCount++
                    }

                    val translatedLine = line.copy(
                        translatedText = translatedText,
                        isFlagged = isLineFlagged
                    )
                    finalLines.add(translatedLine)
                    batchToSave.add(translatedLine)

                    // Checkpoint: save only the uncommitted batch to Room!
                    if (batchToSave.size >= 5 || idx == totalLines - 1) {
                        subtitleRepository.saveLines(batchToSave)
                        batchToSave.clear()

                        val transProgress = 0.75f + (0.24f * ((idx + 1).toFloat() / totalLines.coerceAtLeast(1).toFloat()))
                        updateState(project.id) {
                            it.copy(
                                progress = transProgress,
                                failedTranslationsCount = failedCount
                            )
                        }
                        projectRepository.updateProgress(
                            project.id,
                            ProjectStatus.PROCESSING.name,
                            transProgress,
                            "Traduzido ${idx + 1}/$totalLines"
                        )
                        NotificationHelper.updateProgressNotification(context, project.title, "Traduzindo ${idx + 1}/$totalLines", (transProgress * 100).toInt())
                    }
                }

                // Mark Completed
                projectRepository.updateProgress(
                    project.id,
                    ProjectStatus.COMPLETED.name,
                    1.0f,
                    "Concluído"
                )

                val completionMessage = if (failedCount > 0) {
                    "Concluído! ($failedCount falas marcadas para revisão manual)"
                } else {
                    "Processamento concluído com sucesso!"
                }

                updateState(project.id) {
                    PipelineState(
                        step = completionMessage,
                        progress = 1.0f,
                        isRunning = false,
                        extractedLinesCount = finalLines.size,
                        totalDurationMs = durationMs,
                        failedTranslationsCount = failedCount
                    )
                }
                NotificationHelper.updateProgressNotification(context, project.title, completionMessage, 100)

                Result.success(finalLines)
            } catch (c: CancellationException) {
                withContext(NonCancellable) {
                    projectRepository.updateProgress(project.id, ProjectStatus.PAUSED.name, 0f, "Cancelado")
                    updateState(project.id) {
                        PipelineState(
                            step = "Cancelado pelo usuário",
                            progress = 0f,
                            isRunning = false,
                            errorMessage = "Cancelado"
                        )
                    }
                    NotificationHelper.cancelNotification(context)
                }
                throw c // Re-throw CancellationException as required
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Erro durante o processamento do vídeo"
                withContext(NonCancellable) {
                    projectRepository.updateProgress(project.id, ProjectStatus.ERROR.name, 0f, msg)
                    updateState(project.id) {
                        PipelineState(
                            step = "Erro no processamento",
                            progress = 0f,
                            isRunning = false,
                            errorMessage = msg
                        )
                    }
                    NotificationHelper.cancelNotification(context)
                }
                Result.failure(e)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        } finally {
            mutex.unlock()
        }
    }
}
