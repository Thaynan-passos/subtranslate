package com.example.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.OnPrimaryContainerViolet
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
import com.example.ui.theme.SecondaryFixed
import com.example.ui.theme.SecondaryFixedDim
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ReviewEditorUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditorScreen(
    project: Project?,
    subtitleLines: List<SubtitleLine>,
    settings: AppSettings,
    uiState: ReviewEditorUiState,
    onSelectLine: (Long?) -> Unit,
    onToggleCoverOverlay: (Boolean) -> Unit,
    onUpdateLineText: (Long, String, String, Boolean) -> Unit,
    onUpdateLineTimestamps: (Long, Long, Long) -> Unit,
    onDeleteLine: (Long) -> Unit,
    onMergeWithNext: (Long) -> Unit,
    onSplitLine: (Long, Long) -> Unit,
    onRetranslateLine: (Long) -> Unit,
    onAddLine: (Long) -> Unit,
    onClearUserMessage: () -> Unit,
    onExportClick: () -> Unit,
    onBackClick: () -> Unit,
    onRequestTranslateOnline: (suspend (String) -> Result<String>)? = null
) {
    val context = LocalContext.current
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var isPlayerPlaying by remember { mutableStateOf(false) }
    var editingLine by remember { mutableStateOf<SubtitleLine?>(null) }
    var splittingLine by remember { mutableStateOf<SubtitleLine?>(null) }
    var selectedFilter by remember { mutableStateOf("all") }
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
                    isPlayerPlaying = isPlaying
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

    val filteredLines = when (selectedFilter) {
        "reviewed" -> subtitleLines.filter { !it.isFlagged }
        "slang" -> subtitleLines.filter { it.translatedText.isNotBlank() }
        "fallback" -> subtitleLines.filter { it.isFlagged }
        else -> subtitleLines
    }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Editor de Legendas",
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = onExportClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("export_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Exportar",
                            tint = PrimaryFixedDim,
                            modifier = Modifier.size(22.dp)
                        )
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
            // ==========================================
            // 1. Mini Video Preview Card with Live Overlay (Cinematic M3)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(20.dp),
                color = SurfaceContainerLowest,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f))
            ) {
                Column {
                    val videoAspect = if (project != null && project.videoWidth > 0 && project.videoHeight > 0) {
                        (project.videoWidth.toFloat() / project.videoHeight.toFloat()).coerceIn(0.3f, 3.0f)
                    } else {
                        16f / 9f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspect)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
                                        useController = false
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Floating Overlays: Left "PRÉVIA 1080p", Right "1.0x"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerLowest.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF87171))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "PRÉVIA 1080p",
                                        color = OnSurfaceText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = SurfaceContainerLowest.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = PrimaryFixedDim,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "1.0x",
                                        color = PrimaryFixedDim,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Active On-Screen Subtitle Simulation over ROI
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
                                            color = if (uiState.isCoverOverlayActive) overlayColor else Color(0x99090F15),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            width = if (uiState.isCoverOverlayActive) 1.dp else 0.5.dp,
                                            color = if (uiState.isCoverOverlayActive) PrimaryViolet else PrimaryViolet.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = displayText,
                                        color = if (uiState.isCoverOverlayActive) subtitleTextColor else PrimaryFixed,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = settings.subtitleFontSizeSp.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Scrubber Bar & Playback Controls
                    Surface(
                        color = SurfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val durationMs = project?.durationMs ?: 1L
                            val progressFraction = (currentPlaybackPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                            // Scrubber Timeline bar interativa com Seek e thumb proporcional
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .pointerInput(durationMs) {
                                        detectTapGestures { offset ->
                                            if (size.width > 0 && durationMs > 0L) {
                                                val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                val targetMs = (fraction * durationMs).toLong()
                                                exoPlayer?.seekTo(targetMs)
                                                currentPlaybackPosition = targetMs
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val totalWidth = maxWidth
                                // Background Track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceContainerHighest)
                                )
                                // Active progress
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryFixedDim)
                                )
                                // Thumb perfeitamente posicionado proporcionalmente na largura
                                val thumbOffset = ((totalWidth - 12.dp) * progressFraction).coerceAtLeast(0.dp)
                                Box(
                                    modifier = Modifier
                                        .offset(x = thumbOffset)
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryBlue)
                                        .shadow(4.dp, CircleShape)
                                )
                            }

                            // Time stamps and playback transport controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val curSec = currentPlaybackPosition / 1000
                                val curMin = curSec / 60
                                val curS = curSec % 60

                                val totSec = (project?.durationMs ?: 0L) / 1000
                                val totMin = totSec / 60
                                val totS = totSec % 60

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(SecondaryBlue)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${String.format("%02d:%02d", curMin, curS)} / ${String.format("%02d:%02d", totMin, totS)}",
                                        color = SecondaryBlue,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Skip previous
                                    IconButton(
                                        onClick = {
                                            val prev = subtitleLines.lastOrNull { it.endTimeMs < currentPlaybackPosition }
                                            if (prev != null) {
                                                exoPlayer?.seekTo(prev.startTimeMs)
                                                currentPlaybackPosition = prev.startTimeMs
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Segmento anterior",
                                            tint = OnSurfaceVariantText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Play/Pause circular button
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryContainerViolet,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                if (isPlayerPlaying) {
                                                    exoPlayer?.pause()
                                                    isPlayerPlaying = false
                                                } else {
                                                    exoPlayer?.play()
                                                    isPlayerPlaying = true
                                                }
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isPlayerPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlayerPlaying) "Pausar" else "Reproduzir",
                                                tint = OnPrimaryContainerViolet,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Skip next
                                    IconButton(
                                        onClick = {
                                            val next = subtitleLines.firstOrNull { it.startTimeMs > currentPlaybackPosition }
                                            if (next != null) {
                                                exoPlayer?.seekTo(next.startTimeMs)
                                                currentPlaybackPosition = next.startTimeMs
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Próximo segmento",
                                            tint = OnSurfaceVariantText,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. Summary Metric & Live Indicators Bar
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PrimaryFixedDim)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${subtitleLines.size} Legendas Extraídas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = SurfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = SecondaryBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Auto-Sync Ativo",
                            color = OnSurfaceVariantText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ==========================================
            // 3. Filter Chips (Horizontal Scrollable Strip)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                M3FilterPill(
                    label = "Todas (${subtitleLines.size})",
                    isSelected = selectedFilter == "all",
                    icon = Icons.Default.Check,
                    onClick = { selectedFilter = "all" }
                )
                M3FilterPill(
                    label = "Revisadas",
                    isSelected = selectedFilter == "reviewed",
                    icon = Icons.Default.Verified,
                    onClick = { selectedFilter = "reviewed" }
                )
                M3FilterPill(
                    label = "Com Gírias / Gemini",
                    isSelected = selectedFilter == "slang",
                    icon = Icons.Default.AutoAwesome,
                    onClick = { selectedFilter = "slang" }
                )
                M3FilterPill(
                    label = "Fallback ML Kit",
                    isSelected = selectedFilter == "fallback",
                    icon = Icons.Default.OfflineBolt,
                    onClick = { selectedFilter = "fallback" }
                )
            }

            // Controls Bar (Toggle Mask & Add Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleCoverOverlay(!uiState.isCoverOverlayActive) }
                ) {
                    Icon(
                        imageVector = if (uiState.isCoverOverlayActive) Icons.Default.Layers else Icons.Default.LayersClear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PrimaryFixedDim
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cobrir legenda original",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = uiState.isCoverOverlayActive,
                        onCheckedChange = onToggleCoverOverlay,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnPrimaryFixed,
                            checkedTrackColor = PrimaryFixedDim,
                            uncheckedThumbColor = OnSurfaceVariantText,
                            uncheckedTrackColor = SurfaceContainerHighest
                        ),
                        modifier = Modifier.testTag("cover_overlay_switch")
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = SurfaceContainerHigh,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onAddLine(currentPlaybackPosition) }
                        .testTag("add_subtitle_line_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryFixedDim, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Adicionar Fala",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryFixedDim
                        )
                    }
                }
            }

            // ==========================================
            // 4. Subtitle Comparison Stream (Dual Language Cards)
            // ==========================================
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredLines, key = { it.id }) { line ->
                    val isActive = currentPlaybackPosition in line.startTimeMs..line.endTimeMs
                    M3SubtitleLineCard(
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
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    // Edit Subtitle Dialog
    if (editingLine != null) {
        EditSubtitleDialog(
            line = editingLine!!,
            videoDurationMs = project?.durationMs ?: Long.MAX_VALUE,
            onRequestTranslateOnline = onRequestTranslateOnline,
            onDismiss = { editingLine = null },
            onSave = { updatedOrig, updatedTrans, startMs, endMs, saveToMemory ->
                onUpdateLineText(editingLine!!.id, updatedOrig, updatedTrans, saveToMemory)
                onUpdateLineTimestamps(editingLine!!.id, startMs, endMs)
                editingLine = null
            }
        )
    }

    // Split Line Dialog
    if (splittingLine != null) {
        val targetLine = splittingLine!!
        var splitPosSec by remember(targetLine.id) {
            val midMs = (targetLine.startTimeMs + targetLine.endTimeMs) / 2
            mutableStateOf(String.format(java.util.Locale.US, "%.2f", midMs / 1000f))
        }

        AlertDialog(
            onDismissRequest = { splittingLine = null },
            containerColor = SurfaceContainerHigh,
            title = {
                Text(
                    text = "Dividir Fala #${targetLine.index}",
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Informe o tempo de corte (em segundos) entre ${(targetLine.startTimeMs / 1000f)}s e ${(targetLine.endTimeMs / 1000f)}s:",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText
                    )
                    OutlinedTextField(
                        value = splitPosSec,
                        onValueChange = { splitPosSec = it },
                        label = { Text("Tempo de corte (s)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceText,
                            unfocusedTextColor = OnSurfaceText,
                            focusedBorderColor = PrimaryViolet,
                            unfocusedBorderColor = OutlineVariantBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sec = splitPosSec.toFloatOrNull()
                        if (sec != null) {
                            val splitMs = (sec * 1000).toLong()
                            onSplitLine(targetLine.id, splitMs)
                        }
                        splittingLine = null
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                ) {
                    Text("Dividir", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                }
            },
            dismissButton = {
                TextButton(onClick = { splittingLine = null }) {
                    Text("Cancelar", color = OnSurfaceVariantText)
                }
            }
        )
    }
}

@Composable
private fun M3FilterPill(
    label: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isSelected) PrimaryContainerViolet else SurfaceContainerHigh,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) OnPrimaryContainerViolet else PrimaryFixedDim,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) OnPrimaryContainerViolet else OnSurfaceVariantText
            )
        }
    }
}

@Composable
fun M3SubtitleLineCard(
    line: SubtitleLine,
    isActive: Boolean,
    onSeekTo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onRetranslate: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) SurfaceContainerHigh else SurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = if (isActive) PrimaryViolet else OutlineVariantBorder.copy(alpha = 0.5f)
        ),
        shadowElevation = if (isActive) 6.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onSeekTo)
            .testTag("subtitle_line_${line.id}")
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Glowing Accent Strip for Active Segment
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(180.dp)
                        .background(PrimaryFixedDim)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header Meta Row: Index, Timecode, Engine Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SurfaceContainerHighest
                        ) {
                            Text(
                                text = "#${String.format("%03d", line.index)}",
                                color = SecondaryBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = PrimaryFixedDim,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${line.formattedStartTime()} ➔ ${line.formattedEndTime()}",
                                color = OnSurfaceText,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Engine Badge
                    Surface(
                        shape = CircleShape,
                        color = PrimaryContainerViolet.copy(alpha = 0.25f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = PrimaryFixedDim,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gemini IA",
                                color = PrimaryFixedDim,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Dual Language Stack
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Original OCR Block
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ORIGINAL EM INGLÊS (OCR)",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = OnSurfaceVariantText,
                                    letterSpacing = 0.05.sp
                                )
                                Text(
                                    text = "Precisão 99.4%",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = SecondaryFixedDim
                                )
                            }
                            Text(
                                text = "“${line.originalText}”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = OnSurfaceVariantText
                            )
                        }
                    }

                    // Translated Block (PT-BR)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isActive) SurfaceContainerHighest else SurfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TRADUÇÃO EM PORTUGUÊS (PT-BR)",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryFixedDim,
                                    letterSpacing = 0.05.sp
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryViolet.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Linguagem Informal",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = PrimaryFixed,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (line.translatedText.isNotBlank()) "“${line.translatedText}”" else "“${line.originalText}”",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceText,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Quick Action Bar on Card Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onSeekTo, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Ouvir trecho", tint = SecondaryBlue, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = OnSurfaceText, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onRetranslate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Re-traduzir", tint = PrimaryFixedDim, modifier = Modifier.size(16.dp))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onSplit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.CallSplit, contentDescription = "Dividir fala", tint = OnSurfaceVariantText, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onMerge, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.CallMerge, contentDescription = "Mesclar com próxima", tint = OnSurfaceVariantText, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = ErrorCrimson, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditSubtitleDialog(
    line: SubtitleLine,
    videoDurationMs: Long,
    onRequestTranslateOnline: (suspend (String) -> Result<String>)? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, Long, Boolean) -> Unit
) {
    var original by remember(line.id) { mutableStateOf(line.originalText) }
    var translated by remember(line.id) { mutableStateOf(line.translatedText) }
    var startSec by remember(line.id) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", line.startTimeMs / 1000f)) }
    var endSec by remember(line.id) { mutableStateOf(String.format(java.util.Locale.US, "%.2f", line.endTimeMs / 1000f)) }
    var saveToMemory by remember(line.id) { mutableStateOf(true) }
    var isTranslatingOnline by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceContainerHigh,
        title = {
            Text(
                text = "Editar Fala #${line.index}",
                fontWeight = FontWeight.Bold,
                color = OnSurfaceText
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = original,
                    onValueChange = {
                        original = it
                        onlineError = null
                    },
                    label = { Text("Texto Original (Inglês)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurfaceText,
                        unfocusedTextColor = OnSurfaceText,
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = OutlineVariantBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = translated,
                    onValueChange = { translated = it },
                    label = { Text("Tradução (Português)") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = OnSurfaceText,
                        unfocusedTextColor = OnSurfaceText,
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = OutlineVariantBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startSec,
                        onValueChange = { startSec = it },
                        label = { Text("Início (s)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceText,
                            unfocusedTextColor = OnSurfaceText,
                            focusedBorderColor = PrimaryViolet,
                            unfocusedBorderColor = OutlineVariantBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endSec,
                        onValueChange = { endSec = it },
                        label = { Text("Fim (s)") },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurfaceText,
                            unfocusedTextColor = OnSurfaceText,
                            focusedBorderColor = PrimaryViolet,
                            unfocusedBorderColor = OutlineVariantBorder
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // AI Retranslate trigger in modal
                if (onRequestTranslateOnline != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isTranslatingOnline = true
                                val res = onRequestTranslateOnline(original)
                                res.onSuccess { translated = it }
                                res.onFailure { onlineError = it.message }
                                isTranslatingOnline = false
                            }
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTranslatingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryFixedDim, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-traduzir com Gemini IA", color = PrimaryFixedDim)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { saveToMemory = !saveToMemory }
                ) {
                    Checkbox(
                        checked = saveToMemory,
                        onCheckedChange = { saveToMemory = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryViolet)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Salvar tradução no glossário de memória",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sSec = startSec.toFloatOrNull() ?: 0f
                    val eSec = endSec.toFloatOrNull() ?: 0f
                    val sMs = (sSec * 1000).toLong().coerceIn(0L, videoDurationMs)
                    val eMs = (eSec * 1000).toLong().coerceIn(sMs, videoDurationMs)
                    onSave(original, translated, sMs, eMs, saveToMemory)
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
            ) {
                Text("Salvar", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = OnSurfaceVariantText)
            }
        }
    )
}

// Backward-compatibility alias
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
) = M3SubtitleLineCard(line, isActive, onSeekTo, onEdit, onDelete, onMerge, onSplit, onRetranslate)
