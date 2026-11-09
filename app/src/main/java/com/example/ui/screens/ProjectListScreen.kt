package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.OutputMode
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
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
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SurfaceCanvas
import com.example.ui.theme.SurfaceContainer
import com.example.ui.theme.SurfaceContainerHigh
import com.example.ui.theme.SurfaceContainerHighest
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    projects: List<Project>,
    onNewProjectClick: () -> Unit,
    onProjectClick: (Project) -> Unit,
    onSettingsClick: () -> Unit,
    onDeleteProject: (Long) -> Unit,
    selectedMode: OutputMode = OutputMode.SRT_ONLY,
    onModeSelected: (OutputMode) -> Unit = {}
) {
    val isExtractMode = selectedMode == OutputMode.EXTRACT_ONLY
    val visibleProjects = projects.filter { it.outputMode == selectedMode }

    Scaffold(
        containerColor = SurfaceCanvas,
        topBar = {
            SubTranslateTopBar(
                subtitle = "Projetos e Traduções",
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.title_settings),
                            tint = OnSurfaceVariantText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceCanvas,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = !isExtractMode,
                    onClick = { onModeSelected(OutputMode.SRT_ONLY) },
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    label = { Text("Tradução Automática") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryViolet,
                        selectedTextColor = OnSurfaceText,
                        indicatorColor = PrimaryViolet.copy(alpha = 0.20f),
                        unselectedIconColor = OnSurfaceVariantText,
                        unselectedTextColor = OnSurfaceVariantText
                    ),
                    modifier = Modifier.testTag("tab_auto_translate")
                )
                NavigationBarItem(
                    selected = isExtractMode,
                    onClick = { onModeSelected(OutputMode.EXTRACT_ONLY) },
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null) },
                    label = { Text("Extrair Texto") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryViolet,
                        selectedTextColor = OnSurfaceText,
                        indicatorColor = PrimaryViolet.copy(alpha = 0.20f),
                        unselectedIconColor = OnSurfaceVariantText,
                        unselectedTextColor = OnSurfaceVariantText
                    ),
                    modifier = Modifier.testTag("tab_extract_text")
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProjectClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = OnPrimaryFixed) },
                text = {
                    Text(
                        text = if (isExtractMode) "Extrair Texto" else stringResource(R.string.btn_new_project),
                        color = OnPrimaryFixed,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = PrimaryViolet,
                contentColor = OnPrimaryFixed,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier.testTag("new_project_fab")
            )
        }
    ) { paddingValues ->
        if (visibleProjects.isEmpty()) {
            EmptyProjectsView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                isExtractMode = isExtractMode,
                onNewProjectClick = onNewProjectClick
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleProjects, key = { it.id }) { project ->
                    M3ProjectItemCard(
                        project = project,
                        onClick = { onProjectClick(project) },
                        onDelete = { onDeleteProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsView(
    modifier: Modifier = Modifier,
    isExtractMode: Boolean = false,
    onNewProjectClick: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PrimaryContainerViolet.copy(alpha = 0.35f),
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isExtractMode) Icons.Default.DocumentScanner else Icons.Default.Movie,
                        contentDescription = null,
                        tint = PrimaryFixedDim,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (isExtractMode) "Nenhum texto extraído ainda" else stringResource(R.string.title_empty_projects),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceText
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isExtractMode) {
                    "Extraia o texto das legendas do vídeo, gere um arquivo editável, traduza as frases " +
                            "no seu idioma e importe o arquivo de volta para gerar a legenda."
                } else {
                    stringResource(R.string.desc_empty_projects)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariantText,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun M3ProjectItemCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("project_item_${project.id}"),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineVariantBorder.copy(alpha = 0.5f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video icon in Secondary container
            Box(
                modifier = Modifier
                    .size(52.dp)
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

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusChip(status = project.status)

                    Surface(
                        shape = CircleShape,
                        color = SurfaceContainerHigh
                    ) {
                        Text(
                            text = "${project.sourceLanguage.uppercase()} ➔ ${project.targetLanguage.uppercase()}",
                            color = PrimaryFixedDim,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(project.createdAt))
                Text(
                    text = "$dateStr • ${project.linesCount} falas",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = OnSurfaceVariantText
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("delete_project_${project.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Excluir projeto",
                    tint = ErrorCrimson.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = OnSurfaceVariantText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Backward compatibility alias
@Composable
fun ProjectItemCard(
    project: Project,
    onClick: () -> Unit,
    onDelete: () -> Unit
) = M3ProjectItemCard(project, onClick, onDelete)

@Composable
fun StatusChip(status: ProjectStatus) {
    val (color, icon, label) = when (status) {
        ProjectStatus.COMPLETED -> Triple(SuccessEmerald, Icons.Default.CheckCircle, "Concluído")
        ProjectStatus.PROCESSING -> Triple(SecondaryBlue, Icons.Default.HourglassTop, "Processando")
        ProjectStatus.PAUSED -> Triple(WarningAmber, Icons.Default.HourglassTop, "Pausado")
        ProjectStatus.ERROR -> Triple(ErrorCrimson, Icons.Default.Warning, "Erro")
        ProjectStatus.IDLE -> Triple(OnSurfaceVariantText, Icons.Default.HourglassTop, "Pronto")
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = color,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
