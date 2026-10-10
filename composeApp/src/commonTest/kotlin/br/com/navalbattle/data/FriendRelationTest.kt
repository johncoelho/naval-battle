package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendRelationTest {

    private fun f(from: String, to: String, status: String) =
        Friendship("$from-$to", from, to, from, to, status)

    @Test
    fun pedidoAceitoApareceComoAmigoNaoComoPedidoEnviado() {
        // A pediu, B aceitou: na busca de A, B é amigo (regressão da issue #7)
        assertEquals(FriendRelation.FRIEND, friendRelation("a", "b", listOf(f("a", "b", "accepted"))))
        // mesmo com o "Adicionar" otimista ainda marcado na tela
        assertEquals(FriendRelation.FRIEND, friendRelation("a", "b", listOf(f("a", "b", "accepted")), setOf("b")))
        // e do lado de quem aceitou
        assertEquals(FriendRelation.FRIEND, friendRelation("b", "a", listOf(f("a", "b", "accepted"))))
    }

    @Test
    fun pedidoPendenteDependeDeQuemPediu() {
        val pending = listOf(f("a", "b", "pending"))
        assertEquals(FriendRelation.REQUEST_SENT, friendRelation("a", "b", pending))
        assertEquals(FriendRelation.REQUEST_RECEIVED, friendRelation("b", "a", pending))
    }

    @Test
    fun linhaRecusadaNuncaViraRecebidoNemAdicionar() {
        val declined = listOf(f("a", "b", "declined"))
        // quem pediu e quem recusou: nenhum dos dois ganha Aceitar nem Adicionar
        assertEquals(FriendRelation.REQUEST_SENT, friendRelation("a", "b", declined))
        assertEquals(FriendRelation.REQUEST_SENT, friendRelation("b", "a", declined))
    }

    @Test
    fun semAmizadeSoMostraEnviadoEnquantoOServidorNaoResponde() {
        assertEquals(FriendRelation.NONE, friendRelation("a", "c", listOf(f("a", "b", "accepted"))))
        assertEquals(FriendRelation.REQUEST_SENT, friendRelation("a", "c", emptyList(), setOf("c")))
    }

    @Test
    fun aceitarNaBuscaSoComPedidoPendenteRecebido() {
        // #20: B pediu a A, pendente -> A pode aceitar direto na busca
        val pending = f("b", "a", "pending")
        assertEquals(pending, pendingRequestFrom("a", "b", listOf(pending)))
        // pedido enviado por mim nao vira Aceitar
        assertNull(pendingRequestFrom("b", "a", listOf(pending)))
        // recusado, aceito ou sem linha: nada a aceitar
        assertNull(pendingRequestFrom("a", "b", listOf(f("b", "a", "declined"))))
        assertNull(pendingRequestFrom("a", "b", listOf(f("b", "a", "accepted"))))
        assertNull(pendingRequestFrom("a", "b", emptyList()))
    }
}
