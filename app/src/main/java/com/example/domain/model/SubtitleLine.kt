package com.example.domain.model

import java.io.Serializable
import java.util.Locale

data class SubtitleLine(
    val id: Long = 0,
    val projectId: Long,
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    val translatedText: String,
    val confidence: Float = 1.0f,
    val isFlagged: Boolean = false
) : Serializable {

    val durationMs: Long
        get() = (endTimeMs - startTimeMs).coerceAtLeast(0)

    fun formattedStartTime(): String = formatTimestamp(startTimeMs)
    fun formattedEndTime(): String = formatTimestamp(endTimeMs)

    companion object {
        fun formatTimestamp(timeMs: Long, isVtt: Boolean = false): String {
            val totalSeconds = timeMs / 1000
            val millis = timeMs % 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            val separator = if (isVtt) "." else ","
            return String.format(Locale.US, "%02d:%02d:%02d%s%03d", hours, minutes, seconds, separator, millis)
        }
    }
}
