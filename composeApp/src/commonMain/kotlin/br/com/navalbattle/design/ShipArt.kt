package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.navalbattle.game.ShipClass

/**
 * Cada classe é desenhada em um viewBox de (tamanho * 50) x 50 — vista de topo,
 * proa à direita. As três cores da libré são os únicos parâmetros de pintura.
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
    val livery = skin.livery
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
        drawProw(type, line, livery, alpha)
        when (type) {
            ShipClass.CARRIER -> drawCarrier(livery, alpha)
            ShipClass.BATTLESHIP -> drawBattleship(livery, alpha)
            ShipClass.CRUISER -> drawCruiser(livery, alpha)
            ShipClass.SUBMARINE -> drawSubmarine(livery, alpha)
            ShipClass.DESTROYER -> drawDestroyer(livery, alpha)
        }
        if (type != ShipClass.SUBMARINE) {
            drawFunnels(type, line, livery, alpha)
            drawTower(type, line, livery, alpha)
        }
    }
}

/** Ponta da proa: fica atrás do casco, prolongando a linha da embarcação. */
private fun DrawScope.drawProw(type: ShipClass, line: FleetLine, l: Livery, a: Float) {
    if (line.prow == Prow.PADRAO) return
    val bow = bowX(type)
    val half = hullHalf(type)
    when (line.prow) {
        Prow.CLIPPER -> {
            val p = Path().apply {
                moveTo(bow - 6f, 25f - half * 0.75f)
                lineTo(bow + half * 0.95f, 25f)
                lineTo(bow - 6f, 25f + half * 0.75f)
                close()
            }
            drawPath(p, l.hull, alpha = a)
            drawPath(p, l.dark, alpha = a, style = Stroke(1.2f))
        }

        Prow.BULBOSA -> {
            drawCircle(l.hull, radius = half * 0.55f, center = Offset(bow + half * 0.2f, 25f), alpha = a)
            drawCircle(l.dark, radius = half * 0.55f, center = Offset(bow + half * 0.2f, 25f), alpha = a * 0.9f, style = Stroke(1.2f))
        }

        Prow.FACETADA -> {
            val p = Path().apply {
                moveTo(bow - 10f, 25f - half)
                lineTo(bow + half * 0.7f, 25f - half * 0.22f)
                lineTo(bow + half * 0.7f, 25f + half * 0.22f)
                lineTo(bow - 10f, 25f + half)
                close()
            }
            drawPath(p, l.hull, alpha = a)
            drawPath(p, l.dark, alpha = a, style = Stroke(1.2f))
            // quinas do casco de baixa assinatura
            drawLine(l.dark.copy(alpha = 0.75f), Offset(sternX(type) + 4f, 25f - half * 0.45f), Offset(bow - 6f, 25f - half * 0.2f), 1.1f, alpha = a)
            drawLine(l.dark.copy(alpha = 0.75f), Offset(sternX(type) + 4f, 25f + half * 0.45f), Offset(bow - 6f, 25f + half * 0.2f), 1.1f, alpha = a)
        }

        Prow.PADRAO -> Unit
    }
}

/** Chaminés inclinadas, logo atrás do meio do navio. */
private fun DrawScope.drawFunnels(type: ShipClass, line: FleetLine, l: Livery, a: Float) {
    if (line.funnels == 0) return
    val bow = bowX(type)
    val half = hullHalf(type)
    val w = half * 0.34f
    val h = half * 0.62f
    for (i in 0 until line.funnels) {
        val x = bow * (0.36f + i * 0.10f)
        box(x, 25f - h / 2f, w, h, l.dark, a)
        box(x + w * 0.18f, 25f - h / 2f, w * 0.3f, h * 0.28f, l.trim, a * 0.8f)
    }
}

/** Superestrutura característica da linha, desenhada sobre o convés. */
private fun DrawScope.drawTower(type: ShipClass, line: FleetLine, l: Livery, a: Float) {
    if (line.tower == Tower.PADRAO) return
    val bow = bowX(type)
    val half = hullHalf(type)
    val cx = bow * 0.55f
    when (line.tower) {
        Tower.PAGODE -> {
            // torre em pagode: caixas empilhadas afinando para cima
            for (i in 0 until 3) {
                val w = half * (1.05f - i * 0.26f)
                val h = half * (0.34f - i * 0.06f)
                box(cx - w / 2f, 25f - half * (0.30f + i * 0.34f), w, h, l.dark, a)
            }
            box(cx - half * 0.06f, 25f - half * 1.5f, half * 0.12f, half * 0.42f, l.trim, a)
        }

        Tower.BLOCO -> {
            val w = half * 1.5f
            box(cx - w / 2f, 25f - half * 0.62f, w, half * 1.24f, l.dark, a)
            box(cx - w / 2f + half * 0.16f, 25f - half * 0.36f, w - half * 0.32f, half * 0.72f, l.deck, a * 0.9f)
            // mastro em treliça
            drawLine(l.trim, Offset(cx - half * 0.3f, 25f - half * 0.62f), Offset(cx + half * 0.3f, 25f + half * 0.62f), 1f, alpha = a * 0.8f)
            drawLine(l.trim, Offset(cx + half * 0.3f, 25f - half * 0.62f), Offset(cx - half * 0.3f, 25f + half * 0.62f), 1f, alpha = a * 0.8f)
        }

        Tower.FACETADA -> {
            val p = Path().apply {
                moveTo(cx - half * 0.8f, 25f - half * 0.52f)
                lineTo(cx + half * 0.55f, 25f - half * 0.30f)
                lineTo(cx + half * 0.55f, 25f + half * 0.30f)
                lineTo(cx - half * 0.8f, 25f + half * 0.52f)
                close()
            }
            drawPath(p, l.dark, alpha = a)
            drawPath(p, l.trim.copy(alpha = 0.5f), alpha = a, style = Stroke(1f))
        }

        Tower.PADRAO -> Unit
    }
}

private fun bowX(type: ShipClass): Float = when (type) {
    ShipClass.CARRIER -> 247f
    ShipClass.BATTLESHIP -> 198f
    ShipClass.CRUISER -> 148f
    ShipClass.SUBMARINE -> 146f
    ShipClass.DESTROYER -> 98.5f
}

private fun sternX(type: ShipClass): Float = when (type) {
    ShipClass.CARRIER -> 10f
    ShipClass.BATTLESHIP -> 8f
    ShipClass.CRUISER -> 7f
    ShipClass.SUBMARINE -> 9f
    ShipClass.DESTROYER -> 6f
}

private fun hullHalf(type: ShipClass): Float = when (type) {
    ShipClass.CARRIER -> 15f
    ShipClass.BATTLESHIP -> 14f
    ShipClass.CRUISER -> 12.5f
    ShipClass.SUBMARINE -> 11f
    ShipClass.DESTROYER -> 11f
}

private fun DrawScope.hull(path: Path, livery: Livery, alpha: Float, stroke: Float = 1.4f) {
    drawPath(path, livery.hull, alpha = alpha)
    if (livery.camo != Camo.LISA) {
        clipPath(path) { drawCamo(livery, alpha) }
    }
    drawPath(path, livery.dark, alpha = alpha, style = Stroke(width = stroke))
}

/** Padrão de camuflagem, sempre recortado no contorno do casco. */
private fun DrawScope.drawCamo(l: Livery, a: Float) {
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

private fun DrawScope.box(
    x: Float, y: Float, w: Float, h: Float,
    color: androidx.compose.ui.graphics.Color, alpha: Float
) = drawRect(color, topLeft = Offset(x, y), size = Size(w, h), alpha = alpha)

// ---------------------------------------------------------------- carrier

private fun DrawScope.drawCarrier(l: Livery, a: Float) {
    val h = Path().apply {
        moveTo(10f, 25f)
        cubicTo(10f, 15f, 17f, 10f, 27f, 10f)
        lineTo(196f, 10f)
        cubicTo(222f, 10f, 242f, 17f, 247f, 25f)
        cubicTo(242f, 33f, 222f, 40f, 196f, 40f)
        lineTo(27f, 40f)
        cubicTo(17f, 40f, 10f, 35f, 10f, 25f)
        close()
    }
    hull(h, l, a)

    val deck = Path().apply {
        moveTo(22f, 15f)
        lineTo(198f, 15f)
        cubicTo(214f, 15f, 228f, 19f, 234f, 25f)
        cubicTo(228f, 31f, 214f, 35f, 198f, 35f)
        lineTo(22f, 35f)
        close()
    }
    drawPath(deck, l.deck, alpha = a)

    // faixa central intermitente da pista
    var x = 34f
    while (x < 212f) {
        box(x, 24.2f, 9f, 1.8f, l.trim, a * 0.85f)
        x += 16f
    }
    box(36f, 18.5f, 16f, 13f, l.dark, a * 0.5f)
    box(150f, 18.5f, 16f, 13f, l.dark, a * 0.5f)

    // ilha de comando a boreste
    box(176f, 7f, 30f, 9f, l.dark, a)
    box(182f, 3.5f, 8f, 4f, l.trim, a)
    drawCircle(l.trim, radius = 2f, center = Offset(198f, 11.5f), alpha = a)
}

// ------------------------------------------------------------ battleship

private fun DrawScope.drawBattleship(l: Livery, a: Float) {
    val h = Path().apply {
        moveTo(8f, 25f)
        cubicTo(8f, 16f, 14f, 11f, 23f, 11f)
        lineTo(158f, 11f)
        cubicTo(180f, 11f, 194f, 18f, 198f, 25f)
        cubicTo(194f, 32f, 180f, 39f, 158f, 39f)
        lineTo(23f, 39f)
        cubicTo(14f, 39f, 8f, 34f, 8f, 25f)
        close()
    }
    hull(h, l, a)

    val deck = Path().apply {
        moveTo(20f, 16f)
        lineTo(156f, 16f)
        cubicTo(172f, 16f, 184f, 20f, 189f, 25f)
        cubicTo(184f, 30f, 172f, 34f, 156f, 34f)
        lineTo(20f, 34f)
        close()
    }
    drawPath(deck, l.deck, alpha = a * 0.9f)

    turret(44f, 25f, 8f, 50f, 23.2f, 18f, 3.6f, l, a)
    turret(74f, 25f, 7f, 79f, 23.4f, 15f, 3.2f, l, a)
    turret(150f, 25f, 7.5f, 130f, 23.4f, 17f, 3.2f, l, a)

    box(96f, 16f, 26f, 18f, l.dark, a)
    box(102f, 19f, 14f, 12f, l.deck, a)
    box(107f, 10f, 4f, 8f, l.trim, a)
    drawCircle(l.trim, radius = 2.4f, center = Offset(109f, 25f), alpha = a)
}

// ---------------------------------------------------------------- cruiser

private fun DrawScope.drawCruiser(l: Livery, a: Float) {
    val h = Path().apply {
        moveTo(7f, 25f)
        cubicTo(7f, 17f, 12f, 12.5f, 21f, 12.5f)
        lineTo(116f, 12.5f)
        cubicTo(136f, 12.5f, 145f, 18.5f, 148f, 25f)
        cubicTo(145f, 31.5f, 136f, 37.5f, 116f, 37.5f)
        lineTo(21f, 37.5f)
        cubicTo(12f, 37.5f, 7f, 33f, 7f, 25f)
        close()
    }
    hull(h, l, a, 1.3f)

    val deck = Path().apply {
        moveTo(18f, 17f)
        lineTo(114f, 17f)
        cubicTo(128f, 17f, 137f, 21f, 141f, 25f)
        cubicTo(137f, 29f, 128f, 33f, 114f, 33f)
        lineTo(18f, 33f)
        close()
    }
    drawPath(deck, l.deck, alpha = a * 0.9f)

    turret(36f, 25f, 6.5f, 41f, 23.6f, 14f, 2.9f, l, a)
    turret(112f, 25f, 6f, 96f, 23.6f, 13f, 2.9f, l, a)

    box(66f, 17.5f, 22f, 15f, l.dark, a)
    box(71f, 20f, 12f, 10f, l.deck, a)
    box(75f, 11f, 3.4f, 8f, l.trim, a)
    drawCircle(l.trim, radius = 2.1f, center = Offset(76.5f, 25f), alpha = a)
}

// -------------------------------------------------------------- submarine

private fun DrawScope.drawSubmarine(l: Livery, a: Float) {
    val h = Path().apply {
        moveTo(9f, 25f)
        cubicTo(9f, 18.5f, 19f, 14f, 40f, 14f)
        lineTo(108f, 14f)
        cubicTo(130f, 14f, 142f, 19.5f, 146f, 25f)
        cubicTo(142f, 30.5f, 130f, 36f, 108f, 36f)
        lineTo(40f, 36f)
        cubicTo(19f, 36f, 9f, 31.5f, 9f, 25f)
        close()
    }
    hull(h, l, a, 1.3f)

    val deck = Path().apply {
        moveTo(28f, 18f)
        lineTo(110f, 18f)
        cubicTo(124f, 18f, 134f, 21.5f, 138f, 25f)
        cubicTo(134f, 28.5f, 124f, 32f, 110f, 32f)
        lineTo(28f, 32f)
        close()
    }
    drawPath(deck, l.deck, alpha = a * 0.55f)

    box(58f, 16.5f, 24f, 17f, l.dark, a)
    box(63f, 19.5f, 14f, 11f, l.deck, a)
    box(68f, 9.5f, 3f, 8f, l.trim, a)
    box(16f, 24.2f, 10f, 1.6f, l.trim, a * 0.8f)
    box(28.5f, 10f, 3f, 5f, l.dark, a)
    box(28.5f, 35f, 3f, 5f, l.dark, a)
}

// -------------------------------------------------------------- destroyer

private fun DrawScope.drawDestroyer(l: Livery, a: Float) {
    val h = Path().apply {
        moveTo(6f, 25f)
        cubicTo(6f, 18f, 10f, 14f, 18f, 14f)
        lineTo(74f, 14f)
        cubicTo(90f, 14f, 96f, 19.5f, 98.5f, 25f)
        cubicTo(96f, 30.5f, 90f, 36f, 74f, 36f)
        lineTo(18f, 36f)
        cubicTo(10f, 36f, 6f, 32f, 6f, 25f)
        close()
    }
    hull(h, l, a, 1.2f)

    val deck = Path().apply {
        moveTo(15f, 18f)
        lineTo(72f, 18f)
        cubicTo(84f, 18f, 90f, 21.5f, 93f, 25f)
        cubicTo(90f, 28.5f, 84f, 32f, 72f, 32f)
        lineTo(15f, 32f)
        close()
    }
    drawPath(deck, l.deck, alpha = a * 0.9f)

    turret(28f, 25f, 5.6f, 32f, 23.7f, 12f, 2.6f, l, a)
    box(50f, 19f, 16f, 12f, l.dark, a)
    box(54f, 21.5f, 8f, 7f, l.deck, a)
    box(57f, 13f, 3f, 7f, l.trim, a)
    drawCircle(l.trim, radius = 1.9f, center = Offset(58.5f, 25f), alpha = a)
}

private fun DrawScope.turret(
    cx: Float, cy: Float, r: Float,
    bx: Float, by: Float, bw: Float, bh: Float,
    l: Livery, a: Float
) {
    drawCircle(l.dark, radius = r, center = Offset(cx, cy), alpha = a)
    box(bx, by, bw, bh, l.dark, a)
}
