package com.example.domain.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
    fun binarizeOtsu(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        val grayValues = IntArray(pixels.size)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            grayValues[i] = gray
            histogram[gray]++
        }

        val totalPixels = pixels.size
        var sum = 0f
        for (t in 0..255) sum += t * histogram[t]

        var sumB = 0f
        var wB = 0
        var varMax = 0f
        var threshold = 128

        for (t in 0..255) {
            wB += histogram[t]
            if (wB == 0) continue
            val wF = totalPixels - wB
            if (wF == 0) break

            sumB += (t * histogram[t]).toFloat()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF

            val varBetween = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            if (varBetween > varMax) {
                varMax = varBetween
                threshold = t
            }
        }

        val outPixels = IntArray(pixels.size)
        val black = Color.BLACK
        val white = Color.WHITE

        for (i in pixels.indices) {
            outPixels[i] = if (grayValues[i] >= threshold) white else black
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Calculates median background color at edges of bounding box for text inpainting / masking.
     */
    fun estimateBorderColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return Color.BLACK

        val samples = mutableListOf<Int>()
        for (x in 0 until width step (width / 10).coerceAtLeast(1)) {
            samples.add(bitmap.getPixel(x, 0))
            samples.add(bitmap.getPixel(x, height - 1))
        }
        for (y in 0 until height step (height / 6).coerceAtLeast(1)) {
            samples.add(bitmap.getPixel(0, y))
            samples.add(bitmap.getPixel(width - 1, y))
        }

        if (samples.isEmpty()) return Color.BLACK

        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (color in samples) {
            sumR += Color.red(color)
            sumG += Color.green(color)
            sumB += Color.blue(color)
        }
        val count = samples.size
        return Color.rgb(sumR / count, sumG / count, sumB / count)
    }
}
