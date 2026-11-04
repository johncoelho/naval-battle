package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Sair da conta (#64): nada é apagado enquanto houver progresso que a nuvem ainda não tem. */
class SignOutRulesTest {
    @Test
    fun salvoESemPendenciaSai() {
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = true, awardsUnsent = false, force = false))
    }

    @Test
    fun carreiraNaoSalvaAvisa() {
        assertEquals(SignOutDecision.WARN, SignOutRules.decide(careerSaved = false, awardsUnsent = false, force = false))
    }

    @Test
    fun premioPendenteAvisa() {
        assertEquals(SignOutDecision.WARN, SignOutRules.decide(careerSaved = true, awardsUnsent = true, force = false))
    }

    @Test
    fun forcarSempreSai() {
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = false, awardsUnsent = true, force = true))
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = true, awardsUnsent = false, force = true))
    }

    @Test
    fun soPremioSemRespostaOuRecenteBloqueia() {
        // o servidor respondeu no_result (sala sem vencedor) e a sala é antiga: não bloqueia a saída (#73)
        assertEquals(SignOutDecision.PROCEED, SignOutRules.decide(careerSaved = true, awardsUnsent = false, force = false))
        assertEquals(SignOutDecision.WARN, SignOutRules.decide(careerSaved = true, awardsUnsent = true, force = false))
    }

    @Test
    fun motivoDoAvisoSoDizRedeQuandoFoiRede() {
        assertEquals(SignOutReason.OFFLINE, SignOutRules.reason(careerSaved = false, careerNetworkFail = true, awardsNetworkFail = false))
        assertEquals(SignOutReason.OFFLINE, SignOutRules.reason(careerSaved = true, careerNetworkFail = false, awardsNetworkFail = true))
        assertEquals(SignOutReason.SERVER, SignOutRules.reason(careerSaved = false, careerNetworkFail = false, awardsNetworkFail = false))
        assertEquals(SignOutReason.SERVER, SignOutRules.reason(careerSaved = true, careerNetworkFail = true, awardsNetworkFail = false))
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
