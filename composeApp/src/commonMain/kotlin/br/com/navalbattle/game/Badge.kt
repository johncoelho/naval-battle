package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/**
 * Conquistas permanentes, gravadas na nuvem (`public.user_badges`) e concedidas só
 * pelo servidor — diferente de [Medal], que é recalculada a cada partida e nunca
 * fica salva. O [code] é o texto que trafega com o Supabase; nunca renomear um
 * código já concedido, ou quem já tem o badge perde a referência.
 */
enum class Badge(val code: String, val key: K, val descKey: K) {
    BETA_TESTER("beta_tester", K.BADGE_BETA_TESTER, K.BADGE_BETA_TESTER_DESC),
    FEEDBACK_CONTRIBUTOR("feedback_contributor", K.BADGE_FEEDBACK_CONTRIBUTOR, K.BADGE_FEEDBACK_CONTRIBUTOR_DESC);

    companion object {
        /** Nulo para um código que esta versão do app ainda não conhece — ignorado, sem erro. */
        fun of(code: String): Badge? = entries.firstOrNull { it.code == code }
    }
}

/** Um badge do próprio comandante + quando foi conquistado ("AAAA-MM-DD..."), como veio do servidor. */
data class EarnedBadge(val badge: Badge, val earnedAt: String) {
    /** "2026-09-22T12:32:30+00:00" → "22/09/2026". */
    val earnedDate: String
        get() {
            val day = earnedAt.take(10).split("-")
            return if (day.size == 3) "${day[2]}/${day[1]}/${day[0]}" else ""
        }
}
