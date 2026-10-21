package com.example.domain.util

import kotlin.math.max
import kotlin.math.min

object Levenshtein {

    /**
     * Calculates the Levenshtein distance between two strings.
     */
    fun distance(s1: String, s2: String): Int {
        val a = s1.trim().lowercase()
        val b = s2.trim().lowercase()

        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,       // deletion
                    min(
                        dp[i][j - 1] + 1,   // insertion
                        dp[i - 1][j - 1] + cost // substitution
                    )
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Calculates normalized similarity in the range [0.0, 1.0].
     * 1.0 indicates an exact match.
     */
    fun normalizedSimilarity(s1: String, s2: String): Float {
        val clean1 = cleanForComparison(s1)
        val clean2 = cleanForComparison(s2)

        if (clean1 == clean2) return 1.0f
        val maxLen = max(clean1.length, clean2.length)
        if (maxLen == 0) return 1.0f

        val dist = distance(clean1, clean2)
        return 1.0f - (dist.toFloat() / maxLen.toFloat())
    }

    private fun cleanForComparison(text: String): String {
        return text.trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .replace("[.,!?:;\"'()\\-_]".toRegex(), "")
    }
}
