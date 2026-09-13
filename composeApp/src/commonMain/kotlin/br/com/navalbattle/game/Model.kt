package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

const val BOARD_SIZE = 10

/** Os textos de domínio vêm do dicionário: o jogo inteiro troca de idioma na hora. */
enum class GameMode(val key: K, val descriptionKey: K) {
    CLASSIC(K.MODE_CLASSIC, K.MODE_CLASSIC_SUB),
    TACTICAL(K.MODE_TACTICAL, K.MODE_TACTICAL_SUB);

    val label: String get() = t(key)
    val description: String get() = t(descriptionKey)
}

/**
 * Contra quem se joga: a IA do aparelho, outra pessoa no mesmo celular, ou outro
 * aparelho na mesma rede sem fio.
 */
enum class Opponent { AI, LOCAL, LAN }

enum class Ability(
    val code: String,
    /** Glifo Unicode — mantém a regra de nenhuma imagem no APK. */
    val icon: String,
    val key: K,
    val shortKey: K,
    val descKey: K,
    val cooldown: Int,
    val active: Boolean
) {
    AIR_RECON("REC", "📡", K.ABILITY_RADAR_FULL, K.ABILITY_RADAR, K.ABILITY_RADAR_DESC, 4, true),
    DOUBLE_BARRAGE("2X", "💥", K.ABILITY_DOUBLE_FULL, K.ABILITY_DOUBLE, K.ABILITY_DOUBLE_DESC, 3, true),
    SONAR_PING("SNR", "📶", K.ABILITY_SONAR_FULL, K.ABILITY_SONAR, K.ABILITY_SONAR_DESC, 2, true),
    DIVE("IMR", "🫧", K.ABILITY_DIVE_FULL, K.ABILITY_DIVE, K.ABILITY_DIVE_DESC, 0, false),
    SMOKE("FUM", "💨", K.ABILITY_SMOKE_FULL, K.ABILITY_SMOKE, K.ABILITY_SMOKE_DESC, 4, true);

    val label: String get() = t(key)
    val shortName: String get() = t(shortKey)
    val description: String get() = t(descKey)
}

enum class ShipClass(
    val key: K,
    val codename: String,
    val size: Int,
    val ability: Ability
) {
    CARRIER(K.SHIP_CARRIER, "Vanguarda", 5, Ability.AIR_RECON),
    BATTLESHIP(K.SHIP_BATTLESHIP, "Bastion", 4, Ability.DOUBLE_BARRAGE),
    CRUISER(K.SHIP_CRUISER, "Corsário", 3, Ability.SONAR_PING),
    SUBMARINE(K.SHIP_SUBMARINE, "Fantasma", 3, Ability.DIVE),
    DESTROYER(K.SHIP_DESTROYER, "Falcão", 2, Ability.SMOKE);

    val label: String get() = t(key)

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
