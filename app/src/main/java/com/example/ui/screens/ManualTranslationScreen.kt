package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.ui.components.SubTranslateTopBar
import com.example.ui.theme.OnPrimaryContainerViolet
import com.example.ui.theme.OnPrimaryFixed
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
import com.example.ui.viewmodel.ManualTranslationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTranslationScreen(
    project: Project?,
    subtitleLines: List<SubtitleLine>,
    uiState: ManualTranslationUiState,
    buildExportContent: () -> String,
    onImportContent: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onClearUserMessage: () -> Unit,
    onOpenEditorClick: () -> Unit,
    onExportSubtitlesClick: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val translatedCount = subtitleLines.count { it.translatedText.isNotBlank() }
    val totalCount = subtitleLines.size

    val sanitizedTitle = remember(project?.title) {
        (project?.title ?: "legendas")
            .replace("\\.[^.]+$".toRegex(), "")
            .replace("[/\\\\:*?\"<>|]".toRegex(), "_")
            .trim()
            .ifBlank { "legendas" }
    }
    val exportFileName = "${sanitizedTitle}_para_traduzir.txt"

    LaunchedEffect(uiState.userMessage) {
        val message = uiState.userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearUserMessage()
    }

    val createTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val content = buildExportContent()
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Não foi possível abrir o arquivo para escrita")
                    stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    withContext(Dispatchers.Main) {
                        onShowMessage("Arquivo salvo! Traduza as frases e depois importe o arquivo aqui.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onShowMessage("Erro ao salvar arquivo: ${e.message}")
                    }
                }
            }
        }
    }

    val openTranslatedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        if (data.size > MAX_IMPORT_BYTES) {
                            throw IOException("Arquivo muito grande (máximo 5 MB)")
                        }
                        data
                    } ?: throw IOException("Não foi possível ler o arquivo")
                    val content = String(bytes, Charsets.UTF_8)
                    withContext(Dispatchers.Main) { onImportContent(content) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onShowMessage("Erro ao ler arquivo: ${e.message}")
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Extrair Texto e Traduzir",
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Card
            item {
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
                            text = project?.title ?: "Projeto",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(shape = CircleShape, color = SurfaceContainerHigh) {
                                Text(
                                    text = "${(project?.sourceLanguage ?: "").uppercase()} ➔ ${(project?.targetLanguage ?: "").uppercase()}",
                                    color = PrimaryFixedDim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$translatedCount de $totalCount frases traduzidas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceText
                            )
                            val pct = if (totalCount == 0) 0 else ((translatedCount.toFloat() / totalCount) * 100).toInt()
                            Text(
                                text = "$pct%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pct == 100) SuccessEmerald else PrimaryFixedDim
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainerHighest)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (totalCount == 0) 0f else (translatedCount.toFloat() / totalCount).coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(listOf(SecondaryBlue, PrimaryFixedDim))
                                    )
                            )
                        }
                    }
                }
            }

            // Step 1 - export
            item {
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
                        StepTitle(number = 1, title = "Gerar arquivo editável")
                        Text(
                            text = "Salve o texto extraído em um arquivo .TXT. Cada frase fica abaixo de um marcador [1], [2], [3]... Traduza o texto mantendo os marcadores.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantText
                        )
                        Button(
                            onClick = { createTxtLauncher.launch(exportFileName) },
                            enabled = totalCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("manual_export_txt_button"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = OnPrimaryFixed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Salvar arquivo .TXT", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val content = buildExportContent()
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                                            val file = File(dir, exportFileName)
                                            file.writeText(content, Charsets.UTF_8)
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            withContext(Dispatchers.Main) {
                                                val chooser = Intent.createChooser(sendIntent, "Compartilhar texto para tradução")
                                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(chooser)
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                onShowMessage("Erro ao compartilhar: ${e.message}")
                                            }
                                        }
                                    }
                                },
                                enabled = totalCount > 0,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceText)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Compartilhar", color = OnSurfaceText)
                            }
                            OutlinedButton(
                                onClick = {
                                    val content = buildExportContent()
                                    if (content.length > 150_000) {
                                        onShowMessage("Texto muito grande para copiar. Use \"Salvar arquivo .TXT\".")
                                    } else {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Texto para tradução", content))
                                        Toast.makeText(context, "Texto copiado!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = totalCount > 0,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceText)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar", color = OnSurfaceText)
                            }
                        }
                    }
                }
            }

            // Step 2 - import
            item {
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
                        StepTitle(number = 2, title = "Importar tradução")
                        Text(
                            text = "Depois de traduzir, abra o arquivo aqui (.TXT, .SRT ou .VTT) ou cole o texto traduzido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariantText
                        )
                        Button(
                            onClick = {
                                openTranslatedLauncher.launch(
                                    arrayOf("text/*", "application/x-subrip", "application/octet-stream")
                                )
                            },
                            enabled = totalCount > 0 && !uiState.isImporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("manual_import_button"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                        ) {
                            if (uiState.isImporting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = OnPrimaryFixed)
                            } else {
                                Icon(Icons.Default.UploadFile, contentDescription = null, tint = OnPrimaryFixed)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir arquivo traduzido", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = clipboard.primaryClip
                                    ?.takeIf { it.itemCount > 0 }
                                    ?.getItemAt(0)
                                    ?.coerceToText(context)
                                    ?.toString()
                                if (text.isNullOrBlank()) {
                                    onShowMessage("A área de transferência está vazia.")
                                } else {
                                    onImportContent(text)
                                }
                            },
                            enabled = totalCount > 0 && !uiState.isImporting,
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Colar tradução", color = OnSurfaceText)
                        }
                    }
                }
            }

            // Step 3 - review / export subtitles
            item {
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
                        StepTitle(number = 3, title = "Revisar e gerar legenda")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenEditorClick,
                                enabled = totalCount > 0,
                                shape = CircleShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnSurfaceText)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Editor", color = OnSurfaceText)
                            }
                            Button(
                                onClick = onExportSubtitlesClick,
                                enabled = translatedCount > 0,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp), tint = OnPrimaryFixed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SRT / VTT", fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Frases extraídas ($totalCount)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceText,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            itemsIndexed(subtitleLines, key = { _, line -> line.id }) { position, line ->
                M3PhraseRow(number = position + 1, line = line)
            }
        }
    }
}

@Composable
private fun StepTitle(number: Int, title: String) {
    Text(
        text = "$number. $title",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = OnSurfaceText
    )
}

@Composable
private fun M3PhraseRow(number: Int, line: SubtitleLine) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceContainerHighest,
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text(
                    text = "#$number",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = SecondaryBlue,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${line.formattedStartTime()} ➔ ${line.formattedEndTime()}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceVariantText
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = line.originalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceText
                )
                if (line.translatedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryFixedDim,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
