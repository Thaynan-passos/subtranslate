package com.example.domain.util

import com.example.domain.model.RawFrameDetection
import java.util.Locale

object WatermarkFilter {

    // Common handle patterns: @user_name, @canal.dorama, etc.
    private val HANDLE_PATTERN = Regex("(?i)@[a-z0-9_.-]{2,}")

    // Web URLs, Telegram channels, and domain names
    private val URL_PATTERN = Regex("(?i)\\b(?:https?://|www\\.)[a-z0-9_./?=&%-]+")
    private val TELEGRAM_PATTERN = Regex("(?i)\\b(?:t\\.me|telegram\\.me|discord\\.(?:gg|io|com)/invite)/[a-z0-9_./-]+")
    private val DOMAIN_PATTERN = Regex("(?i)\\b[a-z0-9_.-]{2,}\\.(?:com|org|net|tv|io|me|cc|to|app|xyz|site|vip|top|club|link|online|br)(?:/[a-z0-9_./-]*)?\\b")

    // Video encodings, resolutions, rip tags
    private val RIP_TAGS_PATTERN = Regex("(?i)\\b(?:1080p|720p|480p|2160p|4k|x264|x265|hevc|web-?dl|bluray|hdrip|bdrip|dvdrip|h\\.?264|h\\.?265|aac)\\b")

    // Social media / promotion keywords and phrases
    private val PROMO_KEYWORDS_PATTERN = Regex(
        "(?i)\\b(?:tiktok|kwai|telegram|subscribe(?:\\s+to\\s+(?:our|my|the)\\s+channel)?|inscreva-se(?:\\s+(?:no|em\\s+nosso)\\s+canal)?|inscreva\\s+no\\s+canal|fansub|watermark|repost|like\\s*(?:&|and|e)\\s*(?:follow|subscribe)|follow\\s+(?:me|us|our\\s+channel)|canal:)\\b"
    )

    /**
     * Splits comma or newline-separated strings into a clean list of ignored terms.
     */
    fun parseIgnoredTerms(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",", "\n", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * Checks if a single line or segment matches known watermark indicators.
     */
    fun isLineWatermark(line: String, customIgnoredTerms: List<String> = emptyList()): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false

        // Check custom user terms
        for (term in customIgnoredTerms) {
            val cleanTerm = term.trim()
            if (cleanTerm.isNotEmpty() && trimmed.contains(cleanTerm, ignoreCase = true)) {
                // If the entire line is essentially just the term or dominated by it
                val withoutTerm = trimmed.replace(cleanTerm, "", ignoreCase = true).trim()
                if (withoutTerm.count { it.isLetterOrDigit() } <= 2) {
                    return true
                }
            }
        }

        // Check URL or Telegram handles
        if (URL_PATTERN.containsMatchIn(trimmed) || TELEGRAM_PATTERN.containsMatchIn(trimmed)) {
            val stripped = trimmed
                .replace(URL_PATTERN, "")
                .replace(TELEGRAM_PATTERN, "")
                .trim()
            if (stripped.count { it.isLetterOrDigit() } <= 3) return true
        }

        // Check domain pattern
        if (DOMAIN_PATTERN.containsMatchIn(trimmed)) {
            val stripped = trimmed.replace(DOMAIN_PATTERN, "").trim()
            if (stripped.count { it.isLetterOrDigit() } <= 3) return true
        }

        // Check handle pattern (@usuario)
        if (HANDLE_PATTERN.containsMatchIn(trimmed)) {
            val stripped = trimmed.replace(HANDLE_PATTERN, "").trim()
            if (stripped.count { it.isLetterOrDigit() } <= 3) return true
        }

        // Check rip / resolution tags (e.g. "1080p HEVC")
        if (RIP_TAGS_PATTERN.containsMatchIn(trimmed)) {
            val stripped = trimmed.replace(RIP_TAGS_PATTERN, "").trim()
            if (stripped.count { it.isLetterOrDigit() } <= 2) return true
        }

        // Check promo keywords
        if (PROMO_KEYWORDS_PATTERN.containsMatchIn(trimmed)) {
            val stripped = trimmed.replace(PROMO_KEYWORDS_PATTERN, "").trim()
            if (stripped.count { it.isLetterOrDigit() } <= 3) return true
        }

        return false
    }

    /**
     * Cleans OCR text by stripping out watermark lines, promotional handles, and URLs,
     * while preserving actual spoken dialogue lines.
     */
    fun cleanText(
        text: String,
        filterEnabled: Boolean = true,
        customIgnoredTerms: List<String> = emptyList()
    ): String {
        if (!filterEnabled || text.isBlank()) return text.trim()

        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()

        for (rawLine in lines) {
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) continue

            // If the entire line is a watermark / username / channel tag, skip it
            if (isLineWatermark(trimmedLine, customIgnoredTerms)) {
                continue
            }

            // If line contains embedded handles or URLs alongside spoken text, strip the watermark portion
            var lineWithoutWatermarks = trimmedLine

            for (term in customIgnoredTerms) {
                val cleanTerm = term.trim()
                if (cleanTerm.isNotBlank()) {
                    val escaped = Regex.escape(cleanTerm)
                    val prefix = if (cleanTerm.first().isLetterOrDigit()) "(?<![\\p{L}\\p{N}])" else ""
                    val suffix = if (cleanTerm.last().isLetterOrDigit()) "(?![\\p{L}\\p{N}])" else ""
                    lineWithoutWatermarks = lineWithoutWatermarks.replace(
                        Regex("(?i)$prefix$escaped$suffix"),
                        ""
                    ).trim()
                }
            }

            lineWithoutWatermarks = lineWithoutWatermarks
                .replace(URL_PATTERN, "")
                .replace(TELEGRAM_PATTERN, "")
                .replace(DOMAIN_PATTERN, "")
                .replace(HANDLE_PATTERN, "")
                .replace(RIP_TAGS_PATTERN, "")
                .replace(PROMO_KEYWORDS_PATTERN, "")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (lineWithoutWatermarks.isNotBlank() && lineWithoutWatermarks.any { it.isLetterOrDigit() }) {
                cleanedLines.add(lineWithoutWatermarks)
            }
        }

        return cleanedLines.joinToString("\n").trim()
    }

    /**
     * Detects if a cluster of detections is a static watermark (e.g., unchanging text that stays
     * on screen uninterrupted for an unnaturally long duration > maxSpeechDurationMs).
     */
    fun isStaticWatermarkCluster(
        cluster: List<RawFrameDetection>,
        durationMs: Long,
        maxSpeechDurationMs: Long = 15_000L
    ): Boolean {
        if (cluster.isEmpty()) return false

        // Spoken dialogue in media rarely stays completely identical without pause for > 15s,
        // and static watermarks are typically short tags/logos (1 line, <= 35 characters).
        if (durationMs > maxSpeechDurationMs) {
            val firstText = cluster.first().text.trim()
            if (firstText.lines().size > 1 || firstText.length > 35) {
                return false
            }
            // Check if 85%+ of detections in this cluster have identical or near-identical text
            val identicalCount = cluster.count {
                Levenshtein.normalizedSimilarity(it.text.trim(), firstText) >= 0.88f
            }
            if (identicalCount.toFloat() / cluster.size.toFloat() >= 0.85f) {
                return true
            }
        }
        return false
    }
}

