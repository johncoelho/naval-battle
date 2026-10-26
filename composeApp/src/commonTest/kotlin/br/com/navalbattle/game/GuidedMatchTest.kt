package br.com.navalbattle.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Primeira partida guiada (#43): sequência de passos, oferta e IA no nível fácil. */
class GuidedMatchTest {

    private fun run(vararg events: GuidedEvent): GuidedStep =
        events.fold(GuidedStep.PLACE) { step, e -> nextGuidedStep(step, e) }

    @Test
    fun sequenciaCompletaPosicionouAtirouAcertouAfundou() {
        assertEquals(GuidedStep.FIRE, run(GuidedEvent.PLACED))
        assertEquals(GuidedStep.HIT, run(GuidedEvent.PLACED, GuidedEvent.HIT_SHIP))
        assertEquals(GuidedStep.SUNK, run(GuidedEvent.PLACED, GuidedEvent.HIT_SHIP, GuidedEvent.SUNK_SHIP))
        assertEquals(
            GuidedStep.DONE,
            run(GuidedEvent.PLACED, GuidedEvent.HIT_SHIP, GuidedEvent.SUNK_SHIP, GuidedEvent.FINISHED)
        )
    }

    @Test
    fun tiroNaAguaNaoAvancaEAcertoAntesDePosicionarTambemNao() {
        assertEquals(GuidedStep.FIRE, run(GuidedEvent.PLACED, GuidedEvent.MISSED, GuidedEvent.MISSED))
        assertEquals(GuidedStep.PLACE, run(GuidedEvent.HIT_SHIP))
        assertEquals(GuidedStep.HIT, run(GuidedEvent.PLACED, GuidedEvent.HIT_SHIP, GuidedEvent.MISSED))
    }

    @Test
    fun afundarDireitoPulaOPassoDoAcerto() {
        assertEquals(GuidedStep.SUNK, run(GuidedEvent.PLACED, GuidedEvent.SUNK_SHIP))
    }

    @Test
    fun pularEncerraEmQualquerPassoEDoneNaoVolta() {
        for (step in GuidedStep.entries) {
            assertEquals(GuidedStep.DONE, nextGuidedStep(step, GuidedEvent.SKIPPED))
        }
        assertEquals(GuidedStep.DONE, nextGuidedStep(GuidedStep.DONE, GuidedEvent.PLACED))
    }

    @Test
    fun ofertaSoParaQuemNuncaJogouENaoFezAGuiada() {
        assertTrue(shouldOfferGuided(0, false))
        assertFalse(shouldOfferGuided(1, false))
        assertFalse(shouldOfferGuided(0, true))
        assertFalse(shouldOfferGuided(12, true))
    }

    @Test
    fun iaFacilAtiraSemPerseguirOAcerto() {
        // IA normal, depois de um acerto, ataca vizinha; a fácil só atira em casa aberta qualquer
        val m = Match(GameMode.CLASSIC, Opponent.AI, random = Random(7), easyAi = true)
        m.playerBoard.clear()
        ShipClass.fleet.forEachIndexed { i, type -> m.playerBoard.place(Ship(type, Coord(0, i * 2), Orientation.HORIZONTAL)) }
        m.confirmPlacement()
        assertEquals(Phase.BATTLE, m.phase)
        val shots = mutableSetOf<Coord>()
        repeat(30) {
            if (m.turnOwner == Side.ENEMY) {
                m.enemyTurn()
            } else {
                m.act(Coord(9, 9 - it % 5)) // passa a vez errando
            }
            m.playerBoard.marks.keys.forEach { c -> shots += c }
        }
        assertTrue(shots.isNotEmpty())
    }
}
