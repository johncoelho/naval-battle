package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Um lado do combate: a frota posicionada e o que o adversário já descobriu dela.
 * As coleções são observáveis para a UI redesenhar sozinha a cada jogada.
 */
class Board {
    private val _ships = mutableStateListOf<Ship>()
    val ships: List<Ship> get() = _ships

    /** Marcações que o ATACANTE enxerga deste tabuleiro. */
    val marks = mutableStateMapOf<Coord, Mark>()

    private val hits = mutableSetOf<Coord>()
    private val diveUsed = mutableSetOf<ShipClass>()

    /** Ativada pelo dono do tabuleiro: anula a próxima varredura inimiga. */
    var smokeActive by mutableStateOf(false)

    fun canPlace(ship: Ship): Boolean {
        if (!ship.fitsOnBoard()) return false
        val occupied = _ships.flatMap { it.cells }.toSet()
        return ship.cells.none { it in occupied }
    }

    fun place(ship: Ship): Boolean {
        if (!canPlace(ship)) return false
        _ships.removeAll { it.type == ship.type }
        _ships.add(ship)
        return true
    }

    fun remove(type: ShipClass) = _ships.removeAll { it.type == type }

    fun shipAt(coord: Coord): Ship? = _ships.firstOrNull { coord in it.cells }

    fun isSunk(ship: Ship): Boolean = ship.cells.all { it in hits }

    fun allSunk(): Boolean = _ships.isNotEmpty() && _ships.all { isSunk(it) }

    fun remainingShips(): List<Ship> = _ships.filterNot { isSunk(it) }

    fun fireAt(coord: Coord, abilitiesEnabled: Boolean = true): ShotOutcome {
        val existing = marks[coord]
        if (existing == Mark.MISS || existing == Mark.HIT || existing == Mark.SUNK) {
            return ShotOutcome(coord, ShotResult.ALREADY_FIRED)
        }

        val ship = shipAt(coord)
        if (ship == null) {
            marks[coord] = Mark.MISS
            return ShotOutcome(coord, ShotResult.MISS)
        }

        // Imersão: só existe no modo Tático. No Clássico nenhum navio tem habilidade.
        if (abilitiesEnabled && ship.type == ShipClass.SUBMARINE && ShipClass.SUBMARINE !in diveUsed) {
            diveUsed += ShipClass.SUBMARINE
            marks[coord] = Mark.MISS
            return ShotOutcome(coord, ShotResult.MISS, ship.type, absorbedByDive = true)
        }

        hits += coord
        return if (isSunk(ship)) {
            ship.cells.forEach { marks[it] = Mark.SUNK }
            ShotOutcome(coord, ShotResult.SUNK, ship.type)
        } else {
            marks[coord] = Mark.HIT
            ShotOutcome(coord, ShotResult.HIT, ship.type)
        }
    }

    /** Revela uma linha inteira: células com navio viram quentes, o resto frias. */
    fun revealRow(y: Int): Boolean {
        if (consumeSmoke()) return false
        for (x in 0 until BOARD_SIZE) {
            val c = Coord(x, y)
            if (marks[c] == null) {
                marks[c] = if (shipAt(c) != null) Mark.SCAN_HOT else Mark.SCAN_COLD
            }
        }
        return true
    }

    /** Ping de sonar 3x3 centrado em [center]. */
    fun sonarPing(center: Coord): Boolean {
        if (consumeSmoke()) return false
        for (dy in -1..1) for (dx in -1..1) {
            val c = Coord(center.x + dx, center.y + dy)
            if (c.isValid() && marks[c] == null) {
                marks[c] = if (shipAt(c) != null) Mark.SCAN_HOT else Mark.SCAN_COLD
            }
        }
        return true
    }

    private fun consumeSmoke(): Boolean {
        if (!smokeActive) return false
        smokeActive = false
        return true
    }

    fun clear() {
        _ships.clear()
        marks.clear()
        hits.clear()
        diveUsed.clear()
        smokeActive = false
    }

    fun randomize(random: Random = Random.Default) {
        _ships.clear()
        for (type in ShipClass.fleet) {
            var placed = false
            var guard = 0
            while (!placed && guard++ < 500) {
                val orientation =
                    if (random.nextBoolean()) Orientation.HORIZONTAL else Orientation.VERTICAL
                val maxX = if (orientation == Orientation.HORIZONTAL) BOARD_SIZE - type.size else BOARD_SIZE - 1
                val maxY = if (orientation == Orientation.VERTICAL) BOARD_SIZE - type.size else BOARD_SIZE - 1
                val ship = Ship(
                    type,
                    Coord(random.nextInt(maxX + 1), random.nextInt(maxY + 1)),
                    orientation
                )
                placed = place(ship)
            }
        }
    }
}
