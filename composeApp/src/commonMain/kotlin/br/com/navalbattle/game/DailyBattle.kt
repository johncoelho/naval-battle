package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import kotlin.random.Random

/**
 * Batalha do Dia (#138): o mesmo mar para todo mundo no mesmo dia de Brasília, contra a IA, no Clássico. Regras puras:
 * o dia, a semente, o sorteio, o "já jogou hoje" e o texto do placar. Sem servidor e **sem recompensa nenhuma** (#169):
 * a partida não conta na carreira (XP, patente, estatísticas), nas conquistas nem no desafio do dia ([countsForCareer]);
 * funciona offline.
 *
 * - **Dia:** relógio do aparelho com o fuso de Brasília **fixo em UTC-3** (o Brasil não tem horário de verão desde 2019),
 *   nunca o fuso do aparelho: um viajante joga o mesmo mar que quem está no Brasil. Relógio adiantado só antecipa o mar
 *   do dia seguinte para quem mexeu nele; sem prêmio, não vale trava no servidor.
 * - **Semente e sorteio:** PRNG próprio ([DailyRandom], SplitMix64) para o mar não depender da implementação do
 *   `Random(seed)` da biblioteca, que pode mudar de uma versão do Kotlin para outra (Android e iPhone precisam do mesmo).
 * - **Uma tentativa por dia:** marcada quando a batalha começa (abandonar ou fechar o app no meio gasta a vez) e guardada
 *   só no aparelho ([encode]/[decode]).
 * - **Placar:** o dia, o resultado, os tiros e a sequência deles em emoji, um por tiro e na ordem jogada (#169: erro,
 *   acerto, afundado). Nunca coordenada, grade posicional, navio não atingido, apelido, e-mail nem id: com o mesmo mar
 *   para todos, a posição dos tiros entregaria a frota a quem ainda não jogou; a ordem dos resultados não entrega.
 */
object DailyBattle {
    private const val DAY_MILLIS = 86_400_000L
    private const val BRT_OFFSET_MILLIS = -3 * 3_600_000L

    /** Dia corrido (desde 1970) da Batalha do Dia nº 1: 23/11/2026, data de Brasília. */
    private const val FIRST_DAY = 20_780L

    /** Dia corrido de Brasília (UTC-3 fixo) para o instante [nowMillis] (ms desde 1970, UTC). */
    fun dayOf(nowMillis: Long): Long = (nowMillis + BRT_OFFSET_MILLIS).floorDiv(DAY_MILLIS)

    /** Número público do dia ("Batalha do Dia #N"), a partir de 1. */
    fun number(day: Long): Int = (day - FIRST_DAY + 1).coerceAtLeast(1L).toInt()

    /** Semente do dia: o dia embaralhado, para dias vizinhos não começarem parecidos. */
    fun seed(day: Long): Long = DailyRandom.mix(day xor 0x4E415641_4C424154L)

    /** Sorteio da partida do dia: com ele a frota inimiga sai igual em todo aparelho. */
    fun random(day: Long): Random = DailyRandom(seed(day))

    /**
     * Resultado guardado no aparelho. [won] e [shots] nulos = começou e não terminou (abandono ou app fechado).
     * [sequence]: um caractere por tiro, na ordem (`M` erro, `H` acerto, `S` afundado); nulo no registro antigo
     * (0.48.1 a 0.48.3), que não guardava a ordem.
     */
    data class Record(val day: Long, val won: Boolean?, val shots: Int?, val sequence: String? = null)

    fun playedToday(record: Record?, today: Long): Boolean = record?.day == today

    /** Marca de "começou hoje", gravada quando a batalha começa. */
    fun started(day: Long): Record = Record(day, null, null)

    fun finished(day: Long, won: Boolean, shots: Int, sequence: String? = null): Record =
        Record(day, won, shots.coerceAtLeast(0), sequence)

    /**
     * A Batalha do Dia não paga nada (#169): sem XP, patente, estatísticas de carreira, conquistas nem desafio do dia.
     * A tela de resultado consulta esta regra antes de registrar a partida.
     */
    fun countsForCareer(daily: Boolean): Boolean = !daily

    /** Sequência dos tiros para o registro: `M`, `H` ou `S` por tiro, na ordem; tiro repetido não conta. */
    fun sequenceOf(shots: List<ShotResult>): String = buildString {
        shots.forEach { r ->
            when (r) {
                ShotResult.MISS -> append('M')
                ShotResult.HIT -> append('H')
                ShotResult.SUNK -> append('S')
                ShotResult.ALREADY_FIRED -> Unit
            }
        }
    }

    /** Emoji de erro (onda), acerto (fogo) e afundado (explosão), nessa ordem. */
    internal val SHOT_EMOJIS: List<String> = listOf("🌊", "🔥", "💥")

    /** Emoji de um caractere da sequência (`M`, `H`, `S`); outro caractere não aparece. */
    internal fun emojiOf(c: Char): String = when (c) {
        'M' -> SHOT_EMOJIS[0]
        'H' -> SHOT_EMOJIS[1]
        'S' -> SHOT_EMOJIS[2]
        else -> ""
    }

    /** Tiros por linha na sequência compartilhada: só para ler melhor, sem relação com o tabuleiro. */
    private const val EMOJIS_PER_LINE = 10

    private fun validSequence(seq: String, shots: Int): Boolean =
        seq.length == shots && seq.all { it == 'M' || it == 'H' || it == 'S' }

    /** Formato no aparelho: `dia`, ou `dia:1:tiros:SEQ` (vitória) / `dia:0:tiros:SEQ` (derrota); sem `:SEQ` no antigo. */
    fun encode(r: Record): String = when {
        r.won == null || r.shots == null -> r.day.toString()
        r.sequence == null -> "${r.day}:${if (r.won) 1 else 0}:${r.shots}"
        else -> "${r.day}:${if (r.won) 1 else 0}:${r.shots}:${r.sequence}"
    }

    fun decode(raw: String): Record? {
        val parts = raw.trim().split(':')
        val day = parts.getOrNull(0)?.toLongOrNull() ?: return null
        if (parts.size == 1) return Record(day, null, null)
        if (parts.size != 3 && parts.size != 4) return null
        val won = when (parts[1]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val shots = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        // sequência estragada não apaga o resultado (o dia continua jogado); só some a linha de emoji
        val sequence = parts.getOrNull(3)?.takeIf { validSequence(it, shots) }
        return Record(day, won, shots, sequence)
    }

    /**
     * Texto do placar para compartilhar: o número do dia, o resultado, os tiros e, depois de uma linha em branco, um emoji
     * por tiro na ordem jogada, quebrando a cada [EMOJIS_PER_LINE] (#169). Sem coordenada nem grade posicional (o mar é o
     * mesmo para todos e a posição entregaria os navios a quem ainda não jogou) e sem link (o iPhone ainda não tem loja).
     * Registro antigo, sem sequência: só o texto.
     */
    fun shareText(record: Record, lang: Lang): String? {
        val won = record.won ?: return null
        val shots = record.shots ?: return null
        val key = if (won) K.DAILY_BATTLE_SHARE_WIN else K.DAILY_BATTLE_SHARE_LOSS
        val text = when (lang) {
            Lang.PT -> key.pt
            Lang.EN -> key.en
            Lang.ES -> key.es
        }
        val head = text.replace("%1", number(record.day).toString()).replace("%2", shots.toString())
        val seq = record.sequence?.takeIf { it.isNotEmpty() } ?: return head
        val grid = seq.chunked(EMOJIS_PER_LINE).joinToString("\n") { line -> line.map(::emojiOf).joinToString("") }
        return "$head\n\n$grid"
    }
}

/**
 * SplitMix64: sorteio pequeno, rápido e determinístico, igual em toda plataforma (só soma, xor, deslocamento e
 * multiplicação de Long). As outras funções de [Random] (`nextInt(n)`, `nextBoolean`) são código comum da biblioteca
 * em cima de [nextBits].
 */
internal class DailyRandom(seed: Long) : Random() {
    private var state = seed

    override fun nextBits(bitCount: Int): Int {
        state += GOLDEN
        val z = mix(state)
        // os bitCount bits mais altos; bitCount 0 devolve 0 (mesma regra do takeUpperBits da biblioteca)
        return (z ushr (64 - bitCount)).toInt() and (-bitCount shr 31)
    }

    companion object {
        private const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
    }
}
