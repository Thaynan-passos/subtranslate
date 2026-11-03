package com.example.domain.util

import com.example.domain.model.RawFrameDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalSpeechGrouperTest {

    @Test
    fun testLevenshteinShortStringTolerance() {
        // <= 6 caracteres: tolerar distância absoluta de até 1 caractere
        assertTrue("Olá vs 0lá deve ser similar (dist 1)", Levenshtein.isSimilar("Olá", "0lá"))
        assertTrue("Você vs Voce deve ser similar", Levenshtein.isSimilar("Você", "Voce"))
        assertTrue("Oi vs O1 deve ser similar (dist 1)", Levenshtein.isSimilar("Oi", "O1"))
        assertFalse("Oi vs Tchau não deve ser similar", Levenshtein.isSimilar("Oi", "Tchau"))

        // 7 a 12 caracteres: tolerar distância absoluta de até 2 caracteres
        assertTrue("Bom dia vs B0m d1a deve ser similar (dist 2)", Levenshtein.isSimilar("Bom dia", "B0m d1a"))
        assertFalse("Bom dia vs Boa noite não deve ser similar", Levenshtein.isSimilar("Bom dia", "Boa noite"))

        // > 12 caracteres: threshold percentual flexibilizado (~0.80)
        val original = "Hoje vamos falar sobre isso"
        val slightlyNoisy = "Hoje vamos falar sobro iss0" // dist 2 / 27 chars = 92% similaridade
        assertTrue(Levenshtein.isSimilar(original, slightlyNoisy, threshold = 0.80f))
        val veryNoisy = "Hoje amanhã comer pastel agora" // completamente diferente
        assertFalse(Levenshtein.isSimilar(original, veryNoisy, threshold = 0.80f))
    }

    @Test
    fun testCumulativeDynamicSubtitlesGrouping() {
        val grouper = TemporalSpeechGrouper(
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2,
            minDurationMs = 300L
        )

        // Simula legendas dinâmicas de Reels / TikTok / Karaokê:
        // Palavras adicionadas sequencialmente a cada 500ms
        val detections = listOf(
            RawFrameDetection(timestampMs = 0L, text = "Hoje", confidence = 0.90f),
            RawFrameDetection(timestampMs = 500L, text = "Hoje vamos", confidence = 0.92f),
            RawFrameDetection(timestampMs = 1000L, text = "Hoje vamos falar", confidence = 0.95f),
            RawFrameDetection(timestampMs = 1500L, text = "Hoje vamos falar sobre isso", confidence = 0.96f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        assertEquals("Deve agrupar em 1 único bloco contínuo em vez de fragmentar", 1, result.size)
        val line = result.first()
        assertEquals("O texto final deve ser a versão mais completa do diálogo", "Hoje vamos falar sobre isso", line.originalText)
        assertEquals("O tempo inicial deve refletir o primeiro frame", 0L, line.startTimeMs)
        assertTrue("O tempo final deve cobrir a sequência acumulada", line.endTimeMs >= 1500L)
    }

    @Test
    fun testShortPhrasesWithOcrNoiseDoNotBreak() {
        val grouper = TemporalSpeechGrouper(
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2,
            minDurationMs = 300L
        )

        // Frases curtas com ruído de OCR de 1 letra
        val detections = listOf(
            RawFrameDetection(timestampMs = 0L, text = "Olá", confidence = 0.90f),
            RawFrameDetection(timestampMs = 500L, text = "0lá", confidence = 0.85f),
            RawFrameDetection(timestampMs = 1000L, text = "Olá", confidence = 0.95f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        assertEquals("Não deve quebrar a fala curta com 1 caractere de ruído", 1, result.size)
        assertEquals("Olá", result.first().originalText)
    }

    @Test
    fun testComparisonWithLongestDetectionPreventsFragmentation() {
        val grouper = TemporalSpeechGrouper(
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2,
            minDurationMs = 300L
        )

        // Frame intermediário perde palavras por oclusão/ruído momentâneo, mas frame seguinte recupera o texto completo
        val detections = listOf(
            RawFrameDetection(timestampMs = 0L, text = "Hoje vamos falar sobre o projeto", confidence = 0.95f),
            RawFrameDetection(timestampMs = 500L, text = "vamos falar sobre o projeto", confidence = 0.80f), // oclusão momentânea
            RawFrameDetection(timestampMs = 1000L, text = "Hoje vamos falar sobre o projeto", confidence = 0.96f) // recupera texto mais longo
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        assertEquals("Deve manter no mesmo cluster comparando com o texto mais longo", 1, result.size)
        assertEquals("Hoje vamos falar sobre o projeto", result.first().originalText)
    }

    @Test
    fun testMergeAdjacentSimilarLinesWithin800ms() {
        val grouper = TemporalSpeechGrouper(
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2,
            minDurationMs = 300L
        )

        // Dois clusters que tiveram um pequeno hiato temporal (ex: 600ms entre fim de um e início de outro)
        // onde a fala seguinte é quase idêntica ou continuação
        val detections = listOf(
            // Cluster 1: t=0 a 500ms -> end ~750ms
            RawFrameDetection(timestampMs = 0L, text = "Bem-vindos a todos", confidence = 0.90f),
            RawFrameDetection(timestampMs = 500L, text = "Bem-vindos a todos", confidence = 0.92f),

            // Cluster 2: t=1350 a 1850ms -> gap entre 750ms e 1100ms é ~350-600ms (<= 800ms)
            RawFrameDetection(timestampMs = 1350L, text = "Bem-vindos a todos vocês", confidence = 0.95f),
            RawFrameDetection(timestampMs = 1850L, text = "Bem-vindos a todos vocês", confidence = 0.95f)
        )

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        assertEquals("Deve fundir falas vizinhas complementares dentro de 800ms", 1, result.size)
        assertEquals("Bem-vindos a todos vocês", result.first().originalText)
    }

    @Test
    fun testStaticWatermarkClusterDiscarded() {
        val grouper = TemporalSpeechGrouper(
            sampleIntervalMs = 500L,
            minConsecutiveFrames = 2,
            minDurationMs = 300L,
            filterWatermarks = true,
            maxSpeechDurationMs = 15_000L
        )

        // Detecções estáticas repetidas de uma watermark curta por mais de 16 segundos
        val detections = mutableListOf<RawFrameDetection>()
        var t = 0L
        while (t <= 16_000L) {
            detections.add(RawFrameDetection(timestampMs = t, text = "@meu_canal", confidence = 0.99f))
            t += 500L
        }

        val result = grouper.groupDetections(projectId = 1L, detections = detections)

        assertTrue("Watermark estática exibida continuamente por > 15s deve ser descartada", result.isEmpty())
    }
}

