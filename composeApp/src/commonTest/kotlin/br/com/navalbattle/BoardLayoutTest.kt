package br.com.navalbattle

import br.com.navalbattle.ui.BATTLE_CHROME_DP
import br.com.navalbattle.ui.BATTLE_CALLOUT_DP
import br.com.navalbattle.ui.BoardArrangement
import br.com.navalbattle.ui.MIN_FLEET_DP
import br.com.navalbattle.ui.PLACEMENT_MIN_BOARD_DP
import br.com.navalbattle.ui.PLACEMENT_RESERVED_DP
import br.com.navalbattle.ui.placementBoardSideDp
import br.com.navalbattle.ui.boardLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardLayoutTest {
    @Test
    fun celularEmRetratoEmpilha() {
        val l = boardLayout(412f, 892f)
        assertEquals(BoardArrangement.STACKED, l.arrangement)
        assertEquals(380f, l.boardSideDp) // largura menos a margem
    }

    @Test
    fun tabletEmPaisagemFicaLadoALado() {
        val l = boardLayout(1280f, 800f)
        assertEquals(BoardArrangement.SIDE_BY_SIDE, l.arrangement)
        assertTrue(l.sideBySide)
        // dois tabuleiros cabem na largura útil
        assertTrue(l.boardSideDp * 2 + 16f <= 1280f)
    }

    @Test
    fun tabletEmRetratoEmpilhaEFicaNaLarguraMaxima() {
        val l = boardLayout(800f, 1280f)
        assertEquals(BoardArrangement.STACKED, l.arrangement)
        assertEquals(568f, l.boardSideDp) // 600 dp de conteúdo menos a margem
    }

    @Test
    fun dobravelQuaseQuadradoEmpilha() {
        assertEquals(BoardArrangement.STACKED, boardLayout(840f, 800f).arrangement)
    }

    @Test
    fun janelaEstreitaEmPaisagemNaoVaiLadoALado() {
        // celular deitado (dividido ou girado): 800 x 360 não passa do mínimo de largura
        assertEquals(BoardArrangement.STACKED, boardLayout(800f, 360f).arrangement)
    }

    @Test
    fun telaBaixaNuncaGeraTabuleiroMaiorQueAAlturaUtil() {
        listOf(
            360f to 640f, 412f to 892f, 800f to 360f, 1280f to 800f,
            1280f to 500f, 1920f to 1080f, 2560f to 400f, 900f to 120f
        ).forEach { (w, h) ->
            val l = boardLayout(w, h)
            assertTrue(l.boardSideDp <= (h - BATTLE_CHROME_DP).coerceAtLeast(0f), "tabuleiro maior que a altura útil em ${w}x$h")
            assertTrue(l.boardSideDp >= 0f)
        }
    }

    @Test
    fun empilhadoDeixaAlturaMinimaParaAFrota() {
        // dobravel aberto (~820x790) e tablet em janela dividida (640x750) ficam abaixo de 840 dp
        listOf(820f to 790f, 640f to 750f, 800f to 1280f).forEach { (w, h) ->
            val l = boardLayout(w, h)
            assertEquals(BoardArrangement.STACKED, l.arrangement, "${w}x$h")
            val sobra = h - BATTLE_CHROME_DP - BATTLE_CALLOUT_DP - l.boardSideDp
            assertTrue(sobra >= MIN_FLEET_DP, "frota sem altura minima em ${w}x$h: $sobra")
        }
    }

    @Test
    fun celularNormalNaoMudaDeTamanho() {
        assertEquals(380f, boardLayout(412f, 892f).boardSideDp)
    }

    @Test
    fun celularPequenoNaoEncolhe() {
        assertEquals(328f, boardLayout(360f, 640f).boardSideDp)
        assertEquals(328f, boardLayout(360f, 720f).boardSideDp)
        assertEquals(320f, placementBoardSideDp(320f, 540f))
    }

    @Test
    fun posicionamentoNuncaPassaDaAlturaDisponivel() {
        // 1280x800 dp deitado: ~760 dp uteis sem as barras, 1240 de largura
        val side = placementBoardSideDp(560f, 760f)
        assertEquals(430f, side)
        assertTrue(side + PLACEMENT_RESERVED_DP <= 760f)
        // celular em retrato: continua com a largura toda
        assertEquals(372f, placementBoardSideDp(372f, 822f))
        // janela muito baixa nunca zera o tabuleiro
        assertEquals(PLACEMENT_MIN_BOARD_DP, placementBoardSideDp(560f, 300f))
    }

    @Test
    fun telaMuitoLargaNaoEsticaOsTabuleiros() {
        val l = boardLayout(2560f, 1440f)
        assertEquals(BoardArrangement.SIDE_BY_SIDE, l.arrangement)
        assertTrue(l.boardSideDp <= (1100f - 32f - 16f) / 2f)
    }
}
