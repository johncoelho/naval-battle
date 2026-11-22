package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** #118: o texto do Android nao mudou com a ida para expect/actual (conteudo travado nos tres idiomas). */
class AndroidOnlyTextAndroidTest {
    private fun em(lang: Lang, key: AndroidOnlyKey): String {
        val original = I18n.lang
        try {
            I18n.lang = lang
            return androidOnlyText(key)
        } finally {
            I18n.lang = original
        }
    }

    @Test
    fun avisoDeLancamentoSegueIgual() {
        assertEquals("Naval Battle Classic chegou à Google Play!", em(Lang.PT, AndroidOnlyKey.LAUNCH_TITLE))
        assertEquals("Naval Battle Classic is now on Google Play!", em(Lang.EN, AndroidOnlyKey.LAUNCH_TITLE))
        assertEquals("¡Naval Battle Classic llegó a Google Play!", em(Lang.ES, AndroidOnlyKey.LAUNCH_TITLE))
        assertEquals(
            "Obrigado a você que testou o jogo no beta: cada partida, bug relatado e ideia ajudou a chegar aqui. " +
                "Todos os dobrões e brindes que você ganhou continuam na sua conta. " +
                "A partir de agora, pacotes, passe e itens da loja passam a ser pagos para todos.",
            em(Lang.PT, AndroidOnlyKey.LAUNCH_BODY_TESTER)
        )
        assertEquals(
            "Bem-vindo a bordo! Jogue contra a IA, com amigos no mesmo aparelho, na rede local ou online. Bons combates, comandante.",
            em(Lang.PT, AndroidOnlyKey.LAUNCH_BODY_NEW)
        )
    }

    @Test
    fun premioBetaECobrancaDaPlaySeguemIguais() {
        assertEquals("Prêmio Beta", em(Lang.PT, AndroidOnlyKey.PREMIO_TITLE))
        assertEquals("Beta prize", em(Lang.EN, AndroidOnlyKey.PREMIO_TITLE))
        assertEquals("Premio Beta", em(Lang.ES, AndroidOnlyKey.PREMIO_TITLE))
        assertEquals("Pegar o Prêmio", em(Lang.PT, AndroidOnlyKey.PREMIO_TAKE))
        assertEquals("Você já pegou o Prêmio de hoje. Volta em %s.", em(Lang.PT, AndroidOnlyKey.PREMIO_TAKEN))
        assertEquals("Prêmio indisponível agora.", em(Lang.PT, AndroidOnlyKey.PREMIO_UNAVAILABLE))
        assertEquals(
            "Pacotes pagos podem cobrar de verdade no seu Google Play. Só compre se quiser testar a cobrança.",
            em(Lang.PT, AndroidOnlyKey.DOUBLOON_BETA_CHARGE)
        )
    }

    @Test
    fun medalhaDeTestadorSegueIgual() {
        assertEquals("Beta Tester", em(Lang.PT, AndroidOnlyKey.BADGE_BETA_TESTER))
        assertEquals("Jogou durante os testes fechados", em(Lang.PT, AndroidOnlyKey.BADGE_BETA_TESTER_DESC))
        assertEquals("Played during the closed testing", em(Lang.EN, AndroidOnlyKey.BADGE_BETA_TESTER_DESC))
    }
}
