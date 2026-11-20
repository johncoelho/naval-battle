package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * #118: a Apple recusou a 0.44.6 por texto de "\bbeta\b" / "teste fechado" (2.2) e por parecer vender
 * moeda sem IAP (2.1b). Nenhum texto que o iPhone possa mostrar cita beta, teste fechado ou Google Play.
 * Ficam de fora so os textos exclusivos do Android (Premio Beta, aviso de cobranca da Play, vitrine),
 * o aviso de lancamento (so Android), a medalha de quem de fato testou (dado do servidor).
 * As Novidades (RN_*) entram na varredura.
 */
class IosVisibleTextTest {
    private val soAndroid = setOf(
        K.PREMIO_TITLE, K.PREMIO_BODY, K.PREMIO_TAKE, K.PREMIO_TAKEN, K.PREMIO_DONE_NOTICE, K.PREMIO_UNAVAILABLE,
        K.DOUBLOON_BETA_CHARGE, K.DOUBLOON_PENDING, K.BADGE_BETA_TESTER, K.BADGE_BETA_TESTER_DESC
    )
    private val proibidos = listOf("\\bbeta\\b","teste fechado", "closed test", "prueba cerrada", "google play")

    @Test
    fun nenhumTextoVisivelNoIphoneCitaBetaTesteFechadoOuGooglePlay() {
        K.entries.filter { it !in soAndroid && !it.name.startsWith("LAUNCH_") }.forEach { key ->
            listOf(key.pt, key.en, key.es).forEach { texto ->
                proibidos.forEach { p ->
                    assertFalse(Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(texto), "$key cita \"$p\": $texto")
                }
            }
        }
    }
}
