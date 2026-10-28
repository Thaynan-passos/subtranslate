package com.example.data.engine

import android.graphics.Bitmap
import com.example.domain.engine.OcrEngine
import com.example.domain.model.BoundingBox
import com.example.domain.model.OcrResult
import com.example.domain.model.OcrTextBlock
import com.example.domain.util.ImagePreprocessor
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
                return recognizeWithVariants(bitmap, languageCode)
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

    private suspend fun recognizeWithVariants(bitmap: Bitmap, languageCode: String): OcrResult {
        val enhanced = ImagePreprocessor.enhanceForOcr(bitmap)
        var contrast: Bitmap? = null
        var binary: Bitmap? = null
        try {
            val originalResult = recognizeOnce(enhanced, languageCode, bitmap.width, bitmap.height)
            if (!needsAlternateImage(originalResult)) return originalResult

            contrast = ImagePreprocessor.createHighContrastVariant(enhanced)
            val contrastResult = recognizeOnce(contrast, languageCode, bitmap.width, bitmap.height)
            var bestResult = chooseBetterResult(originalResult, contrastResult)

            if (bestResult.fullText.isBlank() ||
                bestResult.fullText.count { it.isLetterOrDigit() } <= 2
            ) {
                binary = ImagePreprocessor.createOtsuVariant(enhanced)
                val binaryResult = recognizeOnce(binary, languageCode, bitmap.width, bitmap.height)
                bestResult = chooseBetterResult(bestResult, binaryResult)
            }
            return bestResult
        } finally {
            if (binary != null && !binary.isRecycled) binary.recycle()
            if (contrast != null && !contrast.isRecycled) contrast.recycle()
            if (enhanced !== bitmap && !enhanced.isRecycled) enhanced.recycle()
        }
    }

    private suspend fun recognizeOnce(
        bitmap: Bitmap,
        languageCode: String,
        coordinateWidth: Int,
        coordinateHeight: Int
    ): OcrResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                val client = getRecognizer(languageCode)
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                client.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val result = processVisionText(visionText, bitmap.width, bitmap.height, coordinateWidth, coordinateHeight)
                        if (continuation.isActive) continuation.resume(result)
                    }
                    .addOnFailureListener { ex ->
                        if (continuation.isActive) continuation.resumeWithException(ex)
                    }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

    private fun needsAlternateImage(result: OcrResult): Boolean {
        val usefulCharacters = result.fullText.count { it.isLetterOrDigit() }
        return result.fullText.isBlank() || usefulCharacters <= 3 || result.confidence < 0.70f
    }

    private fun chooseBetterResult(first: OcrResult, second: OcrResult): OcrResult {
        val firstCharacters = first.fullText.count { it.isLetterOrDigit() }
        val secondCharacters = second.fullText.count { it.isLetterOrDigit() }
        if (second.fullText.isBlank()) return first
        if (first.fullText.isBlank()) return second

        val meaningfulRecovery = if (firstCharacters <= 3) {
            secondCharacters > firstCharacters
        } else {
            secondCharacters >= firstCharacters + maxOf(3, firstCharacters / 3)
        }
        if (meaningfulRecovery && second.confidence >= first.confidence - 0.10f) return second

        val comparableLength = secondCharacters >= (firstCharacters * 0.8f).toInt()
        return if (second.confidence > first.confidence + 0.05f && comparableLength) second else first
    }

    private fun processVisionText(
        visionText: Text,
        imageWidth: Int,
        imageHeight: Int,
        coordinateWidth: Int,
        coordinateHeight: Int
    ): OcrResult {
        val blocks = mutableListOf<OcrTextBlock>()
        val rawLines = mutableListOf<String>()
        val allConfidences = mutableListOf<Float>()

        for (block in visionText.textBlocks) {
            val box = block.boundingBox?.let {
                BoundingBox(
                    left = (it.left.toFloat() * coordinateWidth / imageWidth).toInt(),
                    top = (it.top.toFloat() * coordinateHeight / imageHeight).toInt(),
                    right = (it.right.toFloat() * coordinateWidth / imageWidth).toInt(),
                    bottom = (it.bottom.toFloat() * coordinateHeight / imageHeight).toInt()
                )
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
