package br.com.navalbattle.game

import br.com.navalbattle.i18n.PlatformGatedKey
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.platformGatedText
import br.com.navalbattle.i18n.t

/**
 * Conquistas permanentes, gravadas na nuvem (`public.user_badges`) e concedidas só
 * pelo servidor — diferente de [Medal], que é recalculada a cada partida e nunca
 * fica salva. O [code] é o texto que trafega com o Supabase; nunca renomear um
 * código já concedido, ou quem já tem o badge perde a referência.
 */
enum class Badge(val code: String, private val titulo: () -> String, private val descricao: () -> String) {
    // texto só do Android (#118): no iPhone fica vazio e a medalha nem aparece
    BETA_TESTER(
        "beta_tester",
        { platformGatedText(PlatformGatedKey.BADGE_TESTER) },
        { platformGatedText(PlatformGatedKey.BADGE_TESTER_DESC) }
    ),
    FEEDBACK_CONTRIBUTOR(
        "feedback_contributor",
        { t(K.BADGE_FEEDBACK_CONTRIBUTOR) },
        { t(K.BADGE_FEEDBACK_CONTRIBUTOR_DESC) }
    );

    /** Nome da medalha no idioma atual. */
    val label: String get() = titulo()

    /** Descrição da medalha no idioma atual. */
    val blurb: String get() = descricao()

    /** Falso quando a plataforma não tem texto para a medalha (a de testador, no iPhone). */
    val available: Boolean get() = titulo().isNotEmpty()

    companion object {
        /** Nulo para um código que esta versão do app ainda não conhece, ou sem texto nesta plataforma — ignorado, sem erro. */
        fun of(code: String): Badge? = entries.firstOrNull { it.code == code }?.takeIf { it.available }
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
