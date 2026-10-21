package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.example.domain.model.Project
import com.example.domain.model.SubtitleLine
import com.example.domain.util.SubtitleFormatter

object VideoBurnInService {

    /**
     * Renders a subtitle overlay bitmap for a specific timestamp, fitting dynamically to the video resolution and ROI.
     */
    fun createSubtitleOverlayBitmap(
        width: Int,
        height: Int,
        currentTimestampMs: Long,
        lines: List<SubtitleLine>,
        project: Project,
        backgroundColor: Int = Color.BLACK,
        textColor: Int = Color.WHITE,
        outlineColor: Int = Color.BLACK
    ): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val activeLine = lines.firstOrNull {
            currentTimestampMs in it.startTimeMs..it.endTimeMs
        } ?: return null

        val displayText = if (activeLine.translatedText.isNotBlank()) {
            activeLine.translatedText
        } else {
            activeLine.originalText
        }

        val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)

        val roiLeftPx = (project.roiLeft * width).toInt()
        val roiTopPx = (project.roiTop * height).toInt()
        val roiRightPx = (project.roiRight * width).toInt()
        val roiBottomPx = (project.roiBottom * height).toInt()
        val roiW = (roiRightPx - roiLeftPx).coerceAtLeast(10)
        val roiH = (roiBottomPx - roiTopPx).coerceAtLeast(10)

        // 1. Draw opaque/translucent mask to cover original burned-in text
        val maskPaint = Paint().apply {
            color = backgroundColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val maskRect = Rect(roiLeftPx, roiTopPx, roiRightPx, roiBottomPx)
        canvas.drawRect(maskRect, maskPaint)

        // 2. Measure and auto-fit font size according to ROI dimensions
        val wrappedText = SubtitleFormatter.wrapText(displayText, 38)
        val textLines = wrappedText.split("\n")

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outlineColor
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = (height * 0.003f).coerceAtLeast(2f)
        }

        // Dynamically compute optimal font size by scaling against ROI width and height
        val maxFontSize = (height * 0.08f).coerceAtLeast(12f)
        var fontSize = (roiH / (textLines.size + 0.6f)).coerceIn(12f, maxFontSize)
        textPaint.textSize = fontSize

        // Check if any line overflows ROI width, and downscale font if necessary
        val maxAllowedWidth = roiW * 0.92f
        var maxLineWidth = textLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        while (maxLineWidth > maxAllowedWidth && fontSize > 10f) {
            fontSize *= 0.90f
            textPaint.textSize = fontSize
            maxLineWidth = textLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        }
        outlinePaint.textSize = fontSize

        val lineSpacing = fontSize * 1.25f
        val totalTextHeight = textLines.size * lineSpacing
        val startY = roiTopPx + (roiH - totalTextHeight) / 2f + fontSize
        val centerX = (roiLeftPx + roiRightPx) / 2f

        for ((idx, line) in textLines.withIndex()) {
            val y = startY + (idx * lineSpacing)
            canvas.drawText(line, centerX, y, outlinePaint)
            canvas.drawText(line, centerX, y, textPaint)
        }

        return overlay
    }
}
