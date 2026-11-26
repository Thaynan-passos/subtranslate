package com.example.domain.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt

object ImagePreprocessor {

    private val SCALE_PAINT = Paint(Paint.FILTER_BITMAP_FLAG)

    /**
     * Prepares bitmap for ML Kit OCR.
     * ML Kit's modern neural recognizer performs best on clean ARGB_8888 images.
     * Upscales low-resolution subtitle crops (height < 160px) and caps maximum dimension
     * to 1280px (matching ML Kit's internal resolution) while ensuring ARGB_8888 config
     * so ML Kit never performs an extra RGB_565 -> ARGB_8888 copy per pass.
     */
    fun enhanceForOcr(src: Bitmap): Bitmap {
        val minTargetHeight = 160
        val maxDimension = 1280
        val upscale = if (src.height in 1 until minTargetHeight) {
            minTargetHeight.toFloat() / src.height.toFloat()
        } else {
            1f
        }
        val maxScale = maxDimension.toFloat() / maxOf(src.width, src.height).coerceAtLeast(1)
        val scale = minOf(upscale, maxScale)
        val needsScale = scale != 1f
        val needsConfigConversion = src.config != Bitmap.Config.ARGB_8888

        if (!needsScale && !needsConfigConversion) {
            return src
        }

        val targetW = if (needsScale) (src.width * scale).toInt().coerceAtLeast(1) else src.width
        val targetH = if (needsScale) (src.height * scale).toInt().coerceAtLeast(1) else src.height
        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            src,
            null,
            Rect(0, 0, targetW, targetH),
            if (needsScale) SCALE_PAINT else null
        )
        return output
    }

    /** Creates a grayscale contrast-stretched image for difficult foreground/background colors. */
    fun createHighContrastVariant(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var minGray = 255
        var maxGray = 0
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 77 + g * 150 + b * 29) shr 8
            pixels[i] = gray
            if (gray < minGray) minGray = gray
            if (gray > maxGray) maxGray = gray
        }

        val range = (maxGray - minGray).coerceAtLeast(1)
        for (i in pixels.indices) {
            val adjusted = ((pixels[i] - minGray) * 255 / range).coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (adjusted shl 16) or (adjusted shl 8) or adjusted
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** Creates a black-and-white OCR alternative using Otsu's global threshold. */
    fun createOtsuVariant(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val gray = (r * 77 + g * 150 + b * 29) shr 8
            pixels[i] = gray
            histogram[gray]++
        }

        val total = pixels.size.toDouble()
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
            val diff = backgroundMean - foregroundMean
            val variance = backgroundWeight * foregroundWeight * diff * diff
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = value
            }
        }

        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        for (i in pixels.indices) {
            pixels[i] = if (pixels[i] > threshold) white else black
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    data class ThumbnailMetrics(
        val gray: IntArray,
        val rgb: IntArray
    )

    /**
     * Extrai thumbnail em escala de cinza e RGB em uma única operação de redimensionamento.
     */
    fun createThumbnailMetrics(src: Bitmap, width: Int = 64, height: Int = 16): ThumbnailMetrics {
        val scaled = Bitmap.createScaledBitmap(src, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled != src) {
            scaled.recycle()
        }

        val grays = IntArray(pixels.size)
        val channels = IntArray(pixels.size * 3)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            grays[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            channels[i * 3] = r
            channels[i * 3 + 1] = g
            channels[i * 3 + 2] = b
        }
        return ThumbnailMetrics(gray = grays, rgb = channels)
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
