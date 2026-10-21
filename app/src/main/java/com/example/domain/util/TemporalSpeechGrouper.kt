package com.example.domain.util

import com.example.domain.model.RawFrameDetection
import com.example.domain.model.SubtitleLine

class TemporalSpeechGrouper(
    private val similarityThreshold: Float = 0.85f,
    private val minDurationMs: Long = 300L,
    private val sampleIntervalMs: Long = 333L, // approx for 3 fps
    private val minConsecutiveFrames: Int = 2
) {

    /**
     * Groups raw frame detections into cohesive speech intervals.
     */
    fun groupDetections(
        projectId: Long,
        detections: List<RawFrameDetection>
    ): List<SubtitleLine> {
        val validDetections = detections.filter { it.text.isNotBlank() }
        if (validDetections.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<RawFrameDetection>>()
        var currentCluster = mutableListOf<RawFrameDetection>()

        for (detection in validDetections) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(detection)
            } else {
                val lastDetection = currentCluster.last()
                val gap = detection.timestampMs - lastDetection.timestampMs
                val sim = Levenshtein.normalizedSimilarity(lastDetection.text, detection.text)

                // If within reasonable frame gap (e.g., at most 2 samples missed) and similar text
                val isContinuation = gap <= (sampleIntervalMs * 2.5f) && sim >= similarityThreshold

                if (isContinuation) {
                    currentCluster.add(detection)
                } else {
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(detection)
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            clusters.add(currentCluster)
        }

        // Convert clusters to SubtitleLines using voting & boundary selection
        val initialLines = mutableListOf<SubtitleLine>()
        var index = 1
        val effectiveMinDuration = maxOf(minDurationMs, minConsecutiveFrames * sampleIntervalMs)

        for (cluster in clusters) {
            // Discard clusters that don't meet minimum consecutive frames
            if (cluster.size < minConsecutiveFrames) {
                continue
            }

            val startTime = cluster.first().timestampMs
            val endTime = cluster.last().timestampMs + sampleIntervalMs
            val duration = endTime - startTime

            // Filter out noise shorter than effective min duration
            if (duration < effectiveMinDuration) {
                continue
            }

            // Voting mechanism: find the most frequent and highest confidence string
            val bestText = selectBestText(cluster)
            val avgConfidence = cluster.map { it.confidence }.average().toFloat()

            initialLines.add(
                SubtitleLine(
                    id = 0,
                    projectId = projectId,
                    index = index++,
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    originalText = bestText,
                    translatedText = "",
                    confidence = avgConfidence,
                    isFlagged = avgConfidence < 0.70f
                )
            )
        }

        // Merge adjacent lines if gap is tiny (< 300ms) and text is identical
        return mergeAdjacentSimilarLines(initialLines)
    }

    private fun selectBestText(cluster: List<RawFrameDetection>): String {
        val frequencies = mutableMapOf<String, Int>()
        val confidences = mutableMapOf<String, Float>()

        for (item in cluster) {
            val trimmed = item.text.trim()
            frequencies[trimmed] = (frequencies[trimmed] ?: 0) + 1
            val currentMaxConf = confidences[trimmed] ?: 0f
            if (item.confidence > currentMaxConf) {
                confidences[trimmed] = item.confidence
            }
        }

        // Prefer highest frequency, tie-break with confidence and length
        return frequencies.maxWithOrNull(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { confidences[it.key] ?: 0f }
                .thenBy { it.key.length }
        )?.key ?: cluster.first().text.trim()
    }

    private fun mergeAdjacentSimilarLines(lines: List<SubtitleLine>): List<SubtitleLine> {
        if (lines.size <= 1) return lines

        val merged = mutableListOf<SubtitleLine>()
        var current = lines.first()

        for (i in 1 until lines.size) {
            val next = lines[i]
            val gap = next.startTimeMs - current.endTimeMs
            val sim = Levenshtein.normalizedSimilarity(current.originalText, next.originalText)

            if (gap in 0..400 && sim >= 0.88f) {
                // Merge into current
                current = current.copy(
                    endTimeMs = next.endTimeMs,
                    confidence = (current.confidence + next.confidence) / 2f
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        // Re-index
        return merged.mapIndexed { idx, line -> line.copy(index = idx + 1) }
    }
}
