package br.com.navalbattle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.BOARD_SIZE
import br.com.navalbattle.game.Board
import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.Impact
import br.com.navalbattle.game.Mark
import br.com.navalbattle.game.Orientation
import br.com.navalbattle.game.Ship
import br.com.navalbattle.game.Tone
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Tempo de voo do projétil até o alvo — o áudio de impacto usa o mesmo atraso. */
const val SHOT_TRAVEL_MS = 420

@Composable
fun BoardView(
    board: Board,
    skin: Skin,
    showShips: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    sweep: Boolean = true,
    preview: Ship? = null,
    previewValid: Boolean = true,
    impact: Impact? = null,
    /** Cor do dono desta frota: pinta as marcas para saber de quem é o navio atingido. */
    markTint: Color? = null,
    /** Segunda frota desenhada na mesma carta (modo local): um mapa só para os dois. */
    overlay: Board? = null,
    overlayTint: Color? = null,
    onCellTap: (Coord) -> Unit = {}
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    // fase 1: projétil viajando até o alvo. fase 2: explosão/respingo no impacto.
    val travelAnim = remember { Animatable(1f) }
    val impactAnim = remember { Animatable(1f) }
    val impactDuration = if (impact?.tone == Tone.SUNK) 2600 else 700
    LaunchedEffect(impact?.id) {
        if (impact != null) {
            impactAnim.snapTo(1f)
            travelAnim.snapTo(0f)
            travelAnim.animateTo(1f, tween(SHOT_TRAVEL_MS, easing = LinearEasing))
            impactAnim.snapTo(0f)
            impactAnim.animateTo(1f, tween(impactDuration, easing = LinearEasing))
        }
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(impact?.id) {
        if (impact != null) {
            delay(SHOT_TRAVEL_MS.toLong())
            val pattern = if (impact.tone == Tone.SUNK) {
                listOf(-16f, 13f, -9f, 6f, -3f, 0f)
            } else if (impact.tone == Tone.HIT) {
                listOf(-7f, 5f, -3f, 0f)
            } else {
                emptyList()
            }
            for (offsetPx in pattern) {
                shake.animateTo(offsetPx, tween(42, easing = LinearEasing))
            }
        }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .offset { IntOffset(shake.value.roundToInt(), 0) }
            .background(Naval.abyss)
            .border(1.dp, Naval.line)
            .padding(3.dp)
            .then(
                if (interactive) Modifier.pointerInput(board, interactive) {
                    detectTapGestures { offset ->
                        val cell = size.width / BOARD_SIZE.toFloat()
                        val coord = Coord((offset.x / cell).toInt(), (offset.y / cell).toInt())
                        if (coord.isValid()) onCellTap(coord)
                    }
                } else Modifier
            )
    ) {
        val cell = size.width / BOARD_SIZE
        val center = Offset(size.width / 2f, size.height / 2f)

        // abismo
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Naval.abyss2, Naval.abyss),
                center = Offset(size.width * 0.5f, size.height * 0.45f),
                radius = size.width * 0.75f
            )
        )

        // anéis de radar
        drawCircle(Naval.greenBright.copy(alpha = 0.10f), radius = size.width * 0.46f, center = center, style = Stroke(1f))
        drawCircle(Naval.greenBright.copy(alpha = 0.08f), radius = size.width * 0.30f, center = center, style = Stroke(1f))
        drawCircle(Naval.greenBright.copy(alpha = 0.06f), radius = size.width * 0.15f, center = center, style = Stroke(1f))

        // varredura
        if (sweep) {
            rotate(sweepAngle, center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Naval.greenBright.copy(alpha = 0.22f),
                        0.10f to Naval.greenBright.copy(alpha = 0.05f),
                        0.28f to Color.Transparent,
                        1.00f to Color.Transparent,
                        center = center
                    ),
                    radius = size.width * 0.72f,
                    center = center
                )
            }
        }

        // malha
        for (i in 0..BOARD_SIZE) {
            val p = i * cell
            drawLine(Naval.gridLine, Offset(p, 0f), Offset(p, size.height), 1f)
            drawLine(Naval.gridLine, Offset(0f, p), Offset(size.width, p), 1f)
        }

        // frota própria
        if (showShips) {
            board.ships.forEach { ship ->
                val sunk = board.isSunk(ship)
                drawFleetShip(ship, cell, skin, if (sunk) 0.35f else 1f)
                markTint?.let { drawShipOutline(ship, cell, it) }
            }
            overlay?.ships?.forEach { ship ->
                val sunk = overlay.isSunk(ship)
                drawFleetShip(ship, cell, skin, if (sunk) 0.35f else 1f)
                overlayTint?.let { drawShipOutline(ship, cell, it) }
            }
        }

        // marcações
        board.marks.forEach { (coord, mark) ->
            val topLeft = Offset(coord.x * cell, coord.y * cell)
            drawMark(mark, topLeft, cell, markTint)
        }

        // a segunda frota divide a mesma carta: quando as duas foram atingidas na
        // mesma coordenada, a de baixo entra menor no canto para nenhuma sumir
        overlay?.marks?.forEach { (coord, mark) ->
            val shared = board.marks.containsKey(coord)
            if (shared) {
                drawMark(
                    mark,
                    Offset(coord.x * cell + cell / 2f, coord.y * cell + cell / 2f),
                    cell / 2f,
                    overlayTint
                )
            } else {
                drawMark(mark, Offset(coord.x * cell, coord.y * cell), cell, overlayTint)
            }
        }

        // pré-visualização do posicionamento
        preview?.let { ship ->
            val color = if (previewValid) Naval.amber else Naval.danger
            ship.cells.filter { it.isValid() }.forEach { c ->
                drawRect(
                    color.copy(alpha = 0.18f),
                    topLeft = Offset(c.x * cell, c.y * cell),
                    size = Size(cell, cell)
                )
                drawRect(
                    color,
                    topLeft = Offset(c.x * cell, c.y * cell),
                    size = Size(cell, cell),
                    style = Stroke(1.5f)
                )
            }
        }

        // projétil e impacto
        impact?.let { imp ->
            val target = Offset(imp.coord.x * cell + cell / 2f, imp.coord.y * cell + cell / 2f)
            val tt = travelAnim.value

            if (tt < 1f) {
                drawIncomingMissile(target, cell, tt)
            } else {
                val t = impactAnim.value
                if (t < 1f) {
                    drawImpactBurst(imp, target, cell, t, skin)
                }
            }
        }
    }
}

/** Míssil com corpo, ogiva e aletas, deixando rastro de fumaça e chama de propulsão. */
private fun DrawScope.drawIncomingMissile(target: Offset, cell: Float, tt: Float) {
    val origin = Offset(target.x, 0f)
    val pos = Offset(origin.x, origin.y + (target.y - origin.y) * tt)

    // rastro de fumaça: baforadas que ficam para trás e dissipam
    var back = 0
    while (back < 7) {
        val py = pos.y - back * cell * 0.42f
        if (py < 0f) break
        val age = back / 7f
        drawCircle(
            color = Naval.inkSoft.copy(alpha = (1f - age) * 0.22f),
            radius = cell * (0.07f + age * 0.13f),
            center = Offset(pos.x + sin(back * 1.7f) * cell * 0.05f, py)
        )
        back++
    }

    val len = cell * 0.5f
    val halfW = cell * 0.085f

    // chama de propulsão atrás do míssil
    val flick = 0.75f + 0.25f * sin(tt * 90f)
    drawPath(
        path = Path().apply {
            moveTo(pos.x - halfW * 0.8f, pos.y - len * 0.42f)
            lineTo(pos.x + halfW * 0.8f, pos.y - len * 0.42f)
            lineTo(pos.x, pos.y - len * (0.42f + 0.95f * flick))
            close()
        },
        brush = Brush.verticalGradient(
            colors = listOf(Naval.amberStrong.copy(alpha = 0.1f), Naval.amberStrong.copy(alpha = 0.9f)),
            startY = pos.y - len * 1.3f,
            endY = pos.y - len * 0.42f
        )
    )

    // corpo + ogiva apontando para baixo
    drawPath(
        path = Path().apply {
            moveTo(pos.x, pos.y + len * 0.5f)               // ponta da ogiva
            lineTo(pos.x + halfW, pos.y + len * 0.05f)
            lineTo(pos.x + halfW, pos.y - len * 0.42f)
            lineTo(pos.x - halfW, pos.y - len * 0.42f)
            lineTo(pos.x - halfW, pos.y + len * 0.05f)
            close()
        },
        color = Naval.inkSoft
    )
    // aletas
    drawPath(
        path = Path().apply {
            moveTo(pos.x - halfW, pos.y - len * 0.18f)
            lineTo(pos.x - halfW * 2.1f, pos.y - len * 0.42f)
            lineTo(pos.x - halfW, pos.y - len * 0.42f)
            close()
            moveTo(pos.x + halfW, pos.y - len * 0.18f)
            lineTo(pos.x + halfW * 2.1f, pos.y - len * 0.42f)
            lineTo(pos.x + halfW, pos.y - len * 0.42f)
            close()
        },
        color = Naval.muted
    )
    // ponta quente
    drawCircle(Naval.amberStrong, radius = halfW * 0.55f, center = Offset(pos.x, pos.y + len * 0.28f))
}

/** Chama em forma de gota, com gradiente quente do núcleo à ponta. */
private fun DrawScope.drawFlame(base: Offset, height: Float, width: Float, alpha: Float) {
    if (alpha <= 0.02f || height <= 1f || width <= 0.2f) return
    val path = Path().apply {
        moveTo(base.x, base.y)
        cubicTo(
            base.x - width, base.y - height * 0.35f,
            base.x - width * 0.55f, base.y - height * 0.72f,
            base.x, base.y - height
        )
        cubicTo(
            base.x + width * 0.55f, base.y - height * 0.72f,
            base.x + width, base.y - height * 0.35f,
            base.x, base.y
        )
        close()
    }
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFF3B0).copy(alpha = alpha * 0.9f),
                Naval.amberStrong.copy(alpha = alpha),
                Naval.danger.copy(alpha = alpha * 0.85f)
            ),
            startY = base.y - height,
            endY = base.y
        )
    )
    // núcleo claro
    drawPath(
        path = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(
                base.x - width * 0.45f, base.y - height * 0.3f,
                base.x - width * 0.25f, base.y - height * 0.5f,
                base.x, base.y - height * 0.62f
            )
            cubicTo(
                base.x + width * 0.25f, base.y - height * 0.5f,
                base.x + width * 0.45f, base.y - height * 0.3f,
                base.x, base.y
            )
            close()
        },
        color = Color(0xFFFFF3B0).copy(alpha = alpha * 0.75f)
    )
}

/** Marinheiro: cabeça, tronco e braços erguidos, girando levemente durante o salto. */
private fun DrawScope.drawSailor(pos: Offset, cell: Float, spin: Float, alpha: Float) {
    val h = cell * 0.055f
    rotate(spin, pos) {
        // tronco
        drawLine(
            color = Naval.ink.copy(alpha = alpha),
            start = Offset(pos.x, pos.y - h * 0.4f),
            end = Offset(pos.x, pos.y + h * 1.5f),
            strokeWidth = cell * 0.045f
        )
        // braços erguidos
        drawLine(
            color = Naval.ink.copy(alpha = alpha),
            start = Offset(pos.x - h * 1.1f, pos.y - h * 0.9f),
            end = Offset(pos.x + h * 1.1f, pos.y - h * 0.9f),
            strokeWidth = cell * 0.03f
        )
        // pernas
        drawLine(
            color = Naval.ink.copy(alpha = alpha),
            start = Offset(pos.x, pos.y + h * 1.4f),
            end = Offset(pos.x - h * 0.9f, pos.y + h * 2.3f),
            strokeWidth = cell * 0.03f
        )
        drawLine(
            color = Naval.ink.copy(alpha = alpha),
            start = Offset(pos.x, pos.y + h * 1.4f),
            end = Offset(pos.x + h * 0.9f, pos.y + h * 2.3f),
            strokeWidth = cell * 0.03f
        )
        // cabeça
        drawCircle(Naval.ink.copy(alpha = alpha), radius = h * 0.85f, center = Offset(pos.x, pos.y - h))
    }
}

private fun DrawScope.drawImpactBurst(imp: Impact, c: Offset, cell: Float, t: Float, skin: Skin) {
    val tint = when (imp.tone) {
        Tone.MISS -> Naval.inkSoft
        Tone.SUNK -> Naval.danger
        else -> Naval.amberStrong
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = (1f - t) * 0.9f), Color.Transparent),
            center = c,
            radius = cell * (0.6f + t)
        ),
        radius = cell * (0.6f + t),
        center = c
    )
    drawCircle(
        color = tint.copy(alpha = (1f - t) * 0.85f),
        radius = cell * (0.3f + t * 1.6f),
        center = c,
        style = Stroke(2f)
    )

    when (imp.tone) {
        Tone.MISS -> {
            // coluna d'água subindo e anéis se espalhando
            for (i in 0..2) {
                val local = ((t - i * 0.1f) / (1f - i * 0.1f)).coerceIn(0f, 1f)
                drawCircle(
                    color = Naval.greenBright.copy(alpha = (1f - local) * 0.35f),
                    radius = cell * (0.25f + local * 0.9f),
                    center = c,
                    style = Stroke(1.5f)
                )
            }
            val jet = ((t) / 0.55f).coerceIn(0f, 1f)
            for (d in -2..2) {
                val spread = d * cell * 0.13f * jet
                val hgt = cell * (0.7f - kotlin.math.abs(d) * 0.12f) * sin(3.1416f * jet)
                drawLine(
                    color = Color(0xFFBFE8FF).copy(alpha = (1f - jet) * 0.55f),
                    start = Offset(c.x + spread, c.y),
                    end = Offset(c.x + spread * 1.6f, c.y - hgt),
                    strokeWidth = cell * 0.06f
                )
            }
        }

        Tone.HIT -> {
            // labaredas curtas no ponto atingido
            val flick = 0.7f + 0.3f * sin(t * 60f)
            drawFlame(
                base = Offset(c.x, c.y + cell * 0.15f),
                height = cell * 0.6f * flick * (1f - t),
                width = cell * 0.2f,
                alpha = (1f - t) * 0.9f
            )
        }

        Tone.SUNK -> drawSinkingShip(imp, cell, t, skin)
        else -> Unit
    }
}

/**
 * Sequência de naufrágio: o navio real aparece, inclina, pega fogo e submerge,
 * enquanto a tripulação salta na água e destroços voam.
 */
private fun DrawScope.drawSinkingShip(imp: Impact, cell: Float, t: Float, skin: Skin) {
    val ship = imp.sunkShip ?: return
    val vertical = ship.orientation == Orientation.VERTICAL
    val length = ship.type.size * cell
    val baseCx = if (vertical) ship.origin.x * cell + cell / 2f else ship.origin.x * cell + length / 2f
    val baseCy = if (vertical) ship.origin.y * cell + length / 2f else ship.origin.y * cell + cell / 2f

    // 0..1 ao longo do naufrágio, com uma pausa inicial para a explosão respirar
    val sink = ((t - 0.18f) / 0.82f).coerceIn(0f, 1f)
    val visible = 1f - sink * sink // some mais rápido no fim, como se engolido pela água

    // rastro de espuma em volta do casco
    for (i in 0..1) {
        val local = ((t - i * 0.18f) / 0.8f).coerceIn(0f, 1f)
        drawCircle(
            color = Color(0xFFBFE8FF).copy(alpha = (1f - local) * 0.18f),
            radius = length * (0.28f + local * 0.42f),
            center = Offset(baseCx, baseCy),
            style = Stroke(cell * 0.06f)
        )
    }

    // o navio: adborna, inclina e afunda. O casco é estreito, então quem vende o
    // naufrágio é o emborcar — a boca vai sumindo, como um casco girando na água —
    // mais do que a inclinação em si.
    if (visible > 0.02f) {
        val drop = sink * cell * 0.75f
        val center = Offset(baseCx, baseCy + drop)
        val tilt = sink * 26f * (if (vertical) -1f else 1f)
        val roll = 1f - sink * 0.55f
        rotate(tilt, center) {
            drawShip(
                type = ship.type,
                center = center,
                lengthPx = length,
                thicknessPx = cell * roll,
                vertical = vertical,
                skin = skin,
                alpha = visible.coerceIn(0f, 1f)
            )
        }
    }

    // fogo em três pontos do casco + fumaça subindo
    val firePoints = listOf(-0.3f, 0.05f, 0.34f)
    firePoints.forEachIndexed { i, frac ->
        val along = length * frac
        val fx = if (vertical) baseCx else baseCx + along
        val fy = if (vertical) baseCy + along else baseCy
        val yDrop = sink * cell * 0.75f
        val flick = 0.65f + 0.35f * sin(t * 55f + i * 2.1f)
        val fade = (1f - sink * 1.15f).coerceIn(0f, 1f)

        drawFlame(
            base = Offset(fx, fy + yDrop),
            height = cell * (0.75f + 0.35f * flick) * fade,
            width = cell * 0.22f * flick,
            alpha = fade * 0.95f
        )

        // coluna de fumaça
        for (p in 0..2) {
            val local = ((t - (i * 0.06f + p * 0.16f)) / 0.9f).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val rise = local * cell * 2.4f
            val sway = sin(local * 5f + i * 1.7f) * cell * 0.18f
            drawCircle(
                color = Color(0xFF3A3F36).copy(alpha = (1f - local) * 0.5f),
                radius = cell * (0.12f + local * 0.26f),
                center = Offset(fx + sway, fy - rise)
            )
        }
    }

    // destroços saltando do casco
    for (i in 0..5) {
        val local = ((t - 0.05f) / 0.55f).coerceIn(0f, 1f)
        if (local <= 0f || local >= 1f) continue
        val ang = (i * 61f) * 3.1416f / 180f
        val dist = cell * 1.5f * local
        val dx = sin(ang) * dist
        val dy = -kotlin.math.abs(sin(3.1416f * local)) * cell * 0.9f + (i % 3) * cell * 0.1f
        drawRect(
            color = Naval.muted.copy(alpha = (1f - local) * 0.8f),
            topLeft = Offset(baseCx + dx, baseCy + dy),
            size = Size(cell * 0.07f, cell * 0.05f)
        )
    }

    // tripulação pulando das duas pontas
    val ends = listOf(ship.cells.first(), ship.cells.last())
    ends.forEachIndexed { i, coord ->
        val local = ((t - 0.12f - i * 0.06f) / 0.6f).coerceIn(0f, 1f)
        if (local <= 0f) return@forEachIndexed
        val dir = if (i == 0) -1f else 1f
        val startX = coord.x * cell + cell / 2f
        val startY = coord.y * cell + cell / 2f
        val jumpX = if (vertical) startX + dir * cell * 1.15f * local else startX + dir * cell * 1.35f * local
        val arc = sin(3.1416f * local) * cell * 0.7f
        val jumpY = if (vertical) startY + dir * cell * 0.5f * local - arc else startY - arc + local * cell * 0.35f

        if (local < 0.88f) {
            drawSailor(Offset(jumpX, jumpY), cell, spin = dir * local * 55f, alpha = 1f)
        } else {
            val splashT = ((local - 0.88f) / 0.12f).coerceIn(0f, 1f)
            drawCircle(
                color = Color(0xFFBFE8FF).copy(alpha = (1f - splashT) * 0.7f),
                radius = cell * (0.08f + splashT * 0.3f),
                center = Offset(jumpX, jumpY),
                style = Stroke(cell * 0.045f)
            )
            drawCircle(
                color = Color(0xFFBFE8FF).copy(alpha = (1f - splashT) * 0.4f),
                radius = cell * (0.04f + splashT * 0.16f),
                center = Offset(jumpX, jumpY),
                style = Stroke(cell * 0.03f)
            )
        }
    }
}

private fun DrawScope.drawFleetShip(ship: Ship, cell: Float, skin: Skin, alpha: Float) {
    val vertical = ship.orientation == Orientation.VERTICAL
    val length = ship.type.size * cell
    val cx = if (vertical) ship.origin.x * cell + cell / 2f
    else ship.origin.x * cell + length / 2f
    val cy = if (vertical) ship.origin.y * cell + length / 2f
    else ship.origin.y * cell + cell / 2f

    drawShip(
        type = ship.type,
        center = Offset(cx, cy),
        lengthPx = length,
        thicknessPx = cell,
        vertical = vertical,
        skin = skin,
        alpha = alpha
    )
}

/** Contorno na cor do comandante — diz de quem é cada navio na carta final. */
private fun DrawScope.drawShipOutline(ship: Ship, cell: Float, color: Color) {
    ship.cells.forEach { c ->
        drawRect(
            color.copy(alpha = 0.7f),
            topLeft = Offset(c.x * cell, c.y * cell),
            size = Size(cell, cell),
            style = Stroke(1.2f)
        )
    }
}

private fun DrawScope.drawMark(mark: Mark, topLeft: Offset, cell: Float, tint: Color?) {
    val center = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f)
    val hitColor = tint ?: Naval.danger
    when (mark) {
        Mark.MISS -> drawCircle(
            (tint ?: Naval.inkSoft).copy(alpha = 0.45f),
            radius = cell * 0.17f,
            center = center,
            style = Stroke(1.5f)
        )

        Mark.HIT -> {
            drawRect(hitColor.copy(alpha = 0.32f), topLeft = topLeft, size = Size(cell, cell))
            drawRect(hitColor, topLeft = topLeft, size = Size(cell, cell), style = Stroke(1f))
            drawCircle(tint ?: Naval.amberStrong, radius = cell * 0.16f, center = center)
        }

        Mark.SUNK -> {
            drawRect(hitColor.copy(alpha = if (tint != null) 0.42f else 0.16f), topLeft = topLeft, size = Size(cell, cell))
            val pad = cell * 0.28f
            drawLine(
                hitColor,
                Offset(topLeft.x + pad, topLeft.y + pad),
                Offset(topLeft.x + cell - pad, topLeft.y + cell - pad),
                2f
            )
            drawLine(
                hitColor,
                Offset(topLeft.x + cell - pad, topLeft.y + pad),
                Offset(topLeft.x + pad, topLeft.y + cell - pad),
                2f
            )
        }

        Mark.SCAN_HOT -> {
            drawRect(Naval.greenBright.copy(alpha = 0.16f), topLeft = topLeft, size = Size(cell, cell))
            drawRect(
                Naval.greenBright.copy(alpha = 0.55f),
                topLeft = topLeft,
                size = Size(cell, cell),
                style = Stroke(1f)
            )
        }

        Mark.SCAN_COLD -> drawRect(
            Color.Black.copy(alpha = 0.28f),
            topLeft = topLeft,
            size = Size(cell, cell)
        )
    }
}
