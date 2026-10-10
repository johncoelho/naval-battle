package br.com.navalbattle.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** #8: o relógio do turno só desconta em primeiro plano e com conexão. */
class TurnTickTest {
    @Test
    fun descontaEmPrimeiroPlanoComConexao() {
        assertEquals(19, turnTick(20, foreground = true, offline = false))
    }

    @Test
    fun paraSemConexao() {
        assertEquals(12, turnTick(12, foreground = true, offline = true))
    }

    @Test
    fun paraEmSegundoPlano() {
        assertEquals(12, turnTick(12, foreground = false, offline = false))
        assertEquals(12, turnTick(12, foreground = false, offline = true))
    }

    @Test
    fun nuncaFicaNegativo() {
        assertEquals(0, turnTick(0, foreground = true, offline = false))
    }
}
