package com.example.domain.util

import com.example.domain.engine.TranscribedSegment
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Converte a resposta JSON do Gemini (transcrição de áudio) em [TranscribedSegment]s
 * válidos, ordenados e com tempos absolutos no vídeo.
 */
object TranscriptionParser {

    /** Duração mínima de uma legenda, para que fique legível na tela. */
    const val MIN_SEGMENT_DURATION_MS = 300L

    /** Duração assumida quando o motor não devolve um tempo final válido. */
    const val DEFAULT_SEGMENT_DURATION_MS = 2_000L

    private const val FINISH_REASON_STOP = "STOP"
    private const val FINISH_REASON_MAX_TOKENS = "MAX_TOKENS"

    private val CODE_FENCE_REGEX = Regex("^```[a-zA-Z]*\\s*|\\s*```$")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * Extrai o texto do primeiro candidato da resposta `generateContent`.
     * Lança [IllegalStateException] com mensagem em PT-BR se a resposta foi bloqueada
     * ou truncada.
     */
    fun extractResponseText(responseJson: String): String {
        val root = try {
            JSONObject(responseJson)
        } catch (e: JSONException) {
            throw IllegalStateException("Resposta da API do Google Gemini em formato inválido.", e)
        }

        val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason", "").orEmpty()
        if (blockReason.isNotBlank()) {
            throw IllegalStateException("O Google Gemini recusou o áudio enviado (motivo: $blockReason).")
        }

        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("O Google Gemini não retornou nenhuma transcrição para o áudio.")
        }
        val candidate = candidates.getJSONObject(0)
        val finishReason = candidate.optString("finishReason", FINISH_REASON_STOP)
        if (finishReason == FINISH_REASON_MAX_TOKENS) {
            throw IllegalStateException(
                "A transcrição de um trecho do áudio ficou longa demais e foi interrompida pelo Google Gemini."
            )
        }
        if (finishReason.isNotBlank() && finishReason != FINISH_REASON_STOP) {
            throw IllegalStateException("O Google Gemini interrompeu a transcrição (motivo: $finishReason).")
        }

        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        val builder = StringBuilder()
        for (i in 0 until parts.length()) {
            builder.append(parts.optJSONObject(i)?.optString("text", "").orEmpty())
        }
        return builder.toString().trim()
    }

    /**
     * Converte o JSON de segmentos (relativo ao início do bloco) em segmentos absolutos.
     *
     * @param rawJson array `[{startMs, endMs, text}]` ou objeto `{"segments": [...]}`;
     *        cercas de markdown (```json) são toleradas.
     * @param chunkOffsetMs posição do início do bloco no vídeo.
     * @param chunkDurationMs duração do bloco; tempos fora dele são corrigidos (0 = sem limite).
     */
    fun parse(rawJson: String, chunkOffsetMs: Long, chunkDurationMs: Long): List<TranscribedSegment> {
        val cleaned = rawJson.trim().replace(CODE_FENCE_REGEX, "").trim()
        if (cleaned.isEmpty()) return emptyList()

        val items = try {
            if (cleaned.startsWith("{")) {
                JSONObject(cleaned).optJSONArray("segments") ?: JSONArray()
            } else {
                JSONArray(cleaned)
            }
        } catch (e: JSONException) {
            throw IllegalStateException("A transcrição retornada pelo Google Gemini está em formato inválido.", e)
        }

        val upperBound = if (chunkDurationMs > 0L) chunkDurationMs else Long.MAX_VALUE
        val relative = mutableListOf<TranscribedSegment>()

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val text = item.optString("text", "").replace(WHITESPACE_REGEX, " ").trim()
            if (text.isEmpty()) continue

            val rawStart = readMillis(item, "startMs")
            val rawEnd = readMillis(item, "endMs")
            var suspect = false

            val start = when {
                rawStart == null || rawStart < 0L -> {
                    suspect = true
                    relative.lastOrNull()?.endMs ?: 0L
                }
                rawStart > upperBound -> {
                    suspect = true
                    upperBound
                }
                else -> rawStart
            }

            var end = when {
                rawEnd == null || rawEnd <= start -> {
                    suspect = true
                    start + DEFAULT_SEGMENT_DURATION_MS
                }
                else -> rawEnd
            }
            if (end > upperBound) {
                // Só é suspeito se ultrapassar muito o fim do bloco (arredondamentos são normais).
                if (end - upperBound > DEFAULT_SEGMENT_DURATION_MS) suspect = true
                end = upperBound
            }
            if (end - start < MIN_SEGMENT_DURATION_MS) {
                end = start + MIN_SEGMENT_DURATION_MS
            }

            relative.add(TranscribedSegment(start, end, text, suspect))
        }

        return resolveOverlaps(relative.sortedBy { it.startMs }).map {
            it.copy(startMs = it.startMs + chunkOffsetMs, endMs = it.endMs + chunkOffsetMs)
        }
    }

    /** Encurta o segmento anterior quando ele invade o próximo, sem violar a duração mínima. */
    private fun resolveOverlaps(sorted: List<TranscribedSegment>): List<TranscribedSegment> {
        if (sorted.size < 2) return sorted
        val result = sorted.toMutableList()
        for (i in 0 until result.size - 1) {
            val current = result[i]
            val next = result[i + 1]
            if (current.endMs > next.startMs) {
                val newEnd = maxOf(current.startMs + MIN_SEGMENT_DURATION_MS, next.startMs)
                result[i] = current.copy(endMs = newEnd)
            }
        }
        return result
    }

    private fun readMillis(item: JSONObject, key: String): Long? {
        if (!item.has(key) || item.isNull(key)) return null
        val value = item.opt(key)
        return when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toDoubleOrNull()?.toLong()
            else -> null
        }
    }
}

