package com.example.data.engine

import android.content.Context
import android.net.Uri
import com.example.data.media.AudioChunk
import com.example.data.media.AudioTrackExtractor
import com.example.domain.engine.SpeechTranscriber
import com.example.domain.engine.TranscribedSegment
import com.example.domain.util.TranscriptionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FilterOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Transcreve o áudio do vídeo com o Google Gemini (entrada multimodal de áudio).
 *
 * O áudio é dividido em blocos pelo [AudioTrackExtractor] e cada bloco é enviado inline
 * (base64 transmitido em streaming, sem carregar o arquivo inteiro em memória).
 */
class GeminiAudioTranscriber(
    private val context: Context,
    private val apiKeyProvider: () -> String,
    private val modelProvider: () -> String = { DEFAULT_MODEL },
    private val audioExtractor: AudioTrackExtractor = AudioTrackExtractor(context)
) : SpeechTranscriber {

    /** Erro HTTP com mensagem segura (sem chave/URL) e indicação se vale tentar novamente. */
    private class GeminiHttpException(message: String, val isRetryable: Boolean) : IllegalStateException(message)

    override suspend fun transcribe(
        videoUri: String,
        sourceLanguage: String,
        onChunkDone: suspend (done: Int, total: Int) -> Unit
    ): List<TranscribedSegment> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "Chave da API do Google Gemini não informada nas Configurações. Ela é necessária para transcrever o áudio."
            )
        }
        val model = modelProvider().trim().ifBlank { DEFAULT_MODEL }
        val workDir = context.cacheDir.resolve("audio_transcription_${System.nanoTime()}")

        try {
            val chunks = audioExtractor.extractChunks(
                videoUri = Uri.parse(videoUri),
                maxChunkDurationMs = MAX_CHUNK_DURATION_MS,
                maxChunkBytes = MAX_CHUNK_BYTES,
                outputDir = workDir
            )
            if (chunks.isEmpty()) {
                throw IllegalStateException("Não foi encontrado áudio para transcrever neste vídeo.")
            }

            val segments = mutableListOf<TranscribedSegment>()
            for ((index, chunk) in chunks.withIndex()) {
                val responseJson = callWithRetry(apiKey, model, chunk, sourceLanguage)
                val payload = TranscriptionParser.extractResponseText(responseJson)
                segments += TranscriptionParser.parse(payload, chunk.startMs, chunk.durationMs)
                chunk.file.delete()
                onChunkDone(index + 1, chunks.size)
            }
            segments.sortedBy { it.startMs }
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun callWithRetry(
        apiKey: String,
        model: String,
        chunk: AudioChunk,
        sourceLanguage: String
    ): String {
        var attempt = 1
        while (true) {
            try {
                return callGeminiApi(apiKey, model, chunk, sourceLanguage)
            } catch (e: GeminiHttpException) {
                if (!e.isRetryable || attempt >= MAX_ATTEMPTS) throw e
                delay(RETRY_BASE_DELAY_MS * attempt)
                attempt++
            }
        }
    }

    private fun callGeminiApi(
        apiKey: String,
        model: String,
        chunk: AudioChunk,
        sourceLanguage: String
    ): String {
        val (prefix, suffix) = buildRequestEnvelope(sourceLanguage, chunk.mimeType)
        val prefixBytes = prefix.toByteArray(Charsets.UTF_8)
        val suffixBytes = suffix.toByteArray(Charsets.UTF_8)
        val audioBytes = chunk.file.length()
        val base64Length = ((audioBytes + 2) / 3) * 4
        val contentLength = prefixBytes.size + base64Length + suffixBytes.size

        val conn = URL("$API_BASE_URL/$model:generateContent").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            // Evita que o HttpURLConnection mantenha o corpo inteiro (vários MB) em memória.
            conn.setFixedLengthStreamingMode(contentLength)

            conn.outputStream.use { out ->
                out.write(prefixBytes)
                Base64.getEncoder().wrap(NonClosingOutputStream(out)).use { encoder ->
                    chunk.file.inputStream().use { input -> input.copyTo(encoder, STREAM_BUFFER_BYTES) }
                }
                out.write(suffixBytes)
                out.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                conn.errorStream?.close()
                throw GeminiHttpException(
                    GeminiTranslatorImpl.getSafeErrorMessage(responseCode, model),
                    isRetryable = responseCode == HTTP_TOO_MANY_REQUESTS || responseCode in HTTP_SERVER_ERRORS
                )
            }
        } catch (e: GeminiHttpException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw GeminiHttpException("Tempo limite esgotado ao transcrever o áudio com o Google Gemini.", isRetryable = true)
        } catch (e: java.net.UnknownHostException) {
            throw IllegalStateException(
                "Não foi possível conectar à API do Google Gemini. Verifique sua conexão com a internet."
            )
        } catch (e: java.io.IOException) {
            throw GeminiHttpException("Erro de rede ao enviar o áudio para o Google Gemini.", isRetryable = true)
        } finally {
            conn.disconnect()
        }
    }

    /** Monta o JSON da requisição e o divide em (prefixo, sufixo) em volta do áudio base64. */
    private fun buildRequestEnvelope(sourceLanguage: String, mimeType: String): Pair<String, String> {
        val systemInstruction = JSONObject().put(
            "parts",
            JSONArray().put(JSONObject().put("text", buildSystemInstruction(sourceLanguage)))
        )
        val audioPart = JSONObject().put(
            "inline_data",
            JSONObject().put("mime_type", mimeType).put("data", AUDIO_PLACEHOLDER)
        )
        val userParts = JSONArray()
            .put(JSONObject().put("text", "Transcreva as falas deste áudio no formato JSON solicitado."))
            .put(audioPart)
        val segmentSchema = JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject()
                    .put("startMs", JSONObject().put("type", "INTEGER"))
                    .put("endMs", JSONObject().put("type", "INTEGER"))
                    .put("text", JSONObject().put("type", "STRING"))
            )
            .put("required", JSONArray().put("startMs").put("endMs").put("text"))
            .put("propertyOrdering", JSONArray().put("startMs").put("endMs").put("text"))
        val generationConfig = JSONObject()
            .put("temperature", 0)
            .put("maxOutputTokens", MAX_OUTPUT_TOKENS)
            .put("responseMimeType", "application/json")
            .put("responseSchema", JSONObject().put("type", "ARRAY").put("items", segmentSchema))

        val body = JSONObject()
            .put("systemInstruction", systemInstruction)
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            .put("generationConfig", generationConfig)
            .toString()

        val marker = body.indexOf(AUDIO_PLACEHOLDER)
        check(marker >= 0) { "Falha interna ao montar a requisição de transcrição." }
        return body.substring(0, marker) to body.substring(marker + AUDIO_PLACEHOLDER.length)
    }

    private fun buildSystemInstruction(sourceLanguage: String): String = buildString {
        append("Você é um transcritor profissional de legendas para vídeos.\n")
        append("Transcreva literalmente as falas do áudio no idioma original (código ISO '$sourceLanguage'), sem traduzir.\n")
        append("Divida a transcrição em legendas curtas: frases completas de no máximo ~7 segundos e ~84 caracteres.\n")
        append("startMs e endMs são inteiros em milissegundos, relativos ao início DESTE áudio, e devem acompanhar o momento real da fala.\n")
        append("Ignore música, ruídos e silêncios; não invente falas. Se não houver fala, retorne um array vazio.\n")
        append("Não inclua nomes de falantes, descrições de sons ou comentários.\n")
    }

    /** Permite fechar o encoder base64 (que grava o padding final) sem fechar a conexão. */
    private class NonClosingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
        }

        override fun close() {
            flush()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"

        /** Blocos de até 10 min mantêm a saída JSON bem abaixo do limite de tokens. */
        const val MAX_CHUNK_DURATION_MS = 10L * 60L * 1_000L

        /** 14 MB de áudio ≈ 18,7 MB em base64, abaixo do limite de 20 MB por requisição inline. */
        const val MAX_CHUNK_BYTES = 14L * 1024L * 1024L

        private const val API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val AUDIO_PLACEHOLDER = "__SUBTRANSLATE_AUDIO_BASE64__"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 180_000
        private const val MAX_OUTPUT_TOKENS = 32_768
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_BASE_DELAY_MS = 2_000L
        private const val STREAM_BUFFER_BYTES = 64 * 1024
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERRORS = 500..599
    }
}

