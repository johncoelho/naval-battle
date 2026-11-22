package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals

/** #118: a Primeira missao nao cobre o Passe de Temporada (a Apple achou o botao do Passe "sem resposta"). */
class GuidedOfferVisibleTest {
    private fun vis(
        offer: Boolean = true, menu: Boolean = true, match: Boolean = false,
        season: Boolean = false, end: Boolean = false, locked: Boolean = false, result: Boolean = false
    ) = guidedOfferVisible(offer, menu, match, season, end, locked, result)

    @Test
    fun apareceNoMenuSemOutroPopup() = assertEquals(true, vis())

    @Test
    fun naoApareceSemOferta() = assertEquals(false, vis(offer = false))

    @Test
    fun naoApareceForaDoMenuOuComPartida() {
        assertEquals(false, vis(menu = false))
        assertEquals(false, vis(match = true))
    }

    @Test
    fun cedeAVezAoPasseEAoResultadoDele() {
        assertEquals(false, vis(season = true))
        assertEquals(false, vis(end = true))
        assertEquals(false, vis(locked = true))
        assertEquals(false, vis(result = true))
    }
}
