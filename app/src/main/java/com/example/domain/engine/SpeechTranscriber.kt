package com.example.domain.engine

/**
 * Trecho de fala transcrito do áudio, com tempos absolutos em relação ao início do vídeo.
 *
 * [isTimingSuspect] indica que os tempos devolvidos pelo motor eram inválidos e foram
 * corrigidos heuristicamente — a linha deve ser revisada manualmente.
 */
data class TranscribedSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val isTimingSuspect: Boolean = false
)

/** Motor de transcrição de fala (áudio do vídeo → texto com timestamps). */
interface SpeechTranscriber {

    /**
     * Transcreve as falas do vídeo informado.
     *
     * @param videoUri URI (content:// ou file://) do vídeo.
     * @param sourceLanguage código do idioma falado (ex.: "en").
     * @param onChunkDone chamado após cada bloco de áudio processado; pode lançar
     *        `CancellationException` para interromper ou suspender para pausar.
     */
    suspend fun transcribe(
        videoUri: String,
        sourceLanguage: String,
        onChunkDone: suspend (done: Int, total: Int) -> Unit
    ): List<TranscribedSegment>
}

