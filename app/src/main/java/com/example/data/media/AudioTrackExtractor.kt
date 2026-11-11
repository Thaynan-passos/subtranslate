package com.example.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer

/** Erro de extração de áudio com mensagem em PT-BR, segura para exibir ao usuário. */
class AudioExtractionException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Bloco de áudio extraído do vídeo, pronto para ser enviado a um motor de transcrição. */
data class AudioChunk(
    val file: File,
    val startMs: Long,
    val durationMs: Long,
    val mimeType: String
)

/**
 * Extrai a faixa de áudio de um vídeo **sem recodificar** (remux via [MediaExtractor] +
 * [MediaMuxer]) e a divide em blocos `.m4a` limitados por duração e por tamanho.
 *
 * Suporta apenas áudio AAC, que é o padrão da grande maioria dos arquivos MP4/MOV/MKV.
 */
class AudioTrackExtractor(private val context: Context) {

    suspend fun extractChunks(
        videoUri: Uri,
        maxChunkDurationMs: Long,
        maxChunkBytes: Long,
        outputDir: File
    ): List<AudioChunk> {
        require(maxChunkDurationMs > 0L) { "maxChunkDurationMs deve ser positivo." }
        require(maxChunkBytes > 0L) { "maxChunkBytes deve ser positivo." }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw AudioExtractionException("Não foi possível preparar a pasta temporária para o áudio.")
        }

        val extractor = MediaExtractor()
        val chunks = mutableListOf<AudioChunk>()
        var writer: ChunkWriter? = null

        try {
            try {
                extractor.setDataSource(context, videoUri, null)
            } catch (e: Exception) {
                throw AudioExtractionException("Não foi possível abrir o vídeo para ler o áudio.", e)
            }

            val trackIndex = findAudioTrack(extractor)
                ?: throw AudioExtractionException("O vídeo selecionado não possui faixa de áudio.")
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                throw AudioExtractionException(
                    "Formato de áudio não suportado ($mime). A transcrição funciona com vídeos cujo áudio é AAC."
                )
            }
            extractor.selectTrack(trackIndex)

            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(MIN_SAMPLE_BUFFER_BYTES)
            } else {
                DEFAULT_SAMPLE_BUFFER_BYTES
            }
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            val maxChunkDurationUs = maxChunkDurationMs * MICROS_PER_MILLI
            var samplesRead = 0L

            while (true) {
                if (samplesRead % CANCELLATION_CHECK_INTERVAL == 0L) {
                    currentCoroutineContext().ensureActive()
                }
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)

                val current = writer
                val mustRotate = current != null && current.samples > 0 && (
                    sampleTimeUs - current.startUs >= maxChunkDurationUs ||
                        current.bytes + size > maxChunkBytes
                    )
                if (current == null || mustRotate) {
                    current?.let { chunks.add(it.finish(mime)) }
                    val file = File(outputDir, "audio_chunk_${chunks.size}.m4a")
                    writer = ChunkWriter(file, format, sampleTimeUs)
                }

                val active = writer ?: break
                bufferInfo.set(
                    0,
                    size,
                    sampleTimeUs - active.startUs,
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                )
                active.write(buffer, bufferInfo, sampleTimeUs)
                samplesRead++
                extractor.advance()
            }

            writer?.let { chunks.add(it.finish(mime)) }
            writer = null
            return chunks
        } catch (e: CancellationException) {
            writer?.abort()
            chunks.forEach { it.file.delete() }
            throw e
        } catch (e: AudioExtractionException) {
            writer?.abort()
            chunks.forEach { it.file.delete() }
            throw e
        } catch (e: Exception) {
            writer?.abort()
            chunks.forEach { it.file.delete() }
            throw AudioExtractionException("Falha ao extrair o áudio do vídeo.", e)
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    /** Escreve um único bloco `.m4a`; os tempos das amostras são relativos ao início do bloco. */
    private class ChunkWriter(
        val file: File,
        format: MediaFormat,
        val startUs: Long
    ) {
        private val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val track = muxer.addTrack(format)
        var samples = 0L
            private set
        var bytes = 0L
            private set
        private var lastSampleUs = startUs

        init {
            muxer.start()
        }

        fun write(buffer: ByteBuffer, info: MediaCodec.BufferInfo, absoluteTimeUs: Long) {
            muxer.writeSampleData(track, buffer, info)
            samples++
            bytes += info.size
            lastSampleUs = maxOf(lastSampleUs, absoluteTimeUs)
        }

        fun finish(mime: String): AudioChunk {
            if (samples == 0L) {
                abort()
                throw AudioExtractionException("Nenhuma amostra de áudio válida pôde ser gravada para o bloco.")
            }
            muxer.stop()
            muxer.release()
            val durationMs = ((lastSampleUs - startUs) / MICROS_PER_MILLI) + AAC_FRAME_DURATION_MS
            return AudioChunk(
                file = file,
                startMs = startUs / MICROS_PER_MILLI,
                durationMs = durationMs,
                mimeType = if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) M4A_MIME_TYPE else mime
            )
        }

        fun abort() {
            try {
                muxer.release()
            } catch (e: Exception) {
                // O muxer pode já estar em estado inválido; o arquivo é descartado de qualquer forma.
                android.util.Log.w(TAG, "Falha ao liberar o muxer de áudio: ${e.javaClass.simpleName}")
            }
            file.delete()
        }
    }

    companion object {
        private const val TAG = "AudioTrackExtractor"
        private const val MICROS_PER_MILLI = 1_000L
        private const val DEFAULT_SAMPLE_BUFFER_BYTES = 1024 * 1024
        private const val MIN_SAMPLE_BUFFER_BYTES = 64 * 1024
        private const val CANCELLATION_CHECK_INTERVAL = 500L

        /** Duração aproximada de um quadro AAC (1024 amostras a 44,1 kHz). */
        private const val AAC_FRAME_DURATION_MS = 23L

        const val M4A_MIME_TYPE = "audio/mp4"
    }
}
