package com.example.domain.util

import com.example.domain.model.SubtitleLine

enum class SubtitleExportType {
    TRANSLATED_ONLY,
    ORIGINAL_ONLY,
    BILINGUAL
}

object SubtitleFormatter {

    /**
     * Checks if a character belongs to CJK (Chinese, Japanese, Korean) scripts.
     */
    private fun isCjkChar(c: Char): Boolean {
        val block = java.lang.Character.UnicodeBlock.of(c)
        return block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == java.lang.Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
                block == java.lang.Character.UnicodeBlock.HIRAGANA ||
                block == java.lang.Character.UnicodeBlock.KATAKANA ||
                block == java.lang.Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == java.lang.Character.UnicodeBlock.HANGUL_JAMO
    }

    /**
     * Checks if text predominantly contains CJK characters.
     */
    private fun containsCjk(text: String): Boolean {
        return text.any { isCjkChar(it) }
    }

    /**
     * Splits long lines nicely into at most maxCharsPerLine.
     * Accurately breaks CJK character-by-character, splits oversized words, and strips blank lines.
     */
    fun wrapText(text: String, maxCharsPerLine: Int = 42): String {
        val clean = text.trim().replace("\r\n", "\n").replace("\r", "\n")
        if (clean.isEmpty()) return ""

        val rawLines = clean.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val result = mutableListOf<String>()

        for (line in rawLines) {
            if (line.length <= maxCharsPerLine) {
                result.add(line)
                continue
            }

            if (containsCjk(line)) {
                // Break CJK by characters
                var i = 0
                while (i < line.length) {
                    val end = minOf(i + maxCharsPerLine, line.length)
                    result.add(line.substring(i, end).trim())
                    i = end
                }
            } else {
                // Space-separated languages
                val words = line.split("\\s+".toRegex()).filter { it.isNotEmpty() }
                var currentLine = StringBuilder()

                for (word in words) {
                    // If a single word is larger than the line limit, chunk it
                    if (word.length > maxCharsPerLine) {
                        if (currentLine.isNotEmpty()) {
                            result.add(currentLine.toString())
                            currentLine = StringBuilder()
                        }
                        var wIdx = 0
                        while (wIdx < word.length) {
                            val wEnd = minOf(wIdx + maxCharsPerLine, word.length)
                            val chunk = word.substring(wIdx, wEnd)
                            if (wEnd == word.length) {
                                currentLine.append(chunk)
                            } else {
                                result.add(chunk)
                            }
                            wIdx = wEnd
                        }
                        continue
                    }

                    if (currentLine.isEmpty()) {
                        currentLine.append(word)
                    } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                        currentLine.append(" ").append(word)
                    } else {
                        result.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine.toString())
                }
            }
        }

        return result.filter { it.isNotBlank() }.joinToString("\n")
    }

    /**
     * Generates a standard SRT file string, stripping empty lines from each cue.
     */
    fun toSrt(
        lines: List<SubtitleLine>,
        type: SubtitleExportType = SubtitleExportType.TRANSLATED_ONLY,
        maxCharsPerLine: Int = 42
    ): String {
        val sb = StringBuilder()
        var cueIndex = 1

        for (item in lines) {
            val content = when (type) {
                SubtitleExportType.TRANSLATED_ONLY -> {
                    if (item.translatedText.isNotBlank()) item.translatedText else item.originalText
                }
                SubtitleExportType.ORIGINAL_ONLY -> item.originalText
                SubtitleExportType.BILINGUAL -> {
                    val translated = if (item.translatedText.isNotBlank()) item.translatedText else item.originalText
                    "${item.originalText}\n$translated"
                }
            }

            val wrapped = wrapText(content, maxCharsPerLine)
            if (wrapped.isBlank()) continue

            sb.append(cueIndex++).append("\n")
            val start = SubtitleLine.formatTimestamp(item.startTimeMs, isVtt = false)
            val end = SubtitleLine.formatTimestamp(item.endTimeMs, isVtt = false)
            sb.append(start).append(" --> ").append(end).append("\n")
            sb.append(wrapped).append("\n\n")
        }

        return sb.toString()
    }

    /**
     * Generates a standard WebVTT file string, escaping HTML entities and '-->'.
     */
    fun toWebVtt(
        lines: List<SubtitleLine>,
        type: SubtitleExportType = SubtitleExportType.TRANSLATED_ONLY,
        maxCharsPerLine: Int = 42
    ): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")
        var cueIndex = 1

        for (item in lines) {
            val content = when (type) {
                SubtitleExportType.TRANSLATED_ONLY -> {
                    if (item.translatedText.isNotBlank()) item.translatedText else item.originalText
                }
                SubtitleExportType.ORIGINAL_ONLY -> item.originalText
                SubtitleExportType.BILINGUAL -> {
                    val translated = if (item.translatedText.isNotBlank()) item.translatedText else item.originalText
                    "${item.originalText}\n$translated"
                }
            }

            val wrapped = wrapText(content, maxCharsPerLine)
            if (wrapped.isBlank()) continue

            // Escape '<', '&' and '-->' in WebVTT cue payload
            val escaped = wrapped
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("-->", "--&gt;")

            sb.append(cueIndex++).append("\n")
            val start = SubtitleLine.formatTimestamp(item.startTimeMs, isVtt = true)
            val end = SubtitleLine.formatTimestamp(item.endTimeMs, isVtt = true)
            sb.append(start).append(" --> ").append(end).append("\n")
            sb.append(escaped).append("\n\n")
        }

        return sb.toString()
    }
}
