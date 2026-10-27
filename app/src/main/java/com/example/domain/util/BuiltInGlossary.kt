package com.example.domain.util

/**
 * Biblioteca local de termos difíceis de traduzir (vocabulário adulto / gírias / honoríficos).
 *
 * Funciona como "pré-glossário": o tradutor soma estes termos ao glossário do usuário
 * (Configurações). Se o mesmo termo existir nos dois, o do usuário vence.
 *
 * Regras de manutenção:
 *  - chaves em minúsculas (a busca ignora maiúsculas/minúsculas);
 *  - prefira FRASES a palavras soltas, para não quebrar usos comuns (ex.: "come", "hard");
 *  - alvo igual à chave = "não traduzir" (protege o termo da tradução automática).
 */
object BuiltInGlossary {

    private val enToPt: Map<String, String> = linkedMapOf(
        // Anatomia / gírias
        "pussies" to "bucetas",
        "pussy" to "buceta",
        "clitoris" to "clitóris",
        "clit" to "clitóris",
        "cocks" to "paus",
        "cock" to "pau",
        "tits" to "peitos",
        "titties" to "peitinhos",
        "boobs" to "peitos",
        "nipples" to "mamilos",
        "boner" to "pau duro",
        "horny" to "com tesão",
        "slut" to "vadia",
        "whore" to "puta",
        "nympho" to "ninfomaníaca",
        "nymphomaniac" to "ninfomaníaca",

        // Atos e práticas
        "blow job" to "boquete",
        "blowjob" to "boquete",
        "hand job" to "punheta",
        "handjob" to "punheta",
        "rimming" to "beijo grego",
        "rim job" to "beijo grego",
        "rimjob" to "beijo grego",
        "tit job" to "espanhola",
        "titjob" to "espanhola",
        "titfuck" to "espanhola",
        "deepthroat" to "garganta profunda",
        "deep throat" to "garganta profunda",
        "gangbang" to "suruba",
        "gang bang" to "suruba",
        "threesome" to "ménage",
        "orgy" to "orgia",
        "foreplay" to "preliminares",
        "masturbating" to "se masturbando",
        "anal sex" to "sexo anal",
        "oral sex" to "sexo oral",
        "make love" to "fazer amor",
        "squirting" to "esguicho",

        // Gozo
        "cumshot" to "gozada",
        "cumming" to "gozando",
        "i'm gonna cum" to "vou gozar",
        "i'm going to cum" to "vou gozar",
        "i'm about to cum" to "estou quase gozando",
        "i'm cumming" to "estou gozando",
        "cum inside" to "gozar dentro",
        "cum in me" to "goze dentro de mim",
        "creampie" to "creampie",

        // Frases fixas de cena
        "fuck me harder" to "me fode mais forte",
        "fuck me" to "me fode",
        "spread your legs" to "abra as pernas",
        "suck me" to "me chupa",
        "suck it" to "chupa",
        "lick me" to "me lambe",
        "lick it" to "lambe",
        "good girl" to "boa menina",
        "goodgirl" to "boa menina",

        // Objetos
        "vibrator" to "vibrador",
        "dildo" to "dildo",
        "lube" to "lubrificante",
        "condom" to "camisinha",

        // Termos que devem ficar como estão
        "hentai" to "hentai",
        "futanari" to "futanari",
        "bukkake" to "bukkake",
        "ahegao" to "ahegao",
        "milf" to "MILF",
        "senpai" to "senpai",
        "sensei" to "sensei",
        "onii-chan" to "onii-chan",
        "onee-chan" to "onee-chan",
        "onii-san" to "onii-san",
        "onee-san" to "onee-san"
    )

    /** Retorna o glossário embutido para o par de idiomas, ou vazio se não houver. */
    fun forPair(sourceLanguage: String, targetLanguage: String): Map<String, String> {
        val src = sourceLanguage.lowercase()
        val tgt = targetLanguage.lowercase()
        val isEnPt = (src == "en" || src == "english") &&
                (tgt == "pt" || tgt == "pt-br" || tgt == "portuguese")
        return if (isEnPt) withCurlyApostrophes(enToPt) else emptyMap()
    }

    // O OCR costuma devolver ’ no lugar de '. Gera as duas variantes.
    private fun withCurlyApostrophes(base: Map<String, String>): Map<String, String> {
        val result = LinkedHashMap<String, String>(base)
        for ((k, v) in base) {
            if (k.contains('\'')) result[k.replace('\'', '’')] = v
        }
        return result
    }
}
