package br.com.navalbattle.game

const val BOARD_SIZE = 10

enum class GameMode(val label: String, val description: String) {
    CLASSIC("Clássico", "Um tiro por turno, sem habilidades"),
    TACTICAL("Tático", "Habilidades por classe de navio")
}

/** Contra quem se joga: a IA do aparelho ou outra pessoa no mesmo celular. */
/**
 * Contra quem se joga: a IA do aparelho, outra pessoa no mesmo celular, ou outro
 * aparelho na mesma rede sem fio.
 */
enum class Opponent { AI, LOCAL, LAN }

enum class Ability(
    val code: String,
    val label: String,
    val description: String,
    val cooldown: Int,
    val active: Boolean
) {
    AIR_RECON("REC", "Reconhecimento aéreo", "Revela uma linha inteira do grid inimigo", 4, true),
    DOUBLE_BARRAGE("2X", "Barragem dupla", "Dispara em duas células no mesmo turno", 3, true),
    SONAR_PING("SNR", "Ping de sonar", "Marca uma área 3x3 como quente ou fria", 2, true),
    DIVE("IMR", "Imersão", "Absorve o primeiro acerto no submarino", 0, false),
    SMOKE("FUM", "Cortina de fumaça", "Bloqueia a próxima varredura inimiga", 4, true)
}

enum class ShipClass(
    val label: String,
    val codename: String,
    val size: Int,
    val ability: Ability
) {
    CARRIER("Porta-aviões", "Vanguarda", 5, Ability.AIR_RECON),
    BATTLESHIP("Encouraçado", "Bastion", 4, Ability.DOUBLE_BARRAGE),
    CRUISER("Cruzador", "Corsário", 3, Ability.SONAR_PING),
    SUBMARINE("Submarino", "Fantasma", 3, Ability.DIVE),
    DESTROYER("Destroyer", "Falcão", 2, Ability.SMOKE);

    companion object {
        val fleet: List<ShipClass> = listOf(CARRIER, BATTLESHIP, CRUISER, SUBMARINE, DESTROYER)
    }
}

enum class Orientation { HORIZONTAL, VERTICAL;
    fun flipped() = if (this == HORIZONTAL) VERTICAL else HORIZONTAL
}

data class Coord(val x: Int, val y: Int) {
    val label: String get() = "${('A' + x)}${y + 1}"
    fun isValid() = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE
}

data class Ship(
    val type: ShipClass,
    val origin: Coord,
    val orientation: Orientation
) {
    val cells: List<Coord>
        get() = (0 until type.size).map { i ->
            if (orientation == Orientation.HORIZONTAL) Coord(origin.x + i, origin.y)
            else Coord(origin.x, origin.y + i)
        }

    fun fitsOnBoard(): Boolean = cells.all { it.isValid() }
}

enum class ShotResult { MISS, HIT, SUNK, ALREADY_FIRED }

data class ShotOutcome(
    val coord: Coord,
    val result: ShotResult,
    val ship: ShipClass? = null,
    val absorbedByDive: Boolean = false
)

/** Marcações visíveis num tabuleiro atacado. */
enum class Mark { MISS, HIT, SUNK, SCAN_HOT, SCAN_COLD }
