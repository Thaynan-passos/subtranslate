package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.ui.viewmodel.ReviewEditorUiState
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditorScreen(
    project: Project?,
    subtitleLines: List<SubtitleLine>,
    settings: AppSettings,
    uiState: ReviewEditorUiState,
    onSelectLine: (Long?) -> Unit,
    onToggleCoverOverlay: (Boolean) -> Unit,
    onUpdateLineText: (Long, String, String) -> Unit,
    onUpdateLineTimestamps: (Long, Long, Long) -> Unit,
    onDeleteLine: (Long) -> Unit,
    onMergeWithNext: (Long) -> Unit,
    onSplitLine: (Long, Long) -> Unit,
    onRetranslateLine: (Long) -> Unit,
    onAddLine: (Long) -> Unit,
    onClearUserMessage: () -> Unit,
    onExportClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var editingLine by remember { mutableStateOf<SubtitleLine?>(null) }
    var splittingLine by remember { mutableStateOf<SubtitleLine?>(null) }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Display user messages / toast errors
    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onClearUserMessage()
        }
    }

    // Initialize ExoPlayer & position listener
    DisposableEffect(project?.videoUri) {
        val uriStr = project?.videoUri
        if (uriStr != null) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
                prepare()
            }

            val listener = object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    currentPlaybackPosition = newPosition.positionMs
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    currentPlaybackPosition = player.currentPosition
                }
            }
            player.addListener(listener)
            exoPlayer = player
        }

        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Pause player on lifecycle ON_STOP
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer?.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Polling player playback position when active
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            if (player.isPlaying) {
                currentPlaybackPosition = player.currentPosition
            }
            delay(100)
        }
    }

    val activeLine = subtitleLines.firstOrNull {
        currentPlaybackPosition in it.startTimeMs..it.endTimeMs
    }

    val overlayColor = remember(settings.overlayColorHex) {
        try {
            Color(android.graphics.Color.parseColor(settings.overlayColorHex))
        } catch (_: Exception) {
            Color(0xF0000000)
        }
    }
    val subtitleTextColor = remember(settings.subtitleTextColorHex) {
        try {
            Color(android.graphics.Color.parseColor(settings.subtitleTextColorHex))
        } catch (_: IllegalArgumentException) {
            Color.White
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.title ?: stringResource(R.string.title_review_editor), fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onExportClick,
                        modifier = Modifier.testTag("export_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Exportar", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Player Area with Real-Time Subtitle Overlay positioned on Project ROI
            val videoAspect = if (project != null && project.videoWidth > 0 && project.videoHeight > 0) {
                (project.videoWidth.toFloat() / project.videoHeight.toFloat()).coerceIn(0.6f, 2.4f)
            } else {
                16f / 9f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspect)
                    .background(Color.Black)
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Subtitle Overlay dynamically placed over the ROI coordinates of the project
                if (activeLine != null && project != null) {
                    val displayText = if (activeLine.translatedText.isNotBlank()) {
                        activeLine.translatedText
                    } else {
                        activeLine.originalText
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val boxW = maxWidth
                        val boxH = maxHeight

                        val roiLeftDp = boxW * project.roiLeft
                        val roiTopDp = boxH * project.roiTop
                        val roiWidthDp = boxW * (project.roiRight - project.roiLeft)
                        val roiHeightDp = boxH * (project.roiBottom - project.roiTop)

                        Box(
                            modifier = Modifier
                                .offset(x = roiLeftDp, y = roiTopDp)
                                .size(width = roiWidthDp, height = roiHeightDp)
                                .background(
                                    color = if (uiState.isCoverOverlayActive) overlayColor else Color(0x66000000),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    width = if (uiState.isCoverOverlayActive) 1.dp else 0.dp,
                                    color = if (uiState.isCoverOverlayActive) Color(0xFF38BDF8) else Color.Transparent,
                                    shape = RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayText,
                                color = subtitleTextColor,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = settings.subtitleFontSizeSp.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(4.dp),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Controls Bar (Toggle Mask)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (uiState.isCoverOverlayActive) Icons.Default.Layers else Icons.Default.LayersClear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cobrir legenda original no player",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Switch(
                    checked = uiState.isCoverOverlayActive,
                    onCheckedChange = onToggleCoverOverlay,
                    modifier = Modifier.testTag("cover_overlay_switch")
                )
            }

            // Timeline Header & Add Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Linha do Tempo (${subtitleLines.size} falas)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { onAddLine(currentPlaybackPosition) },
                    modifier = Modifier.testTag("add_subtitle_line_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_add_line))
                }
            }

            // Subtitle Lines List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subtitleLines, key = { it.id }) { line ->
                    val isActive = currentPlaybackPosition in line.startTimeMs..line.endTimeMs
                    SubtitleLineCard(
                        line = line,
                        isActive = isActive,
                        onSeekTo = {
                            exoPlayer?.seekTo(line.startTimeMs)
                            currentPlaybackPosition = line.startTimeMs
                        },
                        onEdit = { editingLine = line },
                        onDelete = { onDeleteLine(line.id) },
                        onMerge = { onMergeWithNext(line.id) },
                        onSplit = { splittingLine = line },
                        onRetranslate = { onRetranslateLine(line.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Edit Subtitle Dialog
    if (editingLine != null) {
        EditSubtitleDialog(
            line = editingLine!!,
            videoDurationMs = project?.durationMs ?: Long.MAX_VALUE,
            onDismiss = { editingLine = null },
            onSave = { updatedOrig, updatedTrans, startMs, endMs ->
                onUpdateLineText(editingLine!!.id, updatedOrig, updatedTrans)
                onUpdateLineTimestamps(editingLine!!.id, startMs, endMs)
                editingLine = null
            }
        )
    }

    // Split Line Dialog
    if (splittingLine != null) {
        val targetLine = splittingLine!!
        var splitPosSec by remember {
            val midMs = (targetLine.startTimeMs + targetLine.endTimeMs) / 2
            mutableStateOf(String.format(java.util.Locale.US, "%.2f", midMs / 1000f))
        }

        AlertDialog(
            onDismissRequest = { splittingLine = null },
            title = { Text("Dividir Fala #${targetLine.index}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Informe o tempo de corte (em segundos) entre ${(targetLine.startTimeMs / 1000f)}s e ${(targetLine.endTimeMs / 1000f)}s:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = splitPosSec,
                        onValueChange = { splitPosSec = it },
                        label = { Text("Tempo de corte (s)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val sec = splitPosSec.toFloatOrNull()
                    if (sec != null) {
                        val splitMs = (sec * 1000).toLong()
                        onSplitLine(targetLine.id, splitMs)
                    }
                    splittingLine = null
                }) {
                    Text("Dividir")
                }
            },
            dismissButton = {
                TextButton(onClick = { splittingLine = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun SubtitleLineCard(
    line: SubtitleLine,
    isActive: Boolean,
    onSeekTo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onRetranslate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeekTo)
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("subtitle_line_${line.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with Index, Timestamps, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (line.isFlagged) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "#${line.index}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (line.isFlagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${line.formattedStartTime()} -> ${line.formattedEndTime()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (line.isFlagged) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Revisão sugerida",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onRetranslate, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-traduzir", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onSplit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.CallSplit, contentDescription = "Dividir fala", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onMerge, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.CallMerge, contentDescription = "Mesclar com próxima", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Original OCR Text
            Text(
                text = line.originalText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Translated Text
            if (line.translatedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = line.translatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun EditSubtitleDialog(
    line: SubtitleLine,
    videoDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long) -> Unit
) {
    var original by remember { mutableStateOf(line.originalText) }
    var translated by remember { mutableStateOf(line.translatedText) }
    var startSec by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", line.startTimeMs / 1000f)) }
    var endSec by remember { mutableStateOf(String.format(java.util.Locale.US, "%.2f", line.endTimeMs / 1000f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar Fala #${line.index}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = translated,
                    onValueChange = { translated = it },
                    label = { Text("Texto Traduzido") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_translated_text_input")
                )
                OutlinedTextField(
                    value = original,
                    onValueChange = { original = it },
                    label = { Text("Texto Original (OCR)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startSec,
                        onValueChange = { startSec = it },
                        label = { Text("Início (s)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endSec,
                        onValueChange = { endSec = it },
                        label = { Text("Fim (s)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sSec = startSec.toFloatOrNull() ?: (line.startTimeMs / 1000f)
                    val eSec = endSec.toFloatOrNull() ?: (line.endTimeMs / 1000f)
                    val knownDuration = videoDurationMs.takeIf { it > 0L && it < Long.MAX_VALUE }
                    val maxStart = (knownDuration ?: Long.MAX_VALUE).coerceAtMost(Long.MAX_VALUE - 2_000L)
                    val startMs = (sSec * 1000f).toLong().coerceIn(0L, maxStart)
                    val minimumEnd = startMs + 100L
                    val maxEnd = knownDuration?.coerceAtLeast(minimumEnd) ?: Long.MAX_VALUE
                    val endMs = (eSec * 1000f).toLong()
                        .coerceAtLeast(minimumEnd)
                        .coerceAtMost(maxEnd)

                    onSave(original, translated, startMs, endMs)
                },
                modifier = Modifier.testTag("save_edit_button")
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
