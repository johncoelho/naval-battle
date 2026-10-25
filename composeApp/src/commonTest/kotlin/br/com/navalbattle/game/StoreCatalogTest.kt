package br.com.navalbattle.game

import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.ui.abilityPrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Os preços do app precisam ser os do servidor (#34): `supabase/dobroes_saldo.sql`, tabela
 * `store_items`. O servidor manda; se divergir o app mostra um preço e cobra outro. Mudou um preço:
 * mudam este teste, o seed do SQL e o app juntos.
 */
class StoreCatalogTest {

    @Test
    fun pinturasNaOrdemEComOsPrecosDoServidor() {
        val esperado = listOf(
            "std" to 0, "br" to 0, "jp" to 450, "us" to 450, "uk" to 600, "pt" to 600, "arc" to 800,
            "dzl" to 1000, "spl" to 1100, "cor" to 1200, "slt" to 1400,
            "s_pri" to 0, "s_ver" to 0, "s_out" to 0, "s_inv" to 0
        )
        assertEquals(esperado, Paint.all.map { it.id to it.price })
    }

    @Test
    fun cascosComOsPrecosDoServidor() {
        assertEquals(
            listOf("std" to 0, "imp" to 900, "atl" to 900, "gho" to 1800),
            FleetLine.all.map { it.id to it.price }
        )
    }

    @Test
    fun cartuchosComOsPrecosDoServidor() {
        val cartuchos = Ability.entries.filter { it.active }.associate { it.code to abilityPrice(it) }
        assertEquals(mapOf("2X" to 100, "REC" to 80, "SNR" to 60, "FUM" to 50), cartuchos)
    }

    @Test
    fun ofertaDoDiaGiraSobreAsOferecivelEComTrintaPorCento() {
        // oferecíveis = preço > 0 e sem estação, na ordem de Paint.all (igual a store_offer_today)
        val oferecivel = Paint.all.filter { it.price > 0 && it.season == null }.map { it.id }
        assertEquals(listOf("jp", "us", "uk", "pt", "arc", "dzl", "spl", "cor", "slt"), oferecivel)
        assertEquals("jp", Paint.dailyOffer(0) { false }?.id)
        assertEquals("us", Paint.dailyOffer(1) { false }?.id)
        // pula a que a conta já tem, como o servidor
        assertEquals("us", Paint.dailyOffer(0) { it == "jp" }?.id)
        assertNull(Paint.dailyOffer(0) { true })
        assertEquals(315, Paint.offerPrice(Paint.JAPAN))
        assertEquals(980, Paint.offerPrice(Paint.STEALTH))
    }
}
