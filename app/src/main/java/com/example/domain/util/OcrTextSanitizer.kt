package com.example.domain.util

import java.util.Locale

/**
 * Utilitário de sanitização e correção de ruídos visuais típicos de OCR em legendas de vídeo.
 *
 * Corrige problemas como:
 * - Apóstrofos espúrios ou duplicados em contrações (ex: "you'd'" -> "you'd", "'don't" -> "don't")
 * - Normalização de apóstrofos tipográficos (ex: curvas `’`, crases `\` e backticks para `'` padrão)
 * - Contrações em inglês sem apóstrofo (ex: "youd" -> "you'd", "dont" -> "don't", "cant" -> "can't")
 * - Glifos rompidos ou confusões de OCR em diálogos (ex: "mayra you'd" / "mayre you'd" -> "maybe you'd")
 * - 'l' minúsculo confundido com 'I' maiúsculo em contrações (ex: "l'm" -> "I'm", "l'd" -> "I'd", "l'll" -> "I'll")
 */
object OcrTextSanitizer {

    // Mapeamento de contrações comuns em inglês onde o OCR frequentemente perde o apóstrofo
    private val ENGLISH_CONTRACTION_MAP = mapOf(
        "youd" to "you'd",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "didnt" to "didn't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "hasnt" to "hasn't",
        "havent" to "haven't",
        "hadnt" to "hadn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "wasnt" to "wasn't",
        "werent" to "weren't",
        "theyre" to "they're",
        "youre" to "you're",
        "weve" to "we've",
        "theyve" to "they've",
        "youve" to "you've",
        "whats" to "what's",
        "thats" to "that's",
        "hows" to "how's",
        "theres" to "there's",
        "heres" to "here's",
        "wherere" to "where're",
        "whos" to "who's"
    )

    // Confusões de OCR em 'maybe' quando 'b' e 'e' se rompem em 'r'/'a' ou 'r'/'e'
    // ex: "mayra you'd", "mayre you'd", "mayba you"
    private val MAYBE_OCR_CONFUSION_REGEX = Regex(
        "(?i)\\b(mayra|mayre|mayba|mayoe)\\s+(?=(?:you(?:'d|'re|'ve)?|we(?:'ll|'re|'ve)?|i(?:'m|'d|'ll)?|he(?:'s|'d)?|she(?:'s|'d)?|they(?:'re|'ve)?|it(?:'s)?|there(?:'s)?|a|the|this|that|so|if|not)\\b)"
    )

    /**
     * Sanitiza o texto extraído por OCR, normalizando pontuações e corrigindo distorções
     * conhecidas antes de enviar para o agrupador temporal ou para a tradução.
     */
    fun sanitize(text: String, sourceLanguage: String = "en"): String {
        if (text.isBlank()) return text

        var processed = text
            // 1. Normaliza apóstrofos e aspas curvas tipográficas para padrão ASCII
            .replace("[’‘`´]".toRegex(), "'")
            .replace("[“”«»]".toRegex(), "\"")

        // 2. Remove apóstrofos/aspas espúrias coladas no final ou início de contrações
        // ex: "you'd'" -> "you'd", "don't''" -> "don't", "'you'd" -> "you'd"
        processed = processed.replace("(?i)\\b([a-z]+'[a-z]+)'+".toRegex(), "$1")
        processed = processed.replace("(?i)'+([a-z]+'[a-z]+)\\b".toRegex(), "$1")

        // 3. Corrige confusão clássica de 'l' minúsculo por 'I' maiúsculo em contrações inglesas
        processed = processed
            .replace("(?i)\\bl'(m|d|ll|ve)\\b".toRegex(), "I'$1")
            .replace("\\bIm\\b".toRegex(), "I'm")
            .replace("\\bIll\\b".toRegex(), "I'll")

        // 4. Correções específicas para inglês (ou quando idioma de origem não for CJK)
        val isEnglish = sourceLanguage.equals("en", ignoreCase = true) ||
                sourceLanguage.equals("english", ignoreCase = true) ||
                sourceLanguage.isBlank()

        if (isEnglish) {
            // Correção de 'maybe' corrompido ("mayra you'd", "mayre you'd", etc.)
            processed = MAYBE_OCR_CONFUSION_REGEX.replace(processed) { matchResult ->
                val originalWord = matchResult.groupValues[1]
                val replacement = if (originalWord.first().isUpperCase()) "Maybe " else "maybe "
                replacement
            }

            // Restauração de contrações sem apóstrofo ("youd" -> "you'd", "dont" -> "don't")
            for ((raw, fixed) in ENGLISH_CONTRACTION_MAP) {
                val pattern = "(?i)\\b$raw\\b".toRegex()
                if (pattern.containsMatchIn(processed)) {
                    processed = pattern.replace(processed) { match ->
                        val matchedText = match.value
                        if (matchedText.first().isUpperCase()) {
                            fixed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                        } else {
                            fixed
                        }
                    }
                }
            }
        }

        return processed.trim()
    }
}

