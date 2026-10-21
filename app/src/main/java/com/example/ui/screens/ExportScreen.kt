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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.domain.util.SubtitleExportType
import com.example.domain.util.SubtitleFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    project: Project?,
    subtitleLines: List<SubtitleLine>,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportType by remember { mutableStateOf(SubtitleExportType.TRANSLATED_ONLY) }

    val sanitizedTitle = remember(project?.title) {
        (project?.title ?: "legendas").replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
    }

    val srtContent = remember(subtitleLines, exportType) {
        SubtitleFormatter.toSrt(subtitleLines, exportType)
    }

    val vttContent = remember(subtitleLines, exportType) {
        SubtitleFormatter.toWebVtt(subtitleLines, exportType)
    }

    // Save File SAF Launcher for SRT
    val createSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Não foi possível abrir o arquivo para escrita")
                    stream.use { os ->
                        os.write(srtContent.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Arquivo .SRT salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao salvar .SRT: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Save File SAF Launcher for VTT
    val createVttLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/vtt")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Não foi possível abrir o arquivo para escrita")
                    stream.use { os ->
                        os.write(vttContent.toByteArray(Charsets.UTF_8))
                        os.flush()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Arquivo .VTT salvo com sucesso!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao salvar .VTT: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_export), fontWeight = FontWeight.Bold) },
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
            // Export Subtitle Type Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Conteúdo das Legendas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exportType == SubtitleExportType.TRANSLATED_ONLY,
                            onClick = { exportType = SubtitleExportType.TRANSLATED_ONLY },
                            label = { Text("Apenas Traduzido") }
                        )
                        FilterChip(
                            selected = exportType == SubtitleExportType.BILINGUAL,
                            onClick = { exportType = SubtitleExportType.BILINGUAL },
                            label = { Text("Bilíngue (2 Linhas)") }
                        )
                        FilterChip(
                            selected = exportType == SubtitleExportType.ORIGINAL_ONLY,
                            onClick = { exportType = SubtitleExportType.ORIGINAL_ONLY },
                            label = { Text("Original (OCR)") }
                        )
                    }
                }
            }

            // Export Actions (.SRT & .VTT)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Arquivos de Legenda (Modo A)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Compatível com players externos (VLC, MX Player, etc.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            createSrtLauncher.launch("$sanitizedTitle.srt")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_srt_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_export_srt))
                    }

                    OutlinedButton(
                        onClick = {
                            createVttLauncher.launch("$sanitizedTitle.vtt")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("export_vtt_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_export_vtt))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Share using FileProvider and EXTRA_STREAM to prevent TransactionTooLargeException
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val subDir = File(context.cacheDir, "subtitles")
                                        if (!subDir.exists()) subDir.mkdirs()
                                        val shareFile = File(subDir, "$sanitizedTitle.srt")
                                        shareFile.writeText(srtContent, Charsets.UTF_8)

                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            shareFile
                                        )

                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/x-subrip"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        withContext(Dispatchers.Main) {
                                            val chooser = Intent.createChooser(sendIntent, "Compartilhar Legenda")
                                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(chooser)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Erro ao compartilhar arquivo: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.btn_share))
                        }

                        // Copy to clipboard with size truncation limit to avoid TransactionTooLargeException
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val safeText = if (srtContent.length > 150_000) {
                                    srtContent.substring(0, 150_000) + "\n...[texto truncado para evitar limite do sistema]"
                                } else {
                                    srtContent
                                }
                                val clip = ClipData.newPlainText("Legendas", safeText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Texto copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar")
                        }
                    }
                }
            }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Modo B — preview no editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "O Modo B é apenas uma pré-visualização da substituição visual no player do editor. Ele não exporta nem queima as legendas no arquivo de vídeo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "A pré-visualização usa a posição da ROI para cobrir a legenda original. Para exportação, salve ou compartilhe o arquivo .SRT / .VTT.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
