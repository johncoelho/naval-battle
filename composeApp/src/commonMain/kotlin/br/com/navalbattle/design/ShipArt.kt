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
 * lugar para verga de mastro, escaler e reparo antiaéreo sem nada vazar para a célula
 * vizinha — é isso que mantém duas embarcações encostadas sem sobreposição.
 */
fun DrawScope.drawShip(
    type: ShipClass,
    center: Offset,
    lengthPx: Float,
    thicknessPx: Float,
    vertical: Boolean,
    skin: Skin,
    alpha: Float = 1f
) {
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
        drawProw(type, line, paint, alpha)
        when (type) {
            ShipClass.CARRIER -> drawCarrier(paint, alpha)
            ShipClass.BATTLESHIP -> drawBattleship(paint, alpha)
            ShipClass.CRUISER -> drawCruiser(paint, alpha)
            ShipClass.SUBMARINE -> drawSubmarine(paint, alpha)
            ShipClass.DESTROYER -> drawDestroyer(paint, alpha)
        }
        if (type != ShipClass.SUBMARINE) {
            drawFunnels(type, line, paint, alpha)
            drawTower(type, line, paint, alpha)
        }
    }
}

// ------------------------------------------------------------ medidas por classe

private fun bowX(type: ShipClass): Float = type.size * 50f - 5f

private fun sternX(type: ShipClass): Float = 5f

/** Meia-boca do casco. Perto de 1/20 do comprimento dos dois lados da quilha. */
private fun hullHalf(type: ShipClass): Float = when (type) {
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

/** Chaminés da linha de construção, logo atrás do meio do navio. */
private fun DrawScope.drawFunnels(type: ShipClass, line: FleetLine, l: Paint, a: Float) {
    if (line.funnels == 0) return
    val bow = bowX(type)
    val half = hullHalf(type)
    for (i in 0 until line.funnels) {
        funnel(bow * (0.34f + i * 0.11f), half * 0.58f, half * 0.46f, l, a)
    }
}

/** Superestrutura característica da linha, desenhada sobre o convés. */
private fun DrawScope.drawTower(type: ShipClass, line: FleetLine, l: Paint, a: Float) {
    if (line.tower == Tower.PADRAO) return
    val bow = bowX(type)
    val half = hullHalf(type)
    val cx = bow * 0.52f
    when (line.tower) {
        Tower.PAGODE -> {
            // torre em pagode: caixas empilhadas afinando para a proa
            for (i in 0 until 3) {
                val w = half * (1.5f - i * 0.36f)
                val h = half * (1.15f - i * 0.30f)
                deckBlock(cx - w / 2f + i * half * 0.2f, 25f - h / 2f, w, h, l.dark, a, 1.6f + i * 0.8f)
            }
            mastAndYards(cx - half * 0.9f, half * 1.9f, l, a)
        }

        Tower.BLOCO -> {
            val w = half * 2.1f
            deckBlock(cx - w / 2f, 25f - half * 0.92f, w, half * 1.84f, l.dark, a, 2f)
            deckBlock(cx - w / 2f + half * 0.26f, 25f - half * 0.54f, w - half * 0.52f, half * 1.08f, l.deck, a * 0.9f, 2.6f)
            mastAndYards(cx - half * 1.2f, half * 2.1f, l, a)
        }

        Tower.FACETADA -> {
            val p = Path().apply {
                moveTo(cx - half * 1.1f, 25f - half * 0.78f)
                lineTo(cx + half * 0.8f, 25f - half * 0.42f)
                lineTo(cx + half * 0.8f, 25f + half * 0.42f)
                lineTo(cx - half * 1.1f, 25f + half * 0.78f)
                close()
            }
            translate(-LIGHT_DX * 1.8f, -LIGHT_DY * 1.8f) {
                drawPath(p, Color.Black.copy(alpha = 0.3f * a))
            }
            drawPath(p, l.dark, alpha = a)
            drawPath(p, l.trim.copy(alpha = 0.5f), alpha = a, style = Stroke(0.7f))
        }

        Tower.PADRAO -> Unit
    }
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
 * Mastro visto de cima: o pau some na vertical, o que se vê são as vergas. Elas
 * avançam sobre a água dos dois bordos, no espaço que o casco fino deixa livre.
 */
private fun DrawScope.mastAndYards(cx: Float, span: Float, l: Paint, a: Float) {
    val c = l.trim.copy(alpha = 0.9f * a)
    drawLine(c, Offset(cx, 25f - span), Offset(cx, 25f + span), 0.7f, cap = StrokeCap.Round)
    listOf(-0.55f, 0.55f).forEach { f ->
        drawLine(c, Offset(cx + span * f * 0.5f, 25f - span * 0.5f), Offset(cx + span * f * 0.5f, 25f + span * 0.5f), 0.6f, cap = StrokeCap.Round)
    }
    drawCircle(l.dark.lit(0.3f), radius = span * 0.16f, center = Offset(cx, 25f), alpha = a)
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

/** Espuma na proa — só um par de lascas claras, para dar sentido de marcha. */
private fun DrawScope.bowWash(bx: Float, half: Float, a: Float) {
    val c = Color(0xFFCFE4F5).copy(alpha = 0.4f * a)
    val up = Path().apply {
        moveTo(bx, 25f)
        lineTo(bx + half * 0.7f, 25f - half * 1.15f)
        lineTo(bx - half * 0.2f, 25f - half * 0.3f)
        close()
    }
    val dn = Path().apply {
        moveTo(bx, 25f)
        lineTo(bx + half * 0.7f, 25f + half * 1.15f)
        lineTo(bx - half * 0.2f, 25f + half * 0.3f)
        close()
    }
    drawPath(up, c)
    drawPath(dn, c)
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
    funnel(islandX + 20f, 2.6f, 2.1f, l, a)
    radar(islandX + 9f, 1.8f, l, a * 0.9f)

    // aeronaves estivadas no convés de voo
    listOf(0f, 1f, 2f, 3f).forEach { i ->
        carrierJet(sx + 34f + i * 21f, 25f - 12.5f, l, a)
    }
    carrierJet(bx - 34f, 25f + 12f, l, a)

    plating(sx, bx, half, l, a)
    bowWash(bx, half, a)
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

private fun DrawScope.drawBattleship(l: Paint, a: Float) {
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

    // ilha central em blocos, com ponte fechada e radar
    val cx = bx * 0.5f
    deckBlock(cx - 16f, 25f - half * 0.68f, 32f, half * 1.36f, l.dark, a, 2.2f)
    deckBlock(cx - 10f, 25f - half * 0.42f, 18f, half * 0.84f, l.deck, a, 2.8f)
    radar(cx - 1f, half * 0.2f, l, a)
    mastAndYards(cx + 12f, half * 1.55f, l, a)
    funnel(cx + 20f, half * 0.5f, half * 0.4f, l, a)
    funnel(cx - 26f, half * 0.4f, half * 0.32f, l, a)

    // secundárias e antiaéreos nas galerias dos dois bordos
    listOf(-1f, 1f).forEach { s ->
        aaTub(bx - 96f, 25f + s * half * 0.86f, half * 0.28f, s, l, a)
        aaTub(cx + 4f, 25f + s * half * 0.9f, half * 0.26f, s, l, a)
        aaTub(sx + 58f, 25f + s * half * 0.86f, half * 0.26f, s, l, a)
        boat(sx + 46f, 25f + s * half * 0.72f, half * 0.9f, l, a)
    }
    bowWash(bx, half, a)
}

// -------------------------------------------------------------------- cruzador

private fun DrawScope.drawCruiser(l: Paint, a: Float) {
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
    deckBlock(cx - 11f, 25f - half * 0.62f, 22f, half * 1.24f, l.dark, a, 2f)
    deckBlock(cx - 6f, 25f - half * 0.38f, 12f, half * 0.76f, l.deck, a, 2.5f)
    radar(cx, half * 0.19f, l, a)
    mastAndYards(cx + 9f, half * 1.5f, l, a)
    funnel(cx - 14f, half * 0.46f, half * 0.37f, l, a)
    funnel(cx - 26f, half * 0.4f, half * 0.32f, l, a)

    // convoo marcado na popa
    drawCircle(l.dark, radius = half * 0.82f, center = Offset(sx + 13f, 25f), alpha = a * 0.5f)
    drawCircle(l.trim.copy(alpha = 0.6f * a), radius = half * 0.82f, center = Offset(sx + 13f, 25f), style = Stroke(0.7f))

    listOf(-1f, 1f).forEach { s ->
        aaTub(cx + 16f, 25f + s * half * 0.86f, half * 0.26f, s, l, a)
        boat(cx - 34f, 25f + s * half * 0.7f, half * 0.85f, l, a)
    }
    bowWash(bx, half, a)
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

private fun DrawScope.drawDestroyer(l: Paint, a: Float) {
    val bx = bowX(ShipClass.DESTROYER)
    val sx = sternX(ShipClass.DESTROYER)
    val half = hullHalf(ShipClass.DESTROYER)

    hull(hullPath(sx, bx, half, bowRun(ShipClass.DESTROYER)), l, a, 0.9f)
    deck(hullPath(sx + 3f, bx - 6f, half * 0.74f, bowRun(ShipClass.DESTROYER) * 0.8f, 3f), l, a, 0.85f)
    plating(sx, bx, half, l, a)

    turret(cx = bx - 26f, r = half * 0.62f, barrels = 2, barrelLen = half * 1.9f, dir = 1f, l = l, a = a)
    turret(cx = sx + 20f, r = half * 0.56f, barrels = 2, barrelLen = half * 1.6f, dir = -1f, l = l, a = a)

    val cx = bx * 0.5f
    deckBlock(cx - 8f, 25f - half * 0.64f, 16f, half * 1.28f, l.dark, a, 1.8f)
    deckBlock(cx - 4f, 25f - half * 0.4f, 8f, half * 0.8f, l.deck, a, 2.2f)
    mastAndYards(cx + 6f, half * 1.45f, l, a)
    funnel(cx - 11f, half * 0.44f, half * 0.35f, l, a)
    funnel(cx - 21f, half * 0.38f, half * 0.3f, l, a)

    // tubos lança-torpedos girados para um bordo
    deckBlock(cx - 32f, 25f - half * 0.34f, 9f, half * 0.68f, l.dark, a, 1.4f)

    // trilhos de carga de profundidade na popa
    listOf(-0.5f, 0.5f).forEach { s ->
        drawRect(l.dark, topLeft = Offset(sx + 6f, 25f + s * half * 0.9f - half * 0.14f), size = Size(6f, half * 0.28f), alpha = a * 0.8f)
    }

    listOf(-1f, 1f).forEach { s ->
        aaTub(cx + 12f, 25f + s * half * 0.88f, half * 0.28f, s, l, a)
    }
    bowWash(bx, half, a)
}
