package br.com.navalbattle.game

/**
 * Filtro de nome de guerra (#58). É a MESMA regra de `supabase/safety.sql` (`name_fold`, `normalize_name`,
 * `name_tokens`, `name_is_offensive`) e a MESMA lista de termos: o servidor decide e grava; o app responde na
 * hora, filtra o nome do convidado e mascara o nome vindo de app antigo (HELLO do protocolo). Os testes
 * `NameFilterTest` e, na JVM, `NameTermsParityTest` (compara com o `safety.sql`) seguram as duas pontas.
 *
 * Passos: minúsculas, acento vira a letra base, leet vira letra (0 o, 1 i, 3 e, 4 a, 5 s, 7 t, 8 b, @ a, $ s, ! i, | i),
 * fica só a-z e a letra repetida em sequência vira uma. Termo normalizado com 5 letras ou mais vale como pedaço do
 * nome ("sub"); menor que isso só vale como palavra inteira ("word"), para não cair no problema de Scunthorpe.
 * Antes de procurar, as exceções (ALLOW) são retiradas do nome.
 */
object NameFilter {
    enum class Check { OK, INVALID, OFFENSIVE }

    private const val NAME_MIN_LENGTH = 3
    private const val NAME_MAX_LENGTH = 18

    /** Mesma lista do `safety.sql` entre TERMS-BEGIN e TERMS-END: (termo, idioma). */
    internal val TERMS: List<Pair<String, String>> = listOf(
        "caralho" to "pt", "buceta" to "pt", "bucetao" to "pt", "putaria" to "pt", "putinha" to "pt", "puta" to "all",
        "puto" to "pt", "merda" to "pt", "fodase" to "pt", "fodido" to "pt", "foder" to "pt", "foda" to "pt",
        "vadia" to "pt", "viado" to "pt", "viadinho" to "pt", "arrombado" to "pt", "cuzao" to "pt", "escroto" to "pt",
        "otario" to "pt", "vagabundo" to "pt", "vagabunda" to "pt", "punheta" to "pt", "xoxota" to "pt",
        "xereca" to "pt", "cacete" to "pt", "porra" to "pt", "bosta" to "pt", "carai" to "pt", "filhodaputa" to "pt",
        "fdp" to "pt", "pqp" to "pt", "estupro" to "pt", "estuprador" to "pt", "pedofilo" to "pt", "pedofilia" to "pt",
        "nazista" to "pt", "boiola" to "pt", "traveco" to "pt", "sapatao" to "pt", "retardado" to "pt",
        "prostituta" to "pt", "mongoloide" to "pt", "crioulo" to "pt",
        "fuck" to "en", "fucker" to "en", "fucking" to "en", "fucked" to "en", "motherfucker" to "en", "fuckyou" to "en",
        "shit" to "en", "shitty" to "en", "bullshit" to "en", "shithead" to "en", "bitch" to "en", "asshole" to "en",
        "bastard" to "en", "dickhead" to "en", "dick" to "en", "cunt" to "en", "whore" to "en", "slut" to "en",
        "slutty" to "en", "nigger" to "en", "nigga" to "en", "faggot" to "en", "fag" to "en", "retard" to "en",
        "rapist" to "en", "rape" to "en", "pedophile" to "en", "paedophile" to "en", "molester" to "en",
        "cocksucker" to "en", "blowjob" to "en", "dildo" to "en", "penis" to "en", "vagina" to "en", "pussy" to "en",
        "cock" to "en", "twat" to "en", "jizz" to "en", "tits" to "en", "porn" to "en", "porno" to "all",
        "mierda" to "es", "cabron" to "es", "pendejo" to "es", "joder" to "es", "gilipollas" to "es", "marica" to "es",
        "maricon" to "es", "culero" to "es", "verga" to "es", "chingar" to "es", "chingada" to "es", "chinga" to "es",
        "hijueputa" to "es", "hijodeputa" to "es", "malparido" to "es", "pelotudo" to "es",
        "pajero" to "es", "sudaca" to "es", "culiao" to "es",
        "hitler" to "all", "nazi" to "all"
    )

    /** Mesma lista do `safety.sql` entre ALLOW-BEGIN e ALLOW-END: palavras legítimas que contêm um termo. */
    internal val ALLOW: List<String> = listOf(
        "enviado", "desviado", "invadia", "evadia", "nigeria", "nigerian",
        "vergara", "converga", "diverga", "fagote", "retardant", "therapist", "therapists",
        "evergarden", "notario", "notarios"
    )

    private val subTerms: List<String> by lazy {
        TERMS.map { normalize(it.first) }.filter { it.length >= 5 }.distinct()
    }
    private val wordTerms: Set<String> by lazy {
        TERMS.map { normalize(it.first) }.filter { it.length < 5 }.toSet()
    }
    private val allowSorted: List<String> by lazy {
        ALLOW.map { normalize(it) }.distinct().sortedWith(compareByDescending<String> { it.length }.thenBy { it })
    }

    private fun foldChar(ch: Char): Char = when (ch) {
        'á', 'à', 'â', 'ã', 'ä', 'å' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ç' -> 'c'
        'ñ' -> 'n'
        'ý', 'ÿ' -> 'y'
        '0' -> 'o'
        '1', '!', '|' -> 'i'
        '3' -> 'e'
        '4', '@' -> 'a'
        '5', '$' -> 's'
        '7' -> 't'
        '8' -> 'b'
        else -> ch
    }

    private fun fold(text: String): String = text.lowercase().map { foldChar(it) }.joinToString("")

    private fun squeeze(text: String): String {
        val out = StringBuilder()
        for (ch in text) if (out.isEmpty() || out.last() != ch) out.append(ch)
        return out.toString()
    }

    private fun isLetter(ch: Char) = ch in 'a'..'z'

    /** Forma compacta: só a-z e sem letra repetida em sequência. */
    fun normalize(text: String): String = squeeze(fold(text).filter { isLetter(it) })

    /** Palavras separadas (cada uma sem letra repetida). */
    fun tokens(text: String): List<String> {
        val words = mutableListOf<String>()
        val cur = StringBuilder()
        for (ch in fold(text)) {
            if (isLetter(ch)) {
                cur.append(ch)
            } else if (cur.isNotEmpty()) {
                words += squeeze(cur.toString())
                cur.clear()
            }
        }
        if (cur.isNotEmpty()) words += squeeze(cur.toString())
        return words
    }

    fun isOffensive(text: String): Boolean {
        var compact = normalize(text)
        if (compact.isEmpty()) return false
        for (a in allowSorted) compact = compact.replace(a, "")
        val allowSet = allowSorted.toSet()
        val words = tokens(text).filter { it !in allowSet }
        return subTerms.any { compact.contains(it) } || words.any { it in wordTerms }
    }

    /** Tamanho de 3 a 18 caracteres depois do trim, e sem termo ofensivo. */
    fun validate(name: String): Check {
        val v = name.trim()
        if (v.length < NAME_MIN_LENGTH || v.length > NAME_MAX_LENGTH) return Check.INVALID
        return if (isOffensive(v)) Check.OFFENSIVE else Check.OK
    }

    /** Nome vazio ou ofensivo vira [fallback]; o resto passa como veio. */
    fun safeDisplay(name: String, fallback: String): String =
        if (name.isBlank() || isOffensive(name)) fallback else name
}
