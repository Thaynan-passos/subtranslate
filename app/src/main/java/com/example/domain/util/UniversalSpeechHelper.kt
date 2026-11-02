package com.example.domain.util

import java.util.Locale

object UniversalSpeechHelper {

    // Regex para identificar padrões de onomatopeias, risadas e interjeições com letras arrastadas
    private val UNIVERSAL_TOKEN_REGEX = Regex(
        "(?i)^(" +
                // Risadas: haha, haaaa, hehe, hihi, kkkk, rsrs, fufufu, jajaja
                "h+a+[ha]*|h+e+[he]*|h+i+[hi]*|k{2,}|(rs){2,}|f+u+[fu]*|j+a+[ja]*|" +
                // Sons vocálicos arrastados e gemidos: aah, aaah, ooh, oooh, uuh, eeh, umm, mmm
                "a+h+|h+a+|o+h+|h+o+|u+h+|h+u+|e+h+|h+e+|" +
                "a{2,}|o{2,}|u{2,}|e{2,}|i{2,}|" +
                "u+m+|m{2,}[a-z]*|n+g+h*|a*r+g+h+|u*r+g+h+|" +
                // Suspiros, surpresa, choque e reações universais
                "w+o+w+|w+h+o+a+|y+a+y+|o+u+c+h+|k+y+a+[a]*|n+y+a+[a]*|" +
                "p+f+f+t*|s+h{2,}|p+h+e+w+|u+g+h+|y+u+c+k+|y+u+m+|" +
                "s+o+b+|s+i+g+h+|g+a+s+p+|m+o+a+n+|g+r+o+a+n+|p+a+n+t+|" +
                "a+w+[w]*|o+w+[w]*|b+o+[o]*|u+w+u|o+w+o|ara\\s+ara" +
                ")$"
    )

    /**
     * Retorna se a linha é uma pontuação expressiva de legenda (ex: reticências "...", "…", ou "?!", "---"),
     * evitando que pausas dramáticas e silêncios expressivos sejam descartados como ruído de OCR.
     */
    fun isExpressivePunctuation(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // Contém reticências
        if (trimmed.contains("...") || trimmed.contains("…")) return true
        // Sequência de 2 ou mais pontuações expressivas (ex: "?!", "!!", "---", "--")
        return trimmed.matches(Regex("^[.!?…—–\\-~]+$")) && trimmed.length >= 2
    }

    /**
     * Verifica se um único token/palavra é uma onomatopeia ou interjeição universal,
     * incluindo palavras arrastadas ("haaaa", "Ohhhhh", "Mmmgh", "Kyaaa").
     */
    fun isUniversalToken(token: String): Boolean {
        val clean = token.trim().lowercase(Locale.ROOT).replace(Regex("[.,!?:;\"'()\\-_~]"), "")
        if (clean.isEmpty()) return false
        return UNIVERSAL_TOKEN_REGEX.matches(clean)
    }

    /**
     * Verifica se toda a linha/frase consiste unicamente de interjeições/onomatopeias universais
     * e sinais de pontuação (ex: "Haaaa...", "Ohhhhh!", "Ah... Ah...", "Mmm...", "Kyaaa?!").
     */
    fun isUniversalExpression(text: String): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false

        // Se for puramente pontuação expressiva (ex: "..."), já tem sentido universal/dramático
        if (isExpressivePunctuation(clean)) return true

        // Divide em palavras / tokens
        val tokens = clean.split(Regex("\\s+"))
            .map { it.replace(Regex("^[.,!?:;\"'()\\-_~]+|[.,!?:;\"'()\\-_~]+$"), "") }
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return false

        // Todos os tokens devem ser interjeições/onomatopeias universais
        return tokens.all { isUniversalToken(it) }
    }

    /**
     * Extrai tokens de onomatopeias arrastadas dentro de frases mistas
     * (ex: "Ohhhhh, I see!" -> extrai "Ohhhhh") para proteção via placeholders.
     */
    fun findUniversalTokensInMixedText(text: String): Set<String> {
        val found = mutableSetOf<String>()
        val words = text.split(Regex("\\s+"))
        for (w in words) {
            val stripped = w.replace(Regex("^[.,!?:;\"'()\\-_~]+|[.,!?:;\"'()\\-_~]+$"), "")
            if (stripped.length >= 3 && isUniversalToken(stripped)) {
                // Só protege se for uma palavra com repetição ou token puramente onomatopeico
                val hasElongation = stripped.matches(Regex("(?i).*(.)\\1{2,}.*"))
                if (hasElongation || isUniversalToken(stripped)) {
                    found.add(stripped)
                }
            }
        }
        return found
    }
}

