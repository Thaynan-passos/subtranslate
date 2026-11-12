package com.example

import com.example.data.engine.SmartHybridTranslator
import com.example.data.repository.SettingsRepository
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.util.BuiltInGlossary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorIntegrationTest {

    private class FakeTranslator(
        override val name: String,
        private val behavior: (String) -> String = { it }
    ) : Translator {
        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
            glossary: Map<String, String>,
            bypassCache: Boolean
        ): String = behavior(text)

        override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean = true
        override suspend fun downloadModelIfNeeded(
            sourceLanguage: String,
            targetLanguage: String,
            requireWifi: Boolean,
            onProgress: (Float) -> Unit
        ): Boolean = true

        override fun close() {}
    }

    @Test
    fun builtInGlossary_hasExtensiveOfflineLibrary() {
        val enToPt = BuiltInGlossary.forPair("en", "pt")
        assertTrue("A biblioteca deve conter mais de 150 termos essenciais", enToPt.size >= 150)

        // Phrasal verbs e expressões frequentes
        assertEquals("moleza", enToPt["piece of cake"])
        assertEquals("eu topo", enToPt["i'm down"])
        assertEquals("descobrir", enToPt["figure it out"])
        assertEquals("vai com calma", enToPt["take it easy"])
        assertEquals("sem caô", enToPt["no cap"])

        // Suporte a apóstrofos normais e curvos (OCR comum)
        assertEquals("eu topo", enToPt["i’m down"])
        assertEquals("vai com calma", enToPt["take it easy"])
    }

    @Test
    fun parseGlossary_parsesCustomEntriesCorrectly() {
        val raw = """
            fireball -> Bola de Fogo
            hp = Vida
            invalid entry without separator
            cool phrase -> Frase Legal
        """.trimIndent()

        val parsed = SettingsRepository.parseGlossary(raw)

        assertEquals("Bola de Fogo", parsed["fireball"])
        assertEquals("Vida", parsed["hp"])
        assertEquals("Frase Legal", parsed["cool phrase"])
        assertEquals(3, parsed.size)
    }

    @Test
    fun smartHybridTranslator_usesMlKitWhenConfigured() = runBlocking {
        val mlKit = FakeTranslator("MLKit") { "MLKit: $it" }
        val gemini = FakeTranslator("Gemini") { "Gemini: $it" }

        val hybrid = SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = gemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "mlkit",
                    geminiApiKey = "fake-key"
                )
            }
        )

        val result = hybrid.translate("Hello world", "en", "pt")
        assertEquals("MLKit: Hello world", result)
    }

    @Test
    fun smartHybridTranslator_usesGeminiWhenConfiguredWithKey() = runBlocking {
        val mlKit = FakeTranslator("MLKit") { "MLKit: $it" }
        val gemini = FakeTranslator("Gemini") { "Gemini: $it" }

        val hybrid = SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = gemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "gemini",
                    geminiApiKey = "fake-api-key"
                )
            }
        )

        val result = hybrid.translate("I'm totally down for that", "en", "pt")
        assertEquals("Gemini: I'm totally down for that", result)
    }

    @Test
    fun smartHybridTranslator_fallsBackToMlKitWhenGeminiFails() = runBlocking {
        val mlKit = FakeTranslator("MLKit") { "Fallback MLKit: $it" }
        val failingGemini = FakeTranslator("Gemini") {
            throw IllegalStateException("Simulated Network/Quota Error")
        }

        val hybrid = SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = failingGemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "gemini",
                    geminiApiKey = "fake-api-key"
                )
            }
        )

        // Deve capturar o erro e realizar o fallback transparente para o ML Kit
        val result = hybrid.translate("What's up buddy?", "en", "pt")
        assertEquals("Fallback MLKit: What's up buddy?", result)
    }

    @Test(expected = CancellationException::class)
    fun smartHybridTranslator_propagatesCancellationExceptionWithoutFallback() = runBlocking {
        val mlKit = FakeTranslator("MLKit") { "Fallback MLKit: $it" }
        val cancellingGemini = FakeTranslator("Gemini") {
            throw CancellationException("Cancelado pelo usuário")
        }

        val hybrid = SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = cancellingGemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "gemini",
                    geminiApiKey = "fake-api-key"
                )
            }
        )

        // Deve propagar a CancellationException e NÃO engolir o cancelamento chamando o ML Kit
        hybrid.translate("Cancelling...", "en", "pt")
    }
}
