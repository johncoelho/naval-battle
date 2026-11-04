package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Carteira de dobrões no servidor (#34): plano de abertura, fila de recompensa online e IA = 0. As
 * regras do servidor (maior saldo, sem soma dupla, limites) estão no ensaio
 * `supabase/tests/dobroes_saldo_ensaio.sql`.
 */
class WalletTest {

    @Test
    fun aberturaLevaOSaldoDoAparelhoELimitaNoTeto() {
        assertEquals(800, Wallet.openPlan(800, emptySet()).local)
        assertEquals(Wallet.OPEN_LIMIT, Wallet.openPlan(999_999, emptySet()).local)
        assertEquals(0, Wallet.openPlan(-5, emptySet()).local)
    }

    @Test
    fun aberturaLevaOsTokensJaSomadosSemRepetir() {
        val plan = Wallet.openPlan(100, setOf("b", "a", "b"))
        assertEquals(listOf("a", "b"), plan.delivered)
    }

    @Test
    fun saldoDeAberturaEOMaiorEntreServidorEAparelho() {
        assertEquals(1000, Wallet.openingBalance(server = 1000, local = 800))
        assertEquals(5000, Wallet.openingBalance(server = 300, local = 5000))
        assertEquals(Wallet.OPEN_LIMIT, Wallet.openingBalance(server = 300, local = 999_999))
        // servidor acima do teto continua inteiro: nunca perde
        assertEquals(250_000, Wallet.openingBalance(server = 250_000, local = 10))
    }

    @Test
    fun iaPagaZeroEmQualquerResultado() {
        assertEquals(0, Wallet.aiCredits(victory = true))
        assertEquals(0, Wallet.aiCredits(victory = false))
    }

    @Test
    fun pedidoDoCartuchoReaproveitaOIdGuardadoDaMesmaHabilidade() {
        val guardado = ChargeRequest.encode("2X", "abc123")
        assertEquals("abc123", ChargeRequest.pick(guardado, "2X", fresh = "novo"))
        // outra habilidade, lixo ou vazio: id novo
        assertEquals("novo", ChargeRequest.pick(guardado, "SNR", fresh = "novo"))
        assertEquals("novo", ChargeRequest.pick("", "2X", fresh = "novo"))
        assertEquals("novo", ChargeRequest.pick("2X|", "2X", fresh = "novo"))
    }

    @Test
    fun pedidoDoCartuchoSoSomeQuandoOServidorDecidiu() {
        // resposta perdida (rede) mantém o id para a nova tentativa não cobrar duas vezes
        assertFalse(ChargeRequest.finished("offline"))
        assertFalse(ChargeRequest.finished("busy"))
        assertFalse(ChargeRequest.finished("no_wallet"))
        assertTrue(ChargeRequest.finished("ok"))
        assertTrue(ChargeRequest.finished("insufficient"))
        assertTrue(ChargeRequest.finished("not_found"))
    }

    @Test
    fun filaGuardaEDevolveAsPartidas() {
        val a = PendingAward("sala-1", 30, 20, 3, 25, true)
        val b = PendingAward("sala-2", 10, 2, 0, 40, false)
        val raw = OnlineAwardQueue.encode(listOf(a, b))
        assertEquals(listOf(a, b), OnlineAwardQueue.decode(raw))
        assertTrue(OnlineAwardQueue.decode("").isEmpty())
    }

    @Test
    fun filaIgnoraLixoNasPreferencias() {
        assertEquals(1, OnlineAwardQueue.decode("sala-1,1,1,1,1,1;lixo;a,b,c,d,e,f").size)
    }

    @Test
    fun filaNaoRepeteASalaEMantemAsMaisNovas() {
        val a = PendingAward("sala-1", 1, 1, 1, 1, true)
        val again = a.copy(shots = 9)
        val q = OnlineAwardQueue.add(OnlineAwardQueue.add(emptyList(), a), again)
        assertEquals(listOf(again), q)
        var many = emptyList<PendingAward>()
        repeat(OnlineAwardQueue.MAX_PENDING + 5) { many = OnlineAwardQueue.add(many, a.copy(matchId = "s$it")) }
        assertEquals(OnlineAwardQueue.MAX_PENDING, many.size)
        assertEquals("s${OnlineAwardQueue.MAX_PENDING + 4}", many.last().matchId)
    }

    @Test
    fun respostaDefinitivaSaiDaFilaEResultadoPendenteFica() {
        assertTrue(OnlineAwardQueue.settled("ok"))
        assertTrue(OnlineAwardQueue.settled("already"))
        assertTrue(OnlineAwardQueue.settled("limit"))
        assertFalse(OnlineAwardQueue.settled("no_result"))
        val a = PendingAward("sala-1", 1, 1, 1, 1, true)
        assertTrue(OnlineAwardQueue.remove(listOf(a), "sala-1").isEmpty())
    }

    @Test
    fun filaAntigaComSeisCamposAindaLe() {
        val q = OnlineAwardQueue.decode("sala-1,1,2,3,4,1;sala-2,1,2,3,4,0,1700000000000")
        assertEquals(2, q.size)
        assertEquals(0L, q[0].at)
        assertEquals(1_700_000_000_000L, q[1].at)
        assertEquals(q, OnlineAwardQueue.decode(OnlineAwardQueue.encode(q)))
    }

    @Test
    fun saidaSoFicaPresaPorPremioSemRespostaOuNoResultRecente() {
        val now = 10_000_000L
        val grace = OnlineAwardQueue.NO_RESULT_GRACE_MS
        val recent = PendingAward("r", 1, 1, 1, 1, true, at = now - 1_000L)
        val old = PendingAward("o", 1, 1, 1, 1, true, at = now - grace - 1L)
        val unknownAge = PendingAward("u", 1, 1, 1, 1, true)  // fila gravada pelo app antigo
        // fila vazia não bloqueia
        assertFalse(OnlineAwardQueue.blocksSignOut(emptyList(), emptyMap(), now))
        // só no_result respondido e antigo (ou de idade desconhecida): sai
        assertFalse(OnlineAwardQueue.blocksSignOut(listOf(old, unknownAge), mapOf("o" to "no_result", "u" to "no_result"), now))
        // no_result recente: segura
        assertTrue(OnlineAwardQueue.blocksSignOut(listOf(recent), mapOf("r" to "no_result"), now))
        // sem resposta do servidor (rede/erro): segura, mesmo antigo
        assertTrue(OnlineAwardQueue.blocksSignOut(listOf(old), emptyMap(), now))
        // carteira ainda sem abrir no servidor: segura
        assertTrue(OnlineAwardQueue.blocksSignOut(listOf(old), mapOf("o" to "no_wallet"), now))
        // um sem resposta no meio de outros antigos basta para segurar
        assertTrue(OnlineAwardQueue.blocksSignOut(listOf(old, unknownAge), mapOf("o" to "no_result"), now))
    }
}
