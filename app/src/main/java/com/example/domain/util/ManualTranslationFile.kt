package com.example.domain.util

import com.example.domain.model.SubtitleLine

/**
 * Builds and parses the editable text file used by the "Extrair Texto" (manual translation) flow.
 *
 * Exported format (UTF-8 .txt):
 * ```
 * # SubTranslate - arquivo para tradução manual
 * # ... instruções (linhas com # antes do primeiro marcador são ignoradas)
 *
 * [1]
 * Hello there.
 *
 * [2]
 * How are you?
 * ```
 *
 * The user translates only the text under each `[N]` marker (by hand, Google Tradutor, DeepL,
 * ChatGPT, etc.) and imports the file back. Imports also accept `.srt` / `.vtt` files and, as a
 * fallback, plain text with one translated phrase per line (matched by order).
 */
object ManualTranslationFile {

    enum class ImportFormat { MARKERS, SUBTITLE, PLAIN_LINES }

    data class ParseResult(
        /** 1-based phrase number -> translated text. */
        val translations: Map<Int, String>,
        val format: ImportFormat
    )

    data class ApplyResult(
        val updatedLines: List<SubtitleLine>,
        val appliedCount: Int,
        val missingCount: Int,
        val ignoredCount: Int
    )

    private val markerRegex = Regex("""^\s*[\[【［]\s*(\d{1,6})\s*[\]】］]\s*[:：.\-–]?\s*(.*)$""")
    private val timestampRegex = Regex("""\d{1,2}:\d{2}(:\d{2})?[,.]\d{1,3}\s*-->""")

    /** Generates the editable file with one numbered block per phrase. */
    fun export(
        lines: List<SubtitleLine>,
        title: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String {
        val sb = StringBuilder()
        sb.append("# SubTranslate - arquivo para tradução manual\n")
        sb.append("# Projeto: ").append(title.replace("\n", " ")).append('\n')
        sb.append("# Idiomas: ").append(sourceLanguage.uppercase()).append(" -> ")
            .append(targetLanguage.uppercase()).append('\n')
        sb.append("# Total de frases: ").append(lines.size).append('\n')
        sb.append("#\n")
        sb.append("# COMO USAR:\n")
        sb.append("# 1. Traduza apenas o texto abaixo de cada marcador [1], [2], [3]...\n")
        sb.append("# 2. Não apague nem altere os marcadores entre colchetes.\n")
        sb.append("# 3. Salve o arquivo e use \"Importar tradução\" no SubTranslate.\n")
        sb.append("# Linhas iniciadas com # neste cabeçalho são ignoradas.\n")
        sb.append('\n')

        lines.forEachIndexed { position, line ->
            sb.append('[').append(position + 1).append("]\n")
            val text = line.originalText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
            sb.append(text).append("\n\n")
        }
        return sb.toString()
    }

    /** Parses an imported file (marker .txt, .srt/.vtt or plain lines). */
    fun parse(content: String): ParseResult {
        val normalized = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        if (timestampRegex.containsMatchIn(normalized)) {
            val subtitle = parseSubtitle(normalized)
            if (subtitle.isNotEmpty()) return ParseResult(subtitle, ImportFormat.SUBTITLE)
        }

        val markers = parseMarkers(normalized)
        if (markers.isNotEmpty()) return ParseResult(markers, ImportFormat.MARKERS)

        return ParseResult(parsePlainLines(normalized), ImportFormat.PLAIN_LINES)
    }

    /**
     * Applies parsed translations to [lines] by position (phrase N = lines[N - 1]).
     * Phrases missing from the file keep their current translation.
     */
    fun apply(lines: List<SubtitleLine>, translations: Map<Int, String>): ApplyResult {
        var applied = 0
        val updated = lines.mapIndexed { position, line ->
            val translated = translations[position + 1]?.trim()
            if (!translated.isNullOrEmpty()) {
                applied++
                line.copy(translatedText = translated, isFlagged = false)
            } else {
                line
            }
        }
        val ignored = translations.keys.count { it < 1 || it > lines.size }
        return ApplyResult(
            updatedLines = updated,
            appliedCount = applied,
            missingCount = lines.size - applied,
            ignoredCount = ignored
        )
    }

    private fun parseMarkers(text: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        var currentNumber: Int? = null
        val buffer = mutableListOf<String>()

        fun flush() {
            val number = currentNumber ?: return
            val joined = buffer.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            if (joined.isNotEmpty()) result[number] = joined
            buffer.clear()
        }

        for (rawLine in text.split('\n')) {
            val match = markerRegex.matchEntire(rawLine)
            if (match != null) {
                flush()
                currentNumber = match.groupValues[1].toIntOrNull()
                val inline = match.groupValues[2].trim()
                if (inline.isNotEmpty()) buffer.add(inline)
            } else if (currentNumber != null) {
                buffer.add(rawLine)
            }
            // Lines before the first marker (header / comments) are ignored.
        }
        flush()
        return result
    }

    private fun parseSubtitle(text: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        val blocks = text.split(Regex("""\n\s*\n"""))
        var sequential = 0
        for (block in blocks) {
            val blockLines = block.split('\n').map { it.trimEnd() }.filter { it.isNotBlank() }
            val tsIndex = blockLines.indexOfFirst { timestampRegex.containsMatchIn(it) }
            if (tsIndex < 0) continue

            val explicitIndex = if (tsIndex > 0) {
                blockLines[tsIndex - 1].trim().toIntOrNull()
            } else null

            val targetNumber = explicitIndex ?: (++sequential)
            val payload = blockLines.drop(tsIndex + 1)
                .joinToString("\n") { it.trim() }
                .replace(Regex("<[^>]+>"), "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim()
            if (payload.isNotEmpty()) result[targetNumber] = payload
        }
        return result
    }

    private fun parsePlainLines(text: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        var number = 0
        for (line in text.split('\n')) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            number++
            result[number] = trimmed
        }
        return result
    }
}

