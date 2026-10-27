package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.OutputMode
import com.example.domain.model.RegionOfInterest
import com.example.domain.util.LanguageHelper
import com.example.domain.util.LanguageOption
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_project_config), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Video Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.videoUri != null) uiState.videoName else "Selecione um vídeo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier.testTag("select_video_button")
                        ) {
                            Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (uiState.videoUri == null) "Escolher Vídeo" else "Trocar")
                        }
                    }

                    if (uiState.videoUri != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val durationSec = uiState.durationMs / 1000
                        val min = durationSec / 60
                        val sec = durationSec % 60
                        Text(
                            text = "Duração: ${String.format("%02d:%02d", min, sec)} • Resolução: ${uiState.videoWidth}x${uiState.videoHeight}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // Interactive Frame & Region of Interest (ROI) selection
            if (uiState.videoUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Crop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Região da Legenda (ROI)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = onAutoDetectRoi,
                                enabled = !uiState.isAutoDetectingRoi,
                                modifier = Modifier.testTag("auto_detect_roi_button")
                            ) {
                                if (uiState.isAutoDetectingRoi) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Detectar")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Arraste o retângulo ou use as bordas para ajustar a área exata onde a legenda aparece.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Video Frame Preview with ROI Box and letterbox awareness
                        InteractiveRoiPreview(
                            previewFrame = uiState.previewFrame,
                            videoWidth = uiState.videoWidth,
                            videoHeight = uiState.videoHeight,
                            roi = uiState.roi,
                            onRoiChanged = onRoiUpdated
                        )
                    }
                }
            }

            // Language Selector (Enforcing unified options and avoiding source == target)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Idiomas de Tradução",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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

            // Sampling Rate (FPS)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.label_sampling_rate, uiState.samplingFps),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Valores entre 1 e 2 fps aceleram bastante o processamento mantendo excelente detecção de legendas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = uiState.samplingFps.toFloat(),
                        onValueChange = { onSamplingFpsChanged(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.testTag("sampling_fps_slider")
                    )
                }
            }

            // Output Mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.label_output_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FilterChip(
                        selected = true,
                        onClick = { onOutputModeChanged(OutputMode.SRT_ONLY) },
                        label = { Text("Exportação de Legendas (.SRT / .VTT)") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "O Modo B (substituição visual da legenda com faixa opaca) é somente um preview no editor; não gera um vídeo exportado. Para usar em outros players, exporte as legendas .SRT ou .VTT.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Privacy & Local Processing Notice
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.label_privacy_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Start Button
            Button(
                onClick = onStartExtraction,
                enabled = uiState.videoUri != null &&
                        uiState.durationMs > 0L &&
                        !uiState.isAutoDetectingRoi &&
                        !uiState.isCreatingProject &&
                        uiState.sourceLang != uiState.targetLang,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("start_extraction_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (uiState.isCreatingProject) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isCreatingProject) "Criando projeto..." else stringResource(R.string.btn_start_processing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
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

    // Active drag handle: 0 = center/move, 1 = top, 2 = bottom, 3 = left, 4 = right
    var activeHandle by remember { mutableStateOf<Int?>(null) }

    val videoAspect = if (videoWidth > 0 && videoHeight > 0) {
        (videoWidth.toFloat() / videoHeight.toFloat()).coerceIn(0.5f, 2.5f)
    } else {
        16f / 9f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(videoAspect)
            .clip(RoundedCornerShape(12.dp))
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
                Icon(Icons.Default.VideoFile, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                Text("Carregando frame do vídeo...", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
                            val threshold = 24.dp.toPx()

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
                        onDragEnd = {
                            activeHandle = null
                        },
                        onDragCancel = {
                            activeHandle = null
                        }
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
                color = Color.Black.copy(alpha = 0.50f),
                size = Size(w, h)
            )

            // 2. Clear out ROI area using BlendMode.Clear for sharp contrast
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(leftPx, topPx),
                size = Size(roiWidthPx, roiHeightPx),
                blendMode = BlendMode.Clear
            )

            // 3. Highlighted cyan border around ROI
            drawRect(
                color = Color(0xFF38BDF8),
                topLeft = Offset(leftPx, topPx),
                size = Size(roiWidthPx, roiHeightPx),
                style = Stroke(width = 3.dp.toPx())
            )

            // 4. Subtle corner and edge handle indicators
            val handleColor = Color(0xFF38BDF8)
            drawCircle(handleColor, radius = 5.dp.toPx(), center = Offset(leftPx + roiWidthPx / 2f, topPx))
            drawCircle(handleColor, radius = 5.dp.toPx(), center = Offset(leftPx + roiWidthPx / 2f, topPx + roiHeightPx))
            drawCircle(handleColor, radius = 5.dp.toPx(), center = Offset(leftPx, topPx + roiHeightPx / 2f))
            drawCircle(handleColor, radius = 5.dp.toPx(), center = Offset(leftPx + roiWidthPx, topPx + roiHeightPx / 2f))
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
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { opt ->
                val isDisallowed = opt.code == disallowedCode
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opt.displayName,
                            color = if (isDisallowed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
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
