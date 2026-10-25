package br.com.navalbattle.game

/**
 * Carteira de dobrões no servidor (#34, `supabase/dobroes_saldo.sql`). O saldo de verdade vive no
 * servidor e só muda por RPC; o aparelho só exibe o que o servidor devolveu. Aqui ficam as regras
 * puras (testáveis) que o app usa para abrir a carteira e para reenviar recompensas online.
 */
object Wallet {
    /** Teto do saldo do aparelho na abertura (espelha `app_config.dobroes_open_max`). */
    const val OPEN_MAX = 200_000

    /** O que mandar no `dobroes_open`: o saldo do aparelho (limitado) e os tokens que ele já somou. */
    data class OpenPlan(val local: Int, val delivered: List<String>)

    fun openPlan(localCredits: Int, deliveredTokens: Set<String>): OpenPlan =
        OpenPlan(localCredits.coerceIn(0, OPEN_MAX), deliveredTokens.toSortedSet().toList())

    /** Saldo da carteira ao abrir: o MAIOR entre servidor e aparelho (nunca perde), até [OPEN_MAX]. */
    fun openingBalance(server: Int, local: Int): Int = maxOf(server, local.coerceIn(0, OPEN_MAX))

    /**
     * Dobrões de uma partida contra a IA: zero em qualquer resultado (decisão do John, 25/10). A IA
     * rende XP e estatísticas, mas não dobrões; só o servidor paga, e só partida online.
     */
    @Suppress("UNUSED_PARAMETER")
    fun aiCredits(victory: Boolean): Int = 0
}

/** Partida online cuja recompensa o servidor ainda não confirmou. */
data class PendingAward(
    val matchId: String,
    val shots: Int,
    val hits: Int,
    val sunk: Int,
    val turns: Int,
    val victory: Boolean
)

/**
 * Fila das partidas online sem recompensa confirmada, guardada nas preferências e reenviada na
 * próxima abertura. É idempotente: o servidor paga uma vez por sala, então reenviar nunca duplica.
 */
object OnlineAwardQueue {
    const val MAX = 20

    fun encode(list: List<PendingAward>): String =
        list.joinToString(";") { "${it.matchId},${it.shots},${it.hits},${it.sunk},${it.turns},${if (it.victory) 1 else 0}" }

    fun decode(raw: String): List<PendingAward> =
        raw.split(";").mapNotNull { entry ->
            val p = entry.split(",")
            if (p.size != 6 || p[0].isBlank()) return@mapNotNull null
            PendingAward(
                matchId = p[0],
                shots = p[1].toIntOrNull() ?: return@mapNotNull null,
                hits = p[2].toIntOrNull() ?: return@mapNotNull null,
                sunk = p[3].toIntOrNull() ?: return@mapNotNull null,
                turns = p[4].toIntOrNull() ?: return@mapNotNull null,
                victory = p[5] == "1"
            )
        }

    /** Acrescenta (sem repetir a sala) e mantém só as [MAX] mais novas. */
    fun add(list: List<PendingAward>, award: PendingAward): List<PendingAward> =
        (list.filter { it.matchId != award.matchId } + award).takeLast(MAX)

    fun remove(list: List<PendingAward>, matchId: String): List<PendingAward> =
        list.filter { it.matchId != matchId }

    /**
     * O servidor deu resposta definitiva (some da fila) ou pode mudar depois (fica na fila):
     * `no_result` (ranqueada ainda sem vencedor registrado) e falha de rede voltam a tentar.
     */
    fun settled(status: String): Boolean = status != "no_result"
}
