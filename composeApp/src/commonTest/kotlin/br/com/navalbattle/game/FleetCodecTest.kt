package br.com.navalbattle.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FleetCodecTest {

    @Test
    fun frotaVaiEVoltaPelaRedeIgual() {
        val board = Board()
        board.randomize(Random(7))
        val wire = FleetCodec.encode(board.ships).joinToString(";")
        assertEquals(board.ships.toList(), FleetCodec.decode(wire))
    }

    @Test
    fun linhaMalFormadaEhIgnorada() {
        val ships = FleetCodec.decode("CARRIER,0,0,H;LIXO;SUBMARINE,x,1,V;DESTROYER,3,3,V;")
        assertEquals(listOf(ShipClass.CARRIER, ShipClass.DESTROYER), ships.map { it.type })
        assertTrue(ships[1].orientation == Orientation.VERTICAL)
    }
}
