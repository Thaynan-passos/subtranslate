package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.engine.GeminiTranslatorImpl
import com.example.data.engine.SmartHybridTranslator
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.ProjectWithLineCount
import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.dao.TranslationCacheDao
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubtitleRepository
import com.example.data.security.AndroidKeyStoreApiKeyCipher
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.domain.util.ImagePreprocessor
import com.example.domain.util.Levenshtein
import com.example.ui.viewmodel.ReviewEditorViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditFixesUnitTest {

    private class InMemoryProjectDao(initialProject: Project) : ProjectDao {
        private val entity = MutableStateFlow(ProjectEntity.fromDomain(initialProject))
        override fun getAllProjectsWithCount(): Flow<List<ProjectWithLineCount>> = flowOf(emptyList())
        override suspend fun getProjectByIdWithCount(id: Long): ProjectWithLineCount? =
            if (id == entity.value.id) ProjectWithLineCount(entity.value, 0) else null
        override fun observeProjectByIdWithCount(id: Long): Flow<ProjectWithLineCount?> =
            entity.map { if (id == it.id) ProjectWithLineCount(it, 0) else null }
        override suspend fun insertProject(project: ProjectEntity): Long = project.id
        override suspend fun updateProject(project: ProjectEntity) { entity.value = project }
        override suspend fun updateProgress(id: Long, status: String, progress: Float, currentStep: String) = Unit
        override suspend fun markAbandonedProcessingProjects(activeProjectId: Long?) = Unit
        override suspend fun deleteProject(project: ProjectEntity) = Unit
        override suspend fun deleteProjectById(id: Long) = Unit
    }

    private class InMemorySubtitleLineDao(initialLines: List<SubtitleLineEntity>) : SubtitleLineDao {
        private val lines = MutableStateFlow(initialLines)
        override fun getLinesByProject(projectId: Long): Flow<List<SubtitleLineEntity>> =
            lines.map { list -> list.filter { it.projectId == projectId }.sortedWith(compareBy({ it.startTimeMs }, { it.lineIndex }, { it.id })) }
        override suspend fun getLinesListByProject(projectId: Long): List<SubtitleLineEntity> =
            lines.value.filter { it.projectId == projectId }.sortedWith(compareBy({ it.startTimeMs }, { it.lineIndex }, { it.id }))
        override suspend fun countLinesByProject(projectId: Long): Int = lines.value.count { it.projectId == projectId }
        override suspend fun insertLines(lines: List<SubtitleLineEntity>) { this.lines.value = lines }
        override suspend fun insertLine(line: SubtitleLineEntity): Long {
            val nextId = (lines.value.maxOfOrNull { it.id } ?: 0L) + 1L
            lines.value = lines.value + line.copy(id = nextId)
            return nextId
        }
        override suspend fun updateLine(line: SubtitleLineEntity) {
            lines.value = lines.value.map { if (it.id == line.id) line else it }
        }
        override suspend fun updateLines(lines: List<SubtitleLineEntity>) {
            val byId = lines.associateBy { it.id }
            this.lines.value = this.lines.value.map { byId[it.id] ?: it }
        }
        override suspend fun updateLineText(id: Long, originalText: String, translatedText: String) {
            lines.value = lines.value.map {
                if (it.id == id) it.copy(originalText = originalText, translatedText = translatedText) else it
            }
        }
        override suspend fun updateLineTimestamps(id: Long, startTimeMs: Long, endTimeMs: Long) {
            lines.value = lines.value.map {
                if (it.id == id) it.copy(startTimeMs = startTimeMs, endTimeMs = endTimeMs) else it
            }
        }
        override suspend fun deleteLine(line: SubtitleLineEntity) = Unit
        override suspend fun deleteLineById(id: Long) = Unit
        override suspend fun deleteLinesByProject(projectId: Long) = Unit
    }

    private class RecordingTranslationCacheDao : TranslationCacheDao {
        val inserted = mutableListOf<TranslationCacheEntity>()
        override suspend fun getBestCachedTranslation(text: String, sourceLang: String, targetLang: String): String? =
            inserted.lastOrNull { it.sourceText == text && it.sourceLang == sourceLang && it.targetLang == targetLang }?.translatedText
        override suspend fun getCachedTranslation(text: String, sourceLang: String, targetLang: String, engine: String): String? = null
        override suspend fun insertCache(cache: TranslationCacheEntity) { inserted.add(cache) }
        override suspend fun deleteCache(text: String, sourceLang: String, targetLang: String) = Unit
    }

    private class ConfigurableTranslator(
        override val name: String,
        private val onTranslate: suspend (String) -> String = { it },
        private val onDownload: suspend () -> Boolean = { true }
    ) : Translator {
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
            glossary: Map<String, String>,
            bypassCache: Boolean
        ): String = onTranslate(text)

        override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean = true
        override suspend fun downloadModelIfNeeded(
            sourceLanguage: String,
            targetLanguage: String,
            requireWifi: Boolean,
            onProgress: (Float) -> Unit
        ): Boolean = onDownload()
        override fun close() = Unit
    }

    @Test
    fun geminiEffectiveGlossary_prioritizesAllUserTermsAheadOfBuiltIn() {
        val subtitleRepo = SubtitleRepository(InMemorySubtitleLineDao(emptyList()), RecordingTranslationCacheDao())
        val gemini = GeminiTranslatorImpl(subtitleRepo, apiKeyProvider = { "key" })
        val userGlossary = linkedMapOf(
            "Night City" to "Night City",
            "Choom" to "Parceiro",
            "Sandevistan" to "Sandevistan"
        )

        val effective = gemini.buildEffectiveGlossary(
            sourceText = "Hey Choom, we need to figure out Night City and Sandevistan!",
            sourceLanguage = "en",
            targetLanguage = "pt",
            userGlossary = userGlossary
        )

        // Todos os termos do usuário devem aparecer antes dos termos embutidos
        val keys = effective.keys.toList()
        assertEquals("Night City", keys[0])
        assertEquals("Choom", keys[1])
        assertEquals("Sandevistan", keys[2])
        assertTrue(effective.containsKey("figure out"))
        // Termos embutidos irrelevantes que não aparecem no texto não devem poluir o prompt
        assertFalse(effective.containsKey("piece of cake"))

        val instruction = gemini.buildSystemInstruction("en", "pt", effective)
        assertTrue(instruction.contains("Mantenha o termo 'Night City' intacto"))
        assertTrue(instruction.contains("Traduza 'Choom' obrigatoriamente como 'Parceiro'"))
        assertTrue(instruction.contains("Mantenha o termo 'Sandevistan' intacto"))
    }

    @Test
    fun geminiParseResponse_validatesSafetyBlockAndEmptyResponses() {
        val validJson = """
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Olá mundo"}]}}]}
        """.trimIndent()
        assertEquals("Olá mundo", GeminiTranslatorImpl.parseGeminiResponse(validJson))

        val blockedJson = """{"promptFeedback":{"blockReason":"SAFETY"}}"""
        try {
            GeminiTranslatorImpl.parseGeminiResponse(blockedJson)
            fail("Deveria lançar exceção para blockReason")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }

        val safetyFinishJson = """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}"""
        try {
            GeminiTranslatorImpl.parseGeminiResponse(safetyFinishJson)
            fail("Deveria lançar exceção para finishReason SAFETY")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }

        val maxTokensJson = """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"Parcial"}]}}]}"""
        try {
            GeminiTranslatorImpl.parseGeminiResponse(maxTokensJson)
            fail("Deveria lançar exceção para finishReason MAX_TOKENS")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("MAX_TOKENS"))
        }

        val emptyTextJson = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"   "}]}}]}"""
        try {
            GeminiTranslatorImpl.parseGeminiResponse(emptyTextJson)
            fail("Deveria lançar exceção para texto vazio")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("vazia"))
        }
    }

    @Test
    fun geminiValidateModelName_rejectsPathTraversalAndAcceptsValidIds() {
        assertEquals("gemini-2.5-flash", GeminiTranslatorImpl.validateModelName(" gemini-2.5-flash "))
        assertEquals("gemini-2.5-pro", GeminiTranslatorImpl.validateModelName("gemini-2.5-pro"))

        listOf("", "../etc/passwd", "gemini?key=123", "model/sub").forEach { bad ->
            try {
                GeminiTranslatorImpl.validateModelName(bad)
                fail("Deveria rejeitar modelo inválido: $bad")
            } catch (_: IllegalArgumentException) {
                // esperado
            }
        }
    }

    @Test
    fun smartHybridTranslator_resetsCircuitBreakerWhenKeyOrModelChanges() = runBlocking {
        var currentSettings = AppSettings(
            translationEngine = "gemini",
            geminiApiKey = "bad-key",
            geminiModel = "gemini-2.5-flash",
            autoFallbackToMlKit = true
        )
        var geminiCalls = 0
        val mlKit = ConfigurableTranslator("MLKit", onTranslate = { "MLKit: $it" })
        val gemini = ConfigurableTranslator("Gemini", onTranslate = {
            geminiCalls++
            if (currentSettings.geminiApiKey == "bad-key") {
                throw IllegalStateException("Chave da API do Google Gemini inválida (401).")
            }
            "Gemini: $it"
        })

        val hybrid = SmartHybridTranslator(mlKit, gemini, settingsProvider = { currentSettings })

        assertEquals("MLKit: hi", hybrid.translate("hi", "en", "pt"))
        assertEquals(1, geminiCalls)

        // Com a mesma chave ruim, circuit breaker pula Gemini
        assertEquals("MLKit: hi2", hybrid.translate("hi2", "en", "pt"))
        assertEquals(1, geminiCalls)

        // Ao corrigir a chave nas Configurações, circuit breaker deve resetar automaticamente
        currentSettings = currentSettings.copy(geminiApiKey = "good-key")
        assertEquals("Gemini: hi3", hybrid.translate("hi3", "en", "pt"))
        assertEquals(2, geminiCalls)
    }

    @Test
    fun smartHybridTranslator_propagatesErrorWhenAutoFallbackDisabled() = runBlocking {
        val mlKit = ConfigurableTranslator("MLKit", onTranslate = { "MLKit: $it" })
        val gemini = ConfigurableTranslator("Gemini", onTranslate = {
            throw IllegalStateException("Falha simulada Gemini")
        })
        val hybrid = SmartHybridTranslator(
            mlKit,
            gemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "gemini",
                    geminiApiKey = "valid-key",
                    autoFallbackToMlKit = false
                )
            }
        )

        try {
            hybrid.translate("Hello", "en", "pt")
            fail("Deveria propagar erro quando fallback automático está desativado")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Falha simulada Gemini"))
        }
    }

    @Test
    fun smartHybridTranslator_downloadModelIfNeededDoesNotBlockOnSlowFallback() = runBlocking {
        val downloadGate = CompletableDeferred<Boolean>()
        val mlKit = ConfigurableTranslator("MLKit", onDownload = { downloadGate.await() })
        val gemini = ConfigurableTranslator("Gemini")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val hybrid = SmartHybridTranslator(
            mlKit,
            gemini,
            settingsProvider = { AppSettings(translationEngine = "gemini", geminiApiKey = "k", autoFallbackToMlKit = true) },
            backgroundScope = scope
        )

        // Deve retornar true imediatamente sem aguardar o gate do download do ML Kit
        val ok = hybrid.downloadModelIfNeeded("en", "pt")
        assertTrue(ok)
        downloadGate.complete(true)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reviewEditorSaveLineEdits_updatesTextAndTimestampsSequentially() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val project = Project(id = 1L, title = "Teste", videoUri = "file:///a.mp4", durationMs = 20_000L)
            val line1 = SubtitleLineEntity(1L, 1L, 1, 1000L, 2000L, "Old 1", "Velho 1")
            val line2 = SubtitleLineEntity(2L, 1L, 2, 3000L, 4000L, "Old 2", "Velho 2")

            val projectRepo = ProjectRepository(InMemoryProjectDao(project), InMemorySubtitleLineDao(emptyList()))
            val lineDao = InMemorySubtitleLineDao(listOf(line1, line2))
            val cacheDao = RecordingTranslationCacheDao()
            val subtitleRepo = SubtitleRepository(lineDao, cacheDao)
            val settingsRepo = SettingsRepository(context, AndroidKeyStoreApiKeyCipher())

            val vm = ReviewEditorViewModel(
                projectId = 1L,
                projectRepository = projectRepo,
                subtitleRepository = subtitleRepo,
                settingsRepository = settingsRepo,
                translator = ConfigurableTranslator("test")
            )

            backgroundScope.launch(testDispatcher) { vm.project.collect {} }

            // Move linha 1 para depois da linha 2 alterando texto e timestamps juntos
            vm.saveLineEdits(
                lineId = 1L,
                original = "New 1",
                translated = "Novo 1",
                startMs = 5000L,
                endMs = 6000L,
                saveToMemory = true
            )

            val updated = lineDao.getLinesListByProject(1L)
            val edited = updated.first { it.id == 1L }
            assertEquals("New 1", edited.originalText)
            assertEquals("Novo 1", edited.translatedText)
            assertEquals(5000L, edited.startTimeMs)
            assertEquals(6000L, edited.endTimeMs)
            assertEquals(2, edited.lineIndex) // reindexado após linha 2 (3000ms)
            assertEquals(1, updated.first { it.id == 2L }.lineIndex)
            assertTrue(
                cacheDao.inserted.any {
                    it.engine == "user" && it.sourceText == "New 1" && it.translatedText == "Novo 1"
                }
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun imagePreprocessorCreateThumbnailMetrics_matchesLegacyMethods() {
        val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(120, 60, 200))
        }
        val metrics = ImagePreprocessor.createThumbnailMetrics(bitmap, 8, 4)
        val legacyGray = ImagePreprocessor.createThumbnailGrayscale(bitmap, 8, 4)
        val legacyRgb = ImagePreprocessor.createThumbnailRgb(bitmap, 8, 4)

        assertArrayEquals(legacyGray, metrics.gray)
        assertArrayEquals(legacyRgb, metrics.rgb)
        bitmap.recycle()
    }

    @Test
    fun levenshteinDistance1D_matchesExpectedEditDistances() {
        assertEquals(0, Levenshtein.distance("Hello", "hello"))
        assertEquals(3, Levenshtein.distance("kitten", "sitting"))
        assertEquals(5, Levenshtein.distance("", "abcde"))
        assertEquals(4, Levenshtein.distance("abcd", ""))
        assertEquals(2, Levenshtein.distance("Bom dia", "B0m d1a"))
    }

    @Test
    fun apiKeyCipher_handlesLegacyPlaintextAndCorruptedCiphertextSafely() {
        val cipher = AndroidKeyStoreApiKeyCipher()
        assertEquals("AIzaLegacyPlainKey", cipher.decryptIfEncrypted("AIzaLegacyPlainKey"))
        assertEquals("", cipher.decryptIfEncrypted(""))
        assertEquals("", cipher.decryptIfEncrypted("enc:v1:not_valid_payload"))
    }
}
