package com.example.domain.util

import com.example.domain.model.SubtitleLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualTranslationFileTest {

    private fun line(id: Long, start: Long, text: String) = SubtitleLine(
        id = id,
        projectId = 1L,
        index = id.toInt(),
        startTimeMs = start,
        endTimeMs = start + 1000,
        originalText = text,
        translatedText = ""
    )

    private val lines = listOf(
        line(1, 0, "Hello there."),
        line(2, 2000, "How are you?\nI'm fine."),
        line(3, 4000, "#1 fan")
    )

    @Test
    fun exportContainsNumberedMarkersAndOriginalText() {
        val content = ManualTranslationFile.export(lines, "Movie.mp4", "en", "pt")
        assertTrue(content.contains("[1]\nHello there.\n"))
        assertTrue(content.contains("[2]\nHow are you?\nI'm fine.\n"))
        assertTrue(content.contains("[3]\n#1 fan\n"))
        assertTrue(content.contains("EN -> PT"))
    }

    @Test
    fun roundTripTranslatedMarkerFile() {
        val exported = ManualTranslationFile.export(lines, "Movie", "en", "pt")
        val translated = exported
            .replace("Hello there.", "Olá.")
            .replace("How are you?\nI'm fine.", "Como vai?\nEstou bem.")
            .replace("#1 fan", "Fã nº 1")

        val parsed = ManualTranslationFile.parse(translated)
        assertEquals(ManualTranslationFile.ImportFormat.MARKERS, parsed.format)
        assertEquals("Olá.", parsed.translations[1])
        assertEquals("Como vai?\nEstou bem.", parsed.translations[2])
        assertEquals("Fã nº 1", parsed.translations[3])

        val result = ManualTranslationFile.apply(lines, parsed.translations)
        assertEquals(3, result.appliedCount)
        assertEquals(0, result.missingCount)
        assertEquals("Como vai?\nEstou bem.", result.updatedLines[1].translatedText)
    }

    @Test
    fun parsesInlineMarkersWithCrLfAndBom() {
        val content = "\uFEFF# header\r\n[1] Olá\r\n[ 2 ]: Tudo bem?\r\n[99] extra\r\n"
        val parsed = ManualTranslationFile.parse(content)
        assertEquals("Olá", parsed.translations[1])
        assertEquals("Tudo bem?", parsed.translations[2])

        val result = ManualTranslationFile.apply(lines, parsed.translations)
        assertEquals(2, result.appliedCount)
        assertEquals(1, result.missingCount)
        assertEquals(1, result.ignoredCount)
        assertEquals("", result.updatedLines[2].translatedText)
    }

    @Test
    fun parsesSrtFile() {
        val srt = "1\n00:00:00,000 --> 00:00:01,000\nOlá.\n\n2\n00:00:02,000 --> 00:00:03,000\n<i>Como vai?</i>\nEstou bem.\n\n"
        val parsed = ManualTranslationFile.parse(srt)
        assertEquals(ManualTranslationFile.ImportFormat.SUBTITLE, parsed.format)
        assertEquals("Olá.", parsed.translations[1])
        assertEquals("Como vai?\nEstou bem.", parsed.translations[2])
    }

    @Test
    fun fallsBackToPlainLinesByOrder() {
        val parsed = ManualTranslationFile.parse("Olá.\n\nComo vai?\n")
        assertEquals(ManualTranslationFile.ImportFormat.PLAIN_LINES, parsed.format)
        assertEquals(mapOf(1 to "Olá.", 2 to "Como vai?"), parsed.translations)
    }
}

