package com.example.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextSanitizerTest {

    @Test
    fun testSanitizesMayraYoudWithTrailingApostrophe() {
        val raw = "mayra you'd'"
        val result = OcrTextSanitizer.sanitize(raw, "en")
        assertEquals("maybe you'd", result)
    }

    @Test
    fun testSanitizesMayreYoud() {
        val raw = "Mayre you'd like some coffee?"
        val result = OcrTextSanitizer.sanitize(raw, "en")
        assertEquals("Maybe you'd like some coffee?", result)
    }

    @Test
    fun testRestoresEnglishContractionsMissingApostrophe() {
        assertEquals("you'd", OcrTextSanitizer.sanitize("youd", "en"))
        assertEquals("don't", OcrTextSanitizer.sanitize("dont", "en"))
        assertEquals("can't", OcrTextSanitizer.sanitize("cant", "en"))
        assertEquals("won't", OcrTextSanitizer.sanitize("wont", "en"))
        assertEquals("they're", OcrTextSanitizer.sanitize("theyre", "en"))
    }

    @Test
    fun testStripsSpuriousApostrophesAtWordBoundaries() {
        assertEquals("you'd", OcrTextSanitizer.sanitize("you'd'", "en"))
        assertEquals("don't", OcrTextSanitizer.sanitize("don't''", "en"))
        assertEquals("you'd", OcrTextSanitizer.sanitize("'you'd", "en"))
    }

    @Test
    fun testNormalizesTypographicApostrophesAndQuotes() {
        assertEquals("you'd", OcrTextSanitizer.sanitize("you’d", "en"))
        assertEquals("I'm", OcrTextSanitizer.sanitize("l'm", "en"))
    }
}

