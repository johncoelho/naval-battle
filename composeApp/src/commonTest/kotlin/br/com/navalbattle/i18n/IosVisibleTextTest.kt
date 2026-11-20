package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * #118: a Apple recusou a 0.44.6 por texto de "\bbeta\b" / "teste fechado" (2.2) e por parecer vender
 * moeda sem IAP (2.1b). A 0.45.3 caiu em 2.3.10 (metadados precisos): o binario do iPhone nao pode
 * citar Android, Google Play, Play Store nem Play Games. Nenhum texto que o iPhone possa mostrar cita
 * beta, teste fechado nem essas lojas/plataformas ("Entrar com Google" segue valido: so a palavra Google).
 * Ficam de fora so os textos exclusivos do Android, todos filtrados por plataforma no codigo:
 * Premio Beta, aviso de cobranca da Play (`platformName != "ios"`), o aviso de lancamento (so Android),
 * a medalha de quem de fato testou (dado do servidor).
 * As Novidades (RN_*) entram na varredura.
 */
class IosVisibleTextTest {
    private val soAndroid = setOf(
        K.PREMIO_TITLE, K.PREMIO_BODY, K.PREMIO_TAKE, K.PREMIO_TAKEN, K.PREMIO_DONE_NOTICE, K.PREMIO_UNAVAILABLE,
        K.DOUBLOON_BETA_CHARGE, K.BADGE_BETA_TESTER, K.BADGE_BETA_TESTER_DESC
    )
    private val proibidos = listOf(
        "\\bbeta\\b", "teste fechado", "closed test", "prueba cerrada",
        "android", "google play", "play store", "play games", "play[.]google", "(?:da|na|pela|à|de la|en la) play[ ,.!)]"
    )

    @Test
    fun nenhumTextoVisivelNoIphoneCitaBetaTesteFechadoAndroidOuGooglePlay() {
        K.entries.filter { it !in soAndroid && !it.name.startsWith("LAUNCH_") }.forEach { key ->
            listOf(key.pt, key.en, key.es).forEach { texto ->
                proibidos.forEach { p ->
                    assertFalse(Regex(p, RegexOption.IGNORE_CASE).containsMatchIn(texto), "$key cita \"$p\": $texto")
                }
            }
        }
    }
}
