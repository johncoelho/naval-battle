package br.com.navalbattle.game

import kotlin.random.Random

/**
 * IA de caça: enquanto não tem contato, atira em padrão de tabuleiro de damas
 * (nenhum navio de 2 células escapa dessa malha). Com contato, persegue a linha
 * de acertos até afundar o alvo.
 */
class Ai(private val random: Random = Random.Default) {

    private val pendingTargets = ArrayDeque<Coord>()
    private val confirmedHits = mutableListOf<Coord>()

    fun nextShot(enemyBoard: Board): Coord {
        while (pendingTargets.isNotEmpty()) {
            val candidate = pendingTargets.removeFirst()
            if (isOpen(enemyBoard, candidate)) return candidate
        }
        return parityShot(enemyBoard)
    }

    fun registerOutcome(outcome: ShotOutcome, enemyBoard: Board) {
        when (outcome.result) {
            ShotResult.HIT -> {
                confirmedHits += outcome.coord
                queueNeighbors(outcome.coord, enemyBoard)
            }

            ShotResult.SUNK -> {
                confirmedHits.clear()
                pendingTargets.clear()
            }

            else -> Unit
        }
    }

    /** Alimenta a fila de alvos com contatos vindos de uma varredura de sonar. */
    fun addTargets(coords: List<Coord>) {
        coords.forEach { if (it !in pendingTargets) pendingTargets.addLast(it) }
    }

    private fun queueNeighbors(coord: Coord, enemyBoard: Board) {
        // Com dois acertos alinhados, prioriza continuar na mesma direção.
        val aligned = confirmedHits.filter { it != coord && (it.x == coord.x || it.y == coord.y) }
        val candidates = if (aligned.isNotEmpty()) {
            val other = aligned.first()
            if (other.y == coord.y) {
                val xs = (confirmedHits + coord).filter { it.y == coord.y }.map { it.x }
                listOf(Coord(xs.min() - 1, coord.y), Coord(xs.max() + 1, coord.y))
            } else {
                val ys = (confirmedHits + coord).filter { it.x == coord.x }.map { it.y }
                listOf(Coord(coord.x, ys.min() - 1), Coord(coord.x, ys.max() + 1))
            }
        } else {
            listOf(
                Coord(coord.x - 1, coord.y),
                Coord(coord.x + 1, coord.y),
                Coord(coord.x, coord.y - 1),
                Coord(coord.x, coord.y + 1)
            )
        }
        candidates.filter { isOpen(enemyBoard, it) }.forEach { pendingTargets.addLast(it) }
    }

    private fun parityShot(enemyBoard: Board): Coord {
        val open = mutableListOf<Coord>()
        val openParity = mutableListOf<Coord>()
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) {
            val c = Coord(x, y)
            if (!isOpen(enemyBoard, c)) continue
            open += c
            if ((x + y) % 2 == 0) openParity += c
        }
        val pool = openParity.ifEmpty { open }
        return pool[random.nextInt(pool.size)]
    }

    private fun isOpen(board: Board, coord: Coord): Boolean {
        if (!coord.isValid()) return false
        val mark = board.marks[coord]
        return mark != Mark.MISS && mark != Mark.HIT && mark != Mark.SUNK
    }

    /** No modo tático a IA usa o sonar quando está sem pista alguma. */
    fun shouldUseSonar(mode: GameMode, turn: Int): Boolean =
        mode == GameMode.TACTICAL && pendingTargets.isEmpty() && turn % 4 == 3

    fun sonarTarget(enemyBoard: Board): Coord {
        val unknown = mutableListOf<Coord>()
        for (y in 1 until BOARD_SIZE - 1) for (x in 1 until BOARD_SIZE - 1) {
            val c = Coord(x, y)
            if (enemyBoard.marks[c] == null) unknown += c
        }
        return if (unknown.isEmpty()) Coord(4, 4) else unknown[random.nextInt(unknown.size)]
    }
}
