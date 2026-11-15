package br.com.navalbattle.game

import kotlin.random.Random

/**
 * Adversario virtual da partida rapida (#105). Regras puras, sem tela nem rede: nivel, ritmo de jogo,
 * provocacoes e a decisao de quando o bot entra. A partida em si e a IA de `Ai.kt` no aparelho; o servidor
 * emite a partida, calcula o nivel e julga a ranqueada (`supabase/bots.sql`). Nada aqui aparece como "bot" para o
 * jogador: sem faixa, sem rotulo (decisao do John, opcao C, 14/11).
 */
enum class BotLevel(val code: String, internal val errorChance: Double, internal val thinkMinMs: Long, internal val thinkMaxMs: Long) {
    /** Jogador novo: caca com muito erro, pensa devagar. */
    EASY("easy", 0.50, 1_800L, 6_000L),
    MEDIUM("medium", 0.25, 1_500L, 5_000L),
    HARD("hard", 0.08, 1_200L, 4_000L);

    companion object {
        /** O nivel vem do servidor; valor desconhecido cai no facil (nunca num adversario mais forte que o previsto). */
        fun of(code: String?): BotLevel = entries.firstOrNull { it.code == code } ?: EASY
    }
}

/** Quem e o adversario virtual de uma partida: nome da persona e nivel. Vive na [Match], nunca e exibido como bot. */
data class BotProfile(val name: String, val level: BotLevel)

object BotRules {
    /** Nenhuma jogada do bot sai antes disto (criterio 7: jogada instantanea denuncia). */
    const val MIN_THINK_MS = 1_200L

    /** Pausa extra depois que o proprio bot acerta: gente comemora, confere, pensa de novo. */
    private const val AFTER_HIT_EXTRA_MS = 700L

    /** Tempo ate a proxima jogada do bot, com variacao; depois de acerto, um pouco mais lento. */
    fun thinkMillis(level: BotLevel, afterHit: Boolean, random: Random = Random.Default): Long {
        val span = (level.thinkMaxMs - level.thinkMinMs).coerceAtLeast(1L)
        val base = level.thinkMinMs + (random.nextDouble() * span).toLong()
        return (base + if (afterHit) AFTER_HIT_EXTRA_MS else 0L).coerceAtLeast(MIN_THINK_MS)
    }

    /** Chance de uma provocacao depois de uma jogada do bot. Baixa de proposito. */
    private const val TAUNT_CHANCE = 0.06

    /** Chance do "GG" no fim da partida. */
    private const val GG_CHANCE = 0.55

    /** Provocacao ocasional (so da lista fechada que ja existe), ou nulo na maioria das jogadas. */
    fun tauntAfterShot(random: Random = Random.Default): String? {
        if (random.nextDouble() >= TAUNT_CHANCE) return null
        val pool = TAUNT_EMOJIS + TAUNT_PHRASES.map { it.first }.filter { it != "GG" && it != "MERCY" }
        return pool[random.nextInt(pool.size)]
    }

    /** "GG" ao terminar, na maioria das vezes (mais raro quando o bot perdeu feio nao importa: so GG, nunca texto livre). */
    fun tauntOnFinish(random: Random = Random.Default): String? =
        if (random.nextDouble() < GG_CHANCE) "GG" else null

    /** O que o app faz depois de pedir ao servidor para cancelar a sala. */
    enum class AfterCancel { START_BOT, HUMAN_JOINED, KEEP_SEARCHING }

    /**
     * `cancel_quick_room`: so `cancelled` autoriza o bot (ajuste A1). `joined` e partida humana. Qualquer outra coisa
     * (invalid, erro de rede) deixa a busca como esta, sem bot: o bot nunca coexiste com sala viva.
     */
    fun afterCancel(result: String?): AfterCancel = when (result) {
        "cancelled" -> AfterCancel.START_BOT
        "joined" -> AfterCancel.HUMAN_JOINED
        else -> AfterCancel.KEEP_SEARCHING
    }

    /** `bot_match_start` so vale com `ok`; off/limit/invalid/busy/erro = cai no treino de hoje (A6). */
    fun startAccepted(outcome: String?): Boolean = outcome == "ok"

    /**
     * Quando disparar a espera do bot: so na partida rapida (nunca sala de amigo, codigo ou convite), com o servidor
     * dizendo que o bot esta ligado e dentro da cota deste modo, e sem treino manual (#41) em curso.
     */
    fun shouldArm(isQuick: Boolean, botOn: Boolean, modeOk: Boolean, practicing: Boolean): Boolean =
        isQuick && botOn && modeOk && !practicing

    /** Espera (ms) deste modo, com piso de 10 s: um valor de configuracao absurdo nao pode virar bot instantaneo. */
    fun waitMillis(waitSeconds: Int): Long = waitSeconds.coerceIn(10, 300) * 1000L

    /** O resultado ranqueado conta pontos so quando o servidor aceitou (relato nao feito = sem pontos, criterio 17). */
    fun rankedCounts(serverOutcome: String?): Boolean = serverOutcome == "ok"
}
