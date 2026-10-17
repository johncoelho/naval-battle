package br.com.navalbattle.game

import br.com.navalbattle.ui.formatThousands
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pacotes da loja (#15): os ids e as quantidades batem com dobroes_products do servidor. */
class DoubloonPackTest {

    @Test
    fun quatroPacotesComIdsDoPlayConsole() {
        assertEquals(
            listOf("dobroes_bolsa", "dobroes_bau", "dobroes_cofre", "dobroes_tesouro"),
            DoubloonPack.entries.map { it.code }
        )
    }

    @Test
    fun quantidadesIguaisAoServidor() {
        assertEquals(
            listOf(500, 1100, 3000, 6500),
            DoubloonPack.entries.map { it.doubloons }
        )
    }

    @Test
    fun premioBetaEOTesouro() {
        assertEquals(DoubloonPack.TREASURE, DoubloonPack.entries.first { it.code == "dobroes_tesouro" })
        assertEquals(6500, DoubloonPack.TREASURE.doubloons)
    }

    @Test
    fun precoDeReservaEFormatadoAMao() {
        assertEquals("R$ 4,90", brl(490))
        assertEquals("R$ 49,90", DoubloonPack.TREASURE.priceLabel)
    }

    @Test
    fun milharComPontoBrasileiro() {
        assertEquals("6.500", formatThousands(6500))
        assertEquals("500", formatThousands(500))
    }

    @Test
    fun premioSoAparecePeloTesouroListadoNaPlay() {
        assertEquals(false, premioDisponivel(emptyList()))
        assertEquals(false, premioDisponivel(listOf("dobroes_bolsa", "dobroes_bau", "dobroes_cofre")))
        assertEquals(true, premioDisponivel(listOf("dobroes_tesouro")))
        assertEquals(true, premioDisponivel(DoubloonPack.entries.map { it.code }))
    }
}
