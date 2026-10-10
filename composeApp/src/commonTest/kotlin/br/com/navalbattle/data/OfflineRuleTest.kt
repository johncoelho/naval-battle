package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #8: o relógio do turno para quando este aparelho fica sem conexão. */
class OfflineRuleTest {
    @Test
    fun dentroDoLimiteContinuaOnline() {
        assertFalse(isOffline(now = 10_000, lastOk = 5_000))
        assertFalse(isOffline(now = 11_000, lastOk = 5_000)) // exatamente 6 s
    }

    @Test
    fun foraDoLimiteFicaOffline() {
        assertTrue(isOffline(now = 11_001, lastOk = 5_000))
        assertTrue(isOffline(now = 40_000, lastOk = 0))
    }

    @Test
    fun limitePersonalizado() {
        assertTrue(isOffline(now = 2_001, lastOk = 0, limitMs = 2_000))
        assertFalse(isOffline(now = 2_000, lastOk = 0, limitMs = 2_000))
    }
}
