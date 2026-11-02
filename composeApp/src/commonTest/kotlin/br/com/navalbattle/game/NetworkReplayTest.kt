package br.com.navalbattle.game

import br.com.navalbattle.data.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ressincronização pelo log da sala (#67): a partida reconstruída pelo log tem que ser a
 * mesma que foi jogada ao vivo, igual nos dois aparelhos, e jogada fora da vez não conta.
 */
class NetworkReplayTest {

    private val horizontal: List<Ship> = ShipClass.fleet.mapIndexed { i, type ->
        Ship(type, Coord(0, i * 2), Orientation.HORIZONTAL)
    }
    private val vertical: List<Ship> = ShipClass.fleet.mapIndexed { i, type ->
        Ship(type, Coord(i * 2, 0), Orientation.VERTICAL)
    }

    /** Foto do que importa da partida, para comparar duas partidas. */
    private data class Snapshot(
        val phase: Phase,
        val winner: Side?,
        val turnOwner: Side,
        val turnCount: Int,
        val playerShots: Int,
        val playerHits: Int,
        val enemyShots: Int,
        val enemyHits: Int,
        val playerMarks: Map<Coord, Mark>,
        val enemyMarks: Map<Coord, Mark>,
        val playerSmoke: Boolean,
        val enemySmoke: Boolean,
        val abilitiesPlayer: Int,
        val abilitiesEnemy: Int
    )

    private fun snap(m: Match) = Snapshot(
        m.phase, m.winner, m.turnOwner, m.turnCount,
        m.playerShots, m.playerHits, m.enemyShots, m.enemyHits,
        m.playerBoard.marks.toMap(), m.enemyBoard.marks.toMap(),
        m.playerBoard.smokeActive, m.enemyBoard.smokeActive,
        m.abilitiesUsedBy(Side.PLAYER), m.abilitiesUsedBy(Side.ENEMY)
    )

    private class Played(val live: Match, val log: List<ReplayLine>)

    /**
     * Joga uma partida inteira ao vivo (visão do PLAYER) com um roteiro fixo e devolve o log
     * que a sala teria. [bogusAt] injeta no log, depois desse passo, um ACT de quem NÃO tem a
     * vez: a linha existe na sala, mas nenhum aparelho a aplica.
     */
    private fun play(mode: GameMode, bogusAt: Int? = null, stopAfter: Int = 100_000): Played {
        val live = Match(mode, Opponent.ONLINE, Side.PLAYER)
        val log = mutableListOf<ReplayLine>()
        var id = 100L
        fun add(from: Side, body: String) {
            log += ReplayLine(id, from, body)
            id += 3
        }

        add(Side.PLAYER, Protocol.fleet(FleetCodec.encode(horizontal)))
        live.applyFleetOf(Side.PLAYER, horizontal)
        add(Side.ENEMY, Protocol.fleet(FleetCodec.encode(vertical)))
        live.applyFleetOf(Side.ENEMY, vertical)

        val abilities = listOf(Ability.SMOKE, Ability.DOUBLE_BARRAGE, Ability.AIR_RECON, Ability.SONAR_PING)
        var step = 0
        while (live.phase == Phase.BATTLE && step < stopAfter) {
            val side = live.turnOwner
            if (mode == GameMode.TACTICAL && step % 5 == 2) {
                val ab = abilities[(step / 5) % abilities.size]
                val fromRemote = side != Side.PLAYER
                if (live.abilityAvailable(ab, fromRemote = fromRemote)) {
                    add(side, Protocol.ability(ab.code, false))
                    live.selectAbility(ab, fromRemote = fromRemote)
                }
            }
            if (live.turnOwner == side) {
                val coord = (0 until 10).flatMap { y -> (0 until 10).map { x -> Coord(x, y) } }
                    .firstOrNull { live.canAct(side, it) && (live.pendingAbility != null || live.board(side.other()).marks[it] == null) }
                    ?: break
                add(side, Protocol.act(coord.x, coord.y))
                live.act(coord, by = side)
            }
            if (bogusAt == step) {
                // tiro de quem não tem a vez, gravado na sala mas nunca aplicado
                add(live.turnOwner.other(), Protocol.act(9, 9))
            }
            step++
        }
        return Played(live, log)
    }

    private fun replay(mode: GameMode, mySide: Side, log: List<ReplayLine>): Match =
        assertNotNull(replayNetworkMatch(mode, mySide, "Alfa", "Bravo", log))

    @Test
    fun partidaInteiraClassicaReconstroiIgualAoVivo() {
        val p = play(GameMode.CLASSIC)
        assertEquals(Phase.RESULT, p.live.phase, "o roteiro termina a partida")
        assertEquals(snap(p.live), snap(replay(GameMode.CLASSIC, Side.PLAYER, p.log)))
    }

    @Test
    fun partidaInteiraTaticaComHabilidadesReconstroiIgualAoVivo() {
        val p = play(GameMode.TACTICAL)
        assertTrue(p.live.abilitiesUsedBy(Side.PLAYER) + p.live.abilitiesUsedBy(Side.ENEMY) > 0, "usou habilidade")
        assertEquals(snap(p.live), snap(replay(GameMode.TACTICAL, Side.PLAYER, p.log)))
    }

    @Test
    fun meioDaPartidaTambemReconstroiIgual() {
        for (mode in GameMode.entries) for (cut in listOf(1, 4, 9, 17, 30)) {
            val p = play(mode, stopAfter = cut)
            assertEquals(snap(p.live), snap(replay(mode, Side.PLAYER, p.log)), "$mode corte $cut")
        }
    }

    @Test
    fun osDoisLadosReconstroemOMesmoEstado() {
        for (mode in GameMode.entries) {
            val p = play(mode, stopAfter = 40)
            assertEquals(
                snap(replay(mode, Side.PLAYER, p.log)),
                snap(replay(mode, Side.ENEMY, p.log)),
                "$mode"
            )
        }
    }

    @Test
    fun tiroForaDaVezNoMeioDoLogEIgnoradoPelosDoisLados() {
        for (mode in GameMode.entries) {
            val clean = play(mode, stopAfter = 25)
            val dirty = play(mode, bogusAt = 6, stopAfter = 25)
            assertTrue(dirty.log.size > clean.log.size, "a linha intrusa está no log")
            val expected = snap(clean.live)
            assertEquals(expected, snap(replay(mode, Side.PLAYER, dirty.log)), "$mode PLAYER")
            assertEquals(expected, snap(replay(mode, Side.ENEMY, dirty.log)), "$mode ENEMY")
        }
    }

    @Test
    fun habilidadeForaDaVezNoLogEIgnorada() {
        val p = play(GameMode.TACTICAL, stopAfter = 3)
        val withBogus = p.log + ReplayLine(
            p.log.maxOf { it.id } + 1, p.live.turnOwner.other(), Protocol.ability(Ability.SMOKE.code, false)
        )
        assertEquals(snap(p.live), snap(replay(GameMode.TACTICAL, Side.PLAYER, withBogus)))
    }

    @Test
    fun cenario35x36LadoQueNaoAplicouAUltimaLinhaChegaAoEstadoDoOutro() {
        for (mode in GameMode.entries) {
            val full = play(mode, stopAfter = 14)
            // o aparelho A parou uma linha antes
            val behind = replay(mode, Side.PLAYER, full.log.dropLast(1))
            assertNotEquals(snap(full.live), snap(behind), "$mode: sem a última linha o estado difere")
            // ressincronizado pelo log inteiro, A chega ao estado de B
            assertEquals(snap(full.live), snap(replay(mode, Side.PLAYER, full.log)), "$mode")
            assertEquals(snap(full.live), snap(replay(mode, Side.ENEMY, full.log)), "$mode")
        }
    }

    @Test
    fun ordemDoLogVenceAOrdemDeChegada() {
        val p = play(GameMode.TACTICAL, stopAfter = 30)
        assertEquals(snap(p.live), snap(replay(GameMode.TACTICAL, Side.ENEMY, p.log.reversed())))
    }

    @Test
    fun linhaRepetidaNoLogContaUmaVez() {
        val p = play(GameMode.CLASSIC, stopAfter = 12)
        assertEquals(snap(p.live), snap(replay(GameMode.CLASSIC, Side.PLAYER, p.log + p.log.drop(3))))
    }

    @Test
    fun semAsDuasFrotasNaoHaPartidaParaReconstruir() {
        val onlyOne = listOf(ReplayLine(1, Side.PLAYER, Protocol.fleet(FleetCodec.encode(horizontal))))
        assertNull(replayNetworkMatch(GameMode.CLASSIC, Side.PLAYER, "A", "B", onlyOne))
        assertNull(replayNetworkMatch(GameMode.CLASSIC, Side.PLAYER, "A", "B", emptyList()))
    }

    @Test
    fun herdaNomesEIdLocal() {
        val p = play(GameMode.CLASSIC, stopAfter = 3)
        val id = "x".repeat(32)
        val m = assertNotNull(replayNetworkMatch(GameMode.CLASSIC, Side.ENEMY, "Alfa", "Bravo", p.log, localId = id))
        assertEquals("Alfa", m.nameOne)
        assertEquals("Bravo", m.nameTwo)
        assertEquals(id, m.localId)
        assertEquals(Side.ENEMY, m.mySide)
    }

    @Test
    fun sameBattleStateIgualParaReconstrucaoSemMudancaEDiferenteDepoisDeUmTiro() {
        for (mode in GameMode.entries) {
            val p = play(mode, stopAfter = 12)
            val rebuilt = replay(mode, Side.PLAYER, p.log)
            assertTrue(sameBattleState(p.live, rebuilt), "$mode igual")
            val more = play(mode, stopAfter = 13)
            assertTrue(!sameBattleState(rebuilt, replay(mode, Side.PLAYER, more.log)), "$mode um passo à frente")
            assertTrue(!sameBattleState(replay(mode, Side.PLAYER, p.log.dropLast(1)), rebuilt), "$mode sem a última linha")
        }
    }

    // ---------------- vigia ----------------

    private val t0 = 1_000_000L

    private fun watch(
        now: Long = t0 + 31_000,
        waitingSince: Long? = t0,
        lastOpponentLine: Long? = t0,
        selfOffline: Boolean = false,
        paused: Boolean = false,
        dropLeft: Int? = null,
        lastResync: Long? = null
    ) = shouldResync(now, waitingSince, lastOpponentLine, selfOffline, paused, dropLeft, lastResync)

    @Test
    fun vigiaDisparaAposTrintaSegundosSemAVezENoticiaDoAdversario() {
        assertTrue(watch())
        assertTrue(watch(now = t0 + 30_000))
        assertTrue(watch(lastOpponentLine = null))
    }

    @Test
    fun vigiaNaoDisparaAntesDosTrintaSegundos() {
        assertTrue(!watch(now = t0 + 29_999))
    }

    @Test
    fun vigiaNaoDisparaSeAVezEMinha() {
        assertTrue(!watch(waitingSince = null))
    }

    @Test
    fun vigiaNaoDisparaSeOAdversarioFalouHaPouco() {
        assertTrue(!watch(lastOpponentLine = t0 + 5_000))
    }

    @Test
    fun vigiaNaoDisparaComQuedaPausaOuContagemDeQueda() {
        assertTrue(!watch(selfOffline = true))
        assertTrue(!watch(paused = true))
        assertTrue(!watch(dropLeft = 40))
        assertTrue(!watch(dropLeft = 0))
    }

    @Test
    fun vigiaRepeteNoMaximoACadaQuinzeSegundos() {
        assertTrue(!watch(lastResync = t0 + 31_000 - 14_999))
        assertTrue(watch(lastResync = t0 + 31_000 - 15_000))
    }
}
