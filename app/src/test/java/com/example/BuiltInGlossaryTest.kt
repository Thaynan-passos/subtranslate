package com.example

import com.example.data.engine.MlKitTranslatorImpl
import com.example.domain.util.BuiltInGlossary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInGlossaryTest {

    @Test
    fun enPtHasTermsAndOtherPairsAreEmpty() {
        assertTrue(BuiltInGlossary.forPair("en", "pt").isNotEmpty())
        assertTrue(BuiltInGlossary.forPair("ja", "pt").isEmpty())
        assertTrue(BuiltInGlossary.forPair("en", "es").isEmpty())
    }

    @Test
    fun keysAreLowercaseAndUnique() {
        val keys = BuiltInGlossary.forPair("en", "pt").keys.toList()
        assertTrue(keys.all { it == it.lowercase() })
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun curlyApostropheVariantExists() {
        assertTrue(BuiltInGlossary.forPair("en", "pt").containsKey("i’m cumming"))
    }

    @Test
    fun placeholdersRoundTrip() {
        val glossary = BuiltInGlossary.forPair("en", "pt")
        val (prepared, placeholders) =
            MlKitTranslatorImpl.preparePlaceholders("MY PUSSY IS WET", glossary)
        assertTrue(placeholders.isNotEmpty())
        val restored = MlKitTranslatorImpl.restorePlaceholders(prepared, placeholders)
        assertTrue(restored.contains("buceta"))
    }

    @Test
    fun userGlossaryOverridesBuiltIn() {
        val merged = BuiltInGlossary.forPair("en", "pt") + mapOf("pussy" to "gatinha")
        assertEquals("gatinha", merged["pussy"])
    }
}
