package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.OutputMode
import com.example.domain.model.RegionOfInterest
import com.example.domain.util.LanguageHelper
import com.example.domain.util.LanguageOption
import com.example.ui.components.HeroActionButton
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.OnPrimaryContainerViolet
import com.example.ui.theme.OnPrimaryFixed
import com.example.ui.theme.OnSecondaryBlue
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
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.viewmodel.ProjectConfigUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectConfigScreen(
    uiState: ProjectConfigUiState,
    onVideoSelected: (Uri, String) -> Unit,
    onRoiUpdated: (Float, Float, Float, Float) -> Unit,
    onAutoDetectRoi: () -> Unit,
    onLanguagesChanged: (String, String) -> Unit,
    onSamplingFpsChanged: (Int) -> Unit,
    onOutputModeChanged: (OutputMode) -> Unit,
    onStartExtraction: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                var fileName = "video_${System.currentTimeMillis()}"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                onVideoSelected(uri, fileName)
            } catch (_: SecurityException) {
                Toast.makeText(
                    context,
                    "Não foi possível manter o acesso ao vídeo. Escolha um arquivo de outro provedor.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = if (uiState.outputMode == OutputMode.EXTRACT_ONLY) {
                    "Importar Vídeo e Extrair Texto"
                } else {
                    "Importar Vídeo e Ajustar OCR"
                },
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
            // 1. Top Video Metadata Card (Cinematic M3)
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SecondaryContainerBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = OnSecondaryContainerBlue,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (uiState.videoUri != null) uiState.videoName else "SciFi_Series_Ep04.mp4",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.videoUri != null) OnSurfaceText else OnSurfaceVariantText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = SurfaceContainerHigh
                                ) {
                                    Text(
                                        text = "MP4",
                                        color = PrimaryFixedDim,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            if (uiState.videoUri != null && uiState.durationMs > 0L) {
                                val durationSec = uiState.durationMs / 1000
                                val min = durationSec / 60
                                val sec = durationSec % 60
                                Text(
                                    text = "${String.format("%02d:%02d", min, sec)} • ${uiState.videoWidth}x${uiState.videoHeight} • 60 FPS",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariantText
                                )
                            } else {
                                Text(
                                    text = "Toque em 'Escolher Vídeo' para começar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariantText
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Pill Button: Trocar / Escolher
                    Surface(
                        shape = CircleShape,
                        color = SurfaceContainerHigh,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { videoPickerLauncher.launch(arrayOf("video/*")) }
                            .testTag("select_video_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = OnSurfaceText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.videoUri == null) "Escolher" else "Trocar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceText
                            )
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            // ==========================================
            // 2. Interactive Video OCR Canvas & Bounding Box
            // ==========================================
            if (uiState.videoUri != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            InteractiveRoiPreview(
                                previewFrame = uiState.previewFrame,
                                videoWidth = uiState.videoWidth,
                                videoHeight = uiState.videoHeight,
                                roi = uiState.roi,
                                onRoiChanged = onRoiUpdated
                            )

                            // Timestamp badge on top-left of canvas
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerHighest.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF87171))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val sampleMs = if (uiState.durationMs > 0L) {
                                        (uiState.durationMs * 0.15f).toLong().coerceAtLeast(1000L).coerceAtMost(uiState.durationMs)
                                    } else {
                                        0L
                                    }
                                    val sSec = sampleMs / 1000
                                    val sMin = sSec / 60
                                    val sS = sSec % 60
                                    val sMs = sampleMs % 1000
                                    val durationSec = uiState.durationMs / 1000
                                    val min = durationSec / 60
                                    val sec = durationSec % 60
                                    Text(
                                        text = "${String.format("%02d:%02d.%03d", sMin, sS, sMs)} / ${String.format("%02d:%02d", min, sec)}",
                                        color = OnSurfaceText,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Quick Bounding Box Bar Tools
                        Surface(
                            color = SurfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Auto Detect Region button
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryViolet.copy(alpha = 0.20f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryViolet.copy(alpha = 0.40f)),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = !uiState.isAutoDetectingRoi) { onAutoDetectRoi() }
                                        .testTag("auto_detect_roi_button")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        if (uiState.isAutoDetectingRoi) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = PrimaryViolet,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = PrimaryFixedDim,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Auto-Detectar Região",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryFixed
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Reset Box
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onRoiUpdated(0.05f, 0.70f, 0.95f, 0.95f) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RestartAlt,
                                                contentDescription = null,
                                                tint = OnSurfaceVariantText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Redefinir",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OnSurfaceVariantText
                                            )
                                        }
                                    }

                                    // 16:9 Aspect ratio
                                    Surface(
                                        shape = CircleShape,
                                        color = SurfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onRoiUpdated(0.08f, 0.72f, 0.92f, 0.92f) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AspectRatio,
                                                contentDescription = null,
                                                tint = OnSurfaceVariantText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "16:9",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = OnSurfaceVariantText
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. OCR Sampling Rate (Segmented Pill Selector)
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = PrimaryFixedDim,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Parâmetros de Amostragem de OCR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHigh
                        ) {
                            Text(
                                text = "ML Kit Local",
                                color = SecondaryBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frequência de Captura:",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantText
                        )
                        Text(
                            text = when (uiState.samplingFps) {
                                1 -> "1 FPS (Ultrarrápido)"
                                2 -> "2 FPS (Equilibrado)"
                                else -> "${uiState.samplingFps} FPS (Alta Precisão)"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryFixedDim
                        )
                    }

                    // Segmented Pill Selector (1, 2, 5 FPS)
                    Surface(
                        shape = CircleShape,
                        color = SurfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                Triple(1, "1 FPS", "Ultrarrápido"),
                                Triple(2, "2 FPS", "Recomendado"),
                                Triple(5, "5 FPS", "Alta Precisão")
                            ).forEach { (fpsVal, mainText, subText) ->
                                val isSelected = uiState.samplingFps == fpsVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) PrimaryContainerViolet else Color.Transparent
                                        )
                                        .clickable { onSamplingFpsChanged(fpsVal) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = mainText,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) OnPrimaryContainerViolet else OnSurfaceVariantText
                                        )
                                        Text(
                                            text = subText,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) OnPrimaryContainerViolet.copy(alpha = 0.8f) else OnSurfaceVariantText.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Hidden/Accessible Slider for tests and fine tuning
                    Slider(
                        value = uiState.samplingFps.toFloat(),
                        onValueChange = { onSamplingFpsChanged(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = PrimaryViolet,
                            activeTrackColor = PrimaryViolet,
                            inactiveTrackColor = SurfaceContainerHighest
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sampling_fps_slider")
                    )

                    // Informational callout card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerHigh.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = SecondaryBlue,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "A frequência de 2 FPS analisa 1 quadro a cada 500ms, ideal para falas contínuas mantendo baixo consumo de bateria e alta precisão temporal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariantText,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 4. Pre-Processing Switches (M3 Style)
            // ==========================================
            var highContrastEnabled by remember { mutableStateOf(true) }
            var binarizationEnabled by remember { mutableStateOf(false) }
            var fullFrameFallbackEnabled by remember { mutableStateOf(true) }

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
                    Text(
                        text = "Pré-Processamento de Imagem",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )

                    // Switch 1: High Contrast
                    M3PreprocessRow(
                        icon = Icons.Default.Contrast,
                        iconTint = PrimaryFixedDim,
                        title = "Filtro de Alto Contraste",
                        description = "Melhora a leitura sobre fundos complexos ou brilhantes",
                        checked = highContrastEnabled,
                        onCheckedChange = { highContrastEnabled = it }
                    )

                    // Switch 2: Black & White
                    M3PreprocessRow(
                        icon = Icons.Default.FilterBAndW,
                        iconTint = SecondaryBlue,
                        title = "Binarização em Preto e Branco",
                        description = "Isola caracteres eliminando gradientes e cores do fundo",
                        checked = binarizationEnabled,
                        onCheckedChange = { binarizationEnabled = it }
                    )

                    // Switch 3: Full Frame Fallback
                    M3PreprocessRow(
                        icon = Icons.Default.DocumentScanner,
                        iconTint = Color(0xFFBDC2FF),
                        title = "Varredura do Quadro Inteiro",
                        description = "Executa varredura completa se a área delimitada estiver vazia",
                        checked = fullFrameFallbackEnabled,
                        onCheckedChange = { fullFrameFallbackEnabled = it }
                    )
                }
            }

            // ==========================================
            // 5. Output Mode Chips
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
                    Text(
                        text = stringResource(R.string.label_output_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.outputMode == OutputMode.SRT_ONLY,
                            onClick = { onOutputModeChanged(OutputMode.SRT_ONLY) },
                            label = { Text("Tradução automática") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null,
                            modifier = Modifier.testTag("mode_auto_chip")
                        )
                        FilterChip(
                            selected = uiState.outputMode == OutputMode.EXTRACT_ONLY,
                            onClick = { onOutputModeChanged(OutputMode.EXTRACT_ONLY) },
                            label = { Text("Só extrair texto") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null,
                            modifier = Modifier.testTag("mode_extract_chip")
                        )
                    }

                    Text(
                        text = if (uiState.outputMode == OutputMode.EXTRACT_ONLY) {
                            "Apenas extrai o texto do vídeo, sem tradução automática. Você poderá salvar um arquivo .TXT editável e importar de volta."
                        } else {
                            "Extrai e traduz automaticamente usando Gemini Cloud AI ou ML Kit Offline com sincronização labial e gírias."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText
                    )
                }
            }

            // ==========================================
            // 6. Language Selector Dropdowns
            // ==========================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Idiomas de Tradução",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LanguageDropdown(
                            label = stringResource(R.string.label_source_lang),
                            selectedCode = uiState.sourceLang,
                            options = LanguageHelper.sourceLanguages,
                            disallowedCode = uiState.targetLang,
                            onSelected = { onLanguagesChanged(it, uiState.targetLang) },
                            modifier = Modifier.weight(1f)
                        )
                        LanguageDropdown(
                            label = stringResource(R.string.label_target_lang),
                            selectedCode = uiState.targetLang,
                            options = LanguageHelper.targetLanguages,
                            disallowedCode = uiState.sourceLang,
                            onSelected = { onLanguagesChanged(uiState.sourceLang, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ==========================================
            // 7. Hero Action CTA Button
            // ==========================================
            val isButtonEnabled = uiState.videoUri != null &&
                    uiState.durationMs > 0L &&
                    !uiState.isAutoDetectingRoi &&
                    !uiState.isCreatingProject &&
                    uiState.sourceLang != uiState.targetLang

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroActionButton(
                    text = when {
                        uiState.isCreatingProject -> "Criando projeto..."
                        uiState.outputMode == OutputMode.EXTRACT_ONLY -> "Iniciar Extração de Texto"
                        else -> "Iniciar Extração e Tradução"
                    },
                    onClick = onStartExtraction,
                    enabled = isButtonEnabled,
                    isLoading = uiState.isCreatingProject,
                    tag = "start_extraction_button"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = SecondaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gemini 1.5 Flash pronto para sincronização de legendas",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = OnSurfaceVariantText
                    )
                }
            }
        }
    }
}

@Composable
private fun M3PreprocessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceText
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnPrimaryFixed,
                    checkedTrackColor = PrimaryFixedDim,
                    uncheckedThumbColor = OnSurfaceVariantText,
                    uncheckedTrackColor = SurfaceContainerHighest,
                    uncheckedBorderColor = OutlineBorder
                )
            )
        }
    }
}

@Composable
fun InteractiveRoiPreview(
    previewFrame: Bitmap?,
    videoWidth: Int,
    videoHeight: Int,
    roi: RegionOfInterest,
    onRoiChanged: (Float, Float, Float, Float) -> Unit
) {
    val currentRoi by rememberUpdatedState(roi)
    var activeHandle by remember { mutableStateOf<Int?>(null) }

    val videoAspect = if (videoWidth > 0 && videoHeight > 0) {
        (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.3f, 3.0f)
    } else {
        16f / 9f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(videoAspect)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (previewFrame != null) {
            Image(
                bitmap = previewFrame.asImageBitmap(),
                contentDescription = "Frame de pré-visualização",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = OnSurfaceVariantText.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Carregando frame do vídeo...",
                    color = OnSurfaceVariantText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Overlay with BlendMode.Clear inside Offscreen CompositingStrategy
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val r = currentRoi
                            val w = size.width
                            val h = size.height

                            val leftPx = r.left * w
                            val topPx = r.top * h
                            val rightPx = r.right * w
                            val bottomPx = r.bottom * h
                            val threshold = 28.dp.toPx()

                            activeHandle = when {
                                kotlin.math.abs(offset.y - topPx) <= threshold -> 1
                                kotlin.math.abs(offset.y - bottomPx) <= threshold -> 2
                                kotlin.math.abs(offset.x - leftPx) <= threshold -> 3
                                kotlin.math.abs(offset.x - rightPx) <= threshold -> 4
                                offset.x in leftPx..rightPx && offset.y in topPx..bottomPx -> 0
                                else -> 0
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val r = currentRoi
                            val w = size.width
                            val h = size.height

                            val dx = dragAmount.x / w
                            val dy = dragAmount.y / h

                            when (activeHandle) {
                                1 -> { // Top handle
                                    val newTop = (r.top + dy).coerceIn(0f, r.bottom - 0.05f)
                                    onRoiChanged(r.left, newTop, r.right, r.bottom)
                                }
                                2 -> { // Bottom handle
                                    val newBottom = (r.bottom + dy).coerceIn(r.top + 0.05f, 1f)
                                    onRoiChanged(r.left, r.top, r.right, newBottom)
                                }
                                3 -> { // Left handle
                                    val newLeft = (r.left + dx).coerceIn(0f, r.right - 0.05f)
                                    onRoiChanged(newLeft, r.top, r.right, r.bottom)
                                }
                                4 -> { // Right handle
                                    val newRight = (r.right + dx).coerceIn(r.left + 0.05f, 1f)
                                    onRoiChanged(r.left, r.top, newRight, r.bottom)
                                }
                                else -> { // Move whole ROI
                                    val boxW = r.right - r.left
                                    val boxH = r.bottom - r.top
                                    val newLeft = (r.left + dx).coerceIn(0f, 1f - boxW)
                                    val newTop = (r.top + dy).coerceIn(0f, 1f - boxH)
                                    onRoiChanged(newLeft, newTop, newLeft + boxW, newTop + boxH)
                                }
                            }
                        },
                        onDragEnd = { activeHandle = null },
                        onDragCancel = { activeHandle = null }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            val leftPx = currentRoi.left * w
            val topPx = currentRoi.top * h
            val roiWidthPx = (currentRoi.right - currentRoi.left) * w
            val roiHeightPx = (currentRoi.bottom - currentRoi.top) * h

            // 1. Semi-transparent dark scrim over entire frame
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                size = Size(w, h)
            )

            // 2. Clear out ROI area using BlendMode.Clear for sharp contrast
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(leftPx, topPx),
                size = Size(roiWidthPx, roiHeightPx),
                blendMode = BlendMode.Clear
            )

            // 3. Highlighted violet-lavender border around ROI (M3 Expressive Glow)
            drawRect(
                color = Color(0xFFD0BCFF),
                topLeft = Offset(leftPx, topPx),
                size = Size(roiWidthPx, roiHeightPx),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // 4. Corner bracket handles for tactile editing
            val handleLen = 14.dp.toPx()
            val handleStroke = 3.5.dp.toPx()
            val handleColor = Color(0xFF8AB4F8)

            // Top-left
            drawLine(handleColor, Offset(leftPx, topPx), Offset(leftPx + handleLen, topPx), strokeWidth = handleStroke)
            drawLine(handleColor, Offset(leftPx, topPx), Offset(leftPx, topPx + handleLen), strokeWidth = handleStroke)

            // Top-right
            drawLine(handleColor, Offset(leftPx + roiWidthPx, topPx), Offset(leftPx + roiWidthPx - handleLen, topPx), strokeWidth = handleStroke)
            drawLine(handleColor, Offset(leftPx + roiWidthPx, topPx), Offset(leftPx + roiWidthPx, topPx + handleLen), strokeWidth = handleStroke)

            // Bottom-left
            drawLine(handleColor, Offset(leftPx, topPx + roiHeightPx), Offset(leftPx + handleLen, topPx + roiHeightPx), strokeWidth = handleStroke)
            drawLine(handleColor, Offset(leftPx, topPx + roiHeightPx), Offset(leftPx, topPx + roiHeightPx - handleLen), strokeWidth = handleStroke)

            // Bottom-right
            drawLine(handleColor, Offset(leftPx + roiWidthPx, topPx + roiHeightPx), Offset(leftPx + roiWidthPx - handleLen, topPx + roiHeightPx), strokeWidth = handleStroke)
            drawLine(handleColor, Offset(leftPx + roiWidthPx, topPx + roiHeightPx), Offset(leftPx + roiWidthPx, topPx + roiHeightPx - handleLen), strokeWidth = handleStroke)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageDropdown(
    label: String,
    selectedCode: String,
    options: List<LanguageOption>,
    disallowedCode: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.code == selectedCode }?.displayName ?: selectedCode

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, color = OnSurfaceVariantText) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceContainer,
                unfocusedContainerColor = SurfaceContainer,
                focusedBorderColor = PrimaryViolet,
                unfocusedBorderColor = OutlineVariantBorder,
                focusedTextColor = OnSurfaceText,
                unfocusedTextColor = OnSurfaceText
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceContainerHigh)
        ) {
            options.forEach { opt ->
                val isDisallowed = opt.code == disallowedCode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt.displayName,
                            color = if (isDisallowed) OnSurfaceVariantText.copy(alpha = 0.38f) else OnSurfaceText
                        )
                    },
                    enabled = !isDisallowed,
                    onClick = {
                        onSelected(opt.code)
                        expanded = false
                    }
                )
            }
        }
    }
}
