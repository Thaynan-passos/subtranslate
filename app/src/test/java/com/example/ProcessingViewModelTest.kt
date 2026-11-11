package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.ProjectWithLineCount
import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.dao.TranslationCacheDao
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.Translator
import com.example.domain.model.OcrResult
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.ui.viewmodel.ProcessingViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProcessingViewModelTest {

    private class VmTestProjectDao(initialProject: Project) : ProjectDao {
        var project = ProjectEntity.fromDomain(initialProject)
            private set

        override fun getAllProjectsWithCount(): Flow<List<ProjectWithLineCount>> = flowOf(emptyList())
        override suspend fun getProjectByIdWithCount(id: Long) =
            if (id == project.id) ProjectWithLineCount(project, 0) else null
        override fun observeProjectByIdWithCount(id: Long): Flow<ProjectWithLineCount?> =
            flowOf(if (id == project.id) ProjectWithLineCount(project, 0) else null)
        override suspend fun insertProject(project: ProjectEntity) = project.id
        override suspend fun updateProject(project: ProjectEntity) {
            this.project = project
        }
        override suspend fun updateProgress(id: Long, status: String, progress: Float, currentStep: String) {
            project = project.copy(
                status = status,
                progress = progress,
                currentStep = currentStep,
                errorMessage = if (status == ProjectStatus.ERROR.name) currentStep else null
            )
        }
        override suspend fun markAbandonedProcessingProjects(activeProjectId: Long?) = Unit
        override suspend fun deleteProject(project: ProjectEntity) = Unit
        override suspend fun deleteProjectById(id: Long) = Unit
    }

    private class VmTestSubtitleLineDao : SubtitleLineDao {
        override fun getLinesByProject(projectId: Long): Flow<List<SubtitleLineEntity>> = flowOf(emptyList())
        override suspend fun getLinesListByProject(projectId: Long) = emptyList<SubtitleLineEntity>()
        override suspend fun countLinesByProject(projectId: Long) = 0
        override suspend fun insertLines(lines: List<SubtitleLineEntity>) = Unit
        override suspend fun insertLine(line: SubtitleLineEntity) = 1L
        override suspend fun updateLine(line: SubtitleLineEntity) = Unit
        override suspend fun updateLines(lines: List<SubtitleLineEntity>) = Unit
        override suspend fun updateLineText(id: Long, originalText: String, translatedText: String) = Unit
        override suspend fun updateLineTimestamps(id: Long, startTimeMs: Long, endTimeMs: Long) = Unit
        override suspend fun deleteLine(line: SubtitleLineEntity) = Unit
        override suspend fun deleteLineById(id: Long) = Unit
        override suspend fun deleteLinesByProject(projectId: Long) = Unit
    }

    private class VmTestTranslationCacheDao : TranslationCacheDao {
        override suspend fun getBestCachedTranslation(text: String, sourceLang: String, targetLang: String): String? = null
        override suspend fun getCachedTranslation(text: String, sourceLang: String, targetLang: String, engine: String): String? = null
        override suspend fun insertCache(cache: TranslationCacheEntity) = Unit
        override suspend fun deleteCache(text: String, sourceLang: String, targetLang: String) = Unit
    }

    private class VmTestTranslator : Translator {
        override val name = "test"
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
            glossary: Map<String, String>,
            bypassCache: Boolean
        ): String = text
        override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String) = true
        override suspend fun downloadModelIfNeeded(
            sourceLanguage: String,
            targetLanguage: String,
            requireWifi: Boolean,
            onProgress: (Float) -> Unit
        ) = true
        override fun close() = Unit
    }

    private class VmTestOcrEngine : OcrEngine {
        override val name = "test"
        override suspend fun recognizeText(bitmap: android.graphics.Bitmap, languageCode: String): OcrResult = OcrResult()
        override fun close() = Unit
    }

    @Test
    fun startProcessingStartsWithoutRequiringSettingsParameter() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = Project(
            id = 200L,
            title = "Test Video",
            videoUri = "file:///dummy.mp4",
            durationMs = 1000L,
            status = ProjectStatus.IDLE
        )
        val dao = VmTestProjectDao(project)
        val projectRepository = ProjectRepository(dao, VmTestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(VmTestSubtitleLineDao(), VmTestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = context,
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = VmTestOcrEngine(),
            translator = VmTestTranslator()
        )

        val viewModel = ProcessingViewModel(
            projectId = 200L,
            context = context,
            projectRepository = projectRepository,
            pipeline = pipeline
        )

        // Deve ser possível chamar startProcessing() diretamente sem passar AppSettings
        viewModel.startProcessing()
    }

    @Test
    fun cancelUpdatesPipelineAndRepositoryCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = Project(
            id = 201L,
            title = "Cancel VM Test",
            videoUri = "file:///dummy.mp4",
            durationMs = 1000L,
            status = ProjectStatus.PROCESSING
        )
        val dao = VmTestProjectDao(project)
        val projectRepository = ProjectRepository(dao, VmTestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(VmTestSubtitleLineDao(), VmTestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = context,
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = VmTestOcrEngine(),
            translator = VmTestTranslator()
        )

        val viewModel = ProcessingViewModel(
            projectId = 201L,
            context = context,
            projectRepository = projectRepository,
            pipeline = pipeline
        )

        viewModel.cancel()

        assertEquals(ProjectStatus.PAUSED.name, dao.project.status)
        assertEquals("Cancelado pelo usuário", dao.project.currentStep)
        assertNull(dao.project.errorMessage)

        val state = viewModel.pipelineState.value
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals("Cancelado pelo usuário", state.step)
        assertNull(state.errorMessage)
    }

    @Test
    fun pauseAndResumeUpdatePipelineState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = Project(
            id = 202L,
            title = "Pause Resume VM Test",
            videoUri = "file:///dummy.mp4",
            durationMs = 1000L
        )
        val dao = VmTestProjectDao(project)
        val projectRepository = ProjectRepository(dao, VmTestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(VmTestSubtitleLineDao(), VmTestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = context,
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = VmTestOcrEngine(),
            translator = VmTestTranslator()
        )

        val viewModel = ProcessingViewModel(
            projectId = 202L,
            context = context,
            projectRepository = projectRepository,
            pipeline = pipeline
        )

        viewModel.pause()
        assertTrue(viewModel.pipelineState.value.isPaused)

        viewModel.resume()
        assertFalse(viewModel.pipelineState.value.isPaused)
    }
}

