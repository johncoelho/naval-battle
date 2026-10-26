package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleAccountTest {

    @Test
    fun emailDeRelayDaApple() {
        assertTrue(isAppleRelayEmail("x7k2ab9qzd@privaterelay.appleid.com"))
        assertTrue(isAppleRelayEmail("  ABC@PrivateRelay.AppleID.com "))
    }

    @Test
    fun emailComumEVazioNaoSaoRelay() {
        assertFalse(isAppleRelayEmail("joao@gmail.com"))
        assertFalse(isAppleRelayEmail("joao@icloud.com"))
        assertFalse(isAppleRelayEmail(""))
    }

    @Test
    fun carreiraVirgemUsaOGivenName() {
        assertEquals("Maria", appleInitialName("Maria", "x7k@privaterelay.appleid.com", "x7k", 0, 0, ""))
        assertEquals("Maria", appleInitialName("  Maria ", "x7k@x.com", "X7K", 0, 0, ""))
    }

    @Test
    fun carreiraComProgressoNaoRenomeia() {
        assertNull(appleInitialName("Maria", "x7k@x.com", "x7k", 10, 0, ""))
        assertNull(appleInitialName("Maria", "x7k@x.com", "x7k", 0, 1, ""))
    }

    @Test
    fun nomeJaTrocadoNaoRenomeia() {
        assertNull(appleInitialName("Maria", "x7k@x.com", "Capitao Nemo", 0, 0, ""))
        assertNull(appleInitialName("Maria", "x7k@x.com", null, 0, 0, ""))
    }

    @Test
    fun givenNameNuloOuEmBrancoNaoRenomeia() {
        assertNull(appleInitialName(null, "x7k@x.com", "x7k", 0, 0, ""))
        assertNull(appleInitialName("   ", "x7k@x.com", "x7k", 0, 0, ""))
    }

    @Test
    fun cortaNoTamanhoMaximoDoNome() {
        val r = appleInitialName("Maximiliano Alexandrino de Souza", "x7k@x.com", "x7k", 0, 0, "")
        assertEquals(18, r?.length)
        assertEquals("Maximiliano Alexan", r)
    }

    @Test
    fun revogaSoComIdentidadeApple() {
        assertTrue(shouldRevokeApple(listOf("email", "apple")))
        assertTrue(shouldRevokeApple(listOf("Apple")))
        assertFalse(shouldRevokeApple(listOf("email", "google")))
        assertFalse(shouldRevokeApple(emptyList()))
    }

    @Test
    fun convidadoComNomeNaoERenomeado() {
        assertNull(appleInitialName("Maria", "x7k@x.com", "x7k", 0, 0, "Capitao Nemo"))
        assertEquals("Maria", appleInitialName("Maria", "x7k@x.com", "x7k", 0, 0, "   "))
    }

    @Test
    fun candidatosDeNomeTemSufixoDoIdECorteEm18() {
        assertEquals(listOf("Maria", "Maria-ab12"), appleNameCandidates("Maria", "ab12cd34-0000"))
        val longos = appleNameCandidates("Maximiliano Alexandrino de Souza", "ab12cd34")
        assertEquals("Maximiliano Alexan", longos[0])
        assertEquals("Maximiliano A-ab12", longos[1])
        assertTrue(longos.all { it.length <= 18 })
        assertEquals(emptyList(), appleNameCandidates("   ", "ab12"))
    }
}
