package com.example

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.AppSettings
import com.example.domain.model.OutputMode
import com.example.domain.model.ProjectStatus
import com.example.data.service.SubtitleExtractionService
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.ManualTranslationScreen
import com.example.ui.screens.ProcessingScreen
import com.example.ui.screens.ProjectConfigScreen
import com.example.ui.screens.ProjectListScreen
import com.example.ui.screens.ReviewEditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ManualTranslationViewModel
import com.example.ui.viewmodel.ProcessingViewModel
import com.example.ui.viewmodel.ProjectConfigViewModel
import com.example.ui.viewmodel.ProjectViewModel
import com.example.ui.viewmodel.ReviewEditorViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as SubTranslateApp
        val container = app.container

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Navigation backStack saved across configuration changes and process death
                    val backStack = rememberSaveable(
                        saver = listSaver<SnapshotStateList<String>, String>(
                            save = { it.toList() },
                            restore = { it.toMutableStateList() }
                        )
                    ) { mutableStateListOf("projects") }
                    val currentRoute = backStack.lastOrNull() ?: "projects"

                    fun navigateTo(route: String) {
                        backStack.add(route)
                    }

                    fun navigateBack() {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    }

                    // Request notification permission for Android 13+
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        container.projectRepository.markAbandonedProcessingProjects(
                            SubtitleExtractionService.activeProjectId
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val status = ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            )
                            if (status != PackageManager.PERMISSION_GRANTED) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    val projectViewModel: ProjectViewModel = viewModel(
                        factory = ProjectViewModel.Factory(
                            repository = container.projectRepository,
                            context = applicationContext,
                            extractionPipeline = container.extractionPipeline
                        )
                    )
                    val projects by projectViewModel.projects.collectAsState()

                    val settings by container.settingsRepository.settings.collectAsState(
                        initial = AppSettings()
                    )

                    val scope = rememberCoroutineScope()

                    // Selected home tab: automatic translation or text extraction (manual translation)
                    var homeMode by rememberSaveable { mutableStateOf(OutputMode.SRT_ONLY.name) }

                    when {
                        currentRoute == "projects" -> {
                            ProjectListScreen(
                                projects = projects,
                                selectedMode = OutputMode.valueOf(homeMode),
                                onModeSelected = { homeMode = it.name },
                                onNewProjectClick = {
                                    navigateTo(if (homeMode == OutputMode.EXTRACT_ONLY.name) "config_extract" else "config")
                                },
                                onProjectClick = { project ->
                                    if (project.status == ProjectStatus.COMPLETED) {
                                        if (project.outputMode == OutputMode.EXTRACT_ONLY) {
                                            navigateTo("manual/${project.id}")
                                        } else {
                                            navigateTo("editor/${project.id}")
                                        }
                                    } else {
                                        navigateTo("processing/${project.id}")
                                    }
                                },
                                onSettingsClick = { navigateTo("settings") },
                                onDeleteProject = { projectViewModel.deleteProject(it) }
                            )
                        }

                        currentRoute == "config" || currentRoute == "config_extract" -> {
                            BackHandler { navigateBack() }

                            val configSessionId = rememberSaveable { java.util.UUID.randomUUID().toString() }
                            val configViewModel: ProjectConfigViewModel = viewModel(
                                key = "config_$configSessionId",
                                factory = ProjectConfigViewModel.Factory(
                                    context = applicationContext,
                                    repository = container.projectRepository,
                                    ocrEngine = container.ocrEngine
                                )
                            )
                            DisposableEffect(configViewModel) {
                                onDispose { configViewModel.resetState() }
                            }
                            val uiState by configViewModel.uiState.collectAsState()
                            var initializedDefaultFps by rememberSaveable(configSessionId) { mutableStateOf(false) }
                            var initializedMode by rememberSaveable(configSessionId) { mutableStateOf(false) }

                            // Pre-select the mode matching the tab the user came from
                            LaunchedEffect(currentRoute) {
                                if (!initializedMode) {
                                    configViewModel.setOutputMode(
                                        if (currentRoute == "config_extract") OutputMode.EXTRACT_ONLY else OutputMode.SRT_ONLY
                                    )
                                    initializedMode = true
                                }
                            }

                            // Initialize default FPS from settings
                            LaunchedEffect(settings.defaultFps) {
                                if (!initializedDefaultFps) {
                                    configViewModel.setSamplingFps(settings.defaultFps)
                                    initializedDefaultFps = true
                                }
                            }

                            ProjectConfigScreen(
                                uiState = uiState,
                                onVideoSelected = { uri, name -> configViewModel.setVideo(uri, name) },
                                onRoiUpdated = { l, t, r, b -> configViewModel.updateRoi(l, t, r, b) },
                                onAutoDetectRoi = { configViewModel.autoDetectRoi() },
                                onLanguagesChanged = { src, tgt -> configViewModel.setLanguages(src, tgt) },
                                onSamplingFpsChanged = { fps -> configViewModel.setSamplingFps(fps) },
                                onOutputModeChanged = { mode -> configViewModel.setOutputMode(mode) },
                                onStartExtraction = {
                                    scope.launch {
                                        val newId = configViewModel.createProject()
                                        if (newId != null) {
                                            backStack.removeAt(backStack.lastIndex)
                                            navigateTo("processing/$newId")
                                        }
                                    }
                                },
                                onBackClick = { navigateBack() }
                            )
                        }

                        currentRoute.startsWith("processing/") -> {
                            val projId = currentRoute.substringAfter("processing/").toLongOrNull() ?: 0L
                            BackHandler {
                                backStack.clear()
                                backStack.add("projects")
                            }

                            val processingViewModel: ProcessingViewModel = viewModel(
                                key = "processing_$projId",
                                factory = ProcessingViewModel.Factory(
                                    projectId = projId,
                                    context = applicationContext,
                                    projectRepository = container.projectRepository,
                                    pipeline = container.extractionPipeline
                                )
                            )
                            val pipelineState by processingViewModel.pipelineState.collectAsState()
                            val currentProject by processingViewModel.project.collectAsState()

                            // Auto start only if IDLE (Never auto-start on ERROR or PAUSED)
                            LaunchedEffect(projId, currentProject?.status) {
                                val proj = currentProject
                                if (proj != null) {
                                    if (!pipelineState.isRunning && proj.status == ProjectStatus.IDLE) {
                                        processingViewModel.startProcessing()
                                    }
                                }
                            }

                            ProcessingScreen(
                                project = currentProject,
                                pipelineState = pipelineState,
                                onPauseClick = { processingViewModel.pause() },
                                onResumeClick = { processingViewModel.resume() },
                                onCancelClick = {
                                    scope.launch {
                                        processingViewModel.cancel()
                                        navigateBack()
                                    }
                                },
                                onGoToEditorClick = {
                                    backStack.removeAt(backStack.lastIndex)
                                    if (currentProject?.outputMode == OutputMode.EXTRACT_ONLY) {
                                        navigateTo("manual/$projId")
                                    } else {
                                        navigateTo("editor/$projId")
                                    }
                                },
                                onRetryClick = {
                                    processingViewModel.startProcessing()
                                },
                                onBackClick = {
                                    backStack.clear()
                                    backStack.add("projects")
                                }
                            )
                        }

                        currentRoute.startsWith("editor/") -> {
                            val projId = currentRoute.substringAfter("editor/").toLongOrNull() ?: 0L
                            BackHandler {
                                backStack.clear()
                                backStack.add("projects")
                            }

                            val editorViewModel: ReviewEditorViewModel = viewModel(
                                key = "editor_$projId",
                                factory = ReviewEditorViewModel.Factory(
                                    projectId = projId,
                                    projectRepository = container.projectRepository,
                                    subtitleRepository = container.subtitleRepository,
                                    settingsRepository = container.settingsRepository,
                                    translator = container.translator
                                )
                            )
                            val currentProject by editorViewModel.project.collectAsState()
                            val lines by editorViewModel.subtitleLines.collectAsState()
                            val editorUiState by editorViewModel.uiState.collectAsState()
                            val editorSettings by editorViewModel.settings.collectAsState()

                            ReviewEditorScreen(
                                project = currentProject,
                                subtitleLines = lines,
                                settings = editorSettings,
                                uiState = editorUiState,
                                onSelectLine = { editorViewModel.selectLine(it) },
                                onToggleCoverOverlay = { editorViewModel.toggleCoverOverlay(it) },
                                onUpdateLineText = { id, o, t, m -> editorViewModel.updateLineText(id, o, t, m) },
                                onUpdateLineTimestamps = { id, s, e -> editorViewModel.updateLineTimestamps(id, s, e) },
                                onDeleteLine = { editorViewModel.deleteLine(it) },
                                onMergeWithNext = { editorViewModel.mergeWithNext(it) },
                                onSplitLine = { id, t -> editorViewModel.splitLine(id, t) },
                                onRetranslateLine = { editorViewModel.retranslateLine(it) },
                                onAddLine = { editorViewModel.addManualLine(it) },
                                onClearUserMessage = { editorViewModel.clearUserMessage() },
                                onExportClick = { navigateTo("export/$projId") },
                                onBackClick = {
                                    backStack.clear()
                                    backStack.add("projects")
                                },
                                onRequestTranslateOnline = { text -> editorViewModel.translateTextOnline(text) }
                            )
                        }

                        currentRoute.startsWith("manual/") -> {
                            val projId = currentRoute.substringAfter("manual/").toLongOrNull() ?: 0L
                            BackHandler {
                                backStack.clear()
                                backStack.add("projects")
                            }

                            val manualViewModel: ManualTranslationViewModel = viewModel(
                                key = "manual_$projId",
                                factory = ManualTranslationViewModel.Factory(
                                    projectId = projId,
                                    projectRepository = container.projectRepository,
                                    subtitleRepository = container.subtitleRepository
                                )
                            )
                            val currentProject by manualViewModel.project.collectAsState()
                            val lines by manualViewModel.subtitleLines.collectAsState()
                            val manualUiState by manualViewModel.uiState.collectAsState()

                            ManualTranslationScreen(
                                project = currentProject,
                                subtitleLines = lines,
                                uiState = manualUiState,
                                buildExportContent = { manualViewModel.buildExportContent() },
                                onImportContent = { manualViewModel.importTranslation(it) },
                                onShowMessage = { manualViewModel.showMessage(it) },
                                onClearUserMessage = { manualViewModel.clearUserMessage() },
                                onOpenEditorClick = { navigateTo("editor/$projId") },
                                onExportSubtitlesClick = { navigateTo("export/$projId") },
                                onBackClick = {
                                    backStack.clear()
                                    backStack.add("projects")
                                }
                            )
                        }

                        currentRoute.startsWith("export/") -> {
                            val projId = currentRoute.substringAfter("export/").toLongOrNull() ?: 0L
                            BackHandler { navigateBack() }

                            val editorViewModel: ReviewEditorViewModel = viewModel(
                                key = "editor_$projId",
                                factory = ReviewEditorViewModel.Factory(
                                    projectId = projId,
                                    projectRepository = container.projectRepository,
                                    subtitleRepository = container.subtitleRepository,
                                    settingsRepository = container.settingsRepository,
                                    translator = container.translator
                                )
                            )
                            val currentProject by editorViewModel.project.collectAsState()
                            val lines by editorViewModel.subtitleLines.collectAsState()

                            ExportScreen(
                                project = currentProject,
                                subtitleLines = lines,
                                maxCharsPerLine = settings.maxCharsPerLine,
                                onBackClick = { navigateBack() }
                            )
                        }

                        currentRoute == "settings" -> {
                            BackHandler { navigateBack() }

                            SettingsScreen(
                                settings = settings,
                                onCoverOverlayChanged = {
                                    scope.launch { container.settingsRepository.updateCoverOriginal(it) }
                                },
                                onFontSizeChanged = {
                                    scope.launch { container.settingsRepository.updateFontSize(it) }
                                },
                                onMaxCharsPerLineChanged = {
                                    scope.launch { container.settingsRepository.updateMaxCharsPerLine(it) }
                                },
                                onGlossaryChanged = {
                                    scope.launch { container.settingsRepository.updateGlossary(it) }
                                },
                                onFilterWatermarksChanged = {
                                    scope.launch { container.settingsRepository.updateFilterWatermarks(it) }
                                },
                                onIgnoredTermsChanged = {
                                    scope.launch { container.settingsRepository.updateIgnoredTerms(it) }
                                },
                                onTranslationEngineChanged = {
                                    scope.launch { container.settingsRepository.updateTranslationEngine(it) }
                                },
                                onGeminiApiKeyChanged = {
                                    scope.launch { container.settingsRepository.updateGeminiApiKey(it) }
                                },
                                onGeminiModelChanged = {
                                    scope.launch { container.settingsRepository.updateGeminiModel(it) }
                                },
                                onBackClick = { navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
