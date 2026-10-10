package br.com.navalbattle.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardTest {

    @Test
    fun naoPosicionaForaDoTabuleiroNemSobreOutroNavio() {
        val board = Board()
        assertFalse(board.place(Ship(ShipClass.CARRIER, Coord(7, 0), Orientation.HORIZONTAL)), "porta-aviões passaria da coluna J")
        assertTrue(board.place(Ship(ShipClass.CARRIER, Coord(0, 0), Orientation.HORIZONTAL)))
        assertFalse(board.place(Ship(ShipClass.DESTROYER, Coord(2, 0), Orientation.VERTICAL)), "destróier cruzaria o porta-aviões")
        assertTrue(board.place(Ship(ShipClass.DESTROYER, Coord(2, 1), Orientation.VERTICAL)))
    }

    @Test
    fun giroDoProprioNavioNaoColideConsigoMesmo() {
        val board = Board()
        board.place(Ship(ShipClass.CRUISER, Coord(0, 0), Orientation.HORIZONTAL))
        assertTrue(board.place(Ship(ShipClass.CRUISER, Coord(0, 0), Orientation.VERTICAL)))
        assertEquals(1, board.ships.size)
    }

    @Test
    fun tiroNaAguaAcertoAfundadoERepetido() {
        val board = Board()
        board.place(Ship(ShipClass.DESTROYER, Coord(0, 0), Orientation.HORIZONTAL))

        assertEquals(ShotResult.MISS, board.fireAt(Coord(5, 5)).result)
        assertEquals(Mark.MISS, board.marks[Coord(5, 5)])

        val hit = board.fireAt(Coord(0, 0))
        assertEquals(ShotResult.HIT, hit.result)
        assertEquals(ShipClass.DESTROYER, hit.ship)

        assertEquals(ShotResult.SUNK, board.fireAt(Coord(1, 0)).result)
        assertEquals(Mark.SUNK, board.marks[Coord(0, 0)], "afundado marca o navio inteiro")
        assertTrue(board.allSunk())

        assertEquals(ShotResult.ALREADY_FIRED, board.fireAt(Coord(5, 5)).result)
        assertEquals(ShotResult.ALREADY_FIRED, board.fireAt(Coord(0, 0)).result)
    }

    @Test
    fun celulaVarridaAindaPodeLevarTiro() {
        val board = Board()
        board.place(Ship(ShipClass.CRUISER, Coord(0, 3), Orientation.HORIZONTAL))
        board.revealRow(3)
        assertEquals(Mark.SCAN_HOT, board.marks[Coord(1, 3)])
        assertEquals(Mark.SCAN_COLD, board.marks[Coord(9, 3)])
        assertEquals(ShotResult.HIT, board.fireAt(Coord(1, 3)).result)
    }

    @Test
    fun fumacaAnulaSoAProximaVarredura() {
        val board = Board()
        board.place(Ship(ShipClass.CRUISER, Coord(4, 4), Orientation.HORIZONTAL))
        board.smokeActive = true
        assertFalse(board.sonarPing(Coord(5, 4)), "primeira varredura bloqueada")
        assertTrue(board.marks.isEmpty())
        assertTrue(board.sonarPing(Coord(5, 4)), "segunda varredura passa")
        assertEquals(Mark.SCAN_HOT, board.marks[Coord(5, 4)])
    }

    @Test
    fun aleatorioPosicionaAFrotaInteiraSemSobreposicao() {
        repeat(50) { seed ->
            val board = Board()
            board.randomize(Random(seed))
            assertEquals(ShipClass.fleet.size, board.ships.size)
            val cells = board.ships.flatMap { it.cells }
            assertEquals(cells.size, cells.toSet().size, "seed $seed sobrepôs navios")
            assertTrue(cells.all { it.isValid() }, "seed $seed saiu do tabuleiro")
        }
    }
}
