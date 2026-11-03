package com.example.domain.util

import com.example.domain.model.RawFrameDetection
import com.example.domain.model.SubtitleLine

class TemporalSpeechGrouper(
    private val similarityThreshold: Float = 0.80f,
    private val minDurationMs: Long = 300L,
    private val sampleIntervalMs: Long = 500L,
    private val minConsecutiveFrames: Int = 2,
    private val videoDurationMs: Long = Long.MAX_VALUE,
    private val filterWatermarks: Boolean = true,
    private val ignoredTerms: List<String> = emptyList(),
    private val maxSpeechDurationMs: Long = 15_000L
) {

    private data class MatchResult(
        val criterion: String,
        val target: RawFrameDetection
    )

    /**
     * Groups raw frame detections into cohesive speech intervals.
     */
    fun groupDetections(
        projectId: Long,
        detections: List<RawFrameDetection>
    ): List<SubtitleLine> {
        val validDetections = detections
            .map { it.copy(text = cleanSubtitleText(it.text)) }
            .filter { it.text.isNotBlank() }

        if (validDetections.isEmpty()) {
            logDebug("Nenhuma detecção válida após limpeza de texto.")
            return emptyList()
        }

        val clusters = mutableListOf<MutableList<RawFrameDetection>>()
        var currentCluster = mutableListOf<RawFrameDetection>()
        val maxAllowedGap = (sampleIntervalMs * 2.5f).toLong()

        for (detection in validDetections) {
            if (currentCluster.isEmpty()) {
                currentCluster.add(detection)
            } else {
                val lastDetection = currentCluster.last()
                val longestDetection = currentCluster.maxByOrNull { it.text.length } ?: lastDetection
                val gap = detection.timestampMs - lastDetection.timestampMs

                if (gap > maxAllowedGap) {
                    logDebug("Cluster finalizado [GAP_EXCEDIDO]: gap de ${gap}ms excede limite de ${maxAllowedGap}ms entre '${lastDetection.text}' e '${detection.text}'")
                    clusters.add(currentCluster)
                    currentCluster = mutableListOf(detection)
                } else {
                    // Avalia continuidade contra lastDetection e longestDetection
                    val match = evaluateContinuity(
                        detection = detection,
                        lastDetection = lastDetection,
                        longestDetection = longestDetection
                    )

                    if (match != null) {
                        logDebug("Continuidade detectada [${match.criterion}]: '${detection.text}' com '${match.target.text}' (gap=${gap}ms)")
                        currentCluster.add(detection)
                    } else {
                        logDebug("Cluster finalizado [DIVERGENCIA_TEXTO]: texto '${detection.text}' não corresponde a último ('${lastDetection.text}') nem a mais longo ('${longestDetection.text}') (gap=${gap}ms)")
                        clusters.add(currentCluster)
                        currentCluster = mutableListOf(detection)
                    }
                }
            }
        }
        if (currentCluster.isNotEmpty()) {
            logDebug("Cluster finalizado [FIM_DETECOES]: cluster com ${currentCluster.size} detecções finalizado no término das detecções")
            clusters.add(currentCluster)
        }

        // Convert clusters to SubtitleLines using voting & boundary selection
        val initialLines = mutableListOf<SubtitleLine>()
        var index = 1
        for (cluster in clusters) {
            val isSingleFrameAtLowFps = cluster.size == 1 &&
                    sampleIntervalMs >= 1000L &&
                    sampleIntervalMs >= minDurationMs &&
                    cluster.first().confidence >= 0.75f &&
                    (cluster.first().text.count { it.isLetterOrDigit() } >= 1 ||
                     UniversalSpeechHelper.isExpressivePunctuation(cluster.first().text))

            // Discard clusters that don't meet minimum consecutive frames
            if (cluster.size < minConsecutiveFrames && !isSingleFrameAtLowFps) {
                logDebug("Cluster descartado [MIN_FRAMES_INSUFICIENTE]: tamanho ${cluster.size} < $minConsecutiveFrames ('${cluster.firstOrNull()?.text}')")
                continue
            }

            val halfInterval = sampleIntervalMs / 2
            val startTime = (cluster.first().timestampMs - halfInterval).coerceAtLeast(0L)
            val endTime = (cluster.last().timestampMs + sampleIntervalMs - halfInterval)
                .coerceAtMost(videoDurationMs)
            val duration = endTime - startTime

            if (duration < minDurationMs || endTime <= startTime) {
                logDebug("Cluster descartado [DURACAO_INSUFICIENTE]: duração ${duration}ms < ${minDurationMs}ms ('${cluster.firstOrNull()?.text}')")
                continue
            }

            // Discard static watermark clusters that stay on screen continuously for too long
            if (filterWatermarks && WatermarkFilter.isStaticWatermarkCluster(cluster, duration, maxSpeechDurationMs)) {
                logDebug("Cluster descartado [WATERMARK_ESTATICA]: cluster estático por ${duration}ms ('${cluster.firstOrNull()?.text}')")
                continue
            }

            // Voting mechanism: find the most complete, stable and highest confidence string
            val bestText = selectBestText(cluster)
            val avgConfidence = cluster.map { it.confidence }.average().toFloat()

            if (bestText.isNotBlank()) {
                logDebug("Cluster aceito: index=$index [${startTime}ms - ${endTime}ms] ($duration ms) texto='$bestText' conf=$avgConfidence")
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
            } else {
                logDebug("Cluster descartado [TEXTO_VAZIO]: texto limpo resultante está vazio")
            }
        }

        // Merge adjacent lines if gap <= 800ms and lines are similar or continuations
        return mergeAdjacentSimilarLines(initialLines)
    }

    private fun evaluateContinuity(
        detection: RawFrameDetection,
        lastDetection: RawFrameDetection,
        longestDetection: RawFrameDetection
    ): MatchResult? {
        val cleanDetection = Levenshtein.cleanForComparison(detection.text)
        val cleanLast = Levenshtein.cleanForComparison(lastDetection.text)
        val cleanLongest = Levenshtein.cleanForComparison(longestDetection.text)

        // 1. Critério EXATO
        if (cleanDetection == cleanLast) {
            return MatchResult(CRITERION_EXACT, lastDetection)
        }
        if (cleanDetection == cleanLongest) {
            return MatchResult(CRITERION_EXACT, longestDetection)
        }

        // 2. Critério LEVENSHTEIN (tolerância absoluta para curtos e percentual para longos)
        if (Levenshtein.isSimilar(cleanLast, cleanDetection, similarityThreshold)) {
            return MatchResult(CRITERION_LEVENSHTEIN, lastDetection)
        }
        if (cleanLongest != cleanLast && Levenshtein.isSimilar(cleanLongest, cleanDetection, similarityThreshold)) {
            return MatchResult(CRITERION_LEVENSHTEIN, longestDetection)
        }

        // 3. Critério PREFIXO_CUMULATIVO / Token Overlap
        // Testa com lastDetection
        if (isCumulativeOrTokenOverlap(cleanLast, cleanDetection)) {
            return MatchResult(CRITERION_CUMULATIVE_PREFIX, lastDetection)
        }
        // Testa com longestDetection
        if (cleanLongest != cleanLast && isCumulativeOrTokenOverlap(cleanLongest, cleanDetection)) {
            return MatchResult(CRITERION_CUMULATIVE_PREFIX, longestDetection)
        }

        return null
    }

    private fun isCumulativeOrTokenOverlap(baseClean: String, newClean: String): Boolean {
        if (baseClean.isBlank() || newClean.isBlank()) return false

        // Caso 1: newClean contém baseClean como prefixo/início (ou vice-versa para drops momentâneos)
        if (isPrefixContinuation(shortStr = baseClean, longStr = newClean) ||
            isPrefixContinuation(shortStr = newClean, longStr = baseClean)) {
            return true
        }

        // Caso 2: Compartilhar > 70% das palavras na mesma ordem (token overlap)
        val tokensBase = baseClean.split(" ").filter { it.isNotBlank() }
        val tokensNew = newClean.split(" ").filter { it.isNotBlank() }
        if (tokensBase.isNotEmpty() && tokensNew.isNotEmpty()) {
            val ratio = tokenOverlapRatio(tokensBase, tokensNew)
            if (ratio >= 0.70f) {
                // Para 1 único token, exige que esteja no início ou seja idêntico
                if (minOf(tokensBase.size, tokensNew.size) == 1) {
                    if (tokensNew.first() == tokensBase.first() || newClean.startsWith(baseClean) || baseClean.startsWith(newClean)) {
                        return true
                    }
                } else {
                    return true
                }
            }
        }

        return false
    }

    private fun isPrefixContinuation(shortStr: String, longStr: String): Boolean {
        if (shortStr.isBlank() || longStr.isBlank()) return false
        if (longStr.length < shortStr.length) return false

        // Início direto com limite de palavra
        if (longStr.startsWith(shortStr)) {
            if (longStr.length == shortStr.length || longStr[shortStr.length] == ' ') {
                return true
            }
        }

        val shortTokens = shortStr.split(" ").filter { it.isNotBlank() }
        val longTokens = longStr.split(" ").filter { it.isNotBlank() }
        if (shortTokens.isEmpty() || shortTokens.size > longTokens.size) return false

        val prefixTokens = longTokens.take(shortTokens.size)
        // Todas as palavras anteriores coincidem e a última palavra é prefixo ou similar
        val initialMatch = (0 until shortTokens.size - 1).all { idx ->
            shortTokens[idx] == prefixTokens[idx] || Levenshtein.isSimilar(shortTokens[idx], prefixTokens[idx])
        }

        if (initialMatch) {
            val lastShort = shortTokens.last()
            val lastLong = prefixTokens.last()
            if (lastLong.startsWith(lastShort) || Levenshtein.isSimilar(lastShort, lastLong)) {
                return true
            }
        }

        // Tolerância de OCR no bloco de prefixo
        if (shortStr.length >= 4) {
            val longPrefix = longStr.take(shortStr.length)
            if (Levenshtein.isSimilar(shortStr, longPrefix, similarityThreshold)) {
                return true
            }
        }

        return false
    }

    private fun tokenOverlapRatio(tokens1: List<String>, tokens2: List<String>): Float {
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0f
        val m = tokens1.size
        val n = tokens2.size
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 1..m) {
            for (j in 1..n) {
                val t1 = tokens1[i - 1]
                val t2 = tokens2[j - 1]
                val matches = (t1 == t2) || (t1.length >= 3 && t2.length >= 3 && Levenshtein.isSimilar(t1, t2))
                dp[i][j] = if (matches) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        val lcs = dp[m][n]
        val minSize = minOf(m, n)
        return lcs.toFloat() / minSize.toFloat()
    }

    private fun selectBestText(cluster: List<RawFrameDetection>): String {
        val cleanedDetections = cluster.mapNotNull { item ->
            val cleaned = cleanSubtitleText(item.text)
            if (cleaned.isNotBlank()) item.copy(text = cleaned) else null
        }
        if (cleanedDetections.isEmpty()) {
            return cleanSubtitleText(cluster.first().text)
        }

        val frequencies = mutableMapOf<String, Int>()
        val confidences = mutableMapOf<String, Float>()
        for (item in cleanedDetections) {
            frequencies[item.text] = (frequencies[item.text] ?: 0) + 1
            val currentConf = confidences[item.text] ?: 0f
            if (item.confidence > currentConf) {
                confidences[item.text] = item.confidence
            }
        }

        val uniqueTexts = frequencies.keys.toList()
        if (uniqueTexts.size == 1) {
            return uniqueTexts.first()
        }

        // Filtra textos que são estritamente prefixos incompletos de outro texto mais longo no cluster
        val nonPrefixTexts = uniqueTexts.filter { candidate ->
            val candidateNorm = Levenshtein.cleanForComparison(candidate)
            val isStrictPrefixOfAnother = uniqueTexts.any { other ->
                if (other == candidate) return@any false
                val otherNorm = Levenshtein.cleanForComparison(other)
                otherNorm.length > candidateNorm.length && isPrefixContinuation(candidateNorm, otherNorm)
            }
            !isStrictPrefixOfAnother
        }

        val candidates = if (nonPrefixTexts.isNotEmpty()) nonPrefixTexts else uniqueTexts

        // Seleciona o texto mais estável e completo:
        // 1. Maior frequência
        // 2. Maior confiança
        // 3. Maior comprimento
        val best = candidates.maxWithOrNull(
            compareBy<String> { frequencies[it] ?: 0 }
                .thenBy { confidences[it] ?: 0f }
                .thenBy { it.length }
        )

        return best ?: cleanSubtitleText(cluster.first().text)
    }

    private fun cleanSubtitleText(text: String): String {
        val filtered = WatermarkFilter.cleanText(
            text = text,
            filterEnabled = filterWatermarks,
            customIgnoredTerms = ignoredTerms
        )
        return filtered
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() && (line.any { ch -> ch.isLetterOrDigit() } || UniversalSpeechHelper.isExpressivePunctuation(line))
            }
            .joinToString("\n")
            .trim()
    }

    private fun mergeAdjacentSimilarLines(lines: List<SubtitleLine>): List<SubtitleLine> {
        if (lines.size <= 1) return lines

        val merged = mutableListOf<SubtitleLine>()
        var current = lines.first()

        for (i in 1 until lines.size) {
            val next = lines[i]
            val gap = next.startTimeMs - current.endTimeMs

            // Janela temporal expandida para até 800ms
            val isWithinWindow = gap in -200..800

            val cleanCurrent = Levenshtein.cleanForComparison(current.originalText)
            val cleanNext = Levenshtein.cleanForComparison(next.originalText)

            val isSimilar = Levenshtein.isSimilar(cleanCurrent, cleanNext, threshold = 0.80f)
            val isContinuationOrSuffix = isPrefixContinuation(cleanCurrent, cleanNext) ||
                    isPrefixContinuation(cleanNext, cleanCurrent) ||
                    isSuffixContinuation(cleanCurrent, cleanNext) ||
                    isSuffixContinuation(cleanNext, cleanCurrent) ||
                    tokenOverlapRatio(
                        cleanCurrent.split(" ").filter { it.isNotBlank() },
                        cleanNext.split(" ").filter { it.isNotBlank() }
                    ) >= 0.70f

            if (isWithinWindow && (isSimilar || isContinuationOrSuffix)) {
                logDebug("Fusão adjacente [FUSAO_ADJACENTE]: linha ${current.index} ('${current.originalText}') fundida com linha ${next.index} ('${next.originalText}') (gap=${gap}ms)")

                // Escolhe a versão mais completa e representativa do diálogo
                val chosenText = when {
                    next.originalText.length > current.originalText.length -> next.originalText
                    current.originalText.length > next.originalText.length -> current.originalText
                    next.confidence > current.confidence -> next.originalText
                    else -> current.originalText
                }
                val mergedConfidence = (current.confidence + next.confidence) / 2f
                current = current.copy(
                    endTimeMs = maxOf(current.endTimeMs, next.endTimeMs),
                    originalText = chosenText,
                    confidence = mergedConfidence,
                    isFlagged = mergedConfidence < 0.70f
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

    private fun isSuffixContinuation(shortStr: String, longStr: String): Boolean {
        if (shortStr.isBlank() || longStr.isBlank()) return false
        if (longStr.length < shortStr.length) return false

        if (longStr.endsWith(shortStr)) {
            val prefixBoundaryIndex = longStr.length - shortStr.length - 1
            if (prefixBoundaryIndex < 0 || longStr[prefixBoundaryIndex] == ' ') {
                return true
            }
        }

        val shortTokens = shortStr.split(" ").filter { it.isNotBlank() }
        val longTokens = longStr.split(" ").filter { it.isNotBlank() }
        if (shortTokens.isEmpty() || shortTokens.size > longTokens.size) return false

        val suffixTokens = longTokens.takeLast(shortTokens.size)
        return suffixTokens == shortTokens
    }

    companion object {
        const val TAG = "SpeechGrouper"
        const val CRITERION_EXACT = "EXATO"
        const val CRITERION_LEVENSHTEIN = "LEVENSHTEIN"
        const val CRITERION_CUMULATIVE_PREFIX = "PREFIXO_CUMULATIVO"
        const val CRITERION_ADJACENT_MERGE = "FUSAO_ADJACENTE"

        private fun logDebug(message: String) {
            try {
                android.util.Log.d(TAG, message)
            } catch (_: Throwable) {
                println("[$TAG] $message")
            }
        }
    }
}
