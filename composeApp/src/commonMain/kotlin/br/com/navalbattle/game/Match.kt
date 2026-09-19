package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

enum class Phase { PLACEMENT, BATTLE, RESULT }

enum class Side {
    PLAYER, ENEMY;

    fun other(): Side = if (this == PLAYER) ENEMY else PLAYER
}

enum class Tone { HIT, SUNK, MISS, SCAN, INFO }

data class Callout(val main: String, val sub: String, val tone: Tone, val id: Long)

data class Impact(val coord: Coord, val tone: Tone, val id: Long, val sunkShip: Ship? = null)

/** Emoji ou grito de guerra mandado por um lado — decoração, não mexe na partida. */
data class Taunt(val from: Side, val code: String, val id: Long)

/**
 * Estado completo de uma partida. Os dois lados são simétricos: contra a IA o lado
 * ENEMY é jogado pela máquina, no modo local é a segunda pessoa no mesmo aparelho.
 */
class Match(
    val mode: GameMode,
    val opponent: Opponent = Opponent.AI,
    /**
     * Que lado este aparelho comanda. Só muda em rede: quem hospeda joga como PLAYER e
     * abre a partida, quem entra joga como ENEMY. Os dois aparelhos rodam a mesma
     * partida, e a resolução do tiro é idêntica dos dois lados.
     */
    val mySide: Side = Side.PLAYER,
    private val random: Random = Random.Default
) {

    val playerBoard = Board()
    val enemyBoard = Board()

    /** Em rede, cada aparelho só posiciona a própria frota e espera a do adversário. */
    private val remote: Boolean get() = opponent == Opponent.LAN || opponent == Opponent.ONLINE
    var fleetsReady by mutableStateOf(0)
        private set

    fun board(side: Side): Board = if (side == Side.PLAYER) playerBoard else enemyBoard

    private val ai = Ai(random)

    var phase by mutableStateOf(Phase.PLACEMENT)
        private set
    var turnOwner by mutableStateOf(Side.PLAYER)
        private set

    /** Quem está posicionando a frota agora (no modo local os dois posicionam, em sequência). */
    var placingSide by mutableStateOf(Side.PLAYER)
        private set

    var turnCount by mutableStateOf(1)
        private set
    var callout by mutableStateOf<Callout?>(null)
        private set

    /** Impacto causado PELO lado PLAYER (cai no tabuleiro adversário). */
    var playerImpact by mutableStateOf<Impact?>(null)
        private set

    /** Impacto causado PELO lado ENEMY. */
    var enemyImpact by mutableStateOf<Impact?>(null)
        private set

    /** Id do disparo mais recente — a tela só anima o impacto que acabou de acontecer. */
    var lastImpactId by mutableStateOf(0L)
        private set

    var winner by mutableStateOf<Side?>(null)
        private set

    /** Último emoji/grito recebido — só o mais recente fica na tela. */
    var lastTaunt by mutableStateOf<Taunt?>(null)
        private set

    fun sendTaunt(from: Side, code: String) {
        lastTaunt = Taunt(from, code, nextId())
    }

    var pendingAbility by mutableStateOf<Ability?>(null)
        private set
    private var extraShots by mutableStateOf(0)
    private val cooldowns = mutableMapOf<Side, MutableMap<Ability, Int>>()

    var playerShots by mutableStateOf(0)
        private set
    var playerHits by mutableStateOf(0)
        private set
    var enemyShots by mutableStateOf(0)
        private set
    var enemyHits by mutableStateOf(0)
        private set

    private var calloutSeq = 0L

    /** Nomes escolhidos pelos dois jogadores no modo local (vazio = nome padrão). */
    var nameOne by mutableStateOf("")
        private set
    var nameTwo by mutableStateOf("")
        private set

    init {
        playerBoard.randomize(random)
        enemyBoard.randomize(random)
        // em rede cada aparelho posiciona só a própria frota
        if (remote) placingSide = mySide
    }

    fun setName(side: Side, name: String) {
        val clean = name.trim().take(16)
        if (side == Side.PLAYER) nameOne = clean else nameTwo = clean
    }

    fun sideName(side: Side): String = when (opponent) {
        Opponent.AI -> if (side == Side.PLAYER) t(K.YOU) else t(K.ENEMY)
        Opponent.LOCAL, Opponent.LAN, Opponent.ONLINE ->
            if (side == Side.PLAYER) nameOne.ifBlank { t(K.NAMES_ONE) }
            else nameTwo.ifBlank { t(K.NAMES_TWO) }
    }

    // ---------------- posicionamento ----------------

    fun placementBoard(): Board = board(placingSide)

    fun randomizePlacingFleet() = placementBoard().randomize(random)

    fun placementReady(): Boolean = placementBoard().ships.size == ShipClass.fleet.size

    /**
     * Confirma a frota de quem está posicionando. No modo local passa a vez para o
     * segundo comandante; quando os dois terminam, a batalha começa.
     */
    fun confirmPlacement() {
        if (!placementReady()) return
        when {
            // em rede a batalha só abre quando as duas frotas estiverem a bordo
            remote -> markFleetReady()
            opponent == Opponent.LOCAL && placingSide == Side.PLAYER -> placingSide = Side.ENEMY
            else -> startBattle()
        }
    }

    /** Uma frota ficou pronta; com as duas, a batalha começa nos dois aparelhos. */
    private fun markFleetReady() {
        fleetsReady += 1
        if (fleetsReady >= 2) startBattle()
    }

    /**
     * Recebe a frota do adversário pela rede e a coloca no tabuleiro dele. Os dois
     * aparelhos passam a ter a mesma partida e resolvem cada tiro igual.
     */
    fun applyRemoteFleet(ships: List<Ship>) {
        if (!remote) return
        val board = board(mySide.other())
        board.clear()
        ships.forEach { board.place(it) }
        markFleetReady()
    }

    /** Coordenada sorteada para o disparo automático — quem escolhe é o dono do turno. */
    fun pickTarget(): Coord? {
        val target = board(turnOwner.other())
        val open = mutableListOf<Coord>()
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) {
            val c = Coord(x, y)
            val mark = target.marks[c]
            if (mark != Mark.MISS && mark != Mark.HIT && mark != Mark.SUNK) open += c
        }
        return if (open.isEmpty()) null else open[random.nextInt(open.size)]
    }

    /** Botão voltar do modo local: devolve o posicionamento ao primeiro comandante. */
    fun backPlacement(): Boolean {
        if (phase != Phase.PLACEMENT || placingSide != Side.ENEMY) return false
        placingSide = Side.PLAYER
        return true
    }

    /** O adversário deixou a partida: quem ficou leva a vitória. */
    fun abandon(winnerSide: Side) {
        if (phase == Phase.RESULT) return
        finish(winnerSide)
    }

    /**
     * Marcado quando ESTE aparelho ficou 60s em segundo plano numa partida online
     * sem voltar — ver [AppState.onForegroundChanged]. Diferente de uma derrota de
     * verdade: quem passa daqui não soma nem perde pontos ranqueados por isso (só
     * quem venceu por desistência é que registra o resultado normalmente).
     */
    var forfeitedBySelf by mutableStateOf(false)
        private set

    fun forfeitByTimeout() {
        if (phase == Phase.RESULT) return
        forfeitedBySelf = true
        finish(mySide.other())
        say(t(K.CALL_PAUSE_TIMEOUT), t(K.CALL_PAUSE_TIMEOUT_SUB), Tone.MISS)
    }

    private fun startBattle() {
        phase = Phase.BATTLE
        turnOwner = Side.PLAYER
        if (mode == GameMode.TACTICAL) {
            say(t(K.CALL_FLEET_READY), t(K.CALL_FLEET_READY_TACTICAL), Tone.INFO)
        } else {
            say(t(K.CALL_FLEET_READY), t(K.CALL_FLEET_READY_CLASSIC), Tone.INFO)
        }
    }

    // ---------------- habilidades ----------------

    private fun cooldownsOf(side: Side): MutableMap<Ability, Int> =
        cooldowns.getOrPut(side) { mutableMapOf() }

    fun abilityCooldown(ability: Ability): Int = cooldownsOf(turnOwner)[ability] ?: 0

    /**
     * [ignoreCooldown] deixa passar mesmo com a habilidade ainda recarregando — é o
     * cartucho avulso comprado na loja, que a tela consulta antes de habilitar o botão.
     */
    fun abilityAvailable(ability: Ability, ignoreCooldown: Boolean = false): Boolean {
        if (mode != GameMode.TACTICAL || !ability.active) return false
        if (phase != Phase.BATTLE) return false
        // contra a IA as habilidades só ficam ativas na vez do humano
        if (opponent == Opponent.AI && turnOwner != Side.PLAYER) return false
        // em rede, só na sua vez e no seu lado
        if (remote && turnOwner != mySide) return false
        val owner = ShipClass.fleet.firstOrNull { it.ability == ability } ?: return false
        val myBoard = board(turnOwner)
        val ship = myBoard.ships.firstOrNull { it.type == owner } ?: return false
        if (myBoard.isSunk(ship)) return false
        return ignoreCooldown || abilityCooldown(ability) == 0
    }

    /**
     * [ignoreCooldown] precisa viajar para o outro aparelho em rede (ver
     * `Protocol.ability`) — senão o lado que recebe recusa aplicar o efeito porque,
     * do ponto de vista dele, a habilidade ainda está recarregando.
     */
    fun selectAbility(ability: Ability, ignoreCooldown: Boolean = false) {
        if (!abilityAvailable(ability, ignoreCooldown)) return
        when (ability) {
            Ability.SMOKE -> {
                board(turnOwner).smokeActive = true
                cooldownsOf(turnOwner)[ability] = ability.cooldown
                say(t(K.CALL_SMOKE), t(K.CALL_SMOKE_SUB), Tone.SCAN)
                endTurn()
            }

            Ability.DOUBLE_BARRAGE -> {
                cooldownsOf(turnOwner)[ability] = ability.cooldown
                extraShots = 1
                pendingAbility = null
                say(t(K.CALL_DOUBLE), t(K.CALL_DOUBLE_SUB), Tone.INFO)
            }

            else -> {
                pendingAbility = if (pendingAbility == ability) null else ability
            }
        }
    }

    // ---------------- ação de turno ----------------

    /** Dispara (ou usa a habilidade selecionada) contra o tabuleiro adversário de quem tem a vez. */
    fun act(coord: Coord): ShotOutcome? {
        if (phase != Phase.BATTLE) return null
        val attacker = turnOwner
        val target = board(attacker.other())

        when (val ability = pendingAbility) {
            Ability.AIR_RECON -> {
                val worked = target.revealRow(coord.y)
                cooldownsOf(attacker)[ability] = ability.cooldown
                pendingAbility = null
                if (worked) say(t(K.CALL_RECON), t(K.CALL_RECON_SUB, coord.y + 1), Tone.SCAN)
                else say(t(K.CALL_SCAN_FAIL), t(K.CALL_SCAN_FAIL_SUB), Tone.MISS)
                endTurn()
                return null
            }

            Ability.SONAR_PING -> {
                val worked = target.sonarPing(coord)
                cooldownsOf(attacker)[ability] = ability.cooldown
                pendingAbility = null
                if (worked) say(t(K.CALL_SONAR), t(K.CALL_SONAR_SUB, coord.label), Tone.SCAN)
                else say(t(K.CALL_SCAN_FAIL), t(K.CALL_SCAN_FAIL_SUB), Tone.MISS)
                endTurn()
                return null
            }

            else -> Unit
        }

        val outcome = target.fireAt(coord, abilitiesEnabled = mode == GameMode.TACTICAL)
        if (outcome.result == ShotResult.ALREADY_FIRED) return null

        val sunkShip = if (outcome.result == ShotResult.SUNK) {
            target.ships.firstOrNull { it.type == outcome.ship }
        } else null
        val impact = Impact(coord, outcome.tone(), nextId(), sunkShip)
        lastImpactId = impact.id

        val scored = outcome.result == ShotResult.HIT || outcome.result == ShotResult.SUNK
        if (attacker == Side.PLAYER) {
            playerImpact = impact
            playerShots++
            if (scored) playerHits++
        } else {
            enemyImpact = impact
            enemyShots++
            if (scored) enemyHits++
        }

        announce(outcome, attacker)

        if (target.allSunk()) {
            finish(attacker)
            return outcome
        }

        if (extraShots > 0) {
            extraShots--
            return outcome
        }
        // regra clássica da batalha naval: quem acerta joga de novo; só passa a vez no erro
        if (!scored) endTurn()
        return outcome
    }

    /** Disparo automático quando o tempo do turno acaba. */
    fun fireRandom() {
        if (phase != Phase.BATTLE) return
        pendingAbility = null
        val target = board(turnOwner.other())
        val open = mutableListOf<Coord>()
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) {
            val c = Coord(x, y)
            val mark = target.marks[c]
            if (mark != Mark.MISS && mark != Mark.HIT && mark != Mark.SUNK) open += c
        }
        // sem célula disponível o turno tem que passar mesmo assim, senão a partida congela
        if (open.isEmpty()) {
            endTurn()
            return
        }
        act(open[random.nextInt(open.size)])
    }

    private fun endTurn() {
        extraShots = 0
        pendingAbility = null
        val map = cooldownsOf(turnOwner)
        map.keys.toList().forEach { key ->
            val left = (map[key] ?: 0) - 1
            if (left <= 0) map.remove(key) else map[key] = left
        }
        turnOwner = turnOwner.other()
        if (turnOwner == Side.PLAYER) turnCount++
    }

    // ---------------- turno da IA ----------------

    fun enemyTurn() {
        if (opponent != Opponent.AI) return
        if (phase != Phase.BATTLE || turnOwner != Side.ENEMY) return

        if (ai.shouldUseSonar(mode, turnCount)) {
            val center = ai.sonarTarget(playerBoard)
            val worked = playerBoard.sonarPing(center)
            if (worked) {
                val hot = playerBoard.marks.filter { it.value == Mark.SCAN_HOT }.keys.toList()
                ai.addTargets(hot)
                say(t(K.CALL_ENEMY_SONAR), t(K.CALL_ENEMY_SONAR_SUB, center.label), Tone.SCAN)
            } else {
                say(t(K.CALL_SMOKE_HELD), t(K.CALL_SMOKE_HELD_SUB), Tone.SCAN)
            }
            endTurn()
            return
        }

        val shot = ai.nextShot(playerBoard)
        val outcome = act(shot)
        if (outcome != null) ai.registerOutcome(outcome, playerBoard)
    }

    // ---------------- utilidades ----------------

    private fun finish(side: Side) {
        winner = side
        phase = Phase.RESULT
        if (opponent == Opponent.LOCAL) {
            say(t(K.CALL_WON, sideName(side)), t(K.CALL_WON_SUB), Tone.SUNK)
        } else if (side == Side.PLAYER) {
            say(t(K.CALL_VICTORY), t(K.CALL_VICTORY_SUB), Tone.SUNK)
        } else {
            say(t(K.CALL_DEFEAT), t(K.CALL_DEFEAT_SUB), Tone.SUNK)
        }
    }

    private fun announce(outcome: ShotOutcome, attacker: Side) {
        val who = when {
            opponent != Opponent.AI -> "${sideName(attacker)} · "
            attacker == Side.ENEMY -> "${t(K.ENEMY)} · "
            else -> ""
        }
        val alvo = outcome.ship?.label ?: t(K.CALL_TARGET)
        when (outcome.result) {
            ShotResult.HIT ->
                say(t(K.CALL_HIT), who + t(K.CALL_HIT_SUB, alvo, outcome.coord.label), Tone.HIT)
            ShotResult.SUNK ->
                say(t(K.CALL_SUNK), who + t(K.CALL_SUNK_SUB, alvo, outcome.coord.label), Tone.SUNK)
            ShotResult.MISS -> say(t(K.CALL_MISS), "$who${outcome.coord.label}", Tone.MISS)
            ShotResult.ALREADY_FIRED -> Unit
        }
    }

    private fun say(main: String, sub: String, tone: Tone) {
        callout = Callout(main, sub, tone, nextId())
    }

    private fun nextId(): Long = ++calloutSeq

    fun accuracyOf(side: Side): Int {
        val shots = if (side == Side.PLAYER) playerShots else enemyShots
        val hits = if (side == Side.PLAYER) playerHits else enemyHits
        return if (shots == 0) 0 else (hits * 100) / shots
    }

    val accuracy: Int get() = accuracyOf(Side.PLAYER)
}

private fun ShotOutcome.tone(): Tone = when (result) {
    ShotResult.HIT -> Tone.HIT
    ShotResult.SUNK -> Tone.SUNK
    else -> Tone.MISS
}
