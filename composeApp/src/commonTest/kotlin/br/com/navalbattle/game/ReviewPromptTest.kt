package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReviewPromptTest {
    private val day = 86_400_000L
    private val now = 100 * day

    private fun ask(
        wins: Int = 3, rankedUp: Boolean = false, won: Boolean = true,
        last: Long = 0L, count: Int = 0, test: Boolean = false
    ) = ReviewPrompt.shouldAsk(wins, rankedUp, won, last, count, now, test)

    @Test fun segundaVitoriaNaoPede() = assertFalse(ask(wins = 2))

    @Test fun terceiraVitoriaPede() = assertTrue(ask(wins = 3))

    @Test fun vitoriaDepoDaTerceiraAindaPedeSeNuncaPediu() = assertTrue(ask(wins = 7))

    @Test fun subirDePatentePedeMesmoComPoucasVitorias() = assertTrue(ask(wins = 2, rankedUp = true))

    @Test fun derrotaNuncaPede() {
        assertFalse(ask(wins = 3, won = false))
        assertFalse(ask(wins = 3, rankedUp = true, won = false))
    }

    @Test fun menosDe30DiasDoUltimoNaoPede() {
        assertFalse(ask(rankedUp = true, last = now - 29 * day, count = 1))
    }

    @Test fun trintaDiasPedeNaPromocao() {
        assertTrue(ask(rankedUp = true, last = now - 30 * day, count = 1))
    }

    @Test fun depoisDoPrimeiroPedidoSoPromocaoPede() {
        assertFalse(ask(wins = 50, last = now - 60 * day, count = 1))
    }

    @Test fun tresPedidosNaVidaParam() {
        assertFalse(ask(rankedUp = true, last = now - 90 * day, count = 3))
        assertTrue(ask(rankedUp = true, last = now - 90 * day, count = 2))
    }

    @Test fun aparelhoDeTesteNuncaPede() = assertFalse(ask(rankedUp = true, test = true))
}
