package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals

/** Aba Dobrões (#28): quem vê o Prêmio, quem vê a vitrine e os avisos de cada status do servidor. */
class StoreViewTest {

    @Test
    fun testadorComPremioLigadoPegaOPremio() {
        assertEquals(PremioState.PEGAR, premioState(beta = true, premioEnabled = true, premioClaimed = false))
    }

    @Test
    fun testadorQuePegouOPremioHojeVeQueVoltaAmanha() {
        assertEquals(PremioState.JA_PEGOU, premioState(beta = true, premioEnabled = true, premioClaimed = true))
    }

    @Test
    fun premioEscondidoSemTesterOuSemInterruptor() {
        assertEquals(PremioState.ESCONDIDO, premioState(beta = false, premioEnabled = true, premioClaimed = false))
        assertEquals(PremioState.ESCONDIDO, premioState(beta = true, premioEnabled = false, premioClaimed = false))
    }

    @Test
    fun jogadorComumSoVeAVitrinaComAVendaLigada() {
        assertEquals(true, vitrineVisivel(vendaPelaPlay = true, realEnabled = true, vitrineBeta = false))
        assertEquals(false, vitrineVisivel(vendaPelaPlay = true, realEnabled = false, vitrineBeta = false))
    }

    @Test
    fun testadorVeAVitrinaSoComAVitrinaBetaLigada() {
        assertEquals(true, vitrineVisivel(vendaPelaPlay = true, realEnabled = false, vitrineBeta = true))
        assertEquals(true, vitrineVisivel(vendaPelaPlay = true, realEnabled = true, vitrineBeta = true))
    }

    @Test
    fun iPhoneNuncaVeAVitrinaMesmoComVendaOuBetaLigadas() {
        // iPhone não vende pela Play (#30): nenhum interruptor do servidor abre a vitrine
        assertEquals(false, vitrineVisivel(vendaPelaPlay = false, realEnabled = true, vitrineBeta = false))
        assertEquals(false, vitrineVisivel(vendaPelaPlay = false, realEnabled = false, vitrineBeta = true))
        assertEquals(false, vitrineVisivel(vendaPelaPlay = false, realEnabled = true, vitrineBeta = true))
    }

    @Test
    fun iPhoneDeTestadorVeOPremioMasNaoAVitrina() {
        // o Prêmio não depende da Play: premioState segue valendo no iPhone
        assertEquals(PremioState.PEGAR, premioState(beta = true, premioEnabled = true, premioClaimed = false))
        assertEquals(false, vitrineVisivel(vendaPelaPlay = false, realEnabled = false, vitrineBeta = true))
    }

    @Test
    fun avisoDaCompraSemAvisoQuandoCredita() {
        assertEquals(null, avisoCompra("credited"))
        assertEquals(null, avisoCompra("already"))
        assertEquals(null, avisoCompra("premio"))
    }

    @Test
    fun avisoDaCompraRecusadaOuFechada() {
        assertEquals(K.DOUBLOON_CLOSED, avisoCompra("billing_off"))
        assertEquals(K.DOUBLOON_CLOSED, avisoCompra("premio_only"))
        assertEquals(K.PREMIO_DONE_NOTICE, avisoCompra("premio_done"))
        assertEquals(K.DOUBLOON_BUY_FAILED, avisoCompra("account_mismatch"))
        assertEquals(K.DOUBLOON_BUY_FAILED, avisoCompra("not_beta"))
    }

    @Test
    fun avisoDoPremioPorStatusDaRpc() {
        assertEquals(null, avisoPremio("premio"))
        assertEquals(K.PREMIO_DONE_NOTICE, avisoPremio("already"))
        assertEquals(K.PREMIO_UNAVAILABLE, avisoPremio("off"))
        assertEquals(K.PREMIO_UNAVAILABLE, avisoPremio("not_beta"))
        assertEquals(K.DOUBLOON_BUY_FAILED, avisoPremio("erro_qualquer"))
    }
}
