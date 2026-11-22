package br.com.navalbattle.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Fluxo de compra do iPhone (#65) com a ponte StoreKit falsa: DOBIOS-6, 7 e 8 do QA_TEST_PLAN. */
class StoreKitFlowTest {
    private class FakePort : StoreKitPort {
        var purchaseResult = StoreKitItem("purchased", "dobroes_bau", "2000000111", "jws-1", "")
        var open = mutableListOf<StoreKitItem>()
        val finished = mutableListOf<String>()
        var finishOk = true
        var lastToken: String? = null

        /** Ids que a "Apple" devolve; nulo = todos. Lista vazia imita o StoreKit sem produtos (#122). */
        var available: Set<String>? = null

        override suspend fun products(ids: List<String>) = ids.filter { available?.contains(it) ?: true }.map { StoreProduct(it, "R$ 9,90") }

        override suspend fun purchase(productId: String, accountToken: String): StoreKitItem {
            lastToken = accountToken
            return purchaseResult
        }

        override suspend fun unfinished(): List<StoreKitItem> = open

        override suspend fun finish(transactionId: String): Boolean {
            finished += transactionId
            return finishOk
        }
    }

    /** A ponte falsa nunca suspende de verdade: roda até o fim na hora (sem dependência nova). */
    private fun <T> runNow(block: suspend () -> T): T {
        var out: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
        return out!!.getOrThrow()
    }

    private val account = "3f2b1c9e-1111-4222-8333-444455556666"

    @Test
    fun compraDevolveOJwsEPassaAContaComoAppAccountToken() = runNow {
        val port = FakePort()
        val out = StoreKitFlow(port).buy("dobroes_bau", account)
        assertEquals(BuyOutcome.Purchased("dobroes_bau", "jws-1"), out)
        assertEquals(account, port.lastToken)
        assertTrue(port.finished.isEmpty(), "comprar nao finaliza: so o servidor decide")
    }

    @Test
    fun cancelarPendenteEFalhaNaoViramCompra() = runNow {
        val port = FakePort()
        val flow = StoreKitFlow(port)
        port.purchaseResult = StoreKitItem("cancelled", "dobroes_bau", "", "", "")
        assertEquals(BuyOutcome.Cancelled, flow.buy("dobroes_bau", account))
        port.purchaseResult = StoreKitItem("pending", "dobroes_bau", "", "", "")
        assertEquals(BuyOutcome.Pending, flow.buy("dobroes_bau", account))
        port.purchaseResult = StoreKitItem("failed", "dobroes_bau", "", "", "sem rede")
        assertIs<BuyOutcome.Failed>(flow.buy("dobroes_bau", account))
        port.purchaseResult = StoreKitItem("purchased", "dobroes_bau", "", "", "")
        assertIs<BuyOutcome.Failed>(flow.buy("dobroes_bau", account), "compra sem JWS nao vai ao servidor")
        assertTrue(port.finished.isEmpty())
    }

    @Test
    fun soFinalizaDepoisDoServidorMandarFinish() = runNow {
        val port = FakePort()
        val flow = StoreKitFlow(port)
        val token = (flow.buy("dobroes_bau", account) as BuyOutcome.Purchased).token

        val credited = verifyThenSettle(token, { CloudResult.Ok(DobroesVerifyResult("credited", 1100, true)) }) { flow.settle(it) }
        assertIs<CloudResult.Ok<DobroesVerifyResult>>(credited)
        assertEquals(listOf("2000000111"), port.finished)
    }

    @Test
    fun billingOffRedeFalhaEFinishFalsoDeixamATransacaoAberta() = runNow {
        val port = FakePort()
        val flow = StoreKitFlow(port)
        val token = (flow.buy("dobroes_bau", account) as BuyOutcome.Purchased).token

        verifyThenSettle(token, { CloudResult.Ok(DobroesVerifyResult("billing_off", 0, false)) }) { flow.settle(it) }
        verifyThenSettle(token, { CloudResult.Fail("sem rede") }) { flow.settle(it) }
        assertTrue(port.finished.isEmpty(), "sem finish=true a Apple devolve a compra depois")
    }

    @Test
    fun transacaoPendenteDeUmaSessaoAnteriorECreditadaEFinalizada() = runNow {
        val port = FakePort()
        port.open += StoreKitItem("purchased", "dobroes_cofre", "2000000222", "jws-2", "")
        port.open += StoreKitItem("purchased", "dobroes_bolsa", "", "", "") // incompleta: ignorada
        val flow = StoreKitFlow(port)

        val tokens = flow.unfinished()
        assertEquals(listOf(PurchasedToken("dobroes_cofre", "jws-2")), tokens)

        verifyThenSettle("jws-2", { CloudResult.Ok(DobroesVerifyResult("already", 0, true)) }) { flow.settle(it) }
        assertEquals(listOf("2000000222"), port.finished)
    }

    @Test
    fun finalizarFalhoPodeSerRepetido() = runNow {
        val port = FakePort()
        val flow = StoreKitFlow(port)
        val token = (flow.buy("dobroes_bau", account) as BuyOutcome.Purchased).token
        port.finishOk = false
        flow.settle(token)
        port.finishOk = true
        flow.settle(token)
        flow.settle(token) // ja finalizada: nao repete
        assertEquals(listOf("2000000111", "2000000111"), port.finished)
    }

    @Test
    fun listaDeProdutosVaziaComTresOuComQuatro() = runNow {
        val ids = listOf("dobroes_bolsa", "dobroes_bau", "dobroes_cofre", "dobroes_tesouro")
        val port = FakePort()
        val flow = StoreKitFlow(port)
        port.available = emptySet()
        assertEquals(emptyList(), flow.products(ids))
        port.available = ids.take(3).toSet()
        assertEquals(ids.take(3), flow.products(ids).map { it.productId })
        port.available = null
        assertEquals(ids, flow.products(ids).map { it.productId })
    }

    @Test
    fun produtosTrazemOPrecoLocalDaAppStore() = runNow {
        val list = StoreKitFlow(FakePort()).products(listOf("dobroes_bolsa", "dobroes_bau"))
        assertEquals(listOf("dobroes_bolsa", "dobroes_bau"), list.map { it.productId })
        assertEquals("R$ 9,90", list.first().formattedPrice)
    }
}
