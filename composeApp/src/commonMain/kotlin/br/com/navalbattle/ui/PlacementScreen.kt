package br.com.navalbattle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.BOARD_SIZE
import br.com.navalbattle.game.Board
import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Orientation
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.Ship
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import kotlinx.coroutines.launch

private const val DRAG_SLOP_PX = 14f

/** Estado de arrasto em curso, só existe enquanto o dedo está na tela. */
private data class DragState(
    val shipType: ShipClass,
    val grabCell: Coord,
    var isDragging: Boolean = false,
    var previewOrigin: Coord? = null,
    var previewValid: Boolean = false
)

@Composable
fun PlacementScreen(state: AppState, match: Match) {
    val board = match.placementBoard()
    var error by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var rotatingType by remember { mutableStateOf<ShipClass?>(null) }
    var rotatingFrom by remember { mutableStateOf(Orientation.HORIZONTAL) }
    val rotateAnim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar(
            if (match.opponent == Opponent.LOCAL) {
                "POSICIONAMENTO · ${match.sideName(match.placingSide).uppercase()}"
            } else {
                "POSICIONAMENTO"
            },
            match.mode.label.uppercase()
        )
        Gap(10)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Naval.abyss)
                .border(1.dp, Naval.line)
                .padding(3.dp)
                .pointerInput(board.ships) {
                    awaitEachDrag(
                        boardSize = { size.width.toFloat() },
                        findShip = { coord -> board.ships.firstOrNull { coord in it.cells } },
                        onStart = { ship, grabCell -> drag = DragState(ship.type, grabCell) },
                        onMove = { cell ->
                            drag?.let { d ->
                                val ship = board.ships.first { it.type == d.shipType }
                                val delta = Coord(cell.x - d.grabCell.x, cell.y - d.grabCell.y)
                                val len = ship.type.size
                                val maxX = if (ship.orientation == Orientation.HORIZONTAL) BOARD_SIZE - len else BOARD_SIZE - 1
                                val maxY = if (ship.orientation == Orientation.VERTICAL) BOARD_SIZE - len else BOARD_SIZE - 1
                                val origin = Coord(
                                    (ship.origin.x + delta.x).coerceIn(0, maxX),
                                    (ship.origin.y + delta.y).coerceIn(0, maxY)
                                )
                                val candidate = Ship(ship.type, origin, ship.orientation)
                                drag = d.copy(
                                    isDragging = true,
                                    previewOrigin = origin,
                                    previewValid = board.canPlace(candidate)
                                )
                            }
                        },
                        onEnd = {
                            val d = drag
                            if (d != null && d.isDragging) {
                                val ship = board.ships.first { it.type == d.shipType }
                                val origin = d.previewOrigin
                                if (origin != null && d.previewValid) {
                                    board.place(Ship(ship.type, origin, ship.orientation))
                                    error = null
                                } else {
                                    error = "Posição inválida"
                                }
                            } else if (d != null) {
                                // toque sem arrasto: gira o navio no lugar
                                val ship = board.ships.first { it.type == d.shipType }
                                val flipped = ship.orientation.flipped()
                                val len = ship.type.size
                                val maxX = if (flipped == Orientation.HORIZONTAL) BOARD_SIZE - len else BOARD_SIZE - 1
                                val maxY = if (flipped == Orientation.VERTICAL) BOARD_SIZE - len else BOARD_SIZE - 1
                                val origin = Coord(
                                    ship.origin.x.coerceIn(0, maxX),
                                    ship.origin.y.coerceIn(0, maxY)
                                )
                                val rotated = Ship(ship.type, origin, flipped)
                                if (board.canPlace(rotated)) {
                                    error = null
                                    rotatingType = ship.type
                                    rotatingFrom = ship.orientation
                                    scope.launch {
                                        rotateAnim.snapTo(0f)
                                        rotateAnim.animateTo(1f, tween(220, easing = LinearEasing))
                                        board.place(rotated)
                                        rotatingType = null
                                    }
                                } else {
                                    error = "Sem espaço para girar aqui"
                                }
                            }
                            drag = null
                        }
                    )
                }
        ) {
            val cell = size.width / BOARD_SIZE

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Naval.abyss2, Naval.abyss),
                    center = Offset(size.width * 0.5f, size.height * 0.45f),
                    radius = size.width * 0.75f
                )
            )
            for (i in 0..BOARD_SIZE) {
                val p = i * cell
                drawLine(Naval.gridLine, Offset(p, 0f), Offset(p, size.height), 1f)
                drawLine(Naval.gridLine, Offset(0f, p), Offset(size.width, p), 1f)
            }

            val draggingType = drag?.takeIf { it.isDragging }?.shipType
            board.ships.forEach { ship ->
                when {
                    ship.type == draggingType -> Unit // desenhado como preview abaixo
                    ship.type == rotatingType -> drawRotatingShip(ship, rotatingFrom, rotateAnim.value, cell, state.livery)
                    else -> drawFleetShip(ship, cell, state.livery, 1f)
                }
            }

            drag?.let { d ->
                if (d.isDragging) {
                    val ship = board.ships.first { it.type == d.shipType }
                    val origin = d.previewOrigin ?: ship.origin
                    val preview = Ship(ship.type, origin, ship.orientation)
                    val color = if (d.previewValid) Naval.amber else Naval.danger
                    preview.cells.forEach { c ->
                        drawRect(color.copy(alpha = 0.18f), topLeft = Offset(c.x * cell, c.y * cell), size = Size(cell, cell))
                        drawRect(color, topLeft = Offset(c.x * cell, c.y * cell), size = Size(cell, cell), style = Stroke(1.5f))
                    }
                    drawFleetShip(preview, cell, state.livery, 0.85f)
                }
            }
        }

        Gap(10)
        HudLabel(
            error ?: "Toque no navio para girar · arraste para reposicionar",
            if (error != null) Naval.danger else Naval.muted
        )

        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShipClass.fleet.forEach { type ->
                HudLabel("${type.label.uppercase()} ${type.size}", Naval.muted)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Aleatório", modifier = Modifier.weight(1f)) {
                match.randomizePlacingFleet()
                error = null
            }
            SecondaryButton("Voltar", modifier = Modifier.weight(1f)) {
                when {
                    // no local, volta para o posicionamento do primeiro comandante
                    match.backPlacement() -> state.handoffToPlacement(Side.PLAYER)
                    match.opponent == Opponent.LOCAL -> state.screen = Screen.NAMES
                    else -> state.quitToMenu()
                }
            }
        }
        Gap(8)
        PrimaryButton(
            "Confirmar",
            enabled = board.ships.size == ShipClass.fleet.size
        ) {
            match.confirmPlacement()
            // no modo local ainda falta o segundo comandante posicionar a frota dele;
            // a partir daí a batalha inteira corre na mesma tela
            if (match.phase == Phase.PLACEMENT) {
                state.handoffToPlacement(match.placingSide)
            } else {
                state.screen = Screen.BATTLE
            }
        }
        Gap(8)
        HudLabel(
            "ENCERRAR PARTIDA",
            Naval.danger,
            Modifier
                .fillMaxWidth()
                .clickable { state.quitToMenu() }
                .padding(vertical = 6.dp)
        )
    }
}

private fun DrawScope.drawFleetShip(ship: Ship, cell: Float, livery: br.com.navalbattle.design.Livery, alpha: Float) {
    val vertical = ship.orientation == Orientation.VERTICAL
    val length = ship.type.size * cell
    val cx = if (vertical) ship.origin.x * cell + cell / 2f else ship.origin.x * cell + length / 2f
    val cy = if (vertical) ship.origin.y * cell + length / 2f else ship.origin.y * cell + cell / 2f
    drawShip(ship.type, Offset(cx, cy), length, cell, vertical, livery, alpha)
}

/** Gira visualmente em torno do próprio centro enquanto a animação corre, sem mover células. */
private fun DrawScope.drawRotatingShip(
    ship: Ship,
    from: Orientation,
    progress: Float,
    cell: Float,
    livery: br.com.navalbattle.design.Livery
) {
    val vertical = from == Orientation.VERTICAL
    val length = ship.type.size * cell
    val cx = if (vertical) ship.origin.x * cell + cell / 2f else ship.origin.x * cell + length / 2f
    val cy = if (vertical) ship.origin.y * cell + length / 2f else ship.origin.y * cell + cell / 2f
    val center = Offset(cx, cy)
    val sign = if (from == Orientation.HORIZONTAL) 1f else -1f
    rotate(sign * progress * 90f, center) {
        drawShip(ship.type, center, length, cell, vertical, livery, 1f)
    }
}

/**
 * Distingue um toque simples de um arrasto: só passa a reportar movimento
 * depois que o dedo se afasta do ponto inicial além de [DRAG_SLOP_PX].
 */
private suspend fun PointerInputScope.awaitEachDrag(
    boardSize: () -> Float,
    findShip: (Coord) -> Ship?,
    onStart: (Ship, Coord) -> Unit,
    onMove: (Coord) -> Unit,
    onEnd: () -> Unit
) {
    awaitEachGesture {
        var firstDown: androidx.compose.ui.input.pointer.PointerInputChange? = null
        while (firstDown == null) {
            firstDown = awaitPointerEvent().changes.firstOrNull { it.pressed }
        }
        val down = firstDown
        val cellPx = boardSize() / BOARD_SIZE
        val startCoord = Coord((down.position.x / cellPx).toInt(), (down.position.y / cellPx).toInt())
        val ship = findShip(startCoord) ?: return@awaitEachGesture
        onStart(ship, startCoord)
        var dragging = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                onEnd()
                break
            }
            val dist = (change.position - down.position).getDistance()
            if (dist > DRAG_SLOP_PX) dragging = true
            if (dragging) {
                val coord = Coord((change.position.x / cellPx).toInt(), (change.position.y / cellPx).toInt())
                onMove(coord)
            }
            change.consume()
        }
    }
}
