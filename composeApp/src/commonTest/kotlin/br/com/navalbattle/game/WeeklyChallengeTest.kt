package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Desafio da semana (#47): texto das missões, resgate só com alvo batido e resposta do servidor. */
class WeeklyChallengeTest {

    @Test
    fun cadaMissaoTemTextoNasTresLinguas() {
        listOf("ONLINE_PLAY_5", "CHECKIN_5", "RANKED_WIN_3").forEach { code ->
            val k = weeklyMissionKey(code)
            assertNotEquals(K.WEEKLY_MISSION_UNKNOWN, k, code)
            assertTrue(k.pt.isNotBlank() && k.en.isNotBlank() && k.es.isNotBlank(), code)
        }
    }

    @Test
    fun codigoDesconhecidoMostraTextoGenericoESemQuebrar() {
        val k = weeklyMissionKey("MISSAO_DO_FUTURO")
        assertEquals(K.WEEKLY_MISSION_UNKNOWN, k)
        assertTrue(k.pt.isNotBlank() && k.en.isNotBlank() && k.es.isNotBlank())
    }

    @Test
    fun soResgataComAlvoBatidoENaoResgatado() {
        assertFalse(weeklyCanClaim(4, 5, claimed = false))
        assertTrue(weeklyCanClaim(5, 5, claimed = false))
        assertTrue(weeklyCanClaim(7, 5, claimed = false))
        assertFalse(weeklyCanClaim(5, 5, claimed = true))
        assertFalse(weeklyCanClaim(0, 0, claimed = false))
    }

    @Test
    fun fracaoFicaEntreZeroEUm() {
        assertEquals(0f, weeklyFraction(0, 5))
        assertEquals(0.6f, weeklyFraction(3, 5))
        assertEquals(1f, weeklyFraction(9, 5))
        assertEquals(0f, weeklyFraction(-2, 5))
        assertEquals(1f, weeklyFraction(1, 0))
    }

    @Test
    fun statusDoServidorViraDesfechoCerto() {
        fun c(s: String) = WeeklyClaim(s, 250, 5, 900)
        assertEquals(WeeklyOutcome.PAID, weeklyOutcome(c("ok")))
        assertEquals(WeeklyOutcome.DONE_BEFORE, weeklyOutcome(c("already")))
        assertEquals(WeeklyOutcome.NOT_DONE, weeklyOutcome(c("not_done")))
        assertEquals(WeeklyOutcome.NO_WALLET, weeklyOutcome(c("no_wallet")))
        assertEquals(WeeklyOutcome.NOT_ELIGIBLE, weeklyOutcome(c("not_eligible")))
        assertEquals(WeeklyOutcome.OFFLINE, weeklyOutcome(null))
        assertEquals(WeeklyOutcome.OFFLINE, weeklyOutcome(c("status_novo")))
    }
}
