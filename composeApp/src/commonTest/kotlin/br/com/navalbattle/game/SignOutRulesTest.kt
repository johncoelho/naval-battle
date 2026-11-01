package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Sair da conta (#64): nada é apagado enquanto houver progresso que a nuvem ainda não tem. */
class SignOutRulesTest {
    @Test
    fun salvoESemPendenciaSai() {
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = true, awardsPending = false, force = false))
    }

    @Test
    fun carreiraNaoSalvaAvisa() {
        assertEquals(SignOutDecision.WARN, SignOutRules.decide(careerSaved = false, awardsPending = false, force = false))
    }

    @Test
    fun premioPendenteAvisa() {
        assertEquals(SignOutDecision.WARN, SignOutRules.decide(careerSaved = true, awardsPending = true, force = false))
    }

    @Test
    fun forcarSempreSai() {
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = false, awardsPending = true, force = true))
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = true, awardsPending = false, force = true))
    }

    @Test
    fun convidadoPadraoNaoTemNadaDaCarreira() {
        val g = GuestCareer.DEFAULT
        assertEquals("", g.name)
        assertEquals(0, g.xp)
        assertEquals(0, g.credits)  // sem os 500 da instalacao nova: sair nao reabre o saldo inicial
        assertEquals(Insignia.ANCHOR.id, g.insigniaId)
        assertEquals(Avatar.COMMANDER_M.id, g.avatarId)
        assertEquals("std", g.equippedFleet)
        assertEquals("br", g.equipped)
        assertEquals(setOf("std"), g.ownedFleets)
        assertEquals(setOf("std", "br"), g.owned)
    }
}
