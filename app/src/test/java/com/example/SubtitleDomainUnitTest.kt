package com.example

import com.example.data.engine.MlKitTranslatorImpl
import com.example.data.local.dao.ProjectDao
import com.example.data.local.dao.ProjectWithLineCount
import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.dao.TranslationCacheDao
import com.example.data.local.entity.ProjectEntity
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import com.example.data.pipeline.SubtitleExtractionPipeline
import com.example.data.repository.ProjectRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.OcrEngine
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.model.OcrResult
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus
import com.example.domain.model.RawFrameDetection
import com.example.domain.model.RegionOfInterest
import com.example.domain.model.SubtitleLine
import com.example.domain.util.Levenshtein
import com.example.domain.util.SubtitleExportType
import com.example.domain.util.SubtitleFormatter
import com.example.domain.util.TemporalSpeechGrouper
import com.example.ui.viewmodel.ProjectConfigViewModel
import com.example.ui.viewmodel.ReviewEditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SubtitleDomainUnitTest {

    @Test
    fun testLevenshteinNormalizedSimilarity() {
        // Exact match
        assertEquals(1.0f, Levenshtein.normalizedSimilarity("Hello world", "Hello world"), 0.001f)

        // Case insensitivity & punctuation tolerance
        val simPunct = Levenshtein.normalizedSimilarity("Hello, world!", "hello world")
        assertEquals(1.0f, simPunct, 0.001f)

        // Minor OCR typo (e.g. 'O' vs '0', or single char diff)
        val typoSim = Levenshtein.normalizedSimilarity("Welcome to the show", "We1come to the show")
        assertTrue("Similarity should be high for 1 typo", typoSim >= 0.85f)

        // Completely different strings
        val diffSim = Levenshtein.normalizedSimilarity("Apple pie", "Space rocket")
        assertTrue("Similarity should be low", diffSim < 0.50f)
        val disjointSim = Levenshtein.normalizedSimilarity("abcdef", "uvwxyz")
        assertEquals("Disjoint strings should have 0 similarity", 0.0f, disjointSim, 0.001f)
    }

    @Test
    fun testSaveLinesInCheckpointsWithoutDuplicates() {
        // Simulating the batch checkpoint logic in SubtitleExtractionPipeline
        val databaseMock = mutableListOf<SubtitleLine>()

        fun clearLines() {
            databaseMock.clear()
        }

        fun saveLines(batch: List<SubtitleLine>) {
            databaseMock.addAll(batch)
        }

        clearLines()

        val totalGeneratedLines = (1..12).map { i ->
            SubtitleLine(
                id = i.toLong(),
                projectId = 1L,
                index = i,
                startTimeMs = i * 1000L,
                endTimeMs = (i + 1) * 1000L,
                originalText = "Fala $i",
                translatedText = "Speech $i"
            )
        }

        val batchToSave = mutableListOf<SubtitleLine>()
        for ((idx, line) in totalGeneratedLines.withIndex()) {
            batchToSave.add(line)
            if (batchToSave.size >= 5 || idx == totalGeneratedLines.size - 1) {
                saveLines(batchToSave)
                batchToSave.clear()
            }
        }

        // Must equal exactly 12 lines, no duplicates!
        assertEquals(12, databaseMock.size)
        assertEquals(totalGeneratedLines.map { it.id }, databaseMock.map { it.id })
    }

    @Test
    fun testTemporalSpeechGrouperWith333msDiscardsIsolatedFrame() {
        val grouper = TemporalSpeechGrouper(
            similarityThreshold = 0.85f,
            minDurationMs = 300L,
            sampleIntervalMs = 333L,
            minConsecutiveFrames = 2
        )

        val detections = listOf(
            // Single isolated frame at 3 fps (333ms duration)
            RawFrameDetection(timestampMs = 1000L, text = "Ruído Isolado", confidence = 0.95f),
            // Continuous valid speech spanning 3 consecutive frames
            RawFrameDetection(timestampMs = 3000L, text = "Fala legítima", confidence = 0.95f),
            RawFrameDetection(timestampMs = 3333L, text = "Fala legítima", confidence = 0.95f),
            RawFrameDetection(timestampMs = 3666L, text = "Fala legítima", confidence = 0.95f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        // Only the continuous speech should be retained; the single isolated frame is discarded!
        assertEquals(1, result.size)
        assertEquals("Fala legítima", result[0].originalText)
        assertEquals(3000L, result[0].startTimeMs)
        assertEquals(3666L + 333L, result[0].endTimeMs)
    }

    @Test
    fun testApplyGlossarySpecialTermsAndCJK() {
        val glossary = mapOf(
            "C++" to "C Plus Plus",
            "(VIP)" to "Membro Especial",
            "$100" to "Cem Dólares",
            "\\path" to "Caminho",
            "魔王" to "Rei Demônio"
        )

        val text1 = "Aprenda C++ hoje e ganhe $100 se for (VIP) no \\path"
        val res1 = MlKitTranslatorImpl.applyGlossary(text1, glossary)

        assertTrue(res1.contains("C Plus Plus"))
        assertTrue(res1.contains("Cem Dólares"))
        assertTrue(res1.contains("Membro Especial"))
        assertTrue(res1.contains("Caminho"))

        val textCjk = "Derrote o 魔王 antes do anoitecer"
        val resCjk = MlKitTranslatorImpl.applyGlossary(textCjk, glossary)
        assertEquals("Derrote o Rei Demônio antes do anoitecer", resCjk)
    }

    @Test
    fun testParseGlossary() {
        val raw = """
            Hello, world: test -> Olá, mundo: teste
            key = value
            invalid line without separator
            first -> second -> third
            Shield ->
        """.trimIndent()

        val parsed = SettingsRepository.parseGlossary(raw)

        assertEquals(4, parsed.size)
        assertEquals("Olá, mundo: teste", parsed["Hello, world: test"])
        assertEquals("value", parsed["key"])
        assertEquals("second -> third", parsed["first"])
        assertEquals("", parsed["Shield"])
        assertFalse(parsed.containsKey("invalid line without separator"))
    }

    @Test
    fun testFormatTimestampDifferentLocale() {
        val originalLocale = Locale.getDefault()
        try {
            // Set locale to Germany (which usually uses comma for decimal) or French
            Locale.setDefault(Locale.GERMANY)

            val srtTime = SubtitleLine.formatTimestamp(3542L, isVtt = false)
            assertEquals("00:00:03,542", srtTime)

            val vttTime = SubtitleLine.formatTimestamp(3542L, isVtt = true)
            assertEquals("00:00:03.542", vttTime)
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun testWrapTextCJKAndOversizedWords() {
        // CJK text without spaces
        val cjk = "这是一段非常非常长的中文字符串用于测试自动换行功能是否正常运行"
        val wrappedCjk = SubtitleFormatter.wrapText(cjk, maxCharsPerLine = 10)
        for (line in wrappedCjk.split("\n")) {
            assertTrue("CJK lines must be at most 10 chars", line.length <= 10)
        }

        // Oversized single word without spaces
        val longWord = "supercalifragilisticexpialidocious"
        val wrappedWord = SubtitleFormatter.wrapText(longWord, maxCharsPerLine = 12)
        for (line in wrappedWord.split("\n")) {
            assertTrue("Word chunks must be at most 12 chars", line.length <= 12)
        }
        assertEquals(longWord, wrappedWord.replace("\n", ""))
    }

    @Test
    fun testUpdateRoiDoesNotThrowOnAnyInput() {
        fun coerceRoi(left: Float, top: Float, right: Float, bottom: Float): RegionOfInterest {
            val coercedLeft = left.coerceIn(0.0f, 0.85f)
            val coercedTop = top.coerceIn(0.0f, 0.85f)
            val coercedRight = right.coerceIn(coercedLeft + 0.05f, 1.0f)
            val coercedBottom = bottom.coerceIn(coercedTop + 0.05f, 1.0f)
            return RegionOfInterest(coercedLeft, coercedTop, coercedRight, coercedBottom)
        }

        // Extreme negative, inverted, and oversized coordinates
        val r1 = coerceRoi(-100f, 200f, -50f, 0f)
        assertTrue(r1.left <= r1.right)
        assertTrue(r1.top <= r1.bottom)

        val r2 = coerceRoi(0.99f, 0.99f, 0.01f, 0.01f)
        assertTrue(r2.left < r2.right)
        assertTrue(r2.top < r2.bottom)

        val r3 = coerceRoi(1000f, -500f, 0.1f, 0.05f)
        assertTrue(r3.left >= 0f && r3.right <= 1f)
        assertTrue(r3.top >= 0f && r3.bottom <= 1f)
    }

    @Test
    fun testSrtAndVttGenerationFormat() {
        val lines = listOf(
            SubtitleLine(
                id = 1,
                projectId = 1,
                index = 1,
                startTimeMs = 1500L,
                endTimeMs = 4250L,
                originalText = "Hello everyone",
                translatedText = "Olá a todos"
            )
        )

        val srt = SubtitleFormatter.toSrt(lines, SubtitleExportType.TRANSLATED_ONLY)
        assertTrue(srt.contains("1\n"))
        assertTrue(srt.contains("00:00:01,500 --> 00:00:04,250\n"))
        assertTrue(srt.contains("Olá a todos"))

        val vtt = SubtitleFormatter.toWebVtt(lines, SubtitleExportType.TRANSLATED_ONLY)
        assertTrue(vtt.startsWith("WEBVTT\n\n"))
        assertTrue(vtt.contains("00:00:01.500 --> 00:00:04.250\n"))
    }

    @Test
    fun testSplitTextByRatioSingleWordReturnsEmptySecondHalf() {
        // Single word
        val (singlePart1, singlePart2) = ReviewEditorViewModel.splitTextByRatio("Unicórnio", 0.5f)
        assertEquals("Unicórnio", singlePart1)
        assertEquals("", singlePart2)

        // Empty string
        val (empty1, empty2) = ReviewEditorViewModel.splitTextByRatio("", 0.5f)
        assertEquals("", empty1)
        assertEquals("", empty2)

        // Multiple words
        val (multi1, multi2) = ReviewEditorViewModel.splitTextByRatio("One two three four", 0.5f)
        assertEquals("One two", multi1)
        assertEquals("three four", multi2)
    }

    @Test
    fun testGlossaryPlaceholdersPreparationAndRestoration() {
        val glossary = mapOf(
            "Dark Lord" to "Senhor das Trevas",
            "Shield" to "" // not translate: target is empty/blank
        )

        val originalText = "The Dark Lord has a Shield"
        val (prepared, placeholders) = MlKitTranslatorImpl.preparePlaceholders(originalText, glossary)

        assertEquals(2, placeholders.size)
        assertTrue("Prepared text must contain ⟦G0⟧", prepared.contains("⟦G0⟧"))
        assertTrue("Prepared text must contain ⟦G1⟧", prepared.contains("⟦G1⟧"))

        // Simulate successful translation with placeholders intact
        val simulatedTranslated = "O ⟦G0⟧ tem um ⟦G1⟧"
        val restored = MlKitTranslatorImpl.restorePlaceholders(simulatedTranslated, placeholders, glossary)
        assertEquals("O Senhor das Trevas tem um Shield", restored)

        // Simulate corrupted placeholder (e.g. translator modified or dropped placeholder)
        val corruptedTranslated = "O Dark Lord tem um ⟦G1⟧"
        val restoredFallback = MlKitTranslatorImpl.restorePlaceholders(corruptedTranslated, placeholders, glossary)
        assertEquals("O Senhor das Trevas tem um Shield", restoredFallback)
    }

    @Test
    fun testGlossaryPlaceholdersAvoidCollisionsWithInput() {
        val (prepared, placeholders) = MlKitTranslatorImpl.preparePlaceholders(
            "Token ⟦G0⟧ remains and Dragon appears",
            mapOf("Dragon" to "Dragão")
        )

        assertEquals("⟦G1⟧", placeholders.single().placeholder)
        assertTrue(prepared.contains("Token ⟦G0⟧ remains and ⟦G1⟧"))
    }

    @Test
    fun testAutoDetectRoiClampingExtremeValues() {
        // Extreme high values (e.g. near bottom/overflow)
        val roiHigh = com.example.ui.viewmodel.ProjectConfigViewModel.calculateAutoDetectedRoi(
            detectedTopSum = 0.96f,
            detectedBottomSum = 0.99f,
            sampleCount = 1
        )
        assertTrue(roiHigh.top < roiHigh.bottom)
        assertTrue(roiHigh.top in 0.1f..0.85f)
        assertTrue(roiHigh.bottom in 0.1f..0.98f)

        // Extreme negative values
        val roiLow = com.example.ui.viewmodel.ProjectConfigViewModel.calculateAutoDetectedRoi(
            detectedTopSum = -50f,
            detectedBottomSum = -10f,
            sampleCount = 1
        )
        assertTrue(roiLow.top < roiLow.bottom)
        assertTrue(roiLow.top >= 0.1f)

        // Empty sample count returns default
        val roiDefault = com.example.ui.viewmodel.ProjectConfigViewModel.calculateAutoDetectedRoi(
            detectedTopSum = 0f,
            detectedBottomSum = 0f,
            sampleCount = 0
        )
        assertEquals(0.70f, roiDefault.top, 0.001f)
        assertEquals(0.95f, roiDefault.bottom, 0.001f)
    }

    @Test
    fun pipelineDownloadFailureMarksProjectAsError() = runTest {
        val dao = FakeProjectDao(Project(
            id = 42L,
            title = "Teste",
            videoUri = "file:///unused.mp4",
            durationMs = 10_000L
        ))
        val projectRepository = ProjectRepository(dao, FakeSubtitleLineDao())
        val subtitleRepository = SubtitleRepository(FakeSubtitleLineDao(), FakeTranslationCacheDao())
        val translator = DownloadFailingTranslator()
        val pipeline = SubtitleExtractionPipeline(
            context = ApplicationProvider.getApplicationContext<Context>(),
            projectRepository = projectRepository,
            subtitleRepository = subtitleRepository,
            ocrEngine = FakeOcrEngine(),
            translator = translator
        )

        val result = pipeline.execute(dao.project.toDomain(), AppSettings())

        assertTrue(result.isFailure)
        assertEquals(ProjectStatus.ERROR.name, dao.project.status)
        assertEquals(
            "Não foi possível baixar o modelo de tradução. Verifique a conexão e tente novamente.",
            dao.project.currentStep
        )
        assertEquals(
            "Não foi possível baixar o modelo de tradução. Verifique a conexão e tente novamente.",
            dao.project.errorMessage
        )
        assertEquals(0, translator.translateCalls)
        assertEquals(0, (pipeline.getPipelineState(42L).value.extractedLinesCount))
    }

    private class DownloadFailingTranslator : Translator {
        var translateCalls = 0
        override val name = "fake"
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
            contextLines: List<String>,
            glossary: Map<String, String>,
            bypassCache: Boolean
        ): String {
            translateCalls++
            return text
        }
        override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String) = false
        override suspend fun downloadModelIfNeeded(
            sourceLanguage: String,
            targetLanguage: String,
            requireWifi: Boolean,
            onProgress: (Float) -> Unit
        ) = false
        override fun close() = Unit
    }

    private class FakeOcrEngine : OcrEngine {
        override val name = "fake"
        override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): OcrResult {
            throw AssertionError("OCR não deve executar se o download falhar")
        }
        override fun close() = Unit
    }

    private class FakeProjectDao(initialProject: Project) : ProjectDao {
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
        override suspend fun deleteProject(project: ProjectEntity) = Unit
        override suspend fun deleteProjectById(id: Long) = Unit
    }

    private class FakeSubtitleLineDao : SubtitleLineDao {
        override fun getLinesByProject(projectId: Long): Flow<List<SubtitleLineEntity>> = flowOf(emptyList())
        override suspend fun getLinesListByProject(projectId: Long) = emptyList<SubtitleLineEntity>()
        override suspend fun countLinesByProject(projectId: Long) = 0
        override suspend fun insertLines(lines: List<SubtitleLineEntity>) = Unit
        override suspend fun insertLine(line: SubtitleLineEntity) = 1L
        override suspend fun updateLine(line: SubtitleLineEntity) = Unit
        override suspend fun deleteLine(line: SubtitleLineEntity) = Unit
        override suspend fun deleteLineById(id: Long) = Unit
        override suspend fun deleteLinesByProject(projectId: Long) = Unit
    }

    private class FakeTranslationCacheDao : TranslationCacheDao {
        override suspend fun getCachedTranslation(
            text: String,
            sourceLang: String,
            targetLang: String,
            engine: String
        ): String? = null
        override suspend fun insertCache(cache: TranslationCacheEntity) = Unit
    }
}
