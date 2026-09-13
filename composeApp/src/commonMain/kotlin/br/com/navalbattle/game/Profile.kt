package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.navalbattle.data.Prefs

/** Patente do comandante. A carreira sobe por XP ganho em combate. */
enum class Rank(val label: String, val xp: Int) {
    RECRUTA("Recruta", 0),
    MARINHEIRO("Marinheiro", 300),
    CABO("Cabo", 800),
    SARGENTO("Sargento", 1600),
    TENENTE("Tenente", 2800),
    CAPITAO_CORVETA("Capitão de Corveta", 4500),
    CAPITAO_FRAGATA("Capitão de Fragata", 7000),
    CAPITAO_MAR_GUERRA("Capitão de Mar e Guerra", 10500),
    CONTRA_ALMIRANTE("Contra-Almirante", 15000),
    ALMIRANTE("Almirante", 21000);

    companion object {
        fun of(xp: Int): Rank = entries.last { xp >= it.xp }
        fun next(xp: Int): Rank? = entries.firstOrNull { it.xp > xp }
    }
}

/** Insígnia escolhida no perfil, desenhada no Canvas — nenhuma imagem no APK. */
enum class Insignia(val id: String, val label: String) {
    ANCHOR("anc", "Âncora"),
    TRIDENT("tri", "Tridente"),
    STAR("str", "Estrela"),
    WHEEL("whl", "Timão"),
    WAVES("wav", "Vagas"),
    SKULL("skl", "Caveira");

    companion object {
        fun of(id: String): Insignia = entries.firstOrNull { it.id == id } ?: ANCHOR
    }
}

/** Resultado de uma partida, do ponto de vista da carreira. */
data class Award(val xp: Int, val credits: Int, val victory: Boolean, val rankUp: Rank?)

/**
 * Carreira do comandante: identidade, patente, créditos e estatísticas de todas as
 * partidas. Vive fora da partida e é gravada a cada mudança.
 */
class Profile(private val prefs: Prefs) {

    var name by mutableStateOf(prefs.getString(K_NAME, "Comandante"))
        private set
    var insignia by mutableStateOf(Insignia.of(prefs.getString(K_INSIGNIA, Insignia.ANCHOR.id)))
        private set

    var xp by mutableStateOf(prefs.getInt(K_XP, 0))
        private set
    var credits by mutableStateOf(prefs.getInt(K_CREDITS, 500))
        private set

    var matches by mutableStateOf(prefs.getInt(K_MATCHES, 0))
        private set
    var wins by mutableStateOf(prefs.getInt(K_WINS, 0))
        private set
    var shots by mutableStateOf(prefs.getInt(K_SHOTS, 0))
        private set
    var hits by mutableStateOf(prefs.getInt(K_HITS, 0))
        private set
    var sunk by mutableStateOf(prefs.getInt(K_SUNK, 0))
        private set
    var streak by mutableStateOf(prefs.getInt(K_STREAK, 0))
        private set
    var bestStreak by mutableStateOf(prefs.getInt(K_BEST_STREAK, 0))
        private set

    /** Librés já conquistadas, além da que vem inclusa. */
    var owned by mutableStateOf(prefs.getString(K_OWNED, "std,br").split(",").filter { it.isNotBlank() }.toSet())
        private set
    var equipped by mutableStateOf(prefs.getString(K_EQUIPPED, "br"))
        private set

    val rank: Rank get() = Rank.of(xp)
    val losses: Int get() = matches - wins
    val accuracy: Int get() = if (shots == 0) 0 else (hits * 100) / shots
    val winRate: Int get() = if (matches == 0) 0 else (wins * 100) / matches

    /** Quanto falta, de 0 a 1, para a próxima patente. */
    val rankProgress: Float
        get() {
            val next = Rank.next(xp) ?: return 1f
            val floor = rank.xp
            return ((xp - floor).toFloat() / (next.xp - floor)).coerceIn(0f, 1f)
        }

    fun rename(value: String) {
        name = value.trim().take(18).ifBlank { "Comandante" }
        prefs.putString(K_NAME, name)
    }

    fun chooseInsignia(value: Insignia) {
        insignia = value
        prefs.putString(K_INSIGNIA, value.id)
    }

    fun owns(liveryId: String): Boolean = liveryId in owned

    /** Compra com créditos de jogo. Devolve falso quando não há saldo. */
    fun buy(liveryId: String, price: Int): Boolean {
        if (owns(liveryId)) return true
        if (credits < price) return false
        credits -= price
        owned = owned + liveryId
        prefs.putInt(K_CREDITS, credits)
        prefs.putString(K_OWNED, owned.joinToString(","))
        return true
    }

    fun equip(liveryId: String) {
        if (!owns(liveryId)) return
        equipped = liveryId
        prefs.putString(K_EQUIPPED, liveryId)
    }

    /**
     * Fecha uma partida contra a IA: soma XP, créditos e estatísticas. Partidas
     * locais não valem carreira — os dois jogam no mesmo perfil do aparelho.
     */
    fun registerMatch(victory: Boolean, shotsFired: Int, hitsLanded: Int, shipsSunk: Int, turns: Int): Award {
        val before = rank
        // vitória rende o dobro; precisão e economia de turnos entram como bônus
        val base = if (victory) 220 else 80
        val precision = if (shotsFired == 0) 0 else (hitsLanded * 100) / shotsFired
        val bonus = precision * 2 + shipsSunk * 15 + if (victory && turns <= 40) 120 else 0
        val gainedXp = base + bonus
        val gainedCredits = (base + bonus) / 3

        xp += gainedXp
        credits += gainedCredits
        matches += 1
        if (victory) wins += 1
        shots += shotsFired
        hits += hitsLanded
        sunk += shipsSunk
        streak = if (victory) streak + 1 else 0
        if (streak > bestStreak) bestStreak = streak

        prefs.putInt(K_XP, xp)
        prefs.putInt(K_CREDITS, credits)
        prefs.putInt(K_MATCHES, matches)
        prefs.putInt(K_WINS, wins)
        prefs.putInt(K_SHOTS, shots)
        prefs.putInt(K_HITS, hits)
        prefs.putInt(K_SUNK, sunk)
        prefs.putInt(K_STREAK, streak)
        prefs.putInt(K_BEST_STREAK, bestStreak)

        return Award(gainedXp, gainedCredits, victory, rank.takeIf { it != before })
    }

    /** Zera a carreira inteira — pedido explícito do comandante no perfil. */
    fun reset() {
        prefs.clear()
        name = "Comandante"
        insignia = Insignia.ANCHOR
        xp = 0
        credits = 500
        matches = 0; wins = 0; shots = 0; hits = 0; sunk = 0
        streak = 0; bestStreak = 0
        owned = setOf("std", "br")
        equipped = "br"
    }

    private companion object {
        const val K_NAME = "name"
        const val K_INSIGNIA = "insignia"
        const val K_XP = "xp"
        const val K_CREDITS = "credits"
        const val K_MATCHES = "matches"
        const val K_WINS = "wins"
        const val K_SHOTS = "shots"
        const val K_HITS = "hits"
        const val K_SUNK = "sunk"
        const val K_STREAK = "streak"
        const val K_BEST_STREAK = "best_streak"
        const val K_OWNED = "owned"
        const val K_EQUIPPED = "equipped"
    }
}
