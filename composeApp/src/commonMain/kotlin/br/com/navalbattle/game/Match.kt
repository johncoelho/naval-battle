package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Phase { PLACEMENT, BATTLE, RESULT }
enum class Side { PLAYER, ENEMY }
enum class Tone { HIT, SUNK, MISS, SCAN, INFO }

data class Callout(val main: String, val sub: String, val tone: Tone, val id: Long)

data class Impact(val coord: Coord, val tone: Tone, val id: Long, val sunkShip: Ship? = null)

/**
 * Estado completo de uma partida contra a IA. Todo o fluxo de turnos vive aqui;
 * a UI só observa e dispara ações.
 */
class Match(val mode: GameMode, private val random: Random = Random.Default) {

    val playerBoard = Board()
    val enemyBoard = Board()
    private val ai = Ai(random)

    var phase by mutableStateOf(Phase.PLACEMENT)
        private set
    var turnOwner by mutableStateOf(Side.PLAYER)
        private set
    var turnCount by mutableStateOf(1)
        private set
    var callout by mutableStateOf<Callout?>(null)
        private set
    var playerImpact by mutableStateOf<Impact?>(null)
        private set
    var enemyImpact by mutableStateOf<Impact?>(null)
        private set
    var winner by mutableStateOf<Side?>(null)
        private set

    var pendingAbility by mutableStateOf<Ability?>(null)
        private set
    private var extraShots by mutableStateOf(0)
    private val cooldowns = mutableMapOf<Ability, Int>()

    var playerShots by mutableStateOf(0)
        private set
    var playerHits by mutableStateOf(0)
        private set

    private var calloutSeq = 0L

    init {
        enemyBoard.randomize(random)
    }

    // ---------------- posicionamento ----------------

    fun randomizePlayerFleet() = playerBoard.randomize(random)

    fun startBattle() {
        if (playerBoard.ships.size != ShipClass.fleet.size) return
        phase = Phase.BATTLE
        turnOwner = Side.PLAYER
        if (mode == GameMode.TACTICAL) {
            say("Frota a postos", "Toque nos ícones abaixo para usar uma habilidade", Tone.INFO)
        } else {
            say("Frota a postos", "Aguardando coordenada", Tone.INFO)
        }
    }

    // ---------------- habilidades ----------------

    fun abilityCooldown(ability: Ability): Int = cooldowns[ability] ?: 0

    fun abilityAvailable(ability: Ability): Boolean {
        if (mode != GameMode.TACTICAL || !ability.active) return false
        if (phase != Phase.BATTLE || turnOwner != Side.PLAYER) return false
        val owner = ShipClass.fleet.firstOrNull { it.ability == ability } ?: return false
        val ship = playerBoard.ships.firstOrNull { it.type == owner } ?: return false
        if (playerBoard.isSunk(ship)) return false
        return abilityCooldown(ability) == 0
    }

    fun selectAbility(ability: Ability) {
        if (!abilityAvailable(ability)) return
        when (ability) {
            Ability.SMOKE -> {
                playerBoard.smokeActive = true
                cooldowns[ability] = ability.cooldown
                say("Cortina lançada", "Próxima varredura inimiga bloqueada", Tone.SCAN)
                endPlayerTurn()
            }

            Ability.DOUBLE_BARRAGE -> {
                cooldowns[ability] = ability.cooldown
                extraShots = 1
                pendingAbility = null
                say("Barragem dupla", "Dois disparos neste turno", Tone.INFO)
            }

            else -> {
                pendingAbility = if (pendingAbility == ability) null else ability
            }
        }
    }

    // ---------------- turno do jogador ----------------

    fun playerAct(coord: Coord) {
        if (phase != Phase.BATTLE || turnOwner != Side.PLAYER) return

        when (val ability = pendingAbility) {
            Ability.AIR_RECON -> {
                val worked = enemyBoard.revealRow(coord.y)
                cooldowns[ability] = ability.cooldown
                pendingAbility = null
                if (worked) say("Reconhecimento aéreo", "Linha ${coord.y + 1} revelada", Tone.SCAN)
                else say("Varredura falhou", "Cortina de fumaça inimiga", Tone.MISS)
                endPlayerTurn()
                return
            }

            Ability.SONAR_PING -> {
                val worked = enemyBoard.sonarPing(coord)
                cooldowns[ability] = ability.cooldown
                pendingAbility = null
                if (worked) say("Contato no sonar", "Setor ${coord.label} varrido", Tone.SCAN)
                else say("Varredura falhou", "Cortina de fumaça inimiga", Tone.MISS)
                endPlayerTurn()
                return
            }

            else -> Unit
        }

        val outcome = enemyBoard.fireAt(coord, abilitiesEnabled = mode == GameMode.TACTICAL)
        if (outcome.result == ShotResult.ALREADY_FIRED) return

        playerShots++
        val sunkShip = if (outcome.result == ShotResult.SUNK) enemyBoard.ships.firstOrNull { it.type == outcome.ship } else null
        playerImpact = Impact(coord, outcome.tone(), nextId(), sunkShip)
        announce(outcome, attackerIsPlayer = true)
        if (outcome.result == ShotResult.HIT || outcome.result == ShotResult.SUNK) playerHits++

        if (enemyBoard.allSunk()) {
            finish(Side.PLAYER)
            return
        }

        if (extraShots > 0) {
            extraShots--
            return
        }
        endPlayerTurn()
    }

    fun playerFireRandom() {
        if (phase != Phase.BATTLE || turnOwner != Side.PLAYER) return
        pendingAbility = null
        val open = mutableListOf<Coord>()
        for (y in 0 until BOARD_SIZE) for (x in 0 until BOARD_SIZE) {
            val c = Coord(x, y)
            val mark = enemyBoard.marks[c]
            if (mark != Mark.MISS && mark != Mark.HIT && mark != Mark.SUNK) open += c
        }
        if (open.isEmpty()) return
        playerAct(open[random.nextInt(open.size)])
    }

    private fun endPlayerTurn() {
        extraShots = 0
        pendingAbility = null
        cooldowns.keys.toList().forEach { key ->
            val left = (cooldowns[key] ?: 0) - 1
            if (left <= 0) cooldowns.remove(key) else cooldowns[key] = left
        }
        turnOwner = Side.ENEMY
    }

    // ---------------- turno da IA ----------------

    fun enemyTurn() {
        if (phase != Phase.BATTLE || turnOwner != Side.ENEMY) return

        if (ai.shouldUseSonar(mode, turnCount)) {
            val center = ai.sonarTarget(playerBoard)
            val worked = playerBoard.sonarPing(center)
            if (worked) {
                val hot = playerBoard.marks.filter { it.value == Mark.SCAN_HOT }.keys.toList()
                ai.addTargets(hot)
                say("Sonar inimigo", "Varredura em ${center.label}", Tone.SCAN)
            } else {
                say("Cortina resistiu", "Varredura inimiga bloqueada", Tone.SCAN)
            }
            finishEnemyTurn()
            return
        }

        val shot = ai.nextShot(playerBoard)
        val outcome = playerBoard.fireAt(shot, abilitiesEnabled = mode == GameMode.TACTICAL)
        ai.registerOutcome(outcome, playerBoard)
        val sunkShip = if (outcome.result == ShotResult.SUNK) playerBoard.ships.firstOrNull { it.type == outcome.ship } else null
        enemyImpact = Impact(shot, outcome.tone(), nextId(), sunkShip)
        announce(outcome, attackerIsPlayer = false)

        if (playerBoard.allSunk()) {
            finish(Side.ENEMY)
            return
        }
        finishEnemyTurn()
    }

    private fun finishEnemyTurn() {
        turnOwner = Side.PLAYER
        turnCount++
    }

    // ---------------- utilidades ----------------

    private fun finish(side: Side) {
        winner = side
        phase = Phase.RESULT
        if (side == Side.PLAYER) say("Frota inimiga neutralizada", "Vitória, comandante", Tone.SUNK)
        else say("Perdemos o contato", "Nossa frota foi destruída", Tone.SUNK)
    }

    private fun announce(outcome: ShotOutcome, attackerIsPlayer: Boolean) {
        val who = if (attackerIsPlayer) "" else "Inimigo · "
        when (outcome.result) {
            ShotResult.HIT -> say("Acerto direto!", "$who${outcome.ship?.label ?: "Alvo"} atingido · ${outcome.coord.label}", Tone.HIT)
            ShotResult.SUNK -> say("Navio afundado", "$who${outcome.ship?.label ?: "Alvo"} abatido · ${outcome.coord.label}", Tone.SUNK)
            ShotResult.MISS -> say("Na água", "$who${outcome.coord.label}", Tone.MISS)
            ShotResult.ALREADY_FIRED -> Unit
        }
    }

    private fun say(main: String, sub: String, tone: Tone) {
        callout = Callout(main, sub, tone, nextId())
    }

    private fun nextId(): Long = ++calloutSeq

    val accuracy: Int
        get() = if (playerShots == 0) 0 else (playerHits * 100) / playerShots
}

private fun ShotOutcome.tone(): Tone = when (result) {
    ShotResult.HIT -> Tone.HIT
    ShotResult.SUNK -> Tone.SUNK
    else -> Tone.MISS
}
