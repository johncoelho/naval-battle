package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/**
 * Desafio da semana (#47, ver `supabase/weekly.sql`). O servidor escolhe a missão, conta o progresso e
 * paga; o aparelho só mostra e pede o resgate. [mission] é só o código (servidor novo pode mandar um
 * código que este app não conhece: o texto cai no genérico).
 */
data class WeeklyStatus(
    val week: String,
    val mission: String,
    val target: Int,
    val progress: Int,
    val claimed: Boolean,
    /** Prêmio fixo, mostrado antes do resgate. */
    val doubloons: Int,
    val miles: Int,
    val resetsInSeconds: Int
)

/** Resposta de `claim_weekly()`: status ok | already | not_done | no_wallet | not_eligible. */
data class WeeklyClaim(val status: String, val doubloons: Int, val miles: Int, val balance: Int)

/** Texto da missão pelo código do servidor; código desconhecido mostra o texto genérico. */
fun weeklyMissionKey(code: String): K = when (code) {
    "ONLINE_PLAY_5" -> K.WEEKLY_MISSION_ONLINE_PLAY_5
    "CHECKIN_5" -> K.WEEKLY_MISSION_CHECKIN_5
    "RANKED_WIN_3" -> K.WEEKLY_MISSION_RANKED_WIN_3
    else -> K.WEEKLY_MISSION_UNKNOWN
}

/** Barra de progresso, de 0 a 1. */
fun weeklyFraction(progress: Int, target: Int): Float =
    (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)

/** Só resgata com o alvo batido e ainda não resgatado nesta semana. */
fun weeklyCanClaim(progress: Int, target: Int, claimed: Boolean): Boolean =
    !claimed && target > 0 && progress >= target

/** O que fazer com a resposta do servidor (o aparelho nunca credita). */
enum class WeeklyOutcome { PAID, DONE_BEFORE, NOT_DONE, NO_WALLET, NOT_ELIGIBLE, OFFLINE }

fun weeklyOutcome(claim: WeeklyClaim?): WeeklyOutcome = when (claim?.status) {
    "ok" -> WeeklyOutcome.PAID
    "already" -> WeeklyOutcome.DONE_BEFORE
    "not_done" -> WeeklyOutcome.NOT_DONE
    "no_wallet" -> WeeklyOutcome.NO_WALLET
    "not_eligible" -> WeeklyOutcome.NOT_ELIGIBLE
    else -> WeeklyOutcome.OFFLINE
}
