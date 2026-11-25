package br.com.navalbattle.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Selo "Adversario virtual" e replay da mira da IA (#139). */
class BotReplayTest {
    private val bot = BotProfile("MarujoLeo", BotLevel.MEDIUM)

    private fun finished(opponent: Opponent = Opponent.AI, withBot: BotProfile? = bot, seed: Int = 11): Match {
        val m = Match(GameMode.CLASSIC, opponent, random = Random(seed), bot = withBot)
        m.confirmPlacement()
        var guard = 0
        while (m.phase == Phase.BATTLE && guard++ < 1_000) {
            if (m.turnOwner == Side.PLAYER) m.fireRandom() else m.enemyTurn()
        }
        assertEquals(Phase.RESULT, m.phase)
        return m
    }

    @Test fun seloSoContraOBot() {
        assertTrue(BotReplay.showBadge(finished()))
        assertFalse(BotReplay.showBadge(finished(withBot = null)), "treino com a IA sem persona nao ganha selo")
        val humano = Match(GameMode.CLASSIC, Opponent.ONLINE)
        assertFalse(BotReplay.showBadge(humano), "humano nunca tem selo")
        assertFalse(BotReplay.showBadge(Match(GameMode.CLASSIC, Opponent.LOCAL)))
    }

    @Test fun logGuardaSoOsTirosDaIAEmOrdemComOResultado() {
        val m = finished()
        assertEquals(m.enemyShots, m.aiShotLog.size)
        assertEquals(m.aiShotLog.size, m.aiShotLog.map { it.coord }.toSet().size, "sem tiro repetido")
        assertEquals(m.enemyHits, m.aiShotLog.count { it.result == ShotResult.HIT || it.result == ShotResult.SUNK })
        // o rastro bate com o tabuleiro final: cada coordenada do log esta marcada no tabuleiro do jogador
        m.aiShotLog.forEach { assertTrue(m.playerBoard.marks[it.coord] != null) }
    }

    @Test fun replayOferecidoSoNoFimEContraOBotSemAbandono() {
        assertTrue(BotReplay.offered(finished()))
        assertFalse(BotReplay.offered(finished(withBot = null)))
        val emCurso = Match(GameMode.CLASSIC, Opponent.AI, random = Random(3), bot = bot)
        emCurso.confirmPlacement()
        assertFalse(BotReplay.offered(emCurso), "partida em andamento")
        val abandonada = Match(GameMode.CLASSIC, Opponent.AI, random = Random(3), bot = bot)
        abandonada.confirmPlacement()
        abandonada.enemyTurn()
        abandonada.forfeitByTimeout()
        assertFalse(BotReplay.offered(abandonada), "abandono nao oferece replay")
    }

    @Test fun boardAtAplicaSoOsPrimeirosTirosEPreservaAFrota() {
        val m = finished()
        val log = m.aiShotLog
        val fleet = m.playerBoard.ships
        assertEquals(0, BotReplay.boardAt(fleet, log, 0).marks.size, "passo 0: frota intocada")
        val dois = BotReplay.boardAt(fleet, log, 2)
        assertEquals(log.take(2).map { it.coord }.toSet(), dois.marks.keys)
        assertEquals(fleet.size, dois.ships.size)
        val fim = BotReplay.boardAt(fleet, log, 9_999)
        assertEquals(log.map { it.coord }.toSet(), fim.marks.keys, "passo alem do fim vale o fim")
        assertEquals(0, BotReplay.boardAt(fleet, log, -5).marks.size)
    }

    // texto de replay/ficha nao pode afirmar o que a IA "enxerga" sem prova (#150): a mira so le as marcas do tabuleiro
    @Test fun aMiraDaIANaoDependeDeOndeEstaAFrotaDoJogador() {
        // as duas frotas diferentes recebem a mesma mira enquanto as marcas forem iguais (ate o primeiro navio tocado)
        val a = Board().also { it.randomize(Random(1)) }
        val b = Board().also { it.randomize(Random(2)) }
        val ai1 = Ai(Random(7), false, 0.0)
        val ai2 = Ai(Random(7), false, 0.0)
        repeat(30) {
            val c1 = ai1.nextShot(a)
            val c2 = ai2.nextShot(b)
            val semAcerto = a.shipAt(c1) == null && b.shipAt(c2) == null
            assertEquals(c1, c2, "com as mesmas marcas a mira e a mesma, qualquer que seja a frota")
            if (!semAcerto) return
            a.fireAt(c1); b.fireAt(c2)
        }
    }
}
