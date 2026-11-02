package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Consulta com piso e ids vistos (#67): linha de id menor que chega depois sai uma vez só. */
class PollCursorTest {

    private fun msg(id: Long) = OnlineMessage(id, "u", "ACT|0|0")
    private fun ids(list: List<OnlineMessage>) = list.map { it.id }

    @Test
    fun primeirasConsultasPedemDesdeOComeco() {
        val c = PollCursor()
        assertEquals(0L, c.floor())
        c.accept(listOf(msg(1)))
        assertEquals(0L, c.floor(), "ainda não há duas consultas boas")
    }

    @Test
    fun pisoFicaDuasConsultasAtras() {
        val c = PollCursor()
        c.accept(listOf(msg(1), msg(2)))
        c.accept(listOf(msg(5)))
        assertEquals(2L, c.floor())
        c.accept(listOf(msg(8)))
        assertEquals(5L, c.floor())
    }

    @Test
    fun consultaSemLinhasNovasAvancaOPisoSemPerderNada() {
        val c = PollCursor()
        c.accept(listOf(msg(3)))
        c.accept(emptyList())
        c.accept(emptyList())
        assertEquals(3L, c.floor())
    }

    @Test
    fun linhaComIdMenorQueChegaDepoisEEntregueUmaVezSo() {
        val c = PollCursor()
        assertEquals(listOf(1L), ids(c.accept(listOf(msg(1)))))
        // o 5 apareceu antes do 4 (identity dá o número antes de a linha ficar visível)
        assertEquals(listOf(5L), ids(c.accept(listOf(msg(5)))))
        // a próxima consulta parte do piso e já traz o 4 junto com o 5 repetido
        assertTrue(c.floor() < 4, "o piso ${c.floor()} precisa estar abaixo do 4")
        assertEquals(listOf(4L), ids(c.accept(listOf(msg(4), msg(5)))))
        // e nunca mais
        assertEquals(emptyList(), ids(c.accept(listOf(msg(4), msg(5)))))
    }

    @Test
    fun repetidasNaMesmaConsultaSaemEmOrdemDeId() {
        val c = PollCursor()
        assertEquals(listOf(2L, 3L, 7L), ids(c.accept(listOf(msg(7), msg(2), msg(3), msg(3)))))
    }

    @Test
    fun resetDepoisDoLogInteiroNaoRepeteOQueJaVeio() {
        val c = PollCursor()
        val all = listOf(msg(1), msg(2), msg(3))
        c.resetTo(all)
        assertEquals(0L, c.floor(), "baixa de novo e o conjunto descarta")
        assertEquals(emptyList(), ids(c.accept(all)))
        assertEquals(listOf(4L), ids(c.accept(all + msg(4))))
    }

    @Test
    fun unseenDevolveSoOQueNaoPassouPeloAcceptEmOrdemDeId() {
        val c = PollCursor()
        c.accept(listOf(msg(1), msg(2)))
        val all = listOf(msg(1), msg(2), msg(4), msg(3))
        assertEquals(listOf(3L, 4L), ids(c.unseen(all)))
        // não altera o cursor: depois do resetTo nada mais é pendente
        assertEquals(listOf(3L, 4L), ids(c.unseen(all)))
        c.resetTo(all)
        assertEquals(emptyList(), ids(c.unseen(all)))
    }
}
