package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.ui.viewmodel.ManualTranslationUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

/**
 * "Extrair Texto" screen: lets the user export the OCR text as an editable file, translate it
 * outside the app and import the translated file back.
 */
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

    // Save the editable .txt file via SAF
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

    // Open the translated file (.txt, .srt or .vtt)
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
        topBar = {
            TopAppBar(
                title = { Text("Extrair Texto e Traduzir", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Summary
            item {
                SectionCard {
                    Text(
                        text = project?.title ?: "Projeto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${(project?.sourceLanguage ?: "").uppercase()} -> ${(project?.targetLanguage ?: "").uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$translatedCount de $totalCount frases traduzidas",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { if (totalCount == 0) 0f else translatedCount.toFloat() / totalCount },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Step 1 - export
            item {
                SectionCard {
                    StepTitle(number = 1, title = "Gerar arquivo editável")
                    Text(
                        text = "Salve o texto extraído em um arquivo .TXT. Cada frase fica abaixo de um marcador [1], [2], [3]... " +
                                "Traduza o texto (à mão, no Google Tradutor, DeepL, ChatGPT etc.) mantendo os marcadores.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { createTxtLauncher.launch(exportFileName) },
                        enabled = totalCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("manual_export_txt_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Salvar arquivo .TXT")
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartilhar")
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
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar")
                        }
                    }
                }
            }

            // Step 2 - import
            item {
                SectionCard {
                    StepTitle(number = 2, title = "Importar tradução")
                    Text(
                        text = "Depois de traduzir, abra o arquivo aqui (.TXT com marcadores, .SRT ou .VTT) ou cole o texto traduzido. " +
                                "As frases são associadas pelo número do marcador.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            .height(50.dp)
                            .testTag("manual_import_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir arquivo traduzido")
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Colar tradução da área de transferência")
                    }
                }
            }

            // Step 3 - review / export subtitles
            item {
                SectionCard {
                    StepTitle(number = 3, title = "Revisar e gerar legenda")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenEditorClick,
                            enabled = totalCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editor")
                        }
                        Button(
                            onClick = onExportSubtitlesClick,
                            enabled = translatedCount > 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SRT / VTT")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Frases extraídas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            itemsIndexed(subtitleLines, key = { _, line -> line.id }) { position, line ->
                PhraseRow(number = position + 1, line = line)
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun StepTitle(number: Int, title: String) {
    Text(
        text = "$number. $title",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PhraseRow(number: Int, line: SubtitleLine) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Text(
                text = "[$number]",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${line.formattedStartTime()} → ${line.formattedEndTime()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = line.originalText, style = MaterialTheme.typography.bodyMedium)
                if (line.translatedText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

