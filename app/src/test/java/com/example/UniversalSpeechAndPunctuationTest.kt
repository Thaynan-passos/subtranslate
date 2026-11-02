package com.example

import com.example.data.engine.MlKitTranslatorImpl
import com.example.domain.model.RawFrameDetection
import com.example.domain.util.Levenshtein
import com.example.domain.util.TemporalSpeechGrouper
import com.example.domain.util.UniversalSpeechHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSpeechAndPunctuationTest {

    @Test
    fun testLevenshteinMatchesElongatedWordsAndPunctuation() {
        // "haaaa" vs "haaaaa" com ou sem pontuação
        val sim1 = Levenshtein.normalizedSimilarity("haaaa...", "haaaaa!")
        assertEquals("Variações de letras arrastadas devem ter similaridade 1.0", 1.0f, sim1, 0.001f)

        // "Ohhhhh" vs "Ohhh"
        val sim2 = Levenshtein.normalizedSimilarity("Ohhhhh", "Ohhh")
        assertEquals(1.0f, sim2, 0.001f)

        // "kyaaaa" vs "kyaaaaaa"
        val sim3 = Levenshtein.normalizedSimilarity("Kyaaaa?!", "kyaaaaaa...")
        assertEquals(1.0f, sim3, 0.001f)

        // "Nooooo" vs "Nooo"
        val sim4 = Levenshtein.normalizedSimilarity("Nooooo!!", "Nooo")
        assertEquals(1.0f, sim4, 0.001f)

        // Palavras normais sem elongação mantêm comportamento
        val diffSim = Levenshtein.normalizedSimilarity("Hello", "World")
        assertTrue(diffSim < 0.3f)
    }

    @Test
    fun testExpressivePunctuationDetection() {
        // Reticências e combinações expressivas
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("..."))
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("…"))
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("?!"))
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("!?"))
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("---"))
        assertTrue(UniversalSpeechHelper.isExpressivePunctuation("...?!"))

        // Ponto isolado ou ruído de 1 caractere deve ser falso para evitar sujeira de OCR
        assertFalse(UniversalSpeechHelper.isExpressivePunctuation("."))
        assertFalse(UniversalSpeechHelper.isExpressivePunctuation(","))
        assertFalse(UniversalSpeechHelper.isExpressivePunctuation(""))
    }

    @Test
    fun testUniversalExpressionDetection() {
        // Expressões puramente onomatopeicas ou universais
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Haaaa..."))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Ohhhhh!"))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Ah... Ah..."))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Mmm..."))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Kyaaa?!"))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Haha!"))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("Whoa..."))
        assertTrue(UniversalSpeechHelper.isUniversalExpression("..."))

        // Frases completas normais NÃO são puramente universais
        assertFalse(UniversalSpeechHelper.isUniversalExpression("Onde você vai?"))
        assertFalse(UniversalSpeechHelper.isUniversalExpression("I don't think so."))
        assertFalse(UniversalSpeechHelper.isUniversalExpression("Wait for me!"))
    }

    @Test
    fun testTemporalGrouperGroupsElongatedWordsAcrossFrames() {
        val grouper = TemporalSpeechGrouper(
            similarityThreshold = 0.90f,
            minDurationMs = 300L,
            sampleIntervalMs = 333L,
            minConsecutiveFrames = 2
        )

        // Frames consecutivos onde o OCR oscila a contagem de 'a' em "Haaaa"
        val detections = listOf(
            RawFrameDetection(timestampMs = 1000L, text = "Haaaa...", confidence = 0.95f),
            RawFrameDetection(timestampMs = 1333L, text = "Haaaaa!", confidence = 0.95f),
            RawFrameDetection(timestampMs = 1666L, text = "Haaa", confidence = 0.95f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        // Deve agrupar em UMA ÚNICA fala coerente
        assertEquals(1, result.size)
        assertTrue(result[0].originalText.startsWith("Haaa"))
        assertEquals(834L, result[0].startTimeMs)
        assertEquals(1833L, result[0].endTimeMs)
    }

    @Test
    fun testTemporalGrouperPreservesExpressiveEllipsis() {
        val grouper = TemporalSpeechGrouper(
            similarityThreshold = 0.90f,
            minDurationMs = 300L,
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2
        )

        val detections = listOf(
            RawFrameDetection(timestampMs = 2000L, text = "...", confidence = 0.90f),
            RawFrameDetection(timestampMs = 2500L, text = "...", confidence = 0.90f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        // Deve manter as reticências expressivas
        assertEquals(1, result.size)
        assertEquals("...", result[0].originalText)
    }

    @Test
    fun testMixedTextProtectsElongatedTokensWithPlaceholders() {
        val mixedText = "Ohhhhh, you are finally here!"
        val (prepared, placeholders) = MlKitTranslatorImpl.preparePlaceholders(mixedText, emptyMap())

        // "Ohhhhh" deve ser protegido como placeholder
        assertTrue("Deve gerar placeholder para a onomatopeia arrastada", placeholders.isNotEmpty())
        assertEquals("Ohhhhh", placeholders.first().sourceTerm)
        assertTrue(prepared.contains(placeholders.first().placeholder))

        // Simula tradução onde o placeholder é mantido
        val simulatedTranslated = "${placeholders.first().placeholder}, você finalmente está aqui!"
        val restored = MlKitTranslatorImpl.restorePlaceholders(simulatedTranslated, placeholders)
        assertEquals("Ohhhhh, você finalmente está aqui!", restored)
    }
}

