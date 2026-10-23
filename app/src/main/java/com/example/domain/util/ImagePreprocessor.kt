package com.example.domain.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.sqrt

object ImagePreprocessor {

    /**
     * Converts bitmap to grayscale with enhanced contrast for OCR.
     */
    fun enhanceForOcr(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // High contrast grayscale color matrix
        val matrix = ColorMatrix(
            floatArrayOf(
                0.33f * 1.5f, 0.59f * 1.5f, 0.11f * 1.5f, 0f, -30f,
                0.33f * 1.5f, 0.59f * 1.5f, 0.11f * 1.5f, 0f, -30f,
                0.33f * 1.5f, 0.59f * 1.5f, 0.11f * 1.5f, 0f, -30f,
                0f,           0f,           0f,           1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return output
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

    /**
     * Binarizes bitmap using Otsu's thresholding.
     */
}
