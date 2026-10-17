package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Lote de escrita (#15): saldo e compras entregues entram juntos; a última escrita de cada chave vale. */
class PrefsBatchTest {

    @Test
    fun guardaTextoEInteiroNoMesmoLote() {
        val batch = PrefsBatch()
        batch.putInt("credits", 6500)
        batch.putString("dobroes_delivered", "tok-1,tok-2")
        assertEquals(mapOf("credits" to 6500), batch.ints)
        assertEquals(mapOf("dobroes_delivered" to "tok-1,tok-2"), batch.strings)
    }

    @Test
    fun ultimaEscritaDaChaveVale() {
        val batch = PrefsBatch()
        batch.putInt("credits", 500)
        batch.putInt("credits", 7000)
        batch.putString("dobroes_delivered", "a")
        batch.putString("dobroes_delivered", "a,b")
        assertEquals(7000, batch.ints["credits"])
        assertEquals("a,b", batch.strings["dobroes_delivered"])
    }
}
