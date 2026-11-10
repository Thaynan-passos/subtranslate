package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.pipeline.PipelineState
import com.example.domain.model.OutputMode
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.ui.components.HeroActionButton
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSecondaryContainerBlue
import com.example.ui.theme.OnSurfaceText
import com.example.ui.theme.OnSurfaceVariantText
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.OutlineVariantBorder
import com.example.ui.theme.PrimaryContainerViolet
import com.example.ui.theme.PrimaryFixed
import com.example.ui.theme.PrimaryFixedDim
import com.example.ui.theme.PrimaryViolet
import com.example.ui.theme.SecondaryBlue
import com.example.ui.theme.SecondaryContainerBlue
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    project: Project?,
    pipelineState: PipelineState,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onGoToEditorClick: () -> Unit,
    onRetryClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val isCompleted = pipelineState.progress >= 1f || project?.status == ProjectStatus.COMPLETED
    val isPausedState = pipelineState.isPaused || project?.status == ProjectStatus.PAUSED

    val effectiveErrorMessage = pipelineState.errorMessage?.ifBlank { null }
        ?: project?.errorMessage?.ifBlank { null }
        ?: if (project?.status == ProjectStatus.ERROR) "O processamento falhou. Tente novamente." else null
    val hasError = project?.status == ProjectStatus.ERROR || (effectiveErrorMessage != null && !isPausedState)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Processo em Segundo Plano",
                onBackClick = onBackClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. Foreground Service Persistent Notice Banner
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(PrimaryContainerViolet.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = PrimaryFixedDim,
                            modifier = Modifier
                                .size(24.dp)
                                .alpha(if (!isCompleted) pulseAlpha else 1f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Serviço em Segundo Plano",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )

                            Surface(
                                shape = CircleShape,
                                color = SecondaryContainerBlue.copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = "CPU WAKE-LOCK",
                                    color = OnSecondaryContainerBlue,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Processamento ativo. Você pode bloquear a tela ou alternar apps com segurança; o pipeline não será suspenso pelo Android.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantText,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // ==========================================
            // 2. Error Card (if any)
            // ==========================================
            if (hasError && !isCompleted) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("processing_error_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Erro",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Falha no Processamento",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = effectiveErrorMessage ?: "Erro desconhecido",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            } else if (isPausedState && !isCompleted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceContainerHigh,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryFixedDim.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pausado",
                            tint = PrimaryFixedDim,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Processamento Pausado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "O pipeline foi pausado. Clique em 'Retomar' para continuar de onde parou.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. Live Circular & Progress Widget Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isCompleted) SuccessEmerald else PrimaryViolet)
                                    .alpha(if (isCompleted) 1f else pulseAlpha)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (project?.outputMode == OutputMode.EXTRACT_ONLY) "Extração de Texto" else "Extração & Tradução",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHighest
                        ) {
                            Text(
                                text = if (isCompleted) "Concluído" else "Fila 1 de 1",
                                color = if (isCompleted) SuccessEmerald else PrimaryFixedDim,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Progress Section (Circular Indicator + Stats)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Circular visualizer
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(96.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { pipelineState.progress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 8.dp,
                                trackColor = SurfaceContainerHighest,
                                color = if (isCompleted) SuccessEmerald else PrimaryFixedDim
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${(pipelineState.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompleted) SuccessEmerald else PrimaryFixed
                                )
                                Text(
                                    text = if (isCompleted) "Concluído" else "Progresso",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariantText
                                )
                            }
                        }

                        // Detailed stats column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Tempo Decorrido",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                                val elapsedSec = pipelineState.currentSampleTimeMs / 1000
                                val eMin = elapsedSec / 60
                                val eSec = elapsedSec % 60
                                Text(
                                    text = "${String.format("%02d:%02d", eMin, eSec)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurfaceText
                                )
                            }

                            // Linear Progress Bar with smooth gradient
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceContainerHighest)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(pipelineState.progress.coerceIn(0.02f, 1f))
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(SecondaryBlue, PrimaryFixedDim)
                                            )
                                        )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Falas Detectadas",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                    Text(
                                        text = "${pipelineState.extractedLinesCount}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurfaceText
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Velocidade",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnSurfaceVariantText
                                    )
                                    Text(
                                        text = if (isCompleted) "Finalizado" else "4,2x (Tempo real)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryFixedDim
                                    )
                                }
                            }
                        }
                    }

                    // Micro Soundwave / Chunk Activity Visualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val heights = listOf(14, 22, 10, 26, 18, 28, 20, 14, 12, 16, 8, 12)
                        heights.forEachIndexed { idx, h ->
                            val isActive = !isCompleted && idx < ((pipelineState.progress * heights.size).toInt() + 2)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(h.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCompleted) SuccessEmerald.copy(alpha = 0.8f)
                                        else if (isActive) {
                                            if (idx % 2 == 0) PrimaryFixedDim else SecondaryBlue
                                        } else SurfaceContainerHighest
                                    )
                            )
                        }
                    }

                    // Step label text
                    Text(
                        text = pipelineState.step.ifBlank { "Extraindo legendas com alta precisão..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryFixedDim,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ==========================================
            // 4. Telemetry Metrics Pills Row
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val srcLang = project?.sourceLanguage?.uppercase() ?: "EN"
                val tgtLang = project?.targetLanguage?.uppercase() ?: "PT"
                TelemetryPill(
                    icon = Icons.Default.Translate,
                    text = "$srcLang ➔ $tgtLang",
                    tint = SecondaryBlue
                )
                TelemetryPill(
                    icon = Icons.Default.ViewInAr,
                    text = "OCR: ML Kit Neural",
                    tint = PrimaryFixedDim
                )
                TelemetryPill(
                    icon = Icons.Default.Videocam,
                    text = "${project?.samplingFps ?: 2} FPS (Amostragem)",
                    tint = SecondaryBlue
                )
                TelemetryPill(
                    icon = Icons.Default.AutoAwesome,
                    text = if (project?.outputMode == OutputMode.EXTRACT_ONLY) "Modo: Extração Manual" else "Tradução Automática",
                    tint = PrimaryFixed,
                    showPulse = !isCompleted && !isPausedState
                )
            }

            // ==========================================
            // 5. Real-Time Subtitle Detection Live Stream Card
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = PrimaryFixedDim,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Detecção em Tempo Real",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = PrimaryContainerViolet.copy(alpha = 0.3f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = PrimaryFixedDim,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isCompleted) "Concluído" else if (isPausedState) "Pausado" else "Ativo",
                                    color = PrimaryFixed,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Detected vs Translated Box
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = null,
                                    tint = OnSurfaceVariantText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Original Extraído (OCR):",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }

                            val detectedText = pipelineState.lastDetectedText.ifBlank { null }
                            Text(
                                text = if (detectedText != null) "“$detectedText”" else "Aguardando detecção de falas no vídeo...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = if (detectedText != null) androidx.compose.ui.text.font.FontStyle.Normal else androidx.compose.ui.text.font.FontStyle.Italic,
                                color = if (detectedText != null) OnSurfaceText else OnSurfaceVariantText,
                                modifier = Modifier.padding(start = 20.dp)
                            )

                            // Translated box (se houver tradução e não for extract-only)
                            if (project?.outputMode != OutputMode.EXTRACT_ONLY && pipelineState.lastTranslatedText.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = PrimaryContainerViolet.copy(alpha = 0.25f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Translate,
                                                contentDescription = null,
                                                tint = PrimaryFixedDim,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tradução Neural:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = PrimaryFixedDim,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "“${pipelineState.lastTranslatedText}”",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurfaceText,
                                            modifier = Modifier.padding(start = 20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 6. Pipeline Stages Summary
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PipelineStepRow(
                        label = stringResource(R.string.step_extraction),
                        isCompleted = pipelineState.progress > 0.1f,
                        isActive = pipelineState.progress in 0.05f..0.1f
                    )
                    PipelineStepRow(
                        label = stringResource(R.string.step_ocr),
                        isCompleted = pipelineState.progress > 0.60f,
                        isActive = pipelineState.progress in 0.1f..0.60f
                    )
                    PipelineStepRow(
                        label = stringResource(R.string.step_grouping),
                        isCompleted = pipelineState.progress > 0.70f,
                        isActive = pipelineState.progress in 0.60f..0.70f
                    )
                    PipelineStepRow(
                        label = if (project?.outputMode == OutputMode.EXTRACT_ONLY) {
                            "4. Preparação do texto para tradução manual"
                        } else {
                            stringResource(R.string.step_translation)
                        },
                        isCompleted = pipelineState.progress >= 0.95f,
                        isActive = pipelineState.progress in 0.70f..0.95f
                    )
                    PipelineStepRow(
                        label = stringResource(R.string.step_completed),
                        isCompleted = pipelineState.progress >= 1f,
                        isActive = pipelineState.progress >= 1f
                    )
                }
            }

            // ==========================================
            // 7. Bottom Actions (Pill Style)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isCompleted) {
                    HeroActionButton(
                        text = if (project?.outputMode == OutputMode.EXTRACT_ONLY) "Abrir Tradução Manual" else "Abrir Editor de Legendas",
                        onClick = onGoToEditorClick,
                        tag = "go_to_editor_button"
                    )
                } else if (hasError) {
                    Button(
                        onClick = onRetryClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("retry_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = OnPrimaryFixed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tentar Novamente", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                    }

                    OutlinedButton(
                        onClick = onCancelClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("cancel_button"),
                        shape = CircleShape
                    ) {
                        Text("Cancelar", color = OnSurfaceVariantText)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isPausedState) {
                            Button(
                                onClick = onResumeClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("resume_button"),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnPrimaryFixed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retomar", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onPauseClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("pause_button"),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = OnSurfaceText)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pausar", color = OnSurfaceText)
                            }
                        }

                        OutlinedButton(
                            onClick = onCancelClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("cancel_button"),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = ErrorCrimson)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar", color = ErrorCrimson)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
    showPulse: Boolean = false
) {
    Surface(
        shape = CircleShape,
        color = SurfaceContainerHigh,
        modifier = Modifier.shadow(1.dp, CircleShape)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                color = OnSurfaceText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            if (showPulse) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PrimaryFixedDim)
                )
            }
        }
    }
}

@Composable
private fun PipelineStepRow(
    label: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> SuccessEmerald.copy(alpha = 0.20f)
                            isActive -> PrimaryViolet.copy(alpha = 0.20f)
                            else -> SurfaceContainerHighest
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SuccessEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryFixedDim
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(OnSurfaceVariantText.copy(alpha = 0.4f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isCompleted -> OnSurfaceText
                    isActive -> PrimaryFixed
                    else -> OnSurfaceVariantText
                },
                fontWeight = if (isActive || isCompleted) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        if (isCompleted) {
            Text(
                text = "OK",
                color = SuccessEmerald,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
