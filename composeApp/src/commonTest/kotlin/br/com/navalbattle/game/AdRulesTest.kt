package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Anúncio premiado (#29): quando o botão de dobrar a vitória aparece, quanto ele pede, qual
 * unidade de anúncio vale e qual aviso aparece. Os limites do dia (6 anúncios, 375 dobrões, por
 * lugar) moram no servidor e estão no ensaio `supabase/tests/ads_ensaio.sql`.
 */
class AdRulesTest {

    private fun win(
        opponent: Opponent = Opponent.AI,
        victory: Boolean = true,
        credits: Int = 130,
        noCombat: Boolean = false,
        absence: Boolean = false,
        forfeit: Boolean = false
    ) = AdRules.winEligible(opponent, victory, credits, noCombat, absence, forfeit)

    @Test
    fun vitoriaContraIaComDobroesVale() = assertTrue(win())

    @Test
    fun vitoriaOnlineDentroDoLimiteVale() = assertTrue(win(opponent = Opponent.ONLINE))

    @Test
    fun derrotaNaoVale() = assertFalse(win(victory = false))

    @Test
    fun doisJogadoresERedeLocalNaoValem() {
        assertFalse(win(opponent = Opponent.LOCAL))
        assertFalse(win(opponent = Opponent.LAN))
    }

    @Test
    fun vitoriaSemDobroesNaoVale() {
        // online acima do limite de 3 por adversário não rende dobrões (award nulo = 0)
        assertFalse(win(opponent = Opponent.ONLINE, credits = 0))
    }

    @Test
    fun semCombateAusenciaOuAbandonoNaoValem() {
        assertFalse(win(opponent = Opponent.ONLINE, noCombat = true))
        assertFalse(win(opponent = Opponent.ONLINE, absence = true))
        assertFalse(win(opponent = Opponent.ONLINE, forfeit = true))
    }

    @Test
    fun valorCortaNoTeto() {
        assertEquals(130, AdRules.winAmount(130, 150))
        assertEquals(150, AdRules.winAmount(205, 150))
        // o teto do servidor já desconta o que sobrou dos 375 do dia
        assertEquals(20, AdRules.winAmount(130, 20))
        assertEquals(0, AdRules.winAmount(130, 0))
    }

    @Test
    fun referenciaDaVitoria() {
        assertEquals("online:sala-1", AdRules.winRef("sala-1", "abc"))
        assertEquals("ai:abc", AdRules.winRef(null, "abc"))
    }

    @Test
    fun unidadeDeTesteEmDebugEContaDeTeste() {
        assertEquals(AdRules.TEST_REWARDED_UNIT, AdRules.unitFor(AdPlace.MILE, buildTest = true, serverTest = false))
        assertEquals(AdRules.TEST_REWARDED_UNIT, AdRules.unitFor(AdPlace.WIN, buildTest = false, serverTest = true))
        assertEquals(
            "ca-app-pub-4691964664970287/4873589041",
            AdRules.unitFor(AdPlace.MILE, buildTest = false, serverTest = false)
        )
        // um bloco real por lugar, todos diferentes
        assertEquals(4, AdRules.REAL_UNITS.values.toSet().size)
    }

    @Test
    fun avisoPorDesfecho() {
        assertNull(AdRules.notice(AdShowOutcome.EARNED, "applied"))
        assertEquals(K.AD_DELAYED, AdRules.notice(AdShowOutcome.EARNED, "pending"))
        assertEquals(K.AD_CLOSED_EARLY, AdRules.notice(AdShowOutcome.CLOSED_EARLY, null))
        assertEquals(K.AD_UNAVAILABLE, AdRules.notice(AdShowOutcome.UNAVAILABLE, null))
    }

    @Test
    fun avisoQuandoOServidorRecusaOVale() {
        assertNull(AdRules.startNotice("ok"))
        assertEquals(K.AD_LIMIT, AdRules.startNotice("limit"))
        assertEquals(K.AD_UNAVAILABLE, AdRules.startNotice("off"))
        assertEquals(K.AD_UNAVAILABLE, AdRules.startNotice("not_eligible"))
    }

    @Test
    fun vitoriaPorAusenciaFicaMarcadaNaPartida() {
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE)
        assertFalse(m.wonByAbsence)
        m.abandon(m.mySide)
        assertTrue(m.wonByAbsence)
    }

    @Test
    fun cadaPartidaTemIdProprio() {
        val a = Match(GameMode.CLASSIC)
        val b = Match(GameMode.CLASSIC)
        assertEquals(32, a.localId.length)
        assertTrue(a.localId != b.localId)
    }
}
