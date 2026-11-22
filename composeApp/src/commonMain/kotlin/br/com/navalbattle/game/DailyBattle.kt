package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import kotlin.random.Random

/**
 * Batalha do Dia (#138): o mesmo mar para todo mundo no mesmo dia de Brasília, contra a IA, no Clássico. Regras puras:
 * o dia, a semente, o sorteio, o "já jogou hoje" e o texto do placar. Sem servidor e sem prêmio novo (a partida conta
 * como qualquer partida contra a IA); funciona offline.
 *
 * - **Dia:** relógio do aparelho com o fuso de Brasília **fixo em UTC-3** (o Brasil não tem horário de verão desde 2019),
 *   nunca o fuso do aparelho: um viajante joga o mesmo mar que quem está no Brasil. Relógio adiantado só antecipa o mar
 *   do dia seguinte para quem mexeu nele; sem prêmio, não vale trava no servidor.
 * - **Semente e sorteio:** PRNG próprio ([DailyRandom], SplitMix64) para o mar não depender da implementação do
 *   `Random(seed)` da biblioteca, que pode mudar de uma versão do Kotlin para outra (Android e iPhone precisam do mesmo).
 * - **Uma tentativa por dia:** marcada quando a batalha começa (abandonar ou fechar o app no meio gasta a vez) e guardada
 *   só no aparelho ([encode]/[decode]).
 * - **Placar:** só o dia, o resultado e os tiros; nunca apelido, e-mail, id, posição de navio nem grade.
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

    /** Resultado guardado no aparelho. [won] e [shots] nulos = começou e não terminou (abandono ou app fechado). */
    data class Record(val day: Long, val won: Boolean?, val shots: Int?)

    fun playedToday(record: Record?, today: Long): Boolean = record?.day == today

    /** Marca de "começou hoje", gravada quando a batalha começa. */
    fun started(day: Long): Record = Record(day, null, null)

    fun finished(day: Long, won: Boolean, shots: Int): Record = Record(day, won, shots.coerceAtLeast(0))

    /** Formato no aparelho: `dia`, ou `dia:1:tiros` (vitória) / `dia:0:tiros` (derrota). */
    fun encode(r: Record): String =
        if (r.won == null || r.shots == null) r.day.toString() else "${r.day}:${if (r.won) 1 else 0}:${r.shots}"

    fun decode(raw: String): Record? {
        val parts = raw.trim().split(':')
        val day = parts.getOrNull(0)?.toLongOrNull() ?: return null
        if (parts.size == 1) return Record(day, null, null)
        if (parts.size != 3) return null
        val won = when (parts[1]) {
            "1" -> true
            "0" -> false
            else -> return null
        }
        val shots = parts[2].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return Record(day, won, shots)
    }

    /**
     * Texto do placar para compartilhar: só o número do dia, o resultado e os tiros. Sem grade (o mar é o mesmo para
     * todos e a grade entregaria a posição dos navios a quem ainda não jogou) e sem link (o iPhone ainda não tem loja).
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
        return text.replace("%1", number(record.day).toString()).replace("%2", shots.toString())
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
