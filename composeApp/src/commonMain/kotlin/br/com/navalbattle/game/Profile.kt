package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.navalbattle.data.CloudProfile
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Session
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/** Patente do comandante. A carreira sobe por XP ganho em combate. */
enum class Rank(val key: K, val xp: Int) {
    RECRUTA(K.RANK_RECRUIT, 0),
    MARINHEIRO(K.RANK_SAILOR, 300),
    CABO(K.RANK_CORPORAL, 800),
    SARGENTO(K.RANK_SERGEANT, 1600),
    TENENTE(K.RANK_LIEUTENANT, 2800),
    CAPITAO_CORVETA(K.RANK_CORVETTE, 4500),
    CAPITAO_FRAGATA(K.RANK_FRIGATE, 7000),
    CAPITAO_MAR_GUERRA(K.RANK_CAPTAIN, 10500),
    CONTRA_ALMIRANTE(K.RANK_REAR_ADMIRAL, 15000),
    ALMIRANTE(K.RANK_ADMIRAL, 21000);

    val label: String get() = t(key)

    companion object {
        fun of(xp: Int): Rank = entries.last { xp >= it.xp }
        fun next(xp: Int): Rank? = entries.firstOrNull { it.xp > xp }
    }
}

/** Insígnia escolhida no perfil, desenhada no Canvas — nenhuma imagem no APK. */
enum class Insignia(val id: String, val key: K) {
    ANCHOR("anc", K.INSIGNIA_ANCHOR),
    TRIDENT("tri", K.INSIGNIA_TRIDENT),
    STAR("str", K.INSIGNIA_STAR),
    WHEEL("whl", K.INSIGNIA_WHEEL),
    WAVES("wav", K.INSIGNIA_WAVES),
    SKULL("skl", K.INSIGNIA_SKULL);

    val label: String get() = t(key)

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

    var name by mutableStateOf(prefs.getString(K_NAME, ""))
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

    /** Camuflagens já conquistadas, além das que vêm inclusas. */
    var owned by mutableStateOf(prefs.getString(K_OWNED, "std,br").split(",").filter { it.isNotBlank() }.toSet())
        private set
    var equipped by mutableStateOf(prefs.getString(K_EQUIPPED, "br"))
        private set

    /** Linhas de casco já conquistadas. A padrão vem com o jogo. */
    var ownedFleets by mutableStateOf(prefs.getString(K_FLEETS, "std").split(",").filter { it.isNotBlank() }.toSet())
        private set
    var equippedFleet by mutableStateOf(prefs.getString(K_FLEET, "std"))
        private set

    // ---------------- conta na nuvem ----------------

    var accountEmail by mutableStateOf(prefs.getString(K_EMAIL, ""))
        private set
    var accountId by mutableStateOf(prefs.getString(K_UID, ""))
        private set
    private var accessToken = prefs.getString(K_TOKEN, "")
    private var refreshToken = prefs.getString(K_REFRESH, "")

    val signedIn: Boolean get() = accountId.isNotBlank()

    /** Preferência de trilha: fica no aparelho, não viaja para a nuvem. */
    var musicOn: Boolean = prefs.getInt(K_MUSIC, 1) == 1
        private set

    fun setMusic(on: Boolean) {
        musicOn = on
        prefs.putInt(K_MUSIC, if (on) 1 else 0)
    }

    fun rememberSession(session: Session) {
        accountId = session.userId
        accountEmail = session.email
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        prefs.putString(K_UID, accountId)
        prefs.putString(K_EMAIL, accountEmail)
        prefs.putString(K_TOKEN, accessToken)
        prefs.putString(K_REFRESH, refreshToken)
        if (name.isBlank()) rename(session.username)
    }

    fun currentSession(): Session? {
        if (!signedIn) return null
        return Session(accountId, accountEmail, displayName, accessToken, refreshToken)
    }

    fun signOut() {
        accountId = ""; accountEmail = ""; accessToken = ""; refreshToken = ""
        prefs.putString(K_UID, ""); prefs.putString(K_EMAIL, "")
        prefs.putString(K_TOKEN, ""); prefs.putString(K_REFRESH, "")
    }

    /** A carreira como ela vai para a nuvem. */
    fun snapshot(): CloudProfile = CloudProfile(
        username = displayName,
        insignia = insignia.id,
        xp = xp,
        credits = credits,
        matches = matches,
        wins = wins,
        shots = shots,
        hits = hits,
        sunk = sunk,
        streak = streak,
        bestStreak = bestStreak,
        owned = owned.joinToString(","),
        equipped = equipped,
        fleets = ownedFleets.joinToString(","),
        fleet = equippedFleet
    )

    /** Adota a carreira que veio da nuvem e grava tudo no aparelho. */
    fun adopt(remote: CloudProfile) {
        name = remote.username.ifBlank { name }
        insignia = Insignia.of(remote.insignia)
        xp = remote.xp
        credits = remote.credits
        matches = remote.matches
        wins = remote.wins
        shots = remote.shots
        hits = remote.hits
        sunk = remote.sunk
        streak = remote.streak
        bestStreak = remote.bestStreak
        owned = remote.owned.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std", "br") }
        equipped = remote.equipped.ifBlank { "br" }
        ownedFleets = remote.fleets.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std") }
        equippedFleet = remote.fleet.ifBlank { "std" }
        persistAll()
    }

    private fun persistAll() {
        prefs.putString(K_NAME, name)
        prefs.putString(K_INSIGNIA, insignia.id)
        prefs.putInt(K_XP, xp)
        prefs.putInt(K_CREDITS, credits)
        prefs.putInt(K_MATCHES, matches)
        prefs.putInt(K_WINS, wins)
        prefs.putInt(K_SHOTS, shots)
        prefs.putInt(K_HITS, hits)
        prefs.putInt(K_SUNK, sunk)
        prefs.putInt(K_STREAK, streak)
        prefs.putInt(K_BEST_STREAK, bestStreak)
        prefs.putString(K_OWNED, owned.joinToString(","))
        prefs.putString(K_EQUIPPED, equipped)
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
        prefs.putString(K_FLEET, equippedFleet)
    }

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

    /** Em branco, o jogo chama o comandante pelo título traduzido. */
    val displayName: String get() = name.ifBlank { t(K.COMMANDER) }

    fun rename(value: String) {
        name = value.trim().take(18)
        prefs.putString(K_NAME, name)
    }

    /** Idioma escolhido, gravado no aparelho. */
    var langCode: String = prefs.getString(K_LANG, "pt")
        private set

    fun setLang(code: String) {
        langCode = code
        prefs.putString(K_LANG, code)
    }

    fun chooseInsignia(value: Insignia) {
        insignia = value
        prefs.putString(K_INSIGNIA, value.id)
    }

    fun owns(liveryId: String): Boolean = liveryId in owned

    fun ownsFleet(fleetId: String): Boolean = fleetId in ownedFleets

    /** Compra uma camuflagem com créditos de jogo. Falso quando não há saldo. */
    fun buy(liveryId: String, price: Int): Boolean {
        if (owns(liveryId)) return true
        if (credits < price) return false
        credits -= price
        owned = owned + liveryId
        prefs.putInt(K_CREDITS, credits)
        prefs.putString(K_OWNED, owned.joinToString(","))
        return true
    }

    /** Compra uma linha de casco com créditos de jogo. */
    fun buyFleet(fleetId: String, price: Int): Boolean {
        if (ownsFleet(fleetId)) return true
        if (credits < price) return false
        credits -= price
        ownedFleets = ownedFleets + fleetId
        prefs.putInt(K_CREDITS, credits)
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
        return true
    }

    fun equip(liveryId: String) {
        if (!owns(liveryId)) return
        equipped = liveryId
        prefs.putString(K_EQUIPPED, liveryId)
    }

    fun equipFleet(fleetId: String) {
        if (!ownsFleet(fleetId)) return
        equippedFleet = fleetId
        prefs.putString(K_FLEET, fleetId)
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
        name = ""
        insignia = Insignia.ANCHOR
        xp = 0
        credits = 500
        matches = 0; wins = 0; shots = 0; hits = 0; sunk = 0
        streak = 0; bestStreak = 0
        owned = setOf("std", "br")
        equipped = "br"
        ownedFleets = setOf("std")
        equippedFleet = "std"
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
        const val K_FLEETS = "fleets"
        const val K_FLEET = "fleet"
        const val K_UID = "uid"
        const val K_EMAIL = "email"
        const val K_TOKEN = "token"
        const val K_REFRESH = "refresh"
        const val K_MUSIC = "music"
        const val K_LANG = "lang"
    }
}
