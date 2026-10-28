package com.example.domain.util

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

object ImagePreprocessor {

    /**
     * Prepares bitmap for ML Kit OCR.
     * ML Kit's modern neural recognizer performs best on clean color images.
     * Upscales low-resolution subtitle crops (height < 160px) so character strokes
     * are well-defined for the neural network.
     */
    fun enhanceForOcr(src: Bitmap): Bitmap {
        val minTargetHeight = 160
        val maxDimension = 1920
        val upscale = if (src.height in 1 until minTargetHeight) {
            minTargetHeight.toFloat() / src.height.toFloat()
        } else {
            1f
        }
        val maxScale = maxDimension.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1)
        val scale = minOf(upscale, maxScale)
        if (scale != 1f) {
            val targetW = (src.width * scale).toInt().coerceAtLeast(1)
            val targetH = (src.height * scale).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, targetW, targetH, true)
        }
        return src
    }

    /** Creates a grayscale contrast-stretched image for difficult foreground/background colors. */
    fun createHighContrastVariant(src: Bitmap): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        var minGray = 255
        var maxGray = 0
        val grays = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = ((pixel shr 16 and 0xFF) * 0.299f +
                    (pixel shr 8 and 0xFF) * 0.587f +
                    (pixel and 0xFF) * 0.114f).toInt()
            grays[i] = gray
            minGray = minOf(minGray, gray)
            maxGray = maxOf(maxGray, gray)
        }

        val range = (maxGray - minGray).coerceAtLeast(1)
        val outputPixels = IntArray(pixels.size)
        for (i in grays.indices) {
            val adjusted = ((grays[i] - minGray) * 255 / range).coerceIn(0, 255)
            outputPixels[i] = (0xFF shl 24) or (adjusted shl 16) or (adjusted shl 8) or adjusted
        }

        return Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, src.width, 0, 0, src.width, src.height)
        }
    }

    /** Creates a black-and-white OCR alternative using Otsu's global threshold. */
    fun createOtsuVariant(src: Bitmap): Bitmap {
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)

        val histogram = IntArray(256)
        val grays = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = ((pixel shr 16 and 0xFF) * 0.299f +
                    (pixel shr 8 and 0xFF) * 0.587f +
                    (pixel and 0xFF) * 0.114f).toInt()
            grays[i] = gray
            histogram[gray]++
        }

        val total = grays.size.toDouble()
        var totalWeighted = 0.0
        for (value in histogram.indices) totalWeighted += value.toDouble() * histogram[value]

        var backgroundWeight = 0.0
        var backgroundWeighted = 0.0
        var threshold = 127
        var maxVariance = -1.0
        for (value in histogram.indices) {
            backgroundWeight += histogram[value]
            if (backgroundWeight == 0.0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0.0) break

            backgroundWeighted += value.toDouble() * histogram[value]
            val backgroundMean = backgroundWeighted / backgroundWeight
            val foregroundMean = (totalWeighted - backgroundWeighted) / foregroundWeight
            val variance = backgroundWeight * foregroundWeight *
                    (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = value
            }
        }

        val outputPixels = IntArray(grays.size)
        for (i in grays.indices) {
            val value = if (grays[i] > threshold) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            outputPixels[i] = value
        }

        return Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, src.width, 0, 0, src.width, src.height)
        }
    }

    /**
     * Extracts a downsampled grayscale thumbnail (64x16 by default) for high-sensitivity frame diffing.
     */
    fun createThumbnailGrayscale(src: Bitmap, width: Int = 64, height: Int = 16): IntArray {
        val scaled = Bitmap.createScaledBitmap(src, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled != src) {
            scaled.recycle()
        }

        val grays = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            grays[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }
        return grays
    }

    fun createThumbnailRgb(src: Bitmap, width: Int = 64, height: Int = 16): IntArray {
        val scaled = Bitmap.createScaledBitmap(src, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled != src) scaled.recycle()

        val channels = IntArray(pixels.size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            channels[i * 3] = pixel shr 16 and 0xFF
            channels[i * 3 + 1] = pixel shr 8 and 0xFF
            channels[i * 3 + 2] = pixel and 0xFF
        }
        return channels
    }

    /**
     * Calculates the standard deviation of grayscale pixels.
     * Low standard deviation (e.g. < 6.0) indicates a flat/empty region without text.
     */
    fun computeStandardDeviation(grays: IntArray): Float {
        if (grays.isEmpty()) return 0f
        var sum = 0.0
        for (v in grays) sum += v
        val mean = sum / grays.size

        var varianceSum = 0.0
        for (v in grays) {
            val diff = v - mean
            varianceSum += diff * diff
        }
        return sqrt(varianceSum / grays.size).toFloat()
    }

    /**
     * Calculates Mean Absolute Error (MAE) between two grayscale thumbnails.
     */
    fun computeMae(thumb1: IntArray, thumb2: IntArray): Float {
        val size = minOf(thumb1.size, thumb2.size)
        if (size == 0) return 0f

        var diffSum = 0L
        for (i in 0 until size) {
            diffSum += abs(thumb1[i] - thumb2[i])
        }
        return diffSum.toFloat() / size.toFloat()
    }

    /**
     * Checks if two frame thumbnails are visually similar within a given MAE threshold.
     */
    fun isFrameSimilar(thumb1: IntArray, thumb2: IntArray, maeThreshold: Float = 6.0f): Boolean {
        return computeMae(thumb1, thumb2) <= maeThreshold
    }

}
