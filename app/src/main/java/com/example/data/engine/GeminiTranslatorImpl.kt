package com.example.data.engine

import com.example.data.repository.SubtitleRepository
import com.example.domain.engine.Translator
import com.example.domain.model.AppSettings
import com.example.domain.util.BuiltInGlossary
import com.example.domain.util.OcrTextSanitizer
import com.example.domain.util.UniversalSpeechHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiTranslatorImpl(
    private val repository: SubtitleRepository,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { DEFAULT_MODEL }
) : Translator {

    override val name: String = "Google Gemini (IA)"

    private val cacheEngine = "gemini-v1"

    private class GeminiHttpException(message: String, val isRetryable: Boolean) : IllegalStateException(message)

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
        if (UniversalSpeechHelper.isUniversalExpression(sanitizedText)) {
            return sanitizedText
        }

        // Consulta cache local do Room primeiro (traduções salvas do usuário têm prioridade máxima)
        if (!bypassCache) {
            val cached = repository.getBestCachedTranslation(sanitizedText, sourceLanguage, targetLanguage)
            if (cached != null) {
                return cached
            }
        }

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chave da API do Google Gemini não informada nas Configurações.")
        }

        val model = validateModelName(modelProvider().ifBlank { DEFAULT_MODEL })
        val effectiveGlossary = buildEffectiveGlossary(sanitizedText, sourceLanguage, targetLanguage, glossary)
        val systemPrompt = buildSystemInstruction(sourceLanguage, targetLanguage, effectiveGlossary)
        val requestBody = buildRequestBody(systemPrompt, sanitizedText)

        val responseJson = callWithRetry(
            apiKey = apiKey,
            model = model,
            requestBody = requestBody,
            readTimeoutMs = READ_TIMEOUT_MS
        )
        val translated = parseGeminiResponse(responseJson)

        // Armazena no cache local para economizar requisições futuras
        if (translated.isNotBlank()) {
            repository.cacheTranslation(sanitizedText, sourceLanguage, targetLanguage, cacheEngine, translated)
        }

        return translated
    }

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>,
        bypassCache: Boolean
    ): List<String> {
        if (texts.isEmpty()) return emptyList()

        val results = MutableList(texts.size) { "" }
        val pending = mutableListOf<Pair<Int, String>>()

        for ((index, raw) in texts.withIndex()) {
            if (raw.isBlank() || UniversalSpeechHelper.isUniversalExpression(raw)) {
                results[index] = raw
                continue
            }
            val sanitized = OcrTextSanitizer.sanitize(raw, sourceLanguage)
            if (UniversalSpeechHelper.isUniversalExpression(sanitized)) {
                results[index] = sanitized
                continue
            }
            if (!bypassCache) {
                val cached = repository.getBestCachedTranslation(sanitized, sourceLanguage, targetLanguage)
                if (cached != null) {
                    results[index] = cached
                    continue
                }
            }
            pending.add(index to sanitized)
        }

        if (pending.isEmpty()) return results

        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException("Chave da API do Google Gemini não informada nas Configurações.")
        }
        val model = validateModelName(modelProvider().ifBlank { DEFAULT_MODEL })

        for (chunk in pending.chunked(BATCH_SIZE)) {
            val combinedSource = chunk.joinToString("\n") { it.second }
            val effectiveGlossary = buildEffectiveGlossary(combinedSource, sourceLanguage, targetLanguage, glossary)
            val systemPrompt = buildBatchSystemInstruction(sourceLanguage, targetLanguage, effectiveGlossary)
            val requestBody = buildBatchRequestBody(systemPrompt, chunk)

            val responseJson = callWithRetry(
                apiKey = apiKey,
                model = model,
                requestBody = requestBody,
                readTimeoutMs = BATCH_READ_TIMEOUT_MS
            )
            val payload = parseGeminiResponse(responseJson)
            val parsedMap = parseBatchTranslationPayload(payload, chunk.size)

            for ((localIdx, item) in chunk.withIndex()) {
                val (originalIdx, sanitizedText) = item
                val translated = parsedMap[localIdx]?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Tradução em lote retornou item ausente ou vazio.")
                results[originalIdx] = translated
                repository.cacheTranslation(sanitizedText, sourceLanguage, targetLanguage, cacheEngine, translated)
            }
        }

        return results
    }

    /**
     * Executa uma chamada mínima real à API do Gemini e retorna a latência medida em milissegundos.
     */
    suspend fun testConnection(apiKey: String, model: String = DEFAULT_MODEL): Result<Long> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Informe sua chave da API do Google Gemini antes de testar.")
            )
        }
        return@withContext try {
            val safeModel = validateModelName(model.ifBlank { DEFAULT_MODEL })
            val requestBody = buildConnectionTestBody()
            val startNs = System.nanoTime()
            val responseJson = executeHttpRequest(
                apiKey = cleanKey,
                model = safeModel,
                requestBody = requestBody,
                readTimeoutMs = READ_TIMEOUT_MS
            )
            parseGeminiResponse(responseJson)
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            Result.success(elapsedMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = redactApiKey(e.message, cleanKey)
                .ifBlank { "Falha ao conectar à API do Google Gemini." }
            Result.failure(IllegalStateException(safeMsg))
        }
    }

    private suspend fun callWithRetry(
        apiKey: String,
        model: String,
        requestBody: String,
        readTimeoutMs: Int
    ): String = withContext(Dispatchers.IO) {
        var lastError: GeminiHttpException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return@withContext executeHttpRequest(apiKey, model, requestBody, readTimeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GeminiHttpException) {
                if (!e.isRetryable || attempt >= MAX_ATTEMPTS) throw e
                lastError = e
                delay(RETRY_BASE_DELAY_MS * attempt)
            }
        }
        throw lastError ?: IllegalStateException("Falha ao comunicar com a API do Google Gemini.")
    }

    private fun executeHttpRequest(
        apiKey: String,
        model: String,
        requestBody: String,
        readTimeoutMs: Int
    ): String {
        val safeModel = validateModelName(model)
        val endpoint = "$API_BASE_URL/$safeModel:generateContent"
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = readTimeoutMs
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
            } else {
                try {
                    conn.errorStream?.close()
                } catch (_: Exception) {}

                val retryable = responseCode == HTTP_TOO_MANY_REQUESTS || responseCode in HTTP_SERVER_ERRORS
                throw GeminiHttpException(getSafeErrorMessage(responseCode, safeModel), isRetryable = retryable)
            }
        } catch (e: GeminiHttpException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw GeminiHttpException("Tempo limite esgotado ao conectar à API do Google Gemini.", isRetryable = true)
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException("Não foi possível conectar à API do Google Gemini. Verifique sua conexão com a internet.")
        } catch (e: java.io.IOException) {
            throw GeminiHttpException("Erro de rede ao comunicar com a API do Google Gemini.", isRetryable = true)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Monta o glossário efetivo priorizando 100% dos termos personalizados do usuário
     * e filtrando o dicionário embutido apenas para termos presentes no texto de entrada.
     */
    internal fun buildEffectiveGlossary(
        sourceText: String,
        sourceLanguage: String,
        targetLanguage: String,
        userGlossary: Map<String, String>
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((src, tgt) in userGlossary) {
            val cleanSrc = src.trim()
            if (cleanSrc.isNotEmpty() && result.size < USER_GLOSSARY_MAX) {
                result[cleanSrc] = tgt.trim()
            }
        }

        val builtIn = BuiltInGlossary.forPair(sourceLanguage, targetLanguage)
        if (builtIn.isNotEmpty() && sourceText.isNotBlank()) {
            var addedBuiltIn = 0
            for ((src, tgt) in builtIn) {
                if (addedBuiltIn >= BUILTIN_GLOSSARY_MAX) break
                if (sourceText.contains(src, ignoreCase = true) && !result.containsKey(src)) {
                    result[src] = tgt
                    addedBuiltIn++
                }
            }
        }
        return result
    }

    internal fun buildSystemInstruction(
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

        appendGlossaryRules(sb, glossary)
        return sb.toString()
    }

    private fun buildBatchSystemInstruction(
        sourceLanguage: String,
        targetLanguage: String,
        glossary: Map<String, String>
    ): String {
        val sb = StringBuilder()
        sb.append("Você é um tradutor especialista de legendas para vídeos, séries, animes e filmes.\n")
        sb.append("Traduza cada fala do array JSON de entrada de '$sourceLanguage' para '$targetLanguage' mantendo a ordem e os IDs exatos.\n")
        sb.append("Use fluência natural em português brasileiro, adaptando gírias, contrações e expressões idiomáticas conforme o contexto do diálogo.\n")
        sb.append("Aviso de OCR: os textos podem conter pequenos ruídos visuais de OCR; reconstrua o sentido natural sem adicionar notas ou explicações.\n")
        sb.append("Retorne um array JSON contendo exatamente um objeto {id, translation} para cada item de entrada.\n")

        appendGlossaryRules(sb, glossary)
        return sb.toString()
    }

    private fun appendGlossaryRules(sb: StringBuilder, glossary: Map<String, String>) {
        if (glossary.isEmpty()) return
        sb.append("\nRegras de vocabulário e termos obrigatórios:\n")
        glossary.entries.take(USER_GLOSSARY_MAX + BUILTIN_GLOSSARY_MAX).forEach { (src, tgt) ->
            if (tgt.isBlank() || src.equals(tgt, ignoreCase = true)) {
                sb.append("- Mantenha o termo '$src' intacto, sem traduzir.\n")
            } else {
                sb.append("- Traduza '$src' obrigatoriamente como '$tgt'.\n")
            }
        }
    }

    private fun buildRequestBody(systemInstruction: String, userText: String): String {
        val json = JSONObject()

        val systemPart = JSONObject().put("text", systemInstruction)
        val systemInstructionObj = JSONObject().put("parts", JSONArray().put(systemPart))
        json.put("systemInstruction", systemInstructionObj)

        val userPart = JSONObject().put("text", userText)
        val contentObj = JSONObject().put("parts", JSONArray().put(userPart))
        json.put("contents", JSONArray().put(contentObj))

        val genConfig = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", SINGLE_MAX_OUTPUT_TOKENS)
        json.put("generationConfig", genConfig)

        return json.toString()
    }

    private fun buildBatchRequestBody(systemInstruction: String, items: List<Pair<Int, String>>): String {
        val inputArray = JSONArray()
        for ((localIdx, pair) in items.withIndex()) {
            inputArray.put(
                JSONObject()
                    .put("id", localIdx)
                    .put("text", pair.second)
            )
        }

        val itemSchema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("id", JSONObject().put("type", "INTEGER"))
                    .put("translation", JSONObject().put("type", "STRING"))
            )
            .put("required", JSONArray().put("id").put("translation"))
            .put("propertyOrdering", JSONArray().put("id").put("translation"))

        val genConfig = JSONObject()
            .put("temperature", 0.2)
            .put("maxOutputTokens", BATCH_MAX_OUTPUT_TOKENS)
            .put("responseMimeType", "application/json")
            .put("responseSchema", JSONObject().put("type", "ARRAY").put("items", itemSchema))

        return JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", inputArray.toString()))
                    )
                )
            )
            .put("generationConfig", genConfig)
            .toString()
    }

    private fun buildConnectionTestBody(): String {
        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", "Responda apenas OK"))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0)
                    .put("maxOutputTokens", TEST_MAX_OUTPUT_TOKENS)
            )
            .toString()
    }

    internal fun parseGeminiResponse(responseJson: String): String {
        return Companion.parseGeminiResponse(responseJson)
    }

    private fun parseBatchTranslationPayload(payload: String, expectedSize: Int): Map<Int, String> {
        val cleaned = payload.trim().replace(CODE_FENCE_REGEX, "").trim()
        val array = try {
            JSONArray(cleaned)
        } catch (e: JSONException) {
            throw IllegalStateException("Resposta em lote do Google Gemini em formato JSON inválido.", e)
        }
        val map = HashMap<Int, String>(expectedSize)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optInt("id", -1)
            val translation = obj.optString("translation", "").trim()
            if (id in 0 until expectedSize && translation.isNotBlank()) {
                map[id] = translation
            }
        }
        return map
    }

    override fun close() {
        // Sem recursos persistentes a fechar
    }

    companion object {
        const val DEFAULT_MODEL = AppSettings.DEFAULT_GEMINI_MODEL
        const val BATCH_SIZE = 20

        private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val BATCH_READ_TIMEOUT_MS = 45_000
        private const val SINGLE_MAX_OUTPUT_TOKENS = 1_024
        private const val BATCH_MAX_OUTPUT_TOKENS = 8_192
        private const val TEST_MAX_OUTPUT_TOKENS = 8
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 1_500L
        private const val USER_GLOSSARY_MAX = 150
        private const val BUILTIN_GLOSSARY_MAX = 50
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERRORS = 500..599
        private const val FINISH_REASON_STOP = "STOP"
        private val MODEL_NAME_REGEX = Regex("^[a-zA-Z0-9._-]+$")
        private val CODE_FENCE_REGEX = Regex("^```[a-zA-Z]*\\s*|\\s*```$")

        fun validateModelName(model: String): String {
            val trimmed = model.trim()
            if (trimmed.isEmpty() || !MODEL_NAME_REGEX.matches(trimmed)) {
                throw IllegalArgumentException("Nome de modelo do Gemini inválido.")
            }
            return trimmed
        }

        internal fun parseGeminiResponse(responseJson: String): String {
            val root = try {
                JSONObject(responseJson)
            } catch (e: JSONException) {
                throw IllegalStateException("Resposta da API do Google Gemini em formato inválido.", e)
            }

            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason", "").orEmpty()
            if (blockReason.isNotBlank()) {
                throw IllegalStateException("O Google Gemini recusou a tradução (motivo: $blockReason).")
            }

            val candidates = root.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw IllegalStateException("O Google Gemini não retornou tradução.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason", FINISH_REASON_STOP)
            if (finishReason.isNotBlank() && finishReason != FINISH_REASON_STOP) {
                throw IllegalStateException("O Google Gemini interrompeu a tradução (motivo: $finishReason).")
            }

            val content = firstCandidate.optJSONObject("content")
                ?: throw IllegalStateException("O Google Gemini não retornou conteúdo traduzido.")
            val parts = content.optJSONArray("parts")
                ?: throw IllegalStateException("O Google Gemini não retornou partes de texto traduzido.")
            if (parts.length() == 0) {
                throw IllegalStateException("O Google Gemini retornou tradução vazia.")
            }

            val resultBuilder = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                resultBuilder.append(part.optString("text", ""))
            }
            val result = resultBuilder.toString().trim()
            if (result.isBlank()) {
                throw IllegalStateException("O Google Gemini retornou tradução vazia.")
            }
            return result
        }

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
