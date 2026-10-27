package com.example.data.engine

import android.graphics.Bitmap
import com.example.domain.engine.OcrEngine
import com.example.domain.model.BoundingBox
import com.example.domain.model.OcrResult
import com.example.domain.model.OcrTextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine : OcrEngine {

    override val name: String = "ML Kit Text Recognition v2"

    private val recognizers = ConcurrentHashMap<String, TextRecognizer>()

    private fun getRecognizer(lang: String): TextRecognizer {
        return recognizers.getOrPut(lang.lowercase()) {
            when (lang.lowercase()) {
                "zh", "chinese" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ja", "japanese" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "ko", "korean" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                "hi", "devanagari", "hindi" -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
        }
    }

    override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): OcrResult {
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                return suspendCancellableCoroutine { continuation ->
                    try {
                        val client = getRecognizer(languageCode)
                        val inputImage = InputImage.fromBitmap(bitmap, 0)

                        client.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val result = processVisionText(visionText)
                                continuation.resume(result)
                            }
                            .addOnFailureListener { ex ->
                                continuation.resumeWithException(ex)
                            }
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                lastException = e
                if (attempt < 3) {
                    delay(1500L)
                }
            }
        }
        throw lastException ?: IllegalStateException("Falha no reconhecimento de texto OCR após 3 tentativas.")
    }

    private fun processVisionText(visionText: Text): OcrResult {
        val blocks = mutableListOf<OcrTextBlock>()
        val rawLines = mutableListOf<String>()
        val allConfidences = mutableListOf<Float>()

        for (block in visionText.textBlocks) {
            val box = block.boundingBox?.let {
                BoundingBox(it.left, it.top, it.right, it.bottom)
            }

            // Calculate real confidence from lines or elements
            val lineConfidences = block.lines.mapNotNull { it.confidence }
            val blockConfidence = when {
                lineConfidences.isNotEmpty() -> lineConfidences.average().toFloat()
                else -> {
                    val elemConfidences = block.lines.flatMap { it.elements }.mapNotNull { it.confidence }
                    if (elemConfidences.isNotEmpty()) elemConfidences.average().toFloat() else 0.85f
                }
            }
            allConfidences.add(blockConfidence)

            val cleanedBlock = normalizeText(block.text)
            if (cleanedBlock.isNotBlank()) {
                blocks.add(
                    OcrTextBlock(
                        text = cleanedBlock,
                        confidence = blockConfidence,
                        boundingBox = box
                    )
                )
                rawLines.add(cleanedBlock)
            }
        }

        val fullCleanText = normalizeText(rawLines.joinToString("\n"))
        val overallConfidence = when {
            allConfidences.isNotEmpty() -> allConfidences.average().toFloat()
            fullCleanText.isNotBlank() -> 0.85f
            else -> 0f
        }

        return OcrResult(
            fullText = fullCleanText,
            confidence = overallConfidence,
            blocks = blocks
        )
    }

    private fun normalizeText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            // Only remove isolated punctuation / noise symbols surrounded by spaces or edges
            .replace("(?<=\\s|^)[|~_\\^#@\\[\\]{}]+(?=\\s|$)".toRegex(), " ")
            // Normalize multiple whitespaces
            .replace("[ \t]+".toRegex(), " ")
            .lines()
            .map { it.trim() }
            .filter { line -> line.isNotBlank() && line.any { ch -> ch.isLetterOrDigit() } }
            .joinToString("\n")
    }

    override fun close() {
        recognizers.values.forEach(TextRecognizer::close)
        recognizers.clear()
    }
}
