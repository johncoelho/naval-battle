package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
    livery: Livery,
    alpha: Float = 1f
) {
    val vbW = type.size * 50f
    val vbH = 50f
    withTransform({
        if (vertical) rotate(90f, center)
        translate(center.x - lengthPx / 2f, center.y - thicknessPx / 2f)
        scale(lengthPx / vbW, thicknessPx / vbH, pivot = Offset.Zero)
    }) {
        when (type) {
            ShipClass.CARRIER -> drawCarrier(livery, alpha)
            ShipClass.BATTLESHIP -> drawBattleship(livery, alpha)
            ShipClass.CRUISER -> drawCruiser(livery, alpha)
            ShipClass.SUBMARINE -> drawSubmarine(livery, alpha)
            ShipClass.DESTROYER -> drawDestroyer(livery, alpha)
        }
    }
}

private fun DrawScope.hull(path: Path, livery: Livery, alpha: Float, stroke: Float = 1.4f) {
    drawPath(path, livery.hull, alpha = alpha)
    drawPath(path, livery.dark, alpha = alpha, style = Stroke(width = stroke))
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
