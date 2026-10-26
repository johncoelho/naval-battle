package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Presente de boas-vindas (#43): a resposta do servidor vira a ação do app, e o aparelho nunca credita. */
class WelcomeGiftTest {

    private fun gift(status: String) = WelcomeGift(status, 150, 5, 650)

    @Test
    fun statusDoServidorViraDesfechoCerto() {
        assertEquals(WelcomeOutcome.PAID, welcomeOutcome(gift("ok")))
        assertEquals(WelcomeOutcome.DONE_BEFORE, welcomeOutcome(gift("already")))
        assertEquals(WelcomeOutcome.NOT_ELIGIBLE, welcomeOutcome(gift("not_eligible")))
        assertEquals(WelcomeOutcome.NO_WALLET, welcomeOutcome(gift("no_wallet")))
        assertEquals(WelcomeOutcome.OFFLINE, welcomeOutcome(null))
        assertEquals(WelcomeOutcome.OFFLINE, welcomeOutcome(gift("qualquer_outra")))
    }

    @Test
    fun soSemCarteiraOuSemRedeTentaDeNovo() {
        assertTrue(welcomeShouldRetry(WelcomeOutcome.OFFLINE))
        assertTrue(welcomeShouldRetry(WelcomeOutcome.NO_WALLET))
        assertFalse(welcomeShouldRetry(WelcomeOutcome.PAID))
        assertFalse(welcomeShouldRetry(WelcomeOutcome.DONE_BEFORE))
        assertFalse(welcomeShouldRetry(WelcomeOutcome.NOT_ELIGIBLE))
    }

    @Test
    fun valoresMostradosBatemComOSemeDoServidor() {
        assertEquals(150, WELCOME_SHOWN_DOUBLOONS)
        assertEquals(5, WELCOME_SHOWN_MILES)
    }
}
