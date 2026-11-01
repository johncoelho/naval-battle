package br.com.navalbattle

import br.com.navalbattle.ui.BATTLE_CHROME_DP
import br.com.navalbattle.ui.BATTLE_CALLOUT_DP
import br.com.navalbattle.ui.BoardArrangement
import br.com.navalbattle.ui.ABILITY_STRIP_MIN_DP
import br.com.navalbattle.ui.FLEET_HEADER_DP
import br.com.navalbattle.ui.MIN_FLEET_BOARD_DP
import br.com.navalbattle.ui.STACKED_MIN_TARGET_DP
import br.com.navalbattle.ui.stackedContentWidthDp
import br.com.navalbattle.ui.stackedTargetSideDp
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
            assertTrue(sobra >= FLEET_HEADER_DP + MIN_FLEET_BOARD_DP, "frota sem altura minima em ${w}x$h: $sobra")
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

    // ---- #66: alvo empilhado a partir da altura MEDIDA da area abaixo do topo ----

    private val dobravelAreas = listOf(464f, 560f, 664f) // 820x700, intermediaria e 820x900 (menos insets e topo)

    @Test
    fun dobravelClassicoDeixaDuzentosDpDeTabuleiroNaFrota() {
        val content = stackedContentWidthDp(820f)
        dobravelAreas.forEach { area ->
            val alvo = stackedTargetSideDp(content, area, phone = false, tactical = false)
            val tabuleiroDaFrota = area - BATTLE_CALLOUT_DP - alvo - FLEET_HEADER_DP
            if (alvo > STACKED_MIN_TARGET_DP) {
                assertTrue(tabuleiroDaFrota >= MIN_FLEET_BOARD_DP, "frota com $tabuleiroDaFrota dp em area $area")
            } else {
                // preso no piso: a area e baixa demais, o alvo nao encolhe mais
                assertEquals(STACKED_MIN_TARGET_DP, alvo)
            }
        }
        // 664 dp (820x900) nao toca no piso: a frota tem 200 dp ou mais de tabuleiro
        val alvo900 = stackedTargetSideDp(content, 664f, phone = false, tactical = false)
        assertTrue(664f - BATTLE_CALLOUT_DP - alvo900 - FLEET_HEADER_DP >= MIN_FLEET_BOARD_DP)
    }

    @Test
    fun dobravelTaticoDeixaAsHabilidadesInteirasQuandoCabe() {
        val content = stackedContentWidthDp(820f)
        dobravelAreas.forEach { area ->
            val alvo = stackedTargetSideDp(content, area, phone = false, tactical = true)
            val faixa = area - BATTLE_CALLOUT_DP - alvo
            if (alvo > STACKED_MIN_TARGET_DP) {
                assertTrue(faixa >= ABILITY_STRIP_MIN_DP, "faixa de $faixa dp em area $area")
            } else {
                assertEquals(STACKED_MIN_TARGET_DP, alvo)
            }
        }
        // com 664 dp a faixa inteira cabe sem rolar
        val alvo = stackedTargetSideDp(content, 664f, phone = false, tactical = true)
        assertTrue(664f - BATTLE_CALLOUT_DP - alvo >= ABILITY_STRIP_MIN_DP)
    }

    @Test
    fun celularFicaComALarguraUtilSemMudar() {
        assertEquals(380f, stackedTargetSideDp(stackedContentWidthDp(412f), 300f, phone = true, tactical = true))
        assertEquals(328f, stackedTargetSideDp(stackedContentWidthDp(360f), 500f, phone = true, tactical = false))
    }

    @Test
    fun alvoEmpilhadoNuncaPassaDaLarguraNemFicaNegativo() {
        listOf(0f, 50f, 300f, 464f, 900f, 4000f).forEach { area ->
            listOf(true, false).forEach { tactical ->
                val alvo = stackedTargetSideDp(568f, area, phone = false, tactical = tactical)
                assertTrue(alvo <= 568f, "passou da largura em area $area")
                assertTrue(alvo >= STACKED_MIN_TARGET_DP, "abaixo do piso em area $area")
            }
        }
        assertEquals(0f, stackedTargetSideDp(0f, 0f, phone = false, tactical = false))
        assertEquals(0f, stackedTargetSideDp(-5f, 100f, phone = true, tactical = false))
        // largura menor que o piso: nao estica alem da largura
        assertEquals(100f, stackedTargetSideDp(100f, 0f, phone = false, tactical = false))
    }

    @Test
    fun arranjoNaoMudaNoDobravel() {
        assertEquals(BoardArrangement.STACKED, boardLayout(820f, 700f).arrangement)
        assertEquals(BoardArrangement.STACKED, boardLayout(820f, 900f).arrangement)
        assertEquals(BoardArrangement.SIDE_BY_SIDE, boardLayout(1280f, 800f).arrangement)
    }
}
