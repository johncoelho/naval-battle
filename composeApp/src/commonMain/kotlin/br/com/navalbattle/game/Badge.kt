package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/**
 * Conquistas permanentes, gravadas na nuvem (`public.user_badges`) e concedidas só
 * pelo servidor — diferente de [Medal], que é recalculada a cada partida e nunca
 * fica salva. O [code] é o texto que trafega com o Supabase; nunca renomear um
 * código já concedido, ou quem já tem o badge perde a referência.
 */
enum class Badge(val code: String, val key: K) {
    BETA_TESTER("beta_tester", K.BADGE_BETA_TESTER),
    FEEDBACK_CONTRIBUTOR("feedback_contributor", K.BADGE_FEEDBACK_CONTRIBUTOR);

    companion object {
        fun of(code: String): Badge? = entries.firstOrNull { it.code == code }
    }
}
