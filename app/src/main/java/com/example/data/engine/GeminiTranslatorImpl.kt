package com.example.data.engine

import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.Translator
import com.example.domain.util.BuiltInGlossary
import com.example.domain.util.OcrTextSanitizer
import com.example.domain.util.UniversalSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiTranslatorImpl(
    private val repository: SubtitleRepository,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { "gemini-2.5-flash" }
) : Translator {

    override val name: String = "Google Gemini (IA)"

    private val cacheEngine = "gemini-v1"

    override suspend fun isModelAvailable(sourceLanguage: String, targetLanguage: String): Boolean {
        return apiKeyProvider().isNotBlank()
    }

    override suspend fun downloadModelIfNeeded(
        sourceLanguage: String,
        targetLanguage: String,
        requireWifi: Boolean,
        onProgress: (Float) -> Unit
    ): Boolean {
        onProgress(1.0f)
        return isModelAvailable(sourceLanguage, targetLanguage)
    }

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>,
        bypassCache: Boolean
    ): String {
        if (text.isBlank()) return text

        // Expressões puramente universais / onomatopeias não necessitam de tradução
        if (UniversalSpeechHelper.isUniversalExpression(text)) {
            return text
        }

        val sanitizedText = OcrTextSanitizer.sanitize(text, sourceLanguage)
        val effectiveGlossary = BuiltInGlossary.forPair(sourceLanguage, targetLanguage) + glossary

        // Consulta cache local do Room primeiro (traduções salvas do usuário têm prioridade máxima)
        if (!bypassCache) {
            val cached = repository.getBestCachedTranslation(sanitizedText, sourceLanguage, targetLanguage)
            if (cached != null) {
                return MlKitTranslatorImpl.applyGlossary(cached, effectiveGlossary)
            }
        }

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chave da API do Google Gemini não informada nas Configurações.")
        }

        val model = modelProvider().trim().ifBlank { "gemini-2.5-flash" }

        val translated = withContext(Dispatchers.IO) {
            callGeminiApi(
                apiKey = apiKey,
                model = model,
                text = sanitizedText,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                glossary = effectiveGlossary
            )
        }

        // Armazena no cache local para economizar requisições futuras
        if (translated.isNotBlank()) {
            repository.cacheTranslation(sanitizedText, sourceLanguage, targetLanguage, cacheEngine, translated)
        }

        return MlKitTranslatorImpl.applyGlossary(translated, effectiveGlossary)
    }

    private fun callGeminiApi(
        apiKey: String,
        model: String,
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>
    ): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.doOutput = true

            val systemPrompt = buildSystemInstruction(sourceLanguage, targetLanguage, glossary)
            val requestBody = buildRequestBody(systemPrompt, text)

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                parseGeminiResponse(responseText)
            } else {
                try {
                    conn.errorStream?.close()
                } catch (_: Exception) {}

                throw IllegalStateException(getSafeErrorMessage(responseCode, model))
            }
        } catch (e: java.net.SocketTimeoutException) {
            throw IllegalStateException("Tempo limite esgotado ao conectar à API do Google Gemini.")
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("Não foi possível conectar à API do Google Gemini. Verifique sua conexão com a internet.")
        } catch (e: java.io.IOException) {
            throw IllegalStateException("Erro de rede ao comunicar com a API do Google Gemini.")
        } finally {
            conn.disconnect()
        }
    }

    private fun buildSystemInstruction(
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.append("Você é um tradutor especialista de legendas para vídeos, séries, animes e filmes.\n")
        sb.append("Traduza o texto de '$sourceLanguage' para '$targetLanguage' com máxima naturalidade e fluência coloquial em português.\n")
        sb.append("Adapte gírias, expressões idiomáticas (idioms), contrações e phrasal verbs sem traduções literais artificiais.\n")
        sb.append("Aviso de OCR: O texto de entrada foi extraído via OCR de vídeo e pode conter ruídos visuais típicos (ex: apóstrofos sobrando ou faltantes como 'you\\'d\\'', confusões de caracteres como 'mayra you\\'d' em vez de 'maybe you\\'d', junção de letras). Reconstrua contextualmente o diálogo original e traduza com precisão.\n")
        sb.append("NUNCA adicione notas explicativas, aspas desnecessárias, prefixos ou comentários. Retorne EXCLUSIVAMENTE a fala traduzida final.\n")

        if (glossary.isNotEmpty()) {
            sb.append("\nRegras de vocabulário e termos obrigatórios:\n")
            glossary.entries.take(50).forEach { (src, tgt) ->
                if (src.equals(tgt, ignoreCase = true)) {
                    sb.append("- Mantenha o termo '$src' intacto, sem traduzir.\n")
                } else {
                    sb.append("- Traduza '$src' obrigatoriamente como '$tgt'.\n")
                }
            }
        }
        return sb.toString()
    }

    private fun buildRequestBody(systemInstruction: String, userText: String): String {
        val json = JSONObject()

        // systemInstruction
        val systemPart = JSONObject().put("text", systemInstruction)
        val systemInstructionObj = JSONObject().put("parts", JSONArray().put(systemPart))
        json.put("systemInstruction", systemInstructionObj)

        // contents
        val userPart = JSONObject().put("text", userText)
        val contentObj = JSONObject().put("parts", JSONArray().put(userPart))
        json.put("contents", JSONArray().put(contentObj))

        // generationConfig
        val genConfig = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", 1024)
        json.put("generationConfig", genConfig)

        return json.toString()
    }

    private fun parseGeminiResponse(responseJson: String): String {
        val root = JSONObject(responseJson)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        if (parts.length() == 0) return ""

        val resultBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            resultBuilder.append(part.optString("text", ""))
        }
        return resultBuilder.toString().trim()
    }

    override fun close() {
        // Sem recursos persistentes a fechar
    }

    companion object {
        fun getSafeErrorMessage(responseCode: Int, model: String = "gemini"): String {
            return when (responseCode) {
                400 -> "Requisição inválida para a API do Google Gemini. Verifique os parâmetros."
                401, 403 -> "Chave da API do Google Gemini inválida ou sem permissões de acesso."
                404 -> "Modelo do Gemini '$model' não encontrado ou indisponível."
                429 -> "Limite de cota ou requisições da API do Google Gemini excedido. Tente novamente mais tarde."
                in 500..599 -> "Serviço do Google Gemini temporariamente indisponível. Tente novamente mais tarde."
                else -> "Falha na comunicação com a API do Google Gemini (código HTTP $responseCode)."
            }
        }

        fun redactApiKey(text: String?, apiKey: String?): String {
            if (text.isNullOrEmpty() || apiKey.isNullOrBlank()) return text ?: ""
            return text.replace(apiKey, "[REDACTED]")
        }
    }
}
