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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.domain.util.SubtitleExportType
import com.example.domain.util.SubtitleFormatter
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
    maxCharsPerLine: Int = AppSettings().maxCharsPerLine,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportType by remember { mutableStateOf(SubtitleExportType.TRANSLATED_ONLY) }

    val sanitizedTitle = remember(project?.title) {
        (project?.title ?: "legendas")
            .replace("\\.[^.]+$".toRegex(), "")
            .replace("[/\\\\:*?\"<>|]".toRegex(), "_")
            .trim()
            .ifBlank { "legendas" }
    }

    val srtContent = remember(subtitleLines, exportType, maxCharsPerLine) {
        SubtitleFormatter.toSrt(subtitleLines, exportType, maxCharsPerLine)
    }

    val vttContent = remember(subtitleLines, exportType, maxCharsPerLine) {
        SubtitleFormatter.toWebVtt(subtitleLines, exportType, maxCharsPerLine)
    }

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
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Exportar Legendas",
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
            // Subtitle Content Mode Filter
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
                        text = "Conteúdo das Legendas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = exportType == SubtitleExportType.TRANSLATED_ONLY,
                            onClick = { exportType = SubtitleExportType.TRANSLATED_ONLY },
                            label = { Text("Apenas Traduzido") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null
                        )
                        FilterChip(
                            selected = exportType == SubtitleExportType.BILINGUAL,
                            onClick = { exportType = SubtitleExportType.BILINGUAL },
                            label = { Text("Bilíngue (2 Linhas)") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null
                        )
                        FilterChip(
                            selected = exportType == SubtitleExportType.ORIGINAL_ONLY,
                            onClick = { exportType = SubtitleExportType.ORIGINAL_ONLY },
                            label = { Text("Original (OCR)") },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryContainerViolet,
                                selectedLabelColor = OnPrimaryContainerViolet,
                                containerColor = SurfaceContainerHigh,
                                labelColor = OnSurfaceVariantText
                            ),
                            border = null
                        )
                    }
                }
            }

            // Export Actions (.SRT & .VTT)
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
                        text = "Arquivos de Legenda (Modo A)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )
                    Text(
                        text = "Compatível com players externos (VLC, MX Player, Smart TVs, etc.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariantText
                    )

                    Button(
                        onClick = { createSrtLauncher.launch("$sanitizedTitle.srt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_srt_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = OnPrimaryFixed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_export_srt), fontWeight = FontWeight.Bold, color = OnPrimaryFixed)
                    }

                    OutlinedButton(
                        onClick = { createVttLauncher.launch("$sanitizedTitle.vtt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_vtt_button"),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = OnSurfaceText)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_export_vtt), color = OnSurfaceText)
                    }
                }
            }

            // Quick Sharing & Clipboard
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
                        text = "Ações Rápidas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Legendas SRT", srtContent)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Legenda copiada para a área de transferência!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_srt_button"),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = OnSurfaceText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar Texto", color = OnSurfaceText, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val dir = File(context.cacheDir, "subtitles").apply { mkdirs() }
                                        val cacheFile = File(dir, "$sanitizedTitle.srt")
                                        cacheFile.writeText(srtContent, Charsets.UTF_8)
                                        val contentUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            cacheFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        withContext(Dispatchers.Main) {
                                            val chooser = Intent.createChooser(shareIntent, "Compartilhar legenda")
                                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(chooser)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_srt_button"),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = PrimaryFixedDim, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartilhar", color = PrimaryFixedDim, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
