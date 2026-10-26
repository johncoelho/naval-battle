package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuickPracticeTest {
    private val t0 = 5_000_000L

    @Test fun ofertaEscondidaAntesDeVinteSegundos() {
        assertFalse(QuickPractice.practiceOfferVisible(t0, t0, true))
        assertFalse(QuickPractice.practiceOfferVisible(t0, t0 + 19_999, true))
    }

    @Test fun ofertaVisivelAPartirDeVinteSegundos() {
        assertTrue(QuickPractice.practiceOfferVisible(t0, t0 + 20_000, true))
        assertTrue(QuickPractice.practiceOfferVisible(t0, t0 + 90_000, true))
    }

    @Test fun nuncaEmSalaDeAmigoOuCodigo() {
        assertFalse(QuickPractice.practiceOfferVisible(t0, t0 + 60_000, false))
    }

    @Test fun semBuscaIniciadaNaoOferece() {
        assertFalse(QuickPractice.practiceOfferVisible(0L, t0, true))
    }

    @Test fun comTreinoAtivoEntradaTrocaSozinha() {
        assertEquals(QuickPractice.JoinAction.AUTO_SWITCH, QuickPractice.onOpponentJoined(true))
    }

    @Test fun semTreinoEntradaSegueOFluxoNormal() {
        assertEquals(QuickPractice.JoinAction.START, QuickPractice.onOpponentJoined(false))
    }

    @Test fun treinoTrocadoNaoContaETerminadoConta() {
        assertFalse(QuickPractice.practiceCounts(true))
        assertTrue(QuickPractice.practiceCounts(false))
    }

    @Test fun avisoDeTresSegundos() {
        assertEquals(3, QuickPractice.switchSeconds)
        assertEquals(3_000L, QuickPractice.switchDelayMillis)
    }
}
