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
    internal const val MIN_THINK_MS = 1_200L

    /** Pausa extra depois que o proprio bot acerta: gente comemora, confere, pensa de novo. */
    private const val AFTER_HIT_EXTRA_MS = 700L

    /** Chance de uma pausa longa (gente se distrai, confere o tabuleiro, olha o celular) numa jogada do bot (#112). */
    internal const val LONG_PAUSE_CHANCE = 0.07
    private const val LONG_PAUSE_MIN_MS = 7_000L
    private const val LONG_PAUSE_MAX_MS = 15_000L

    /** Tempo ate a proxima jogada do bot, com variacao e pausas longas ocasionais; depois de acerto, um pouco mais lento. */
    fun thinkMillis(level: BotLevel, afterHit: Boolean, random: Random = Random.Default): Long {
        if (random.nextDouble() < LONG_PAUSE_CHANCE) return random.nextLong(LONG_PAUSE_MIN_MS, LONG_PAUSE_MAX_MS + 1L)
        val span = (level.thinkMaxMs - level.thinkMinMs).coerceAtLeast(1L)
        val base = level.thinkMinMs + (random.nextDouble() * span).toLong()
        return (base + if (afterHit) AFTER_HIT_EXTRA_MS else 0L).coerceAtLeast(MIN_THINK_MS)
    }

    /**
     * Ajuste suave de dificuldade (#112, pedido do John: tendencia a dar a vitoria ao jogador). Quando o jogador esta
     * ATRAS (menos navios a flote que o adversario), o adversario erra um pouco mais a mira: [CATCH_UP_STEP] de chance
     * de tiro aleatorio por navio de diferenca, no maximo [CATCH_UP_MAX]. Empatado: um erro leve ([EVEN_ERROR]). Adiante: mira normal. Nunca
     * joga fora de proposito (o tiro ruim e um tiro aleatorio valido, como o de quem erra).
     */
    internal const val CATCH_UP_STEP = 0.25
    internal const val CATCH_UP_MAX = 0.60
    internal const val EVEN_ERROR = 0.18
    fun catchUpError(botShipsLeft: Int, humanShipsLeft: Int): Double {
        val behind = botShipsLeft - humanShipsLeft
        return when {
            behind > 0 -> minOf(EVEN_ERROR + behind * CATCH_UP_STEP, CATCH_UP_MAX)
            behind == 0 -> EVEN_ERROR
            else -> 0.0
        }
    }

    /** Revanche pedida ao adversario virtual: espera de 2 a 6 s e, em [REMATCH_ACCEPT_CHANCE], ele topa (senao "saiu da sala"). */
    internal const val REMATCH_ACCEPT_CHANCE = 0.30
    fun rematchWaitMillis(random: Random = Random.Default): Long = random.nextLong(2_000L, 6_001L)
    fun rematchAccepted(random: Random = Random.Default): Boolean = random.nextDouble() < REMATCH_ACCEPT_CHANCE

    /**
     * Depois de `bot_match_start`: so abre a partida se o jogador ainda esta na busca; se cancelou durante a chamada,
     * a linha aberta no servidor e fechada sem partida (P1-2 da auditoria).
     */
    fun openAfterStart(stillSearching: Boolean): Boolean = stillSearching

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

    /** Espera minima do sorteio: abaixo disso a partida "achada" parece instantanea demais (#112). */
    internal const val WAIT_MIN_SECONDS = 3

    /** Teto do sorteio (e da espera): a busca real dura so isto antes do adversario entrar (#112). */
    internal const val WAIT_MAX_SECONDS = 6

    /** Piso absoluto: um valor de configuracao absurdo (0, negativo) nao pode virar adversario instantaneo. */
    private const val WAIT_FLOOR_SECONDS = 3

    /**
     * Espera (ms) antes do adversario, sorteada a cada busca entre 3 e 6 s (#112: a espera de 30/45 s fazia o
     * jogador desistir antes e o valor fixo denunciava o adversario virtual). O `bot_wait_seconds*` do servidor virou
     * so TETO: valor menor que 6 encurta o sorteio (piso de 3 s); 6 ou mais nao muda nada.
     */
    fun waitMillis(waitSeconds: Int, random: Random = Random.Default): Long {
        val cap = waitSeconds.coerceIn(WAIT_FLOOR_SECONDS, WAIT_MAX_SECONDS) * 1000L
        val lo = minOf(WAIT_MIN_SECONDS * 1000L, cap)
        return lo + random.nextLong(cap - lo + 1L)
    }

    /**
     * Resposta do adversario a uma fala do jogador: so emote ou frase pronta da lista fechada (nunca texto livre),
     * em ~45% das vezes; "GG" responde "GG". [random] injetavel para teste.
     */
    fun replyTo(code: String, random: Random = Random.Default): String? {
        if (random.nextDouble() >= 0.45) return null
        if (code == "GG") return "GG"
        val pool = TAUNT_EMOJIS + TAUNT_PHRASES.map { it.first }.filter { it != "GG" && it != "MERCY" }
        return pool[random.nextInt(pool.size)]
    }

    /** O resultado ranqueado conta pontos so quando o servidor aceitou (relato nao feito = sem pontos, criterio 17). */
    fun rankedCounts(serverOutcome: String?): Boolean = serverOutcome == "ok"
}
