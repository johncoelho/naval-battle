package br.com.navalbattle.ui

import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.Mark
import br.com.navalbattle.game.Tone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #22: regras de desenho do tabuleiro extraídas ao separar as camadas — comportamento igual ao de antes. */
class BoardRenderRulesTest {
    private val allMarks = Mark.entries

    @Test
    fun semReconhecimentoTudoAparece() {
        for (mark in allMarks) {
            assertTrue(markVisibleDuringRecon(Coord(9, 3), mark, reconRow = null, progress = 1f))
            assertTrue(markVisibleDuringRecon(Coord(9, 3), mark, reconRow = null, progress = 0f))
        }
    }

    @Test
    fun leituraDoRadarFrenteAoAviaoFicaOcultaAteElePassar() {
        // avião em 30% da linha 4 (coluna 3 de 10)
        assertTrue(markVisibleDuringRecon(Coord(2, 4), Mark.SCAN_HOT, reconRow = 4, progress = 0.3f))
        assertTrue(markVisibleDuringRecon(Coord(3, 4), Mark.SCAN_COLD, reconRow = 4, progress = 0.3f))
        assertFalse(markVisibleDuringRecon(Coord(4, 4), Mark.SCAN_HOT, reconRow = 4, progress = 0.3f))
        assertFalse(markVisibleDuringRecon(Coord(9, 4), Mark.SCAN_COLD, reconRow = 4, progress = 0.3f))
        // no começo da passada só a coluna 0 está acesa; no fim, todas
        assertTrue(markVisibleDuringRecon(Coord(0, 4), Mark.SCAN_HOT, reconRow = 4, progress = 0f))
        assertFalse(markVisibleDuringRecon(Coord(1, 4), Mark.SCAN_HOT, reconRow = 4, progress = 0f))
        assertTrue(markVisibleDuringRecon(Coord(9, 4), Mark.SCAN_COLD, reconRow = 4, progress = 0.99f))
    }

    @Test
    fun tirosNuncaSaoOcultados() {
        for (mark in listOf(Mark.HIT, Mark.MISS, Mark.SUNK)) {
            for (x in 0..9) {
                assertTrue(markVisibleDuringRecon(Coord(x, 4), mark, reconRow = 4, progress = 0f), "$mark em $x")
            }
        }
    }

    @Test
    fun outrasLinhasNaoMudam() {
        for (mark in allMarks) {
            assertTrue(markVisibleDuringRecon(Coord(9, 5), mark, reconRow = 4, progress = 0f))
            assertTrue(markVisibleDuringRecon(Coord(9, 3), mark, reconRow = 4, progress = 0f))
        }
    }

    @Test
    fun tremorIgualAoDeAntes() {
        assertEquals(listOf(-16f, 13f, -9f, 6f, -3f, 0f), shakePattern(Tone.SUNK))
        assertEquals(listOf(-7f, 5f, -3f, 0f), shakePattern(Tone.HIT))
        assertEquals(emptyList(), shakePattern(Tone.MISS))
        assertEquals(emptyList(), shakePattern(Tone.SCAN))
        assertEquals(emptyList(), shakePattern(Tone.INFO))
    }
}
