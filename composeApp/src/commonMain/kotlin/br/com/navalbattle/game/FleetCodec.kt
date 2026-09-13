package br.com.navalbattle.game

/**
 * Converte uma frota em texto para viajar pela rede: `CARRIER,3,4,H`.
 * Formato curto de propósito — cabe numa linha e é legível em depuração.
 */
object FleetCodec {

    fun encode(ships: List<Ship>): List<String> = ships.map { ship ->
        val dir = if (ship.orientation == Orientation.HORIZONTAL) "H" else "V"
        "${ship.type.name},${ship.origin.x},${ship.origin.y},$dir"
    }

    fun decode(raw: String): List<Ship> = raw
        .split(";")
        .filter { it.isNotBlank() }
        .mapNotNull { entry ->
            val parts = entry.split(",")
            if (parts.size != 4) return@mapNotNull null
            val type = ShipClass.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val x = parts[1].toIntOrNull() ?: return@mapNotNull null
            val y = parts[2].toIntOrNull() ?: return@mapNotNull null
            val orientation =
                if (parts[3] == "V") Orientation.VERTICAL else Orientation.HORIZONTAL
            Ship(type, Coord(x, y), orientation)
        }
}
