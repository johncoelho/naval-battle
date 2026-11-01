package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Token do APNs (#14): só hexadecimal minúsculo de 64+ caracteres vai para o servidor. */
class PushTokenTest {
    private val valido = "0123456789abcdef".repeat(4)

    @Test
    fun tokenHexDe64CaracteresVale() {
        assertEquals(64, valido.length)
        assertTrue(isApnsToken(valido))
        assertTrue(isApnsToken(valido + "ab"))
    }

    @Test
    fun vazioCurtoEscritaErradaNaoValem() {
        assertFalse(isApnsToken(""))
        assertFalse(isApnsToken(valido.dropLast(1)))
        assertFalse(isApnsToken(valido.replaceRange(10, 11, " ")))
        assertFalse(isApnsToken(valido.replaceRange(10, 11, "A")))
        assertFalse(isApnsToken(valido.replaceRange(10, 11, "g")))
    }
}
