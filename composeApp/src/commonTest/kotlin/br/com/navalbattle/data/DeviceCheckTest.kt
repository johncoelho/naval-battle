package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DeviceCheckTest {

    @Test
    fun soTrueMarcaAparelhoDeTeste() {
        assertTrue(isTestLabFlag("true"))
        assertTrue(isTestLabFlag(" true "))
    }

    @Test
    fun qualquerOutroValorNaoMarca() {
        assertFalse(isTestLabFlag(null))
        assertFalse(isTestLabFlag(""))
        assertFalse(isTestLabFlag("false"))
        assertFalse(isTestLabFlag("1"))
        assertFalse(isTestLabFlag("TRUE "))
    }

    @Test
    fun chaveDoRelatoMudaComContaOuVersao() {
        val base = deviceCheckKey("u1", "0.32.0")
        assertEquals("u1|0.32.0", base)
        assertEquals(base, deviceCheckKey("u1", "0.32.0"))
        assertNotEquals(base, deviceCheckKey("u2", "0.32.0"))
        assertNotEquals(base, deviceCheckKey("u1", "0.32.1"))
    }
}
