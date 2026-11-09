package com.example

import com.example.data.engine.GeminiTranslatorImpl
import com.example.data.engine.SmartHybridTranslator
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiSecurityAndErrorTest {

    private class StubTranslator(
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
    fun redactApiKey_masksKeyInAnyMessage() {
        val apiKey = "AIzaSySecretApiKey123456789"
        val messageWithKey = "Erro de conexão ao acessar chave AIzaSySecretApiKey123456789 no endpoint"

        val redacted = GeminiTranslatorImpl.redactApiKey(messageWithKey, apiKey)

        assertFalse("A mensagem redigida não deve conter a chave de API", redacted.contains(apiKey))
        assertTrue("A mensagem redigida deve conter [REDACTED]", redacted.contains("[REDACTED]"))
        assertEquals("Erro de conexão ao acessar chave [REDACTED] no endpoint", redacted)
    }

    @Test
    fun redactApiKey_handlesNullOrBlankSafely() {
        assertEquals("normal message", GeminiTranslatorImpl.redactApiKey("normal message", null))
        assertEquals("normal message", GeminiTranslatorImpl.redactApiKey("normal message", ""))
        assertEquals("", GeminiTranslatorImpl.redactApiKey(null, "some-key"))
    }

    @Test
    fun safeErrorMessages_provideCleanUserFriendlyMessagesWithoutSensitiveData() {
        val msg400 = GeminiTranslatorImpl.getSafeErrorMessage(400)
        assertTrue(msg400.contains("Requisição inválida"))
        assertFalse(msg400.contains("key="))

        val msg401 = GeminiTranslatorImpl.getSafeErrorMessage(401)
        assertTrue(msg401.contains("inválida") || msg401.contains("permissões"))
        assertFalse(msg401.contains("AIza"))

        val msg403 = GeminiTranslatorImpl.getSafeErrorMessage(403)
        assertTrue(msg403.contains("permissões") || msg403.contains("inválida"))

        val msg404 = GeminiTranslatorImpl.getSafeErrorMessage(404, "gemini-2.5-flash")
        assertTrue(msg404.contains("gemini-2.5-flash"))
        assertTrue(msg404.contains("não encontrado"))

        val msg429 = GeminiTranslatorImpl.getSafeErrorMessage(429)
        assertTrue(msg429.contains("cota") || msg429.contains("taxa") || msg429.contains("excedido"))

        val msg500 = GeminiTranslatorImpl.getSafeErrorMessage(500)
        assertTrue(msg500.contains("temporariamente indisponível"))

        val msg503 = GeminiTranslatorImpl.getSafeErrorMessage(503)
        assertTrue(msg503.contains("temporariamente indisponível"))

        val msgUnknown = GeminiTranslatorImpl.getSafeErrorMessage(418)
        assertTrue(msgUnknown.contains("418"))
    }

    @Test
    fun smartHybridTranslator_redactsApiKeyDuringFallback() = runBlocking {
        val secretApiKey = "AIzaSySuperSecretKeyToProtect98765"
        val mlKit = StubTranslator("MLKit") { "MLKit: $it" }
        val failingGemini = StubTranslator("Gemini") {
            throw IllegalStateException("API call failed with token: $secretApiKey and url http://example.com?key=$secretApiKey")
        }

        val hybrid = SmartHybridTranslator(
            mlKitTranslator = mlKit,
            geminiTranslator = failingGemini,
            settingsProvider = {
                AppSettings(
                    translationEngine = "gemini",
                    geminiApiKey = secretApiKey
                )
            }
        )

        // O fallback para MLKit deve ocorrer sem que a chave secreta vaze
        val result = hybrid.translate("Hello test", "en", "pt")
        assertEquals("MLKit: Hello test", result)
    }
}

