package com.example

import android.content.Context
import android.graphics.Bitmap
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
import com.example.domain.model.AppSettings
import com.example.domain.model.OcrResult
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
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
class CancellationAndPipelineTest {

    private class TestProjectDao(initialProject: Project) : ProjectDao {
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

    private class TestSubtitleLineDao : SubtitleLineDao {
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

    private class TestTranslationCacheDao : TranslationCacheDao {
        override suspend fun getBestCachedTranslation(text: String, sourceLang: String, targetLang: String): String? = null
        override suspend fun getCachedTranslation(text: String, sourceLang: String, targetLang: String, engine: String): String? = null
        override suspend fun insertCache(cache: TranslationCacheEntity) = Unit
        override suspend fun deleteCache(text: String, sourceLang: String, targetLang: String) = Unit
    }

    private class TestTranslator : Translator {
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

    private class FailingTranslator : Translator {
        override val name = "failing"
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
            glossary: Map<String, String>,
            bypassCache: Boolean
        ): String = throw IllegalStateException("Download falhou")
        override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String) = false
        override suspend fun downloadModelIfNeeded(
            sourceLanguage: String,
            targetLanguage: String,
            requireWifi: Boolean,
            onProgress: (Float) -> Unit
        ) = false
        override fun close() = Unit
    }

    private class TestOcrEngine : OcrEngine {
        override val name = "test"
        override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): OcrResult = OcrResult()
        override fun close() = Unit
    }

    @Test
    fun pipelineCancelLeavesProjectInPausedStateWithoutErrorMessage() = runTest {
        val project = Project(
            id = 101L,
            title = "Video Teste",
            videoUri = "file:///dummy.mp4",
            durationMs = 5000L,
            status = ProjectStatus.PROCESSING
        )
        val dao = TestProjectDao(project)
        val projectRepository = ProjectRepository(dao, TestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(TestSubtitleLineDao(), TestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = ApplicationProvider.getApplicationContext<Context>(),
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = TestOcrEngine(),
            translator = TestTranslator()
        )

        pipeline.cancel(101L)

        // Cancelamento deve definir o status para PAUSED (para evitar reinício acidental)
        assertEquals(ProjectStatus.PAUSED.name, dao.project.status)
        assertEquals("Cancelado pelo usuário", dao.project.currentStep)
        // Cancelamento NÃO é erro: errorMessage no projeto deve ser nulo
        assertNull(dao.project.errorMessage)

        // PipelineState também deve refletir cancelamento sem erro
        val state = pipeline.getPipelineState(101L).value
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals("Cancelado pelo usuário", state.step)
        assertNull(state.errorMessage)
    }

    @Test
    fun pipelineDifferentiatesCancellationFromFailure() = runTest {
        val project = Project(
            id = 102L,
            title = "Video Falha",
            videoUri = "file:///dummy.mp4",
            durationMs = 5000L
        )
        val dao = TestProjectDao(project)
        val projectRepository = ProjectRepository(dao, TestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(TestSubtitleLineDao(), TestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = ApplicationProvider.getApplicationContext<Context>(),
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = TestOcrEngine(),
            translator = FailingTranslator()
        )

        // Execução que falha por erro de download
        val result = pipeline.execute(dao.project.toDomain(), AppSettings())
        assertTrue(result.isFailure)

        // Em caso de falha, o status deve ser ERROR e errorMessage deve existir
        assertEquals(ProjectStatus.ERROR.name, dao.project.status)
        assertTrue(dao.project.errorMessage != null && dao.project.errorMessage!!.isNotBlank())

        val errorState = pipeline.getPipelineState(102L).value
        assertFalse(errorState.isRunning)
        assertTrue(errorState.errorMessage != null && errorState.errorMessage!!.isNotBlank())
    }

    @Test
    fun markCancelledFlagsProjectImmediately() {
        val project = Project(
            id = 103L,
            title = "Video Mark Cancel",
            videoUri = "file:///dummy.mp4",
            durationMs = 5000L
        )
        val dao = TestProjectDao(project)
        val projectRepository = ProjectRepository(dao, TestSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(TestSubtitleLineDao(), TestTranslationCacheDao())
        val pipeline = SubtitleExtractionPipeline(
            context = ApplicationProvider.getApplicationContext<Context>(),
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = TestOcrEngine(),
            translator = TestTranslator()
        )

        pipeline.markCancelled(103L)
        // markCancelled deve ser invocável sem exceção pelo serviço ao receber ACTION_STOP ou onTimeout
    }
}

