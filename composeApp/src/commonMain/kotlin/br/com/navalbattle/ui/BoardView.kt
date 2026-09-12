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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.navalbattle.design.Livery
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

private const val TRAVEL_MS = 260

@Composable
fun BoardView(
    board: Board,
    livery: Livery,
    showShips: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    sweep: Boolean = true,
    preview: Ship? = null,
    previewValid: Boolean = true,
    impact: Impact? = null,
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
    val impactDuration = if (impact?.tone == Tone.SUNK) 1500 else 650
    LaunchedEffect(impact?.id) {
        if (impact != null) {
            impactAnim.snapTo(1f)
            travelAnim.snapTo(0f)
            travelAnim.animateTo(1f, tween(TRAVEL_MS, easing = LinearEasing))
            impactAnim.snapTo(0f)
            impactAnim.animateTo(1f, tween(impactDuration, easing = LinearEasing))
        }
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(impact?.id) {
        if (impact?.tone == Tone.SUNK) {
            delay(TRAVEL_MS.toLong())
            for (offsetPx in listOf(-14f, 10f, -7f, 4f, -2f, 0f)) {
                shake.animateTo(offsetPx, tween(45, easing = LinearEasing))
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
                drawFleetShip(ship, cell, livery, if (sunk) 0.35f else 1f)
            }
        }

        // marcações
        board.marks.forEach { (coord, mark) ->
            val topLeft = Offset(coord.x * cell, coord.y * cell)
            drawMark(mark, topLeft, cell)
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
                    drawImpactBurst(imp, target, cell, t)
                }
            }
        }
    }
}

private fun DrawScope.drawIncomingMissile(target: Offset, cell: Float, tt: Float) {
    val origin = Offset(target.x, 0f)
    val pos = Offset(origin.x, origin.y + (target.y - origin.y) * tt)
    val trailLen = cell * 1.1f
    val trailStart = Offset(pos.x, (pos.y - trailLen).coerceAtLeast(0f))
    drawLine(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Naval.amberStrong.copy(alpha = 0.75f)),
            startY = trailStart.y,
            endY = pos.y
        ),
        start = trailStart,
        end = pos,
        strokeWidth = cell * 0.09f
    )
    drawCircle(Naval.amberStrong, radius = cell * 0.1f, center = pos)
    drawCircle(Color.White.copy(alpha = 0.8f), radius = cell * 0.05f, center = pos)
}

private fun DrawScope.drawImpactBurst(imp: Impact, c: Offset, cell: Float, t: Float) {
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
            // respingo: anéis d'água se espalhando
            for (i in 0..2) {
                val local = ((t - i * 0.1f) / (1f - i * 0.1f)).coerceIn(0f, 1f)
                drawCircle(
                    color = Naval.greenBright.copy(alpha = (1f - local) * 0.35f),
                    radius = cell * (0.25f + local * 0.9f),
                    center = c,
                    style = Stroke(1.5f)
                )
            }
        }

        Tone.SUNK -> drawSinkingShip(imp, c, cell, t)
        else -> Unit
    }
}

/** Fogo ao longo do casco, marinheiros pulando na água e a silhueta submergindo. */
private fun DrawScope.drawSinkingShip(imp: Impact, c: Offset, cell: Float, t: Float) {
    // segundo anel de choque, maior e um pouco atrasado
    val t2 = ((t - 0.1f) / 0.9f).coerceIn(0f, 1f)
    drawCircle(
        color = Naval.amberStrong.copy(alpha = (1f - t2) * 0.6f),
        radius = cell * (0.4f + t2 * 2.6f),
        center = c,
        style = Stroke(2.5f)
    )

    val ship = imp.sunkShip
    val cells = ship?.cells ?: listOf(imp.coord)
    val sinkT = t2 // 0 = recém atingido, 1 = totalmente submerso

    cells.forEachIndexed { i, coord ->
        val cx = coord.x * cell + cell / 2f
        val cy = coord.y * cell + cell / 2f + sinkT * cell * 0.5f // afunda visualmente
        val fade = 1f - sinkT

        // fumaça densa subindo
        val riseSeed = i * 0.37f
        for (p in 0..1) {
            val local = ((t - (i * 0.05f + p * 0.18f)) / 0.85f).coerceIn(0f, 1f)
            val rise = local * cell * 2.0f
            val sway = sin((local * 6f + riseSeed) * 3.1416f) * cell * 0.12f
            drawCircle(
                color = Naval.inkSoft.copy(alpha = (1f - local) * 0.45f),
                radius = cell * (0.13f + local * 0.17f),
                center = Offset(cx + sway, cy - rise)
            )
        }

        // fogo lambendo o casco, tremeluzindo
        if (fade > 0.05f) {
            val flicker = 0.6f + 0.4f * sin((t * 50f + i * 2f))
            drawCircle(
                color = Naval.amberStrong.copy(alpha = fade * 0.8f * flicker),
                radius = cell * 0.16f * flicker,
                center = Offset(cx, cy - cell * 0.1f)
            )
            drawCircle(
                color = Naval.danger.copy(alpha = fade * 0.7f),
                radius = cell * 0.1f,
                center = Offset(cx, cy - cell * 0.05f)
            )
        }
    }

    // marinheiros pulando das pontas do navio para a água
    val ends = listOf(cells.first(), cells.last())
    ends.forEachIndexed { i, coord ->
        val local = ((t - 0.05f) / 0.7f).coerceIn(0f, 1f)
        if (local <= 0f) return@forEachIndexed
        val dir = if (i == 0) -1f else 1f
        val startX = coord.x * cell + cell / 2f
        val startY = coord.y * cell + cell / 2f
        val travel = cell * 1.3f
        val jumpX = startX + dir * travel * local
        val arc = sin(3.1416f * local) * cell * 0.55f
        val jumpY = startY - arc + local * cell * 0.2f

        if (local < 0.92f) {
            drawCircle(
                color = Naval.ink.copy(alpha = (1f - local * 0.5f)),
                radius = cell * 0.06f,
                center = Offset(jumpX, jumpY)
            )
        } else {
            // splash na água
            val splashT = ((local - 0.92f) / 0.08f).coerceIn(0f, 1f)
            drawCircle(
                color = Naval.greenBright.copy(alpha = (1f - splashT) * 0.6f),
                radius = cell * (0.08f + splashT * 0.22f),
                center = Offset(jumpX, jumpY),
                style = Stroke(1.5f)
            )
        }
    }
}

private fun DrawScope.drawFleetShip(ship: Ship, cell: Float, livery: Livery, alpha: Float) {
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
        livery = livery,
        alpha = alpha
    )
}

private fun DrawScope.drawMark(mark: Mark, topLeft: Offset, cell: Float) {
    val center = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f)
    when (mark) {
        Mark.MISS -> drawCircle(
            Naval.inkSoft.copy(alpha = 0.45f),
            radius = cell * 0.17f,
            center = center,
            style = Stroke(1.5f)
        )

        Mark.HIT -> {
            drawRect(Naval.danger.copy(alpha = 0.32f), topLeft = topLeft, size = Size(cell, cell))
            drawRect(Naval.danger, topLeft = topLeft, size = Size(cell, cell), style = Stroke(1f))
            drawCircle(Naval.amberStrong, radius = cell * 0.16f, center = center)
        }

        Mark.SUNK -> {
            drawRect(Naval.danger.copy(alpha = 0.16f), topLeft = topLeft, size = Size(cell, cell))
            val pad = cell * 0.28f
            drawLine(
                Naval.danger,
                Offset(topLeft.x + pad, topLeft.y + pad),
                Offset(topLeft.x + cell - pad, topLeft.y + cell - pad),
                2f
            )
            drawLine(
                Naval.danger,
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
