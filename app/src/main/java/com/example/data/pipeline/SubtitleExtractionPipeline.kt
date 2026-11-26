package com.example.data.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SubtitleRepository
import com.example.data.service.SubtitleExtractionService
import com.example.data.util.NotificationHelper
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.OcrPreprocessingOptions
import com.example.domain.engine.SpeechTranscriber
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.model.CaptureSource
import com.example.domain.model.OutputMode
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.domain.model.RawFrameDetection
import com.example.domain.model.SubtitleLine
import com.example.domain.util.ImagePreprocessor
import com.example.domain.util.OcrTextSanitizer
import com.example.domain.util.TemporalSpeechGrouper
import com.example.domain.util.WatermarkFilter
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
    val failedTranslationsCount: Int = 0,
    val failedOcrFramesCount: Int = 0,
    val lastDetectedText: String = "",
    val lastTranslatedText: String = ""
)

class SubtitleExtractionPipeline(
    private val context: Context,
    private val projectRepository: ProjectRepository,
    private val subtitleRepository: SubtitleRepository,
    private val ocrEngine: OcrEngine,
    private val translator: Translator,
    private val speechTranscriber: SpeechTranscriber? = null
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
        updateState(projectId) { it.copy(isPaused = true, step = "Processamento pausado") }
    }

    fun resume(projectId: Long) {
        pausedProjects.remove(projectId)
        updateState(projectId) { it.copy(isPaused = false, step = "Retomando processamento...") }
    }

    fun markCancelled(projectId: Long) {
        cancelledProjects.add(projectId)
        pausedProjects.remove(projectId)
    }

    suspend fun cancel(projectId: Long) {
        markCancelled(projectId)
        updateState(projectId) {
            it.copy(
                isRunning = false,
                isPaused = false,
                step = "Cancelado pelo usuário",
                errorMessage = null
            )
        }
        projectRepository.updateProgress(projectId, ProjectStatus.PAUSED.name, 0f, "Cancelado pelo usuário")
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

            // In extract-only mode the user translates the text manually, so no translator is needed.
            val extractOnly = project.outputMode == OutputMode.EXTRACT_ONLY

            updateState(project.id) {
                PipelineState(
                    step = if (extractOnly) "Preparando extração de texto..." else "Verificando modelo de tradução offline...",
                    progress = 0.02f,
                    isRunning = true,
                    totalDurationMs = project.durationMs
                )
            }
            projectRepository.updateProgress(
                project.id,
                ProjectStatus.PROCESSING.name,
                0.02f,
                if (extractOnly) "Preparando extração" else "Verificando modelo de tradução"
            )
            val downloadSuccess = ensureTranslatorReady(project, extractOnly)
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
            var failedOcrFrames = 0
            try {
                val videoUri = Uri.parse(project.videoUri)
                retriever.setDataSource(context, videoUri)

                val metadata = extractVideoMetadata(retriever, project)
                val durationMs = metadata.durationMs

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

                val isAudioSource = project.captureSource == CaptureSource.AUDIO_TRANSCRIPTION
                val ocrOptions = OcrPreprocessingOptions(
                    enableHighContrast = appSettings.enableHighContrastOcr,
                    enableOtsuBinarization = appSettings.enableOtsuOcr
                )
                val groupedLines: List<SubtitleLine> = if (isAudioSource) {
                    transcribeAudioToLines(project)
                } else {
                    val extraction = extractAndGroupFromVideo(
                        project = project,
                        appSettings = appSettings,
                        retriever = retriever,
                        metadata = metadata,
                        ocrOptions = ocrOptions
                    )
                    failedOcrFrames = extraction.failedOcrFrames
                    extraction.lines
                }
                if (groupedLines.isEmpty()) {
                    throw IllegalStateException(
                        if (isAudioSource) {
                            "Nenhuma fala foi detectada no áudio do vídeo."
                        } else {
                            "Nenhuma legenda foi detectada. Verifique a região de interesse e as configurações de amostragem e tente novamente."
                        }
                    )
                }

                // Clear previous lines before inserting fresh batch
                subtitleRepository.clearLines(project.id)

                val preparedLines = prepareLinesForTranslation(groupedLines, isAudioSource, project, appSettings)
                val translationResult = translateAndSaveLines(
                    project = project,
                    preparedLines = preparedLines,
                    extractOnly = extractOnly,
                    appSettings = appSettings
                )
                val finalLines = translationResult.finalLines
                val failedCount = translationResult.failedCount

                if (cancelledProjects.contains(project.id)) {
                    throw CancellationException("Operação cancelada pelo usuário")
                }

                if (finalLines.isEmpty()) {
                    throw IllegalStateException(
                        "Todas as falas detectadas foram filtradas como marca d'água. Ajuste o filtro ou a região de interesse e tente novamente."
                    )
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
                        failedTranslationsCount = failedCount,
                        failedOcrFramesCount = failedOcrFrames
                    )
                }
                NotificationHelper.updateProgressNotification(context, project.title, completionMessage, 100)

                Result.success(finalLines)
            } catch (c: CancellationException) {
                withContext(NonCancellable) {
                    cancelledProjects.remove(project.id)
                    pausedProjects.remove(project.id)
                    projectRepository.updateProgress(project.id, ProjectStatus.PAUSED.name, 0f, "Cancelado pelo usuário")
                    updateState(project.id) {
                        PipelineState(
                            step = "Cancelado pelo usuário",
                            progress = 0f,
                            isRunning = false,
                            isPaused = false,
                            errorMessage = null
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
                            errorMessage = msg,
                            failedOcrFramesCount = failedOcrFrames
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

    private suspend fun ensureTranslatorReady(project: Project, extractOnly: Boolean): Boolean {
        if (extractOnly) return true
        return try {
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
    }

    private fun extractVideoMetadata(
        retriever: MediaMetadataRetriever,
        project: Project
    ): VideoMetadata {
        val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        val durationMs = durationStr?.toLongOrNull() ?: project.durationMs
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: project.videoWidth
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: project.videoHeight
        val videoWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
        val videoHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight
        if (durationMs <= 0L) {
            throw IllegalStateException("O vídeo selecionado possui duração inválida (0s).")
        }
        return VideoMetadata(durationMs, videoWidth, videoHeight)
    }

    private suspend fun extractAndGroupFromVideo(
        project: Project,
        appSettings: AppSettings,
        retriever: MediaMetadataRetriever,
        metadata: VideoMetadata,
        ocrOptions: OcrPreprocessingOptions
    ): OcrExtractionResult {
        val durationMs = metadata.durationMs
        val fps = project.samplingFps.coerceIn(1, 10)
        val intervalMs = (1000L / fps).coerceAtLeast(100L)

        val rawDetections = mutableListOf<RawFrameDetection>()
        var lastThumb: IntArray? = null
        var lastOcrText = ""
        var lastConfidence = 0f
        var lastOcrTimeMs = Long.MIN_VALUE
        var failedOcrFrames = 0

        var currentTimeMs = 0L
        val totalSteps = ((durationMs - 1L) / intervalMs) + 1L
        var stepIndex = 0L

        updateState(project.id) {
            it.copy(
                step = "Extraindo frames e detectando legendas (OCR)...",
                progress = 0.1f
            )
        }

        while (currentTimeMs < durationMs) {
            awaitIfPausedOrCancelled(project.id)

            val rawFrame = getScaledFrame(retriever, currentTimeMs * 1000L, metadata.width, metadata.height)
            if (rawFrame != null) {
                var croppedBitmap: Bitmap? = null
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
                    val thumbMetrics = ImagePreprocessor.createThumbnailMetrics(crop, 96, 24)
                    val currentThumb = thumbMetrics.rgb
                    val stdDev = ImagePreprocessor.computeStandardDeviation(thumbMetrics.gray)

                    val detectedText: String
                    val detectedConfidence: Float

                    if (stdDev < 6.0f) {
                        detectedText = ""
                        detectedConfidence = 0f
                        lastThumb = currentThumb
                        lastOcrText = ""
                        lastConfidence = 0f
                        lastOcrTimeMs = currentTimeMs
                    } else {
                        val isSimilarToLast = lastThumb != null &&
                            ImagePreprocessor.isFrameSimilar(lastThumb, currentThumb, maeThreshold = FRAME_SIMILARITY_MAE_THRESHOLD) &&
                            currentTimeMs - lastOcrTimeMs < MAX_FRAME_REUSE_WINDOW_MS

                        if (isSimilarToLast) {
                            detectedText = lastOcrText
                            detectedConfidence = lastConfidence
                        } else {
                            val outcome = recognizeFrameWithFallback(
                                rawFrame = rawFrame,
                                crop = crop,
                                cropW = cropW,
                                cropH = cropH,
                                frameW = frameW,
                                frameH = frameH,
                                project = project,
                                appSettings = appSettings,
                                ocrOptions = ocrOptions
                            )
                            if (outcome.failed) failedOcrFrames++
                            detectedText = outcome.text
                            detectedConfidence = outcome.confidence

                            lastThumb = currentThumb
                            lastOcrText = detectedText
                            lastConfidence = detectedConfidence
                            lastOcrTimeMs = currentTimeMs
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
                    if (croppedBitmap != null && croppedBitmap !== rawFrame && !croppedBitmap.isRecycled) {
                        croppedBitmap.recycle()
                    }
                    if (!rawFrame.isRecycled) rawFrame.recycle()
                }
            }

            stepIndex++
            currentTimeMs += intervalMs

            val frameProgress = (0.1f + (0.50f * (stepIndex.toFloat() / totalSteps.toFloat())))
                .coerceIn(0.1f, 0.6f)
            if (stepIndex % 5 == 0L || currentTimeMs >= durationMs) {
                val latestDetected = rawDetections.lastOrNull()?.text ?: ""
                updateState(project.id) {
                    it.copy(
                        progress = frameProgress,
                        currentSampleTimeMs = currentTimeMs,
                        extractedLinesCount = rawDetections.size,
                        failedOcrFramesCount = failedOcrFrames,
                        lastDetectedText = if (latestDetected.isNotBlank()) latestDetected else it.lastDetectedText
                    )
                }
                val percent = ((currentTimeMs.toDouble() / durationMs) * 100)
                    .toInt()
                    .coerceIn(0, 100)
                projectRepository.updateProgress(
                    project.id,
                    ProjectStatus.PROCESSING.name,
                    frameProgress,
                    "OCR: $percent%"
                )
                NotificationHelper.updateProgressNotification(
                    context,
                    project.title,
                    "OCR: $percent%",
                    (frameProgress * 100).toInt()
                )
            }
        }

        if (failedOcrFrames > 0 && rawDetections.isEmpty()) {
            throw IllegalStateException(
                "O OCR falhou em $failedOcrFrames amostras e nenhuma legenda pôde ser detectada."
            )
        }

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
            minConsecutiveFrames = if (fps == 1) 1 else 2,
            videoDurationMs = durationMs,
            filterWatermarks = appSettings.filterWatermarks,
            ignoredTerms = appSettings.ignoredTerms
        )
        return OcrExtractionResult(
            lines = grouper.groupDetections(project.id, rawDetections),
            failedOcrFrames = failedOcrFrames
        )
    }

    private suspend fun recognizeFrameWithFallback(
        rawFrame: Bitmap,
        crop: Bitmap,
        cropW: Int,
        cropH: Int,
        frameW: Int,
        frameH: Int,
        project: Project,
        appSettings: AppSettings,
        ocrOptions: OcrPreprocessingOptions
    ): FrameOcrOutcome {
        var failed = false
        var ocrRes = try {
            ocrEngine.recognizeText(crop, project.sourceLanguage, ocrOptions)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
            null
        }
        var cleanedOcr = sanitizeDetectedOcrText(
            ocrRes?.fullText.orEmpty(),
            project.sourceLanguage,
            appSettings
        )

        val cropIsSubregion = cropW < (frameW * FULL_FRAME_FALLBACK_RATIO).toInt() ||
            cropH < (frameH * FULL_FRAME_FALLBACK_RATIO).toInt()
        if (cleanedOcr.isBlank() && appSettings.enableFullFrameFallbackOcr && cropIsSubregion) {
            // Full-frame fallback uses a single fast pass without pixel-by-pixel HighContrast/Otsu
            // to avoid allocating tens of MBs and running 3 redundant neural passes on empty scenes.
            val fullFrameRes = try {
                ocrEngine.recognizeText(rawFrame, project.sourceLanguage, FULL_FRAME_FALLBACK_OPTIONS)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (fullFrameRes != null) {
                val fullCleaned = sanitizeDetectedOcrText(
                    fullFrameRes.fullText,
                    project.sourceLanguage,
                    appSettings
                )
                if (fullCleaned.isNotBlank()) {
                    ocrRes = fullFrameRes
                    cleanedOcr = fullCleaned
                }
            }
        }

        return FrameOcrOutcome(
            text = cleanedOcr,
            confidence = ocrRes?.confidence ?: 0f,
            failed = failed
        )
    }

    private fun prepareLinesForTranslation(
        groupedLines: List<SubtitleLine>,
        isAudioSource: Boolean,
        project: Project,
        appSettings: AppSettings
    ): List<PreparedTranslationLine> {
        return groupedLines.mapNotNull { line ->
            val cleanedOriginalText = if (isAudioSource) {
                line.originalText.trim()
            } else {
                WatermarkFilter.cleanText(
                    text = line.originalText,
                    filterEnabled = appSettings.filterWatermarks,
                    customIgnoredTerms = appSettings.ignoredTerms
                )
            }
            if (cleanedOriginalText.isBlank()) return@mapNotNull null

            val sanitizedOriginalText = if (isAudioSource) {
                cleanedOriginalText
            } else {
                OcrTextSanitizer.sanitize(
                    text = cleanedOriginalText,
                    sourceLanguage = project.sourceLanguage
                )
            }
            if (sanitizedOriginalText.isBlank()) return@mapNotNull null

            val coherentSourceText = sanitizedOriginalText
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()
            PreparedTranslationLine(line, sanitizedOriginalText, coherentSourceText)
        }
    }

    private suspend fun translateAndSaveLines(
        project: Project,
        preparedLines: List<PreparedTranslationLine>,
        extractOnly: Boolean,
        appSettings: AppSettings
    ): TranslationStageResult {
        updateState(project.id) {
            it.copy(
                step = if (extractOnly) {
                    "Preparando texto para tradução manual..."
                } else {
                    "Traduzindo falas para ${project.targetLanguage.uppercase()}..."
                },
                progress = 0.75f
            )
        }
        projectRepository.updateProgress(
            project.id,
            ProjectStatus.PROCESSING.name,
            0.75f,
            if (extractOnly) "Organizando texto" else "Tradução"
        )

        val finalLines = mutableListOf<SubtitleLine>()
        val totalPrepared = preparedLines.size
        var processedCount = 0
        var failedCount = 0

        for (chunk in preparedLines.chunked(TRANSLATION_BATCH_SIZE)) {
            awaitIfPausedOrCancelled(project.id)

            val chunkOutcome = translateChunk(
                project = project,
                chunk = chunk,
                extractOnly = extractOnly,
                appSettings = appSettings
            )
            failedCount += chunkOutcome.failedCount

            awaitIfPausedOrCancelled(project.id)
            val batchToSave = chunk.mapIndexed { i, item ->
                val (translatedText, isLineFlagged) = chunkOutcome.translations[i]
                item.line.copy(
                    index = finalLines.size + i + 1,
                    originalText = item.sanitizedOriginalText,
                    translatedText = translatedText,
                    isFlagged = isLineFlagged
                )
            }
            finalLines.addAll(batchToSave)
            subtitleRepository.saveLines(batchToSave)
            processedCount += batchToSave.size

            val lastSaved = batchToSave.lastOrNull()
            val transProgress = 0.75f + (0.24f * (processedCount.toFloat() / totalPrepared.coerceAtLeast(1).toFloat()))
            updateState(project.id) {
                it.copy(
                    progress = transProgress,
                    failedTranslationsCount = failedCount,
                    lastDetectedText = lastSaved?.originalText ?: it.lastDetectedText,
                    lastTranslatedText = lastSaved?.translatedText ?: it.lastTranslatedText
                )
            }
            awaitIfPausedOrCancelled(project.id)
            projectRepository.updateProgress(
                project.id,
                ProjectStatus.PROCESSING.name,
                transProgress,
                if (extractOnly) "Organizado $processedCount/$totalPrepared" else "Traduzido $processedCount/$totalPrepared"
            )
            NotificationHelper.updateProgressNotification(
                context,
                project.title,
                if (extractOnly) "Organizando $processedCount/$totalPrepared" else "Traduzindo $processedCount/$totalPrepared",
                (transProgress * 100).toInt()
            )
        }

        return TranslationStageResult(finalLines, failedCount)
    }

    private suspend fun translateChunk(
        project: Project,
        chunk: List<PreparedTranslationLine>,
        extractOnly: Boolean,
        appSettings: AppSettings
    ): ChunkTranslationOutcome {
        if (extractOnly) {
            return ChunkTranslationOutcome(chunk.map { "" to it.line.isFlagged }, 0)
        }
        var failedCount = 0
        val translations = try {
            val batchResults = translator.translateBatch(
                texts = chunk.map { it.coherentSourceText },
                sourceLanguage = project.sourceLanguage,
                targetLanguage = project.targetLanguage,
                glossary = appSettings.glossaryTerms,
                bypassCache = false
            )
            if (batchResults.size != chunk.size) {
                throw IllegalStateException("Quantidade de traduções retornadas diverge do lote.")
            }
            chunk.mapIndexed { i, item ->
                val tr = batchResults[i].trim()
                if (tr.isBlank()) {
                    failedCount++
                    item.sanitizedOriginalText to true
                } else {
                    tr to item.line.isFlagged
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            chunk.map { item ->
                awaitIfPausedOrCancelled(project.id)
                try {
                    val tr = translator.translate(
                        text = item.coherentSourceText,
                        sourceLanguage = project.sourceLanguage,
                        targetLanguage = project.targetLanguage,
                        glossary = appSettings.glossaryTerms,
                        bypassCache = false
                    )
                    tr to item.line.isFlagged
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    failedCount++
                    item.sanitizedOriginalText to true
                }
            }
        }
        return ChunkTranslationOutcome(translations, failedCount)
    }

    /**
     * Transcreve o áudio do vídeo em [SubtitleLine]s (texto original, sem tradução).
     * Pausa e cancelamento são respeitados entre os blocos de áudio.
     */
    private suspend fun transcribeAudioToLines(project: Project): List<SubtitleLine> {
        val transcriber = speechTranscriber
            ?: throw IllegalStateException("A transcrição de áudio não está disponível nesta instalação.")

        updateState(project.id) {
            it.copy(step = "Extraindo áudio e transcrevendo falas com Gemini IA...", progress = AUDIO_PROGRESS_START)
        }
        projectRepository.updateProgress(
            project.id,
            ProjectStatus.PROCESSING.name,
            AUDIO_PROGRESS_START,
            "Transcrição do áudio"
        )
        NotificationHelper.updateProgressNotification(
            context,
            project.title,
            "Transcrevendo áudio...",
            (AUDIO_PROGRESS_START * 100).toInt()
        )

        val segments = transcriber.transcribe(
            videoUri = project.videoUri,
            sourceLanguage = project.sourceLanguage
        ) { done, total ->
            awaitIfPausedOrCancelled(project.id)
            val progress = AUDIO_PROGRESS_START +
                (AUDIO_PROGRESS_SPAN * (done.toFloat() / total.coerceAtLeast(1).toFloat()))
            updateState(project.id) {
                it.copy(
                    progress = progress,
                    step = "Transcrevendo áudio ($done/$total)..."
                )
            }
            projectRepository.updateProgress(
                project.id,
                ProjectStatus.PROCESSING.name,
                progress,
                "Transcrição: $done/$total"
            )
            NotificationHelper.updateProgressNotification(
                context,
                project.title,
                "Transcrição: $done/$total",
                (progress * 100).toInt()
            )
        }
        awaitIfPausedOrCancelled(project.id)

        val lines = segments.mapIndexed { idx, segment ->
            SubtitleLine(
                projectId = project.id,
                index = idx + 1,
                startTimeMs = segment.startMs,
                endTimeMs = segment.endMs,
                originalText = segment.text,
                translatedText = "",
                confidence = if (segment.isTimingSuspect) SUSPECT_TIMING_CONFIDENCE else 1.0f,
                isFlagged = segment.isTimingSuspect
            )
        }
        updateState(project.id) {
            it.copy(
                extractedLinesCount = lines.size,
                lastDetectedText = lines.lastOrNull()?.originalText ?: it.lastDetectedText
            )
        }
        return lines
    }

    private suspend fun awaitIfPausedOrCancelled(projectId: Long) {
        if (cancelledProjects.contains(projectId)) {
            throw CancellationException("Operação cancelada pelo usuário")
        }
        while (pausedProjects.contains(projectId)) {
            kotlinx.coroutines.delay(PAUSE_POLL_INTERVAL_MS)
            if (cancelledProjects.contains(projectId)) {
                throw CancellationException("Operação cancelada pelo usuário")
            }
        }
    }

    private fun getScaledFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }
        val width = videoWidth.coerceAtLeast(1)
        val height = videoHeight.coerceAtLeast(1)
        // 1280px matches ML Kit's internal resolution while cutting decode time and bitmap memory by >2x vs 1920px.
        val maxDimension = MAX_FRAME_DECODE_DIMENSION
        val scale = minOf(1f, maxDimension / maxOf(width, height))
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return retriever.getScaledFrameAtTime(
            timeUs,
            MediaMetadataRetriever.OPTION_CLOSEST,
            targetWidth,
            targetHeight
        )
    }

    private fun sanitizeDetectedOcrText(
        rawText: String,
        sourceLanguage: String,
        appSettings: AppSettings
    ): String {
        val cleaned = if (appSettings.filterWatermarks) {
            WatermarkFilter.cleanText(
                text = rawText,
                filterEnabled = true,
                customIgnoredTerms = appSettings.ignoredTerms
            )
        } else {
            rawText
        }
        return OcrTextSanitizer.sanitize(cleaned, sourceLanguage)
    }

    private data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int
    )

    private data class OcrExtractionResult(
        val lines: List<SubtitleLine>,
        val failedOcrFrames: Int
    )

    private data class FrameOcrOutcome(
        val text: String,
        val confidence: Float,
        val failed: Boolean
    )

    private data class PreparedTranslationLine(
        val line: SubtitleLine,
        val sanitizedOriginalText: String,
        val coherentSourceText: String
    )

    private data class TranslationStageResult(
        val finalLines: List<SubtitleLine>,
        val failedCount: Int
    )

    private data class ChunkTranslationOutcome(
        val translations: List<Pair<String, Boolean>>,
        val failedCount: Int
    )

    private companion object {
        const val AUDIO_PROGRESS_START = 0.1f
        const val AUDIO_PROGRESS_SPAN = 0.5f
        const val SUSPECT_TIMING_CONFIDENCE = 0.5f
        const val PAUSE_POLL_INTERVAL_MS = 200L
        const val TRANSLATION_BATCH_SIZE = 20
        const val FULL_FRAME_FALLBACK_RATIO = 0.90f
        const val MAX_FRAME_DECODE_DIMENSION = 1280f
        const val FRAME_SIMILARITY_MAE_THRESHOLD = 3.5f
        const val MAX_FRAME_REUSE_WINDOW_MS = 2_000L
        val FULL_FRAME_FALLBACK_OPTIONS = OcrPreprocessingOptions(
            enableHighContrast = false,
            enableOtsuBinarization = false
        )
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}
