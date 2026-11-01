package br.com.navalbattle.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NameDraftTest {
    @Test
    fun campoVazioSegueONomeSalvoQueMuda() {
        assertEquals("Bina", followSaved("", "", "Bina"))
    }

    @Test
    fun campoEditadoNaoTroca() {
        assertEquals("Capita", followSaved("Capita", "", "Bina"))
        assertEquals("Capita", followSaved("Capita", "Bina", "Bina-1a2b"))
    }

    @Test
    fun espacoSobrandoContaComoNaoEditado() {
        assertEquals("Bina", followSaved("  ", "", "Bina"))
        assertEquals("Bina", followSaved("Ana ", "Ana", "Bina"))
    }

    @Test
    fun voltaParaVazioAoSairDaConta() {
        // #64: ao sair, o nome salvo volta a "" e o campo nao editado acompanha
        assertEquals("", followSaved("Bina", "Bina", ""))
    }

    @Test
    fun semMudancaNadaMuda() {
        assertEquals("Bina", followSaved("Bina", "Bina", "Bina"))
        assertEquals("Capita", followSaved("Capita", "Bina", "Bina"))
    }
}
