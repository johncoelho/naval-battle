package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Entrega de dobrões do servidor ao aparelho (#15): cada token soma uma vez. */
class PendingCreditsTest {

    @Test
    fun tokenNovoSoma() {
        val plan = PendingCredits.plan(listOf(PendingCredit("T1", 6500)), delivered = emptySet())
        assertEquals(6500, plan.total)
        assertEquals(setOf("T1"), plan.delivered)
        assertEquals(listOf("T1"), plan.toAck)
    }

    @Test
    fun tokenJaSomadoNaoSomaDeNovo() {
        val plan = PendingCredits.plan(listOf(PendingCredit("T1", 6500)), delivered = setOf("T1"))
        assertEquals(0, plan.total)
        assertEquals(setOf("T1"), plan.delivered)
        // o ack continua indo: o servidor pode ter perdido a primeira confirmação
        assertEquals(listOf("T1"), plan.toAck)
    }

    @Test
    fun premioDoIphoneEntraUmaVezMesmoSeOServidorRepetir() {
        // #30: no iPhone não há replay da Play; o Prêmio chega só por dobroes_pending (token premio:)
        val token = "premio:uid-1:2026-10-18"
        val primeira = PendingCredits.plan(listOf(PendingCredit(token, 6500)), delivered = emptySet())
        assertEquals(6500, primeira.total)
        // o aparelho guarda o conjunto; o servidor devolve o mesmo token de novo (ack perdido)
        val repetida = PendingCredits.plan(listOf(PendingCredit(token, 6500)), delivered = primeira.delivered)
        assertEquals(0, repetida.total)
        assertEquals(listOf(token), repetida.toAck)
    }

    @Test
    fun premioEDuasComprasDaPlayNoMesmoLote() {
        val plan = PendingCredits.plan(
            listOf(PendingCredit("premio:uid-1:2026-10-18", 6500), PendingCredit("GPA.1", 1100)),
            delivered = setOf("premio:uid-1:2026-10-18")
        )
        assertEquals(1100, plan.total)
        assertEquals(listOf("premio:uid-1:2026-10-18", "GPA.1"), plan.toAck)
    }

    @Test
    fun listaVaziaNaoMudaNada() {
        val plan = PendingCredits.plan(emptyList(), delivered = setOf("T0"))
        assertEquals(0, plan.total)
        assertEquals(setOf("T0"), plan.delivered)
        assertTrue(plan.toAck.isEmpty())
    }

    @Test
    fun doisTokensIguaisNaMesmaEntregaSomamUmaVez() {
        val plan = PendingCredits.plan(
            listOf(PendingCredit("T1", 1100), PendingCredit("T1", 1100)),
            delivered = emptySet()
        )
        assertEquals(1100, plan.total)
        assertEquals(listOf("T1"), plan.toAck)
    }

    @Test
    fun variasComprasSomamTudoOQueEhNovo() {
        val plan = PendingCredits.plan(
            listOf(PendingCredit("A", 500), PendingCredit("B", 3000), PendingCredit("C", 6500)),
            delivered = setOf("B")
        )
        assertEquals(500 + 6500, plan.total)
        assertEquals(setOf("A", "B", "C"), plan.delivered)
        assertEquals(listOf("A", "B", "C"), plan.toAck)
    }
}
