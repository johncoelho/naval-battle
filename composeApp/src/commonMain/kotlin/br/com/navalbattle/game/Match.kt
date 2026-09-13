package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

enum class Phase { PLACEMENT, BATTLE, RESULT }

enum class Side {
    PLAYER, ENEMY;

    fun other(): Side = if (this == PLAYER) ENEMY else PLAYER
}

enum class Tone { HIT, SUNK, MISS, SCAN, INFO }

data class Callout(val main: String, val sub: String, val tone: Tone, val id: Long)

data class Impact(val coord: Coord, val tone: Tone, val id: Long, val sunkShip: Ship? = null)

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
    private val remote: Boolean get() = opponent == Opponent.LAN
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
        Opponent.AI -> if (side == Side.PLAYER) "Você" else "Inimigo"
        Opponent.LOCAL, Opponent.LAN ->
            if (side == Side.PLAYER) nameOne.ifBlank { "Comandante 1" }
            else nameTwo.ifBlank { "Comandante 2" }
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

    private fun startBattle() {
        phase = Phase.BATTLE
        turnOwner = Side.PLAYER
        if (mode == GameMode.TACTICAL) {
            say("Frota a postos", "Toque nos ícones abaixo para usar uma habilidade", Tone.INFO)
        } else {
            say("Frota a postos", "Aguardando coordenada", Tone.INFO)
        }
    }

    // ---------------- habilidades ----------------

    private fun cooldownsOf(side: Side): MutableMap<Ability, Int> =
        cooldowns.getOrPut(side) { mutableMapOf() }

    fun abilityCooldown(ability: Ability): Int = cooldownsOf(turnOwner)[ability] ?: 0

    fun abilityAvailable(ability: Ability): Boolean {
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
        return abilityCooldown(ability) == 0
    }

    fun selectAbility(ability: Ability) {
        if (!abilityAvailable(ability)) return
        when (ability) {
            Ability.SMOKE -> {
                board(turnOwner).smokeActive = true
                cooldownsOf(turnOwner)[ability] = ability.cooldown
                say("Cortina lançada", "Próxima varredura inimiga bloqueada", Tone.SCAN)
                endTurn()
            }

            Ability.DOUBLE_BARRAGE -> {
                cooldownsOf(turnOwner)[ability] = ability.cooldown
                extraShots = 1
                pendingAbility = null
                say("Barragem dupla", "Dois disparos neste turno", Tone.INFO)
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
                if (worked) say("Reconhecimento aéreo", "Linha ${coord.y + 1} revelada", Tone.SCAN)
                else say("Varredura falhou", "Cortina de fumaça inimiga", Tone.MISS)
                endTurn()
                return null
            }

            Ability.SONAR_PING -> {
                val worked = target.sonarPing(coord)
                cooldownsOf(attacker)[ability] = ability.cooldown
                pendingAbility = null
                if (worked) say("Contato no sonar", "Setor ${coord.label} varrido", Tone.SCAN)
                else say("Varredura falhou", "Cortina de fumaça inimiga", Tone.MISS)
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
        endTurn()
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
                say("Sonar inimigo", "Varredura em ${center.label}", Tone.SCAN)
            } else {
                say("Cortina resistiu", "Varredura inimiga bloqueada", Tone.SCAN)
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
            say("${sideName(side)} venceu", "Frota adversária neutralizada", Tone.SUNK)
        } else if (side == Side.PLAYER) {
            say("Frota inimiga neutralizada", "Vitória, comandante", Tone.SUNK)
        } else {
            say("Perdemos o contato", "Nossa frota foi destruída", Tone.SUNK)
        }
    }

    private fun announce(outcome: ShotOutcome, attacker: Side) {
        val who = when {
            opponent == Opponent.LOCAL -> "${sideName(attacker)} · "
            attacker == Side.ENEMY -> "Inimigo · "
            else -> ""
        }
        val alvo = outcome.ship?.label ?: "Alvo"
        when (outcome.result) {
            ShotResult.HIT -> say("Acerto direto!", "$who$alvo atingido · ${outcome.coord.label}", Tone.HIT)
            ShotResult.SUNK -> say("Navio afundado", "$who$alvo abatido · ${outcome.coord.label}", Tone.SUNK)
            ShotResult.MISS -> say("Na água", "$who${outcome.coord.label}", Tone.MISS)
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
