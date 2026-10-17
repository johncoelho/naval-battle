package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** Regra da aba Dobrões (#15): quem vê o Prêmio, a vitrine paga ou nada. */
class StoreViewTest {

    @Test
    fun testadorComPremioLigadoVeOPremio() {
        assertEquals(StoreView.PREMIO, storeView(beta = true, realEnabled = false, premioEnabled = true, premioClaimed = false))
    }

    @Test
    fun testadorQuePegouOPremioHojeVeQueVoltaAmanha() {
        assertEquals(StoreView.PREMIO_DONE, storeView(beta = true, realEnabled = false, premioEnabled = true, premioClaimed = true))
    }

    @Test
    fun testadorComPremioDesligadoFicaComAbaFechada() {
        assertEquals(StoreView.CLOSED, storeView(beta = true, realEnabled = false, premioEnabled = false, premioClaimed = false))
    }

    @Test
    fun testadorNuncaVeAVitrinePagaMesmoComAVendaLigada() {
        assertEquals(StoreView.CLOSED, storeView(beta = true, realEnabled = true, premioEnabled = false, premioClaimed = false))
        assertEquals(StoreView.PREMIO, storeView(beta = true, realEnabled = true, premioEnabled = true, premioClaimed = false))
    }

    @Test
    fun jogadorComumVeAVitrinaSoComAVendaLigada() {
        assertEquals(StoreView.PAID, storeView(beta = false, realEnabled = true, premioEnabled = false, premioClaimed = false))
        assertEquals(StoreView.CLOSED, storeView(beta = false, realEnabled = false, premioEnabled = false, premioClaimed = false))
    }

    @Test
    fun jogadorComumNaoVeOPremioMesmoComOInterruptorLigado() {
        assertEquals(StoreView.PAID, storeView(beta = false, realEnabled = true, premioEnabled = true, premioClaimed = false))
    }
}
