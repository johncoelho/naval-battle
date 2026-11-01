package br.com.navalbattle.ui

/** Como a batalha arruma o tabuleiro alvo e a frota própria. */
enum class BoardArrangement {
    /** Um sobre o outro, como no celular em retrato. */
    STACKED,

    /** Lado a lado, para tablet e dobrável aberto em paisagem. */
    SIDE_BY_SIDE
}

/** Decisão de layout da batalha: o arranjo e o lado (dp) do maior tabuleiro que cabe. */
data class BoardLayout(val arrangement: BoardArrangement, val boardSideDp: Float) {
    val sideBySide: Boolean get() = arrangement == BoardArrangement.SIDE_BY_SIDE
}

/** Abaixo disso (largura em dp) a batalha nunca vai lado a lado: celular e tablet em retrato. */
internal const val SIDE_BY_SIDE_MIN_WIDTH_DP = 840f

/** Largura máxima do conteúdo da batalha lado a lado: acima disso só sobra margem. */
internal const val SIDE_BY_SIDE_MAX_WIDTH_DP = 1100f

/** Altura que o topo da batalha (barra, relógio, rótulos) toma antes dos tabuleiros. */
internal const val BATTLE_CHROME_DP = 180f

/** Altura do anúncio do último tiro, abaixo (ou acima) do alvo. */
internal const val BATTLE_CALLOUT_DP = 56f

/** Altura mínima do TABULEIRO da frota própria quando os tabuleiros ficam empilhados (#66). */
internal const val MIN_FLEET_BOARD_DP = 200f

/**
 * Altura do cabeçalho do `OwnFleetPanel` (gap, rótulo, contagem, gap, folga de baixo) antes do tabuleiro.
 * Somado do código: 6 + 14 + 2 + 16 + 6 + 4 = 48, arredondado para 50. O QA confere a medida real no BIG-11.
 */
internal const val FLEET_HEADER_DP = 50f

/**
 * Altura da `AbilityColumn` sem rolar: título (14) + gap (8) + 4 linhas de 75 (botão 48 + 3 + rótulo 14 + 10 de folga)
 * + dica (14), arredondado para 340. O QA confere a medida real no BIG-11.
 */
internal const val ABILITY_STRIP_MIN_DP = 340f

/** Menor lado do alvo empilhado, mesmo em janela muito baixa. */
internal const val STACKED_MIN_TARGET_DP = 160f

/** Altura do posicionamento fora o tabuleiro: barra, dica, legenda mínima, dois botões e "encerrar". */
internal const val PLACEMENT_RESERVED_DP = 330f

/** Menor tabuleiro do posicionamento, mesmo em janela muito baixa. */
internal const val PLACEMENT_MIN_BOARD_DP = 160f

/** Abaixo desta largura de janela (dp) é celular: os tabuleiros ocupam a largura útil, sem reservas. */
private const val PHONE_MAX_WIDTH_DP = 600f

/** Margem horizontal do posicionamento (20 dp de cada lado), para comparar com a largura da janela. */
private const val PLACEMENT_MARGIN_DP = 40f

private const val SCREEN_PADDING_DP = 32f
private const val BOARD_GAP_DP = 16f

/** Mesmo valor de `Naval.contentMaxWidth`, em dp puro para a função continuar sem Compose. */
private const val STACKED_MAX_WIDTH_DP = 600f

/**
 * Escolhe o arranjo dos tabuleiros pela janela (em dp, não pelo aparelho: janela dividida e
 * dobrável mudam de tamanho na hora). Lado a lado só quando a janela é larga **e** mais larga
 * que alta; senão um tabuleiro sobre o outro. O lado devolvido nunca passa da altura útil
 * (`alturaDp` menos o topo da batalha), então uma tela baixa não gera tabuleiro maior que a tela.
 */
fun boardLayout(widthDp: Float, heightDp: Float): BoardLayout {
    val usableHeight = (heightDp - BATTLE_CHROME_DP).coerceAtLeast(0f)
    // empilhado: o anúncio e uma altura mínima da frota própria saem da conta do alvo
    val stackedHeight = (usableHeight - BATTLE_CALLOUT_DP - FLEET_HEADER_DP - MIN_FLEET_BOARD_DP).coerceAtLeast(0f)
    val wide = widthDp >= SIDE_BY_SIDE_MIN_WIDTH_DP && widthDp >= heightDp * 1.2f
    return if (wide) {
        val content = minOf(widthDp, SIDE_BY_SIDE_MAX_WIDTH_DP) - SCREEN_PADDING_DP
        BoardLayout(BoardArrangement.SIDE_BY_SIDE, minOf((content - BOARD_GAP_DP) / 2f, usableHeight))
    } else {
        val content = minOf(widthDp, STACKED_MAX_WIDTH_DP) - SCREEN_PADDING_DP
        // celular (< 600 dp): a largura útil, como sempre; a reserva de anúncio e frota é de tela grande
        val side = if (widthDp < PHONE_MAX_WIDTH_DP) content else minOf(content, stackedHeight)
        BoardLayout(BoardArrangement.STACKED, side)
    }
}

/** Largura útil do conteúdo empilhado (janela limitada a 600 dp, menos a margem), como em [boardLayout]. */
internal fun stackedContentWidthDp(widthDp: Float): Float = minOf(widthDp, STACKED_MAX_WIDTH_DP) - SCREEN_PADDING_DP

/**
 * Lado do alvo no arranjo empilhado, a partir da altura **medida** da área abaixo do topo da batalha (#66).
 * Sobra para a faixa de frota/habilidades o que o alvo e o anúncio não tomam; o alvo encolhe até a faixa
 * ter o mínimo (frota: cabeçalho + 200 dp de tabuleiro; tático: o maior entre isso e as habilidades inteiras).
 * Celular (< 600 dp): a largura útil, sem mudança. Nunca passa de [contentWidthDp], nunca abaixo do piso
 * (a não ser que a própria largura seja menor) e nunca negativo.
 */
fun stackedTargetSideDp(contentWidthDp: Float, areaHeightDp: Float, phone: Boolean, tactical: Boolean): Float {
    val content = contentWidthDp.coerceAtLeast(0f)
    if (phone) return content
    val fleetMin = FLEET_HEADER_DP + MIN_FLEET_BOARD_DP
    val stripMin = if (tactical) maxOf(fleetMin, ABILITY_STRIP_MIN_DP) else fleetMin
    val fit = areaHeightDp.coerceAtLeast(0f) - BATTLE_CALLOUT_DP - stripMin
    return minOf(content, maxOf(fit, STACKED_MIN_TARGET_DP))
}

/**
 * Lado do tabuleiro do posicionamento: o da largura útil, mas nunca tão grande que empurre a legenda,
 * os botões e o Confirmar para fora da tela (tablet em paisagem). [widthDp] e [heightDp] são a área já
 * sem barras do sistema e sem a margem da tela.
 */
fun placementBoardSideDp(widthDp: Float, heightDp: Float): Float =
    // celular (< 600 dp): a largura útil, como sempre; o limite pela altura é de tela grande
    if (widthDp + PLACEMENT_MARGIN_DP < PHONE_MAX_WIDTH_DP) widthDp
    else minOf(widthDp, maxOf(heightDp - PLACEMENT_RESERVED_DP, PLACEMENT_MIN_BOARD_DP))
