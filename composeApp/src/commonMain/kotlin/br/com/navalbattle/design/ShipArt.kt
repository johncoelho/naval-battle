package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.navalbattle.game.ShipClass

/**
 * Luz da cena: vem de cima e da esquerda. Todo relevo do desenho — casco, convés,
 * superestrutura, torres — é sombreado por essa mesma convenção, e é isso que faz a
 * vista de topo, que é plana, ler como volume.
 */
private const val LIGHT_DY = -1.1f
private const val LIGHT_DX = -0.9f

/** Clareia em direção ao branco. [t] de 0 (cor original) a 1 (branco). */
private fun Color.lit(t: Float) = Color(
    red + (1f - red) * t,
    green + (1f - green) * t,
    blue + (1f - blue) * t,
    alpha
)

/** Escurece em direção ao preto. */
private fun Color.shaded(t: Float) = Color(red * (1f - t), green * (1f - t), blue * (1f - t), alpha)

/**
 * Cada classe é desenhada em um viewBox de (tamanho * 50) x 50 — vista de topo,
 * proa à direita, quilha em y = 25.
 *
 * A boca fica em torno de 1/10 do comprimento, como num navio de verdade: o casco
 * ocupa só a faixa central da célula, e a folga que sobra nas laterais é o que deixa
 * lugar para escaler e reparo antiaéreo sem nada vazar para a célula vizinha — é isso
 * que mantém duas embarcações encostadas sem sobreposição. Mastro e verga ficam sempre
 * dentro do contorno do casco (#21: riscos sobre a água liam como falha de desenho).
 */
/**
 * Relógio da animação (segundos) do navio sendo desenhado agora — nulo desenha parado.
 * Só a frota da tela inicial anima (torres girando, caças decolando e pousando); no
 * tabuleiro de batalha os navios ficam parados para não distrair do jogo.
 */
private var animTime: Float? = null

fun DrawScope.drawShip(
    type: ShipClass,
    center: Offset,
    lengthPx: Float,
    thicknessPx: Float,
    vertical: Boolean,
    skin: Skin,
    alpha: Float = 1f,
    animSeconds: Float? = null
) {
    animTime = animSeconds
    val paint = skin.paint
    val line = skin.fleet
    val vbW = type.size * 50f
    val vbH = 50f
    withTransform({
        if (vertical) rotate(90f, center)
        translate(center.x - lengthPx / 2f, center.y - thicknessPx / 2f)
        scale(lengthPx / vbW, thicknessPx / vbH, pivot = Offset.Zero)
        // a boca da linha de construção afina ou alarga o casco em torno da quilha
        if (line.beam != 1f) scale(1f, line.beam, pivot = Offset(0f, 25f))
    }) {
        // navegando (só na tela inicial): esteira na popa e onda de proa abrindo para trás
        animTime?.let { drawWake(type, it, alpha) }
        drawProw(type, line, paint, alpha)
        // a superestrutura da linha (torre e chaminés) é desenhada dentro de cada classe,
        // no lugar da ilha padrão: substitui, nunca empilha por cima (#21)
        when (type) {
            ShipClass.CARRIER -> drawCarrier(paint, alpha)
            ShipClass.BATTLESHIP -> drawBattleship(line, paint, alpha)
            ShipClass.CRUISER -> drawCruiser(line, paint, alpha)
            ShipClass.SUBMARINE -> drawSubmarine(paint, alpha)
            ShipClass.DESTROYER -> drawDestroyer(line, paint, alpha)
        }
        if (type == ShipClass.CARRIER) animTime?.let { drawFlightOps(it, paint, alpha) }
    }
    animTime = null
}

/**
 * Bandeira pintada no convés da popa, como o pavilhão que os navios içam ali. Entra
 * junto com o chapeamento — por cima do convés e por baixo das torres, para não cobrir
 * os canos da torre de ré; em célula pequena vira um ponto de cor, o bastante para
 * separar duas frotas de casco parecido.
 */
private fun DrawScope.drawEnsign(ensign: Ensign, sx: Float, half: Float, a: Float) {
    val h = half * 1.05f
    val w = h * 1.5f
    val x0 = sx + 5f
    val y0 = 25f - h / 2f
    val r = Offset(x0, y0)
    val sz = Size(w, h)
    val cx = x0 + w / 2f
    val cy = 25f
    when (ensign) {
        Ensign.BRAZIL -> {
            drawRect(Color(0xFF1E9A3C), r, sz, alpha = a)
            drawPath(Path().apply {
                moveTo(x0 + w * 0.1f, cy); lineTo(cx, y0 + h * 0.1f)
                lineTo(x0 + w * 0.9f, cy); lineTo(cx, y0 + h * 0.9f); close()
            }, Color(0xFFFFD83D), alpha = a)
            drawCircle(Color(0xFF1F3C8C), radius = h * 0.22f, center = Offset(cx, cy), alpha = a)
        }
        Ensign.JAPAN -> {
            drawRect(Color(0xFFF2F2EE), r, sz, alpha = a)
            drawCircle(Color(0xFFD02A20), radius = h * 0.28f, center = Offset(cx, cy), alpha = a)
        }
        Ensign.USA -> {
            val stripes = 7
            repeat(stripes) { i ->
                drawRect(
                    if (i % 2 == 0) Color(0xFFC0272D) else Color(0xFFF2F2EE),
                    Offset(x0, y0 + h * i / stripes), Size(w, h / stripes + 0.05f), alpha = a
                )
            }
            drawRect(Color(0xFF243A73), r, Size(w * 0.42f, h * 4f / stripes), alpha = a)
        }
        Ensign.UK -> clipRect(x0, y0, x0 + w, y0 + h) {
            drawRect(Color(0xFF1F3A7A), r, sz, alpha = a)
            val diag = h * 0.16f
            drawLine(Color(0xFFF2F2EE), r, Offset(x0 + w, y0 + h), diag, alpha = a)
            drawLine(Color(0xFFF2F2EE), Offset(x0, y0 + h), Offset(x0 + w, y0), diag, alpha = a)
            drawRect(Color(0xFFF2F2EE), Offset(cx - h * 0.15f, y0), Size(h * 0.3f, h), alpha = a)
            drawRect(Color(0xFFF2F2EE), Offset(x0, cy - h * 0.15f), Size(w, h * 0.3f), alpha = a)
            drawRect(Color(0xFFC8102E), Offset(cx - h * 0.08f, y0), Size(h * 0.16f, h), alpha = a)
            drawRect(Color(0xFFC8102E), Offset(x0, cy - h * 0.08f), Size(w, h * 0.16f), alpha = a)
        }
        Ensign.PORTUGAL -> {
            drawRect(Color(0xFF046A38), r, Size(w * 0.4f, h), alpha = a)
            drawRect(Color(0xFFDA291C), Offset(x0 + w * 0.4f, y0), Size(w * 0.6f, h), alpha = a)
            drawCircle(Color(0xFFFFE900), radius = h * 0.2f, center = Offset(x0 + w * 0.4f, cy), alpha = a)
        }
    }
    drawRect(Color.Black.copy(alpha = 0.45f * a), r, sz, style = Stroke(width = 0.6f))
}

// ------------------------------------------------------------ medidas por classe

private fun bowX(type: ShipClass): Float = type.size * 50f - 5f

private fun sternX(type: ShipClass): Float = 5f

/** Meia-boca do casco. Perto de 1/20 do comprimento dos dois lados da quilha. */
internal fun hullHalf(type: ShipClass): Float = when (type) {
    ShipClass.CARRIER -> 11.5f
    ShipClass.BATTLESHIP -> 10.8f
    ShipClass.CRUISER -> 8.8f
    ShipClass.SUBMARINE -> 7.6f
    ShipClass.DESTROYER -> 6.8f
}

/** Quanto do comprimento é consumido pelo afilamento da proa. */
private fun bowRun(type: ShipClass): Float = when (type) {
    ShipClass.CARRIER -> 46f
    ShipClass.BATTLESHIP -> 44f
    ShipClass.CRUISER -> 36f
    ShipClass.SUBMARINE -> 34f
    ShipClass.DESTROYER -> 26f
}

/**
 * Silhueta de casco: proa afilada em curva, corpo paralelo e popa arredondada. É a
 * mesma construção para todas as classes — o que muda é boca e afilamento.
 */
private fun hullPath(
    sx: Float,
    bx: Float,
    half: Float,
    run: Float,
    sternRound: Float = 6f
): Path = Path().apply {
    moveTo(bx, 25f)
    cubicTo(bx - run * 0.30f, 25f - half * 0.36f, bx - run * 0.72f, 25f - half * 0.88f, bx - run, 25f - half)
    lineTo(sx + sternRound, 25f - half)
    cubicTo(sx + sternRound * 0.3f, 25f - half, sx, 25f - half * 0.72f, sx, 25f - half * 0.34f)
    lineTo(sx, 25f + half * 0.34f)
    cubicTo(sx, 25f + half * 0.72f, sx + sternRound * 0.3f, 25f + half, sx + sternRound, 25f + half)
    lineTo(bx - run, 25f + half)
    cubicTo(bx - run * 0.72f, 25f + half * 0.88f, bx - run * 0.30f, 25f + half * 0.36f, bx, 25f)
    close()
}

/** Ponta da proa: fica atrás do casco, prolongando a linha da embarcação. */
private fun DrawScope.drawProw(type: ShipClass, line: FleetLine, l: Paint, a: Float) {
    if (line.prow == Prow.PADRAO) return
    val bow = bowX(type)
    val half = hullHalf(type)
    when (line.prow) {
        Prow.CLIPPER -> {
            val p = Path().apply {
                moveTo(bow - 8f, 25f - half * 0.62f)
                lineTo(bow + half * 1.1f, 25f)
                lineTo(bow - 8f, 25f + half * 0.62f)
                close()
            }
            drawPath(p, l.hull, alpha = a)
            drawPath(p, l.dark, alpha = a, style = Stroke(0.9f))
        }

        Prow.BULBOSA -> {
            drawCircle(l.hull, radius = half * 0.5f, center = Offset(bow + half * 0.22f, 25f), alpha = a)
            drawCircle(l.dark, radius = half * 0.5f, center = Offset(bow + half * 0.22f, 25f), alpha = a * 0.9f, style = Stroke(0.9f))
        }

        Prow.FACETADA -> {
            val p = Path().apply {
                moveTo(bow - 14f, 25f - half)
                lineTo(bow + half * 0.8f, 25f - half * 0.2f)
                lineTo(bow + half * 0.8f, 25f + half * 0.2f)
                lineTo(bow - 14f, 25f + half)
                close()
            }
            drawPath(p, l.hull, alpha = a)
            drawPath(p, l.dark, alpha = a, style = Stroke(0.9f))
            drawLine(l.dark.copy(alpha = 0.75f), Offset(sternX(type) + 6f, 25f - half * 0.5f), Offset(bow - 10f, 25f - half * 0.22f), 0.8f, alpha = a)
            drawLine(l.dark.copy(alpha = 0.75f), Offset(sternX(type) + 6f, 25f + half * 0.5f), Offset(bow - 10f, 25f + half * 0.22f), 0.8f, alpha = a)
        }

        Prow.PADRAO -> Unit
    }
}

/** Chaminé no eixo do navio: posição no comprimento e os dois raios da elipse. */
internal data class FunnelSpot(val x: Float, val rx: Float, val ry: Float)

/**
 * O que vai no meio do navio, decidido antes de desenhar (separado do desenho para
 * poder ser testado): qual ilha ([bridge] PADRAO é a da classe; as outras são a torre
 * da linha), onde ela fica e a pegada dela, o mastro (no máximo um, com a verga dentro
 * do casco) e as chaminés.
 *
 * Regra (#21): a superestrutura da linha **substitui** a da classe, centrada na ilha
 * da classe e sem passar da pegada dela; as chaminés da linha, quando ela tem, também
 * substituem as da classe. Nada é desenhado por cima de outra peça equivalente.
 */
internal data class SuperstructurePlan(
    val bridge: Tower,
    val bridgeCx: Float,
    /** Comprimento da pegada da ilha (o bloco externo da ilha da classe). */
    val bridgeLength: Float,
    /** Meia-largura da pegada da ilha, a partir da quilha. */
    val bridgeHalfBeam: Float,
    /** Posição do mastro no comprimento; nulo quando não há mastro. */
    val mastX: Float?,
    /** Quanto a verga avança para cada bordo, a partir da quilha. */
    val yardHalf: Float,
    val funnels: List<FunnelSpot>
) {
    val mastCount: Int get() = if (mastX == null) 0 else 1
}

/** Verga: esta fração da meia-boca, para ficar sempre dentro do convés. */
private const val YARD_FRACTION = 0.62f

internal fun superstructurePlan(type: ShipClass, line: FleetLine): SuperstructurePlan {
    val bx = bowX(type)
    val half = hullHalf(type)
    val yard = half * YARD_FRACTION
    return when (type) {
        // porta-aviões: a ilha a boreste é sempre a da classe; torre ou chaminé da linha
        // no meio do convés de voo cairia em cima da pista (#21)
        ShipClass.CARRIER -> {
            val islandX = bx - 74f
            SuperstructurePlan(
                Tower.PADRAO, islandX + 13f, 26f, 3.75f, null, 0f,
                listOf(FunnelSpot(islandX + 20f, 2.6f, 2.1f))
            )
        }

        // submarino: só a vela; a linha de casco não muda a superestrutura dele
        ShipClass.SUBMARINE -> SuperstructurePlan(
            Tower.PADRAO, bx * 0.44f, 18f, half * 0.6f, null, 0f, emptyList()
        )

        ShipClass.BATTLESHIP -> {
            val cx = bx * 0.5f
            planFor(
                line, cx, 32f, half * 0.68f, yard,
                classMast = cx + 12f,
                classFunnels = listOf(
                    FunnelSpot(cx + 20f, half * 0.5f, half * 0.4f),
                    FunnelSpot(cx - 26f, half * 0.4f, half * 0.32f)
                ),
                pairFunnels = listOf(
                    FunnelSpot(cx + 20f, half * 0.5f, half * 0.4f),
                    FunnelSpot(cx - 26f, half * 0.5f, half * 0.4f)
                ),
                singleFunnel = FunnelSpot(cx - 26f, half * 0.6f, half * 0.46f)
            )
        }

        ShipClass.CRUISER -> {
            val cx = bx * 0.44f
            planFor(
                line, cx, 22f, half * 0.62f, yard,
                classMast = cx + 9f,
                classFunnels = listOf(
                    FunnelSpot(cx - 14f, half * 0.46f, half * 0.37f),
                    FunnelSpot(cx - 26f, half * 0.4f, half * 0.32f)
                ),
                pairFunnels = listOf(
                    FunnelSpot(cx - 14f, half * 0.46f, half * 0.37f),
                    FunnelSpot(cx - 25f, half * 0.46f, half * 0.37f)
                ),
                singleFunnel = FunnelSpot(cx - 17f, half * 0.6f, half * 0.46f)
            )
        }

        ShipClass.DESTROYER -> {
            val cx = bx * 0.5f
            planFor(
                line, cx, 16f, half * 0.64f, yard,
                classMast = cx + 6f,
                classFunnels = listOf(
                    FunnelSpot(cx - 11f, half * 0.44f, half * 0.35f),
                    FunnelSpot(cx - 21f, half * 0.38f, half * 0.3f)
                ),
                pairFunnels = listOf(
                    FunnelSpot(cx - 11f, half * 0.44f, half * 0.35f),
                    FunnelSpot(cx - 20f, half * 0.44f, half * 0.35f)
                ),
                singleFunnel = FunnelSpot(cx - 13f, half * 0.6f, half * 0.46f)
            )
        }
    }
}

/**
 * Ilha de encouraçado, cruzador e destróier. Padrão: a ilha e o mastro da classe.
 * Linha com torre própria: a torre no mesmo lugar e na mesma pegada; pagode e bloco
 * levam um mastro a ré, a torre facetada (furtiva) não tem mastro. Linha com chaminés
 * próprias ([pairFunnels] para duas, [singleFunnel] para uma, mais larga) troca as da
 * classe; linha sem chaminé fica com as da classe.
 */
private fun planFor(
    line: FleetLine,
    cx: Float,
    length: Float,
    halfBeam: Float,
    yard: Float,
    classMast: Float,
    classFunnels: List<FunnelSpot>,
    pairFunnels: List<FunnelSpot>,
    singleFunnel: FunnelSpot
): SuperstructurePlan {
    val mast = when (line.tower) {
        Tower.PADRAO -> classMast
        Tower.PAGODE -> cx - length * 0.42f
        Tower.BLOCO -> cx - length * 0.38f
        Tower.FACETADA -> null
    }
    val funnels = when (line.funnels) {
        0 -> classFunnels
        1 -> listOf(singleFunnel)
        else -> pairFunnels.take(line.funnels)
    }
    return SuperstructurePlan(line.tower, cx, length, halfBeam, mast, yard, funnels)
}

/** Ilha padrão de cada classe: blocos da ponte e radar (mastro e chaminés vêm do plano). */
private fun DrawScope.classIsland(type: ShipClass, cx: Float, half: Float, l: Paint, a: Float) {
    when (type) {
        ShipClass.BATTLESHIP -> {
            deckBlock(cx - 16f, 25f - half * 0.68f, 32f, half * 1.36f, l.dark, a, 2.2f)
            deckBlock(cx - 10f, 25f - half * 0.42f, 18f, half * 0.84f, l.deck, a, 2.8f)
            radar(cx - 1f, half * 0.2f, l, a)
        }

        ShipClass.CRUISER -> {
            deckBlock(cx - 11f, 25f - half * 0.62f, 22f, half * 1.24f, l.dark, a, 2f)
            deckBlock(cx - 6f, 25f - half * 0.38f, 12f, half * 0.76f, l.deck, a, 2.5f)
            radar(cx, half * 0.19f, l, a)
        }

        ShipClass.DESTROYER -> {
            deckBlock(cx - 8f, 25f - half * 0.64f, 16f, half * 1.28f, l.dark, a, 1.8f)
            deckBlock(cx - 4f, 25f - half * 0.4f, 8f, half * 0.8f, l.deck, a, 2.2f)
        }

        ShipClass.CARRIER, ShipClass.SUBMARINE -> Unit
    }
}

/** Torre característica da linha, ocupando só a pegada da ilha da classe. */
private fun DrawScope.lineTower(p: SuperstructurePlan, l: Paint, a: Float) {
    val cx = p.bridgeCx
    val len = p.bridgeLength
    val hb = p.bridgeHalfBeam
    val aft = cx - len / 2f
    when (p.bridge) {
        Tower.PAGODE -> {
            // caixas empilhadas afinando para a proa
            for (i in 0 until 3) {
                val w = len * (1f - i * 0.24f)
                val h = hb * 2f * (1f - i * 0.26f)
                deckBlock(aft + i * len * 0.16f, 25f - h / 2f, w, h, l.dark, a, 1.6f + i * 0.8f)
            }
        }

        Tower.BLOCO -> {
            deckBlock(aft, 25f - hb, len, hb * 2f, l.dark, a, 2f)
            deckBlock(aft + len * 0.12f, 25f - hb * 0.59f, len * 0.76f, hb * 1.18f, l.deck, a * 0.9f, 2.6f)
        }

        Tower.FACETADA -> {
            val path = Path().apply {
                moveTo(aft, 25f - hb)
                lineTo(cx + len / 2f, 25f - hb * 0.54f)
                lineTo(cx + len / 2f, 25f + hb * 0.54f)
                lineTo(aft, 25f + hb)
                close()
            }
            translate(-LIGHT_DX * 1.8f, -LIGHT_DY * 1.8f) {
                drawPath(path, Color.Black.copy(alpha = 0.3f * a))
            }
            drawPath(path, l.dark, alpha = a)
            drawPath(path, l.trim.copy(alpha = 0.5f), alpha = a, style = Stroke(0.7f))
        }

        Tower.PADRAO -> Unit
    }
}

/**
 * Meio do navio: a ilha (a da classe ou a torre da linha, nunca as duas), o mastro e
 * as chaminés, nessa ordem, antes dos reparos antiaéreos e escaleres, que ficam por cima.
 */
private fun DrawScope.bridge(type: ShipClass, line: FleetLine, l: Paint, a: Float) {
    val plan = superstructurePlan(type, line)
    val half = hullHalf(type)
    if (plan.bridge == Tower.PADRAO) classIsland(type, plan.bridgeCx, half, l, a) else lineTower(plan, l, a)
    plan.mastX?.let { mastAndYard(it, plan.yardHalf, half, l, a) }
    plan.funnels.forEach { funnel(it.x, it.rx, it.ry, l, a) }
}

/**
 * Casco com volume: sombra projetada na água, gradiente de bordo a bordo, camuflagem
 * recortada no contorno e um fio de luz na amurada iluminada.
 */
private fun DrawScope.hull(path: Path, paint: Paint, alpha: Float, stroke: Float = 1.1f) {
    // sombra na água, deslocada no sentido contrário à luz
    translate(-LIGHT_DX * 2.4f, -LIGHT_DY * 2.4f) {
        drawPath(path, Color.Black.copy(alpha = 0.28f * alpha))
    }

    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            0.00f to paint.hull.lit(0.34f),
            0.16f to paint.hull.lit(0.12f),
            0.52f to paint.hull,
            0.82f to paint.hull.shaded(0.22f),
            1.00f to paint.hull.shaded(0.44f)
        ),
        alpha = alpha
    )
    if (paint.camo != Camo.LISA) {
        clipPath(path) { drawCamo(paint, alpha) }
    }
    // amurada iluminada: um traço claro só na borda que recebe a luz
    clipPath(path) {
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                0.00f to Color.White.copy(alpha = 0.42f * alpha),
                0.20f to Color.Transparent
            ),
            style = Stroke(width = stroke * 2.4f)
        )
    }
    drawPath(path, paint.dark.shaded(0.2f), alpha = alpha, style = Stroke(width = stroke))
}

/** Convés interno: a chapa em que a miudeza é montada, mais escura que a amurada. */
private fun DrawScope.deck(path: Path, l: Paint, a: Float, strength: Float = 0.9f) {
    drawPath(path, l.deck, alpha = a * strength)
}

/** Fiadas de chapa correndo no sentido do comprimento — miudeza que dá escala. */
private fun DrawScope.plating(sx: Float, bx: Float, half: Float, l: Paint, a: Float) {
    val c = l.dark.copy(alpha = 0.35f * a)
    listOf(-0.56f, 0f, 0.56f).forEach { f ->
        drawLine(c, Offset(sx + 6f, 25f + half * f), Offset(bx - 10f, 25f + half * f), 0.5f)
    }
    l.ensign?.let { drawEnsign(it, sx, half, a) }
}

/**
 * Bloco de superestrutura em falso relevo: sombra no lado escuro, corpo, e a face de
 * cima puxada na direção da luz. É o que dá altura ao que é um retângulo chapado.
 */
private fun DrawScope.deckBlock(
    x: Float, y: Float, w: Float, h: Float,
    color: Color, alpha: Float, height: Float = 1.6f
) {
    // lado na sombra
    drawRect(
        color = Color.Black.copy(alpha = 0.34f * alpha),
        topLeft = Offset(x - LIGHT_DX * height, y - LIGHT_DY * height),
        size = Size(w, h)
    )
    // corpo
    drawRect(color.shaded(0.18f), topLeft = Offset(x, y), size = Size(w, h), alpha = alpha)
    // face superior, na direção da luz
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.lit(0.34f),
            1f to color.lit(0.05f),
            startY = y + LIGHT_DY * height,
            endY = y + h + LIGHT_DY * height
        ),
        topLeft = Offset(x + LIGHT_DX * height, y + LIGHT_DY * height),
        size = Size(w, h),
        alpha = alpha
    )
    drawRect(
        color = color.shaded(0.55f),
        topLeft = Offset(x + LIGHT_DX * height, y + LIGHT_DY * height),
        size = Size(w, h),
        alpha = alpha * 0.9f,
        style = Stroke(0.5f)
    )
}

/**
 * Torre de artilharia: barbeta redonda, casamata por cima e canos finos deitados no
 * eixo do navio. [dir] é +1 para quem aponta à proa e -1 para as torres de ré, de
 * modo que nenhum cano ultrapasse a ponta do casco.
 */
private fun DrawScope.turret(
    cx: Float, r: Float, barrels: Int, barrelLen: Float, dir: Float,
    l: Paint, a: Float
) {
    // barbeta
    drawCircle(
        color = Color.Black.copy(alpha = 0.3f * a),
        radius = r,
        center = Offset(cx - LIGHT_DX * 1.3f, 25f - LIGHT_DY * 1.3f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(l.dark.lit(0.44f), l.dark, l.dark.shaded(0.34f)),
            center = Offset(cx + LIGHT_DX * r * 0.45f, 25f + LIGHT_DY * r * 0.45f),
            radius = r * 1.5f
        ),
        radius = r,
        center = Offset(cx, 25f),
        alpha = a
    )
    // animada: a torre varre devagar de um lado a outro, cada uma no seu tempo
    val sweep = animTime?.let { t ->
        val phase = cx * 0.37f
        val slow = kotlin.math.sin(t * 0.45f + phase)
        // fica um tempo apontada e depois gira (curva achatada no meio)
        slow * kotlin.math.abs(slow) * 32f
    } ?: 0f
    rotate(sweep, Offset(cx, 25f)) {
        turretBody(cx, r, barrels, barrelLen, dir, l, a)
    }
}

private fun DrawScope.turretBody(
    cx: Float, r: Float, barrels: Int, barrelLen: Float, dir: Float,
    l: Paint, a: Float
) {
    // casamata: face de cima clara, base escura
    val hw = r * 0.95f
    val hh = r * 0.78f
    deckBlock(cx - hw * 0.7f, 25f - hh, hw * 1.5f, hh * 2f, l.dark, a, 1.4f)

    // canos, espaçados na boca da torre
    val step = if (barrels <= 1) 0f else (r * 1.05f) / (barrels - 1)
    val first = -(step * (barrels - 1)) / 2f
    for (i in 0 until barrels) {
        val y = 25f + first + step * i
        val x0 = cx + dir * hw * 0.8f
        val x1 = x0 + dir * barrelLen
        drawLine(Color.Black.copy(alpha = 0.32f * a), Offset(x0, y + 0.55f), Offset(x1, y + 0.55f), r * 0.24f, cap = StrokeCap.Round)
        drawLine(l.dark.shaded(0.1f), Offset(x0, y), Offset(x1, y), r * 0.24f, cap = StrokeCap.Round)
        drawLine(l.dark.lit(0.5f), Offset(x0, y - r * 0.06f), Offset(x1, y - r * 0.06f), r * 0.09f, alpha = a * 0.9f, cap = StrokeCap.Round)
    }
}

/** Reparo antiaéreo: poço redondo com a peça apontando para fora do costado. */
private fun DrawScope.aaTub(cx: Float, cy: Float, r: Float, out: Float, l: Paint, a: Float) {
    drawCircle(Color.Black.copy(alpha = 0.3f * a), radius = r, center = Offset(cx - LIGHT_DX * 0.9f, cy - LIGHT_DY * 0.9f))
    drawCircle(l.deck.lit(0.1f), radius = r, center = Offset(cx, cy), alpha = a)
    drawCircle(l.dark, radius = r * 0.48f, center = Offset(cx, cy), alpha = a)
    drawLine(l.dark.lit(0.35f), Offset(cx, cy), Offset(cx, cy + out * r * 1.7f), r * 0.3f, alpha = a, cap = StrokeCap.Round)
}

/**
 * Mastro visto de cima: o pé do mastro (pino com sombra) e uma verga transversal curta
 * e encorpada, com um fio de luz como o dos canos. A verga avança [yardHalf] para cada
 * bordo, sempre menos que a meia-boca: fica dentro do convés e nunca sai sobre a água
 * (as três linhas finas de antes passavam do costado e liam como falha de desenho, #21).
 */
private fun DrawScope.mastAndYard(cx: Float, yardHalf: Float, half: Float, l: Paint, a: Float) {
    val top = Offset(cx, 25f - yardHalf)
    val bottom = Offset(cx, 25f + yardHalf)
    val w = 1.2f
    val shadow = Offset(-LIGHT_DX * 0.6f, -LIGHT_DY * 0.6f)
    drawLine(Color.Black.copy(alpha = 0.32f * a), top + shadow, bottom + shadow, w, cap = StrokeCap.Round)
    drawLine(l.dark.shaded(0.1f), top, bottom, w, alpha = a, cap = StrokeCap.Round)
    val lit = Offset(LIGHT_DX * 0.3f, 0f)
    drawLine(l.trim, top + lit, bottom + lit, w * 0.38f, alpha = a * 0.9f, cap = StrokeCap.Round)
    // pé do mastro
    val r = half * 0.17f
    drawCircle(Color.Black.copy(alpha = 0.3f * a), radius = r, center = Offset(cx - LIGHT_DX * 0.8f, 25f - LIGHT_DY * 0.8f))
    drawCircle(l.dark.lit(0.3f), radius = r, center = Offset(cx, 25f), alpha = a)
    drawCircle(l.trim, radius = r * 0.45f, center = Offset(cx + LIGHT_DX * r * 0.3f, 25f + LIGHT_DY * r * 0.3f), alpha = a * 0.8f)
}

/** Chaminé: boca escura no meio de um anel claro. */
private fun DrawScope.funnel(cx: Float, rx: Float, ry: Float, l: Paint, a: Float) {
    drawCircle(Color.Black.copy(alpha = 0.3f * a), radius = rx, center = Offset(cx - LIGHT_DX * 1.2f, 25f - LIGHT_DY * 1.2f))
    withTransform({ scale(1f, ry / rx, pivot = Offset(cx, 25f)) }) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(l.dark.lit(0.4f), l.dark.shaded(0.1f)),
                center = Offset(cx + LIGHT_DX * rx * 0.4f, 25f + LIGHT_DY * rx * 0.4f),
                radius = rx * 1.4f
            ),
            radius = rx,
            center = Offset(cx, 25f),
            alpha = a
        )
        drawCircle(Color.Black.copy(alpha = 0.72f * a), radius = rx * 0.62f, center = Offset(cx, 25f))
    }
}

/** Radar de superfície: prato meio iluminado girando sobre a ponte. */
private fun DrawScope.radar(cx: Float, r: Float, l: Paint, a: Float) {
    drawCircle(Color.Black.copy(alpha = 0.28f * a), radius = r, center = Offset(cx - LIGHT_DX, 25f - LIGHT_DY))
    drawCircle(l.trim, radius = r, center = Offset(cx, 25f), alpha = a)
    drawCircle(l.dark, radius = r * 0.4f, center = Offset(cx, 25f), alpha = a)
}

/** Escaler estivado no costado. */
private fun DrawScope.boat(cx: Float, cy: Float, len: Float, l: Paint, a: Float) {
    withTransform({ scale(1f, 0.38f, pivot = Offset(cx, cy)) }) {
        drawCircle(l.deck.lit(0.15f), radius = len / 2f, center = Offset(cx, cy), alpha = a)
        drawCircle(l.dark, radius = len * 0.32f, center = Offset(cx, cy), alpha = a * 0.9f)
    }
}


/** Padrão de camuflagem, sempre recortado no contorno do casco. */
private fun DrawScope.drawCamo(l: Paint, a: Float) {
    val w = size.width
    when (l.camo) {
        Camo.LISA -> Unit

        Camo.DAZZLE -> {
            var x = -60f
            var i = 0
            while (x < w + 60f) {
                val slant = if (i % 2 == 0) 26f else -26f
                val band = if (i % 2 == 0) l.deck else l.dark
                val p = Path().apply {
                    moveTo(x, 0f)
                    lineTo(x + 13f, 0f)
                    lineTo(x + 13f + slant, 50f)
                    lineTo(x + slant, 50f)
                    close()
                }
                drawPath(p, band, alpha = a * 0.55f)
                x += 26f
                i++
            }
        }

        Camo.ESTILHACO -> {
            var x = 0f
            var i = 0
            while (x < w) {
                val up = i % 2 == 0
                val p = Path().apply {
                    moveTo(x, if (up) 0f else 50f)
                    lineTo(x + 34f, if (up) 14f else 36f)
                    lineTo(x + 60f, if (up) 0f else 50f)
                    lineTo(x + 60f, if (up) 22f else 28f)
                    lineTo(x, if (up) 26f else 24f)
                    close()
                }
                drawPath(p, if (up) l.dark else l.deck, alpha = a * 0.5f)
                x += 52f
                i++
            }
        }

        Camo.LISTRAS -> {
            drawRect(l.dark, topLeft = Offset(0f, 0f), size = Size(w, 12f), alpha = a * 0.55f)
            drawRect(l.deck, topLeft = Offset(0f, 20f), size = Size(w, 6f), alpha = a * 0.45f)
            drawRect(l.dark, topLeft = Offset(0f, 38f), size = Size(w, 12f), alpha = a * 0.55f)
        }

        Camo.DIGITAL -> {
            var x = 0f
            while (x < w) {
                var y = 0f
                var j = 0
                while (y < 50f) {
                    if (((x / 7f).toInt() + j) % 3 == 0) {
                        drawRect(l.dark, topLeft = Offset(x, y), size = Size(7f, 6f), alpha = a * 0.45f)
                    }
                    y += 6f
                    j++
                }
                x += 7f
            }
        }
    }
}

// ---------------------------------------------------------------- porta-aviões

private fun DrawScope.drawCarrier(l: Paint, a: Float) {
    val bx = bowX(ShipClass.CARRIER)
    val sx = sternX(ShipClass.CARRIER)
    val half = hullHalf(ShipClass.CARRIER)

    hull(hullPath(sx, bx, half, bowRun(ShipClass.CARRIER)), l, a)

    // convés de voo: mais largo que o casco, cantos cortados na proa
    val deckHalf = 17.5f
    val fd = Path().apply {
        moveTo(bx - 8f, 25f - deckHalf * 0.55f)
        lineTo(sx + 6f, 25f - deckHalf)
        lineTo(sx, 25f - deckHalf * 0.7f)
        lineTo(sx, 25f + deckHalf * 0.7f)
        lineTo(sx + 6f, 25f + deckHalf)
        lineTo(bx - 8f, 25f + deckHalf * 0.55f)
        cubicTo(bx - 2f, 25f + deckHalf * 0.3f, bx, 25f + 3f, bx, 25f)
        cubicTo(bx, 25f - 3f, bx - 2f, 25f - deckHalf * 0.3f, bx - 8f, 25f - deckHalf * 0.55f)
        close()
    }
    translate(-LIGHT_DX * 1.6f, -LIGHT_DY * 1.6f) { drawPath(fd, Color.Black.copy(alpha = 0.26f * a)) }
    drawPath(
        fd,
        brush = Brush.verticalGradient(
            0.00f to l.deck.lit(0.30f),
            0.42f to l.deck,
            1.00f to l.deck.shaded(0.34f)
        ),
        alpha = a
    )
    drawPath(fd, l.dark.shaded(0.15f), alpha = a, style = Stroke(0.9f))

    // área de pouso rebaixada e faixa central
    drawRect(l.dark, topLeft = Offset(sx + 8f, 25f - 6.5f), size = Size(bx - sx - 42f, 13f), alpha = a * 0.4f)
    var x = sx + 14f
    while (x < bx - 40f) {
        drawRect(l.trim, topLeft = Offset(x, 24.3f), size = Size(7f, 1.4f), alpha = a * 0.85f)
        x += 13f
    }
    // cabos de parada
    listOf(0f, 1f, 2f, 3f).forEach { i ->
        val cx = sx + 22f + i * 11f
        drawLine(l.trim.copy(alpha = 0.5f * a), Offset(cx, 25f - 6f), Offset(cx, 25f + 6f), 0.6f)
    }
    // elevadores
    drawRect(l.dark, topLeft = Offset(sx + 62f, 25f + 8f), size = Size(22f, 7f), alpha = a * 0.55f)
    drawRect(l.dark, topLeft = Offset(sx + 132f, 25f - 15f), size = Size(22f, 7f), alpha = a * 0.55f)

    // ilha a boreste, rente à borda do convés
    val islandX = bx - 74f
    deckBlock(islandX, 25f + 8.5f, 26f, 7.5f, l.dark, a, 2.2f)
    deckBlock(islandX + 3f, 25f + 10f, 9f, 4.5f, l.deck, a, 2.8f)
    superstructurePlan(ShipClass.CARRIER, FleetLine.STANDARD).funnels.forEach { funnel(it.x, it.rx, it.ry, l, a) }
    radar(islandX + 9f, 1.8f, l, a * 0.9f)

    // aeronaves estivadas no convés de voo
    listOf(0f, 1f, 2f, 3f).forEach { i ->
        carrierJet(sx + 34f + i * 21f, 25f - 12.5f, l, a)
    }
    carrierJet(bx - 34f, 25f + 12f, l, a)

    plating(sx, bx, half, l, a)
}

/**
 * Operação de voo (só animado): a cada 18s um caça acelera pela pista central e decola
 * pela proa, subindo (cresce e a sombra se afasta) até sumir; depois outro chega por
 * trás, desce e pousa, freando nos cabos de parada, e fica pronto para a próxima.
 */
private fun DrawScope.drawFlightOps(t: Float, l: Paint, a: Float) {
    val bx = bowX(ShipClass.CARRIER)
    val sx = sternX(ShipClass.CARRIER)
    val start = sx + 30f
    val cycle = 18f
    val p = (t % cycle) / cycle
    fun ease(u: Float) = u * u
    fun easeOut(u: Float) = 1f - (1f - u) * (1f - u)
    when {
        // decolagem: corre pela pista e sobe a partir da proa
        p < 0.30f -> {
            val u = p / 0.30f
            val x = start + (bx + 90f - start) * ease(u)
            val alt = ((x - (bx - 20f)) / 110f).coerceIn(0f, 1f)
            flyingJet(x, alt, a * (1f - ((u - 0.85f) / 0.15f).coerceIn(0f, 1f)), l)
        }
        // pouso: vem de trás descendo, toca o convés e freia nos cabos
        p in 0.55f..0.85f -> {
            val u = (p - 0.55f) / 0.30f
            val x = sx - 110f + (start + 110f - sx + 0f) * easeOut(u)
            val alt = ((sx + 14f - x) / 120f).coerceIn(0f, 1f)
            val fadeIn = (u / 0.15f).coerceIn(0f, 1f)
            flyingJet(x, alt, a * fadeIn, l)
        }
        // no convés, esperando a vez de decolar
        else -> flyingJet(start, 0f, a, l)
    }
}

/** Caça em voo ou no convés: [alt] 0 = rodando no convés, 1 = já no ar (maior, sombra longe). */
private fun DrawScope.flyingJet(x: Float, alt: Float, a: Float, l: Paint) {
    if (a <= 0.01f) return
    val sc = 1f + alt * 0.8f
    // sombra no mar/convés: se afasta conforme sobe
    withTransform({ translate(alt * 9f, alt * 7f) }) {
        withTransform({ scale(1f + alt * 0.2f, 1f + alt * 0.2f, Offset(x, 25f)) }) {
            jetShape(x, 25f).let { drawPath(it, Color.Black.copy(alpha = 0.28f * a * (1f - alt * 0.5f))) }
        }
    }
    withTransform({ scale(sc, sc, Offset(x, 25f)) }) {
        drawPath(jetShape(x, 25f), l.trim.lit(0.1f), alpha = a * 0.95f)
        drawCircle(l.dark, radius = 0.8f, center = Offset(x + 1.6f, 25f), alpha = a)
    }
}

private fun jetShape(cx: Float, cy: Float) = Path().apply {
    moveTo(cx + 5.5f, cy)
    lineTo(cx + 1.4f, cy - 1.2f)
    lineTo(cx - 1.6f, cy - 4.4f)
    lineTo(cx - 2.6f, cy - 4.4f)
    lineTo(cx - 1.4f, cy - 1.1f)
    lineTo(cx - 5.4f, cy)
    lineTo(cx - 1.4f, cy + 1.1f)
    lineTo(cx - 2.6f, cy + 4.4f)
    lineTo(cx - 1.6f, cy + 4.4f)
    lineTo(cx + 1.4f, cy + 1.2f)
    close()
}

/**
 * Navio em marcha: a onda de proa nasce na ponta e abre PARA TRÁS, rente ao costado
 * (a versão antiga abria para a frente e parecia um par de chifres), e a esteira sai da
 * popa abrindo e sumindo, com as bolhas correndo para trás.
 */
private fun DrawScope.drawWake(type: ShipClass, t: Float, a: Float) {
    val bx = bowX(type)
    val sx = sternX(type)
    val half = hullHalf(type)
    val foam = Color(0xFFDCEBF5)
    // onda de proa: duas lascas que abrem para trás, tremulando de leve
    val flick = 0.75f + 0.25f * kotlin.math.sin(t * 7f)
    listOf(-1f, 1f).forEach { side ->
        val wave = Path().apply {
            moveTo(bx + 1f, 25f)
            quadraticTo(bx - 10f, 25f + side * half * 1.05f, bx - 34f, 25f + side * half * 1.45f)
            lineTo(bx - 30f, 25f + side * half * 1.15f)
            quadraticTo(bx - 10f, 25f + side * half * 0.75f, bx + 1f, 25f)
            close()
        }
        drawPath(wave, foam.copy(alpha = 0.42f * a * flick))
    }
    // esteira: só bolhas — nascem densas na popa e se espalham para trás e para os
    // lados, sumindo ao se afastar (sem faixa clara fixa por baixo)
    val len = 85f
    val rnd = kotlin.random.Random(type.ordinal * 977 + 13)
    repeat(95) {
        val f0 = rnd.nextFloat() * 2f - 1f
        // puxa parte das bolhas para as bordas da cunha (onde a esteira quebra mais)
        val f = if (rnd.nextFloat() < 0.45f) kotlin.math.sign(f0) * (0.75f + rnd.nextFloat() * 0.25f) else f0
        val speed = 0.35f + rnd.nextFloat() * 0.3f
        val phase = (t * speed + rnd.nextFloat()) % 1f
        val x = sx + 1f - phase * len
        val y = 25f + f * half * (0.5f + phase * 1.8f)
        val r = 0.45f + rnd.nextFloat() * 1.1f
        // mais forte logo atrás da popa, onde a hélice revolve a água
        val alpha = (1f - phase) * (1f - phase) * (0.18f + rnd.nextFloat() * 0.30f) * a
        drawCircle(foam.copy(alpha = alpha), radius = r, center = Offset(x, y))
    }
}

/** Caça estivado: fuselagem, asa em flecha e leme, no tamanho do convés. */
private fun DrawScope.carrierJet(cx: Float, cy: Float, l: Paint, a: Float) {
    val p = Path().apply {
        moveTo(cx + 5.5f, cy)
        lineTo(cx + 1.4f, cy - 1.2f)
        lineTo(cx - 1.6f, cy - 4.4f)
        lineTo(cx - 2.6f, cy - 4.4f)
        lineTo(cx - 1.4f, cy - 1.1f)
        lineTo(cx - 5.4f, cy)
        lineTo(cx - 1.4f, cy + 1.1f)
        lineTo(cx - 2.6f, cy + 4.4f)
        lineTo(cx - 1.6f, cy + 4.4f)
        lineTo(cx + 1.4f, cy + 1.2f)
        close()
    }
    translate(-LIGHT_DX * 0.8f, -LIGHT_DY * 0.8f) { drawPath(p, Color.Black.copy(alpha = 0.3f * a)) }
    drawPath(p, l.trim.lit(0.1f), alpha = a * 0.95f)
    drawCircle(l.dark, radius = 0.8f, center = Offset(cx + 1.6f, cy), alpha = a)
}

// ------------------------------------------------------------------ couraçado

private fun DrawScope.drawBattleship(line: FleetLine, l: Paint, a: Float) {
    val bx = bowX(ShipClass.BATTLESHIP)
    val sx = sternX(ShipClass.BATTLESHIP)
    val half = hullHalf(ShipClass.BATTLESHIP)

    hull(hullPath(sx, bx, half, bowRun(ShipClass.BATTLESHIP)), l, a)
    deck(hullPath(sx + 4f, bx - 8f, half * 0.74f, bowRun(ShipClass.BATTLESHIP) * 0.8f, 4f), l, a, 0.85f)
    plating(sx, bx, half, l, a)

    // três torres triplas: duas à proa apontando para a frente, uma à ré virada
    turret(cx = bx - 52f, r = half * 0.62f, barrels = 3, barrelLen = half * 2.1f, dir = 1f, l = l, a = a)
    turret(cx = bx - 76f, r = half * 0.55f, barrels = 3, barrelLen = half * 1.7f, dir = 1f, l = l, a = a)
    turret(cx = sx + 34f, r = half * 0.6f, barrels = 3, barrelLen = half * 2f, dir = -1f, l = l, a = a)

    // ilha central em blocos, com ponte fechada e radar (ou a torre da linha no lugar)
    val cx = bx * 0.5f
    bridge(ShipClass.BATTLESHIP, line, l, a)

    // secundárias e antiaéreos nas galerias dos dois bordos
    listOf(-1f, 1f).forEach { s ->
        aaTub(bx - 96f, 25f + s * half * 0.86f, half * 0.28f, s, l, a)
        aaTub(cx + 4f, 25f + s * half * 0.9f, half * 0.26f, s, l, a)
        aaTub(sx + 58f, 25f + s * half * 0.86f, half * 0.26f, s, l, a)
        boat(sx + 46f, 25f + s * half * 0.72f, half * 0.9f, l, a)
    }
}

// -------------------------------------------------------------------- cruzador

private fun DrawScope.drawCruiser(line: FleetLine, l: Paint, a: Float) {
    val bx = bowX(ShipClass.CRUISER)
    val sx = sternX(ShipClass.CRUISER)
    val half = hullHalf(ShipClass.CRUISER)

    hull(hullPath(sx, bx, half, bowRun(ShipClass.CRUISER)), l, a, 1f)
    deck(hullPath(sx + 3f, bx - 7f, half * 0.74f, bowRun(ShipClass.CRUISER) * 0.8f, 3.5f), l, a, 0.85f)
    plating(sx, bx, half, l, a)

    turret(cx = bx - 38f, r = half * 0.6f, barrels = 2, barrelLen = half * 1.9f, dir = 1f, l = l, a = a)
    turret(cx = bx - 56f, r = half * 0.54f, barrels = 2, barrelLen = half * 1.5f, dir = 1f, l = l, a = a)
    turret(cx = sx + 30f, r = half * 0.56f, barrels = 2, barrelLen = half * 1.7f, dir = -1f, l = l, a = a)

    // lançadores verticais: tampas em relevo no meio do convés
    var x = bx - 84f
    while (x < bx - 62f) {
        listOf(-0.42f, 0.42f).forEach { s ->
            deckBlock(x, 25f + s * half - half * 0.2f, 4.2f, half * 0.4f, l.dark, a, 1f)
        }
        x += 6f
    }

    val cx = bx * 0.44f
    bridge(ShipClass.CRUISER, line, l, a)

    // convoo marcado na popa
    drawCircle(l.dark, radius = half * 0.82f, center = Offset(sx + 13f, 25f), alpha = a * 0.5f)
    drawCircle(l.trim.copy(alpha = 0.6f * a), radius = half * 0.82f, center = Offset(sx + 13f, 25f), style = Stroke(0.7f))

    listOf(-1f, 1f).forEach { s ->
        aaTub(cx + 16f, 25f + s * half * 0.86f, half * 0.26f, s, l, a)
        boat(cx - 34f, 25f + s * half * 0.7f, half * 0.85f, l, a)
    }
}

// ------------------------------------------------------------------ submarino

private fun DrawScope.drawSubmarine(l: Paint, a: Float) {
    val bx = bowX(ShipClass.SUBMARINE)
    val sx = sternX(ShipClass.SUBMARINE)
    val half = hullHalf(ShipClass.SUBMARINE)

    // casco em gota: sem proa afilada, as duas pontas arredondadas
    val h = Path().apply {
        moveTo(sx + 2f, 25f)
        cubicTo(sx + 2f, 25f - half * 0.86f, sx + 16f, 25f - half, sx + 30f, 25f - half)
        lineTo(bx - 34f, 25f - half)
        cubicTo(bx - 14f, 25f - half, bx, 25f - half * 0.56f, bx, 25f)
        cubicTo(bx, 25f + half * 0.56f, bx - 14f, 25f + half, bx - 34f, 25f + half)
        lineTo(sx + 30f, 25f + half)
        cubicTo(sx + 16f, 25f + half, sx + 2f, 25f + half * 0.86f, sx + 2f, 25f)
        close()
    }
    hull(h, l, a, 1f)

    // lombo iluminado: a faixa clara que faz o casco parecer cilíndrico
    val spine = Path().apply {
        moveTo(sx + 12f, 25f - half * 0.2f)
        cubicTo(sx + 18f, 25f - half * 0.66f, sx + 34f, 25f - half * 0.74f, sx + 52f, 25f - half * 0.74f)
        lineTo(bx - 40f, 25f - half * 0.74f)
        cubicTo(bx - 22f, 25f - half * 0.74f, bx - 10f, 25f - half * 0.5f, bx - 6f, 25f - half * 0.24f)
        cubicTo(bx - 14f, 25f - half * 0.44f, bx - 26f, 25f - half * 0.52f, sx + 48f, 25f - half * 0.5f)
        lineTo(sx + 30f, 25f - half * 0.46f)
        close()
    }
    drawPath(spine, Color.White.copy(alpha = 0.16f * a))
    l.ensign?.let { drawEnsign(it, sx + 8f, half, a) }

    // vela com periscópios
    val vx = bx * 0.44f
    deckBlock(vx - 9f, 25f - half * 0.6f, 18f, half * 1.2f, l.dark, a, 2f)
    deckBlock(vx - 5f, 25f - half * 0.34f, 9f, half * 0.68f, l.deck, a, 2.5f)
    listOf(-2.5f, 0.5f, 3f).forEach { d ->
        drawCircle(l.dark.shaded(0.5f), radius = half * 0.1f, center = Offset(vx + d, 25f), alpha = a)
    }
    // planos de mergulho
    listOf(-1f, 1f).forEach { s ->
        deckBlock(vx - 4f, 25f + s * half * 1.25f - half * 0.2f, 6f, half * 0.4f, l.deck, a, 1f)
    }
    // canhão de convés à proa
    turret(cx = bx - 34f, r = half * 0.42f, barrels = 1, barrelLen = half * 1.5f, dir = 1f, l = l, a = a)

    // leme em X e hélice, recolhidos dentro da célula
    listOf(-1f, 1f).forEach { s ->
        drawLine(
            l.dark.lit(0.2f),
            Offset(sx + 16f, 25f + s * half * 0.6f),
            Offset(sx + 4f, 25f + s * half * 1.5f),
            half * 0.22f,
            alpha = a,
            cap = StrokeCap.Round
        )
    }
    drawCircle(l.trim, radius = half * 0.3f, center = Offset(sx + 3f, 25f), alpha = a * 0.9f)
}

// ------------------------------------------------------------------- destróier

private fun DrawScope.drawDestroyer(line: FleetLine, l: Paint, a: Float) {
    val bx = bowX(ShipClass.DESTROYER)
    val sx = sternX(ShipClass.DESTROYER)
    val half = hullHalf(ShipClass.DESTROYER)

    hull(hullPath(sx, bx, half, bowRun(ShipClass.DESTROYER)), l, a, 0.9f)
    deck(hullPath(sx + 3f, bx - 6f, half * 0.74f, bowRun(ShipClass.DESTROYER) * 0.8f, 3f), l, a, 0.85f)
    plating(sx, bx, half, l, a)

    turret(cx = bx - 26f, r = half * 0.62f, barrels = 2, barrelLen = half * 1.9f, dir = 1f, l = l, a = a)
    turret(cx = sx + 20f, r = half * 0.56f, barrels = 2, barrelLen = half * 1.6f, dir = -1f, l = l, a = a)

    val cx = bx * 0.5f
    bridge(ShipClass.DESTROYER, line, l, a)

    // tubos lança-torpedos girados para um bordo
    deckBlock(cx - 32f, 25f - half * 0.34f, 9f, half * 0.68f, l.dark, a, 1.4f)

    // trilhos de carga de profundidade na popa
    listOf(-0.5f, 0.5f).forEach { s ->
        drawRect(l.dark, topLeft = Offset(sx + 6f, 25f + s * half * 0.9f - half * 0.14f), size = Size(6f, half * 0.28f), alpha = a * 0.8f)
    }

    listOf(-1f, 1f).forEach { s ->
        aaTub(cx + 12f, 25f + s * half * 0.88f, half * 0.28f, s, l, a)
    }
}
