package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatePromptTest {
    @Test fun semAtualizacaoNaoMostra() {
        assertFalse(UpdatePrompt.shouldShowUpdate(false, "0.35.1", null))
    }

    @Test fun naoDispensadoMostra() {
        assertTrue(UpdatePrompt.shouldShowUpdate(true, "0.35.0", null))
        assertTrue(UpdatePrompt.shouldShowUpdate(true, null, null))
    }

    @Test fun dispensouUmaEsaiuOutraMaiorMostra() {
        assertTrue(UpdatePrompt.shouldShowUpdate(true, "0.35.1", "0.35.0"))
        assertTrue(UpdatePrompt.shouldShowUpdate(true, "0.36.0", "0.35.9"))
    }

    @Test fun dispensouAMesmaVersaoNaoMostra() {
        assertFalse(UpdatePrompt.shouldShowUpdate(true, "0.35.1", "0.35.1"))
        assertFalse(UpdatePrompt.shouldShowUpdate(true, "0.35.0", "0.35.1"))
    }

    @Test fun lojaDispensadaSemVersaoNovaNaoMostra() {
        assertFalse(UpdatePrompt.shouldShowUpdate(true, null, UpdatePrompt.STORE_MARK))
    }

    @Test fun lojaDispensadaEDepoisVersaoConhecidaMostra() {
        assertTrue(UpdatePrompt.shouldShowUpdate(true, "0.35.0", UpdatePrompt.STORE_MARK))
    }

    @Test fun marcaDoDepoisEhAVersaoOuALoja() {
        assertEquals("0.35.0", UpdatePrompt.dismissMark("0.35.0"))
        assertEquals(UpdatePrompt.STORE_MARK, UpdatePrompt.dismissMark(null))
    }

    @Test fun maiorVersaoDasNovidades() {
        assertEquals("0.35.10", UpdatePrompt.newestOf(listOf("0.35.2", "0.35.10", "0.34.13")))
        assertEquals(null, UpdatePrompt.newestOf(emptyList()))
    }

    @Test fun reconsultaSoDepoisDeCincoMinutos() {
        val t0 = 1_000_000L
        assertTrue(UpdatePrompt.shouldRecheck(0L, t0))
        assertFalse(UpdatePrompt.shouldRecheck(t0, t0 + 4 * 60_000L))
        assertFalse(UpdatePrompt.shouldRecheck(t0, t0 + 5 * 60_000L - 1))
        assertTrue(UpdatePrompt.shouldRecheck(t0, t0 + 5 * 60_000L))
        // relógio voltou atrás: consulta de novo em vez de travar
        assertTrue(UpdatePrompt.shouldRecheck(t0, t0 - 1))
    }
}
