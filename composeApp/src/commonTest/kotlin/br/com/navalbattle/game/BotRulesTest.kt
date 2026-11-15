package br.com.navalbattle.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adversario virtual (#105): regras puras, persona, ritmo e a partida inteira contra o bot. */
class BotRulesTest {

    @Test fun nivelDesconhecidoCaiNoFacil() {
        assertEquals(BotLevel.EASY, BotLevel.of(null))
        assertEquals(BotLevel.EASY, BotLevel.of("xyz"))
        assertEquals(BotLevel.HARD, BotLevel.of("hard"))
        assertEquals(BotLevel.MEDIUM, BotLevel.of("medium"))
    }

    @Test fun nenhumaJogadaSaiAntesDeUmSegundoEDois() {
        val random = Random(7)
        BotLevel.entries.forEach { level ->
            repeat(500) {
                assertTrue(BotRules.thinkMillis(level, false, random) >= BotRules.MIN_THINK_MS)
                assertTrue(BotRules.thinkMillis(level, true, random) >= BotRules.MIN_THINK_MS)
            }
        }
    }

    @Test fun oRitmoVariaEFicaMaisLentoDepoisDeAcerto() {
        val random = Random(11)
        val plain = List(400) { BotRules.thinkMillis(BotLevel.MEDIUM, false, random) }
        val afterHit = List(400) { BotRules.thinkMillis(BotLevel.MEDIUM, true, random) }
        assertTrue(plain.toSet().size > 50, "o intervalo tem que variar entre jogadas")
        assertTrue(afterHit.average() > plain.average())
        assertTrue(plain.all { it <= 6_000L + 1_000L })
    }

    @Test fun nivelFacilPensaMaisDevagarQueODificil() {
        val random = Random(3)
        val easy = List(400) { BotRules.thinkMillis(BotLevel.EASY, false, random) }.average()
        val hard = List(400) { BotRules.thinkMillis(BotLevel.HARD, false, random) }.average()
        assertTrue(easy > hard)
    }

    @Test fun soCancelledLiberaOBot() {
        assertEquals(BotRules.AfterCancel.START_BOT, BotRules.afterCancel("cancelled"))
        assertEquals(BotRules.AfterCancel.HUMAN_JOINED, BotRules.afterCancel("joined"))
        assertEquals(BotRules.AfterCancel.KEEP_SEARCHING, BotRules.afterCancel("invalid"))
        assertEquals(BotRules.AfterCancel.KEEP_SEARCHING, BotRules.afterCancel(null), "erro de rede nunca vira bot")
        assertEquals(BotRules.AfterCancel.KEEP_SEARCHING, BotRules.afterCancel(""))
    }

    @Test fun soOkIniciaAPartida() {
        assertTrue(BotRules.startAccepted("ok"))
        listOf("off", "limit", "invalid", "busy", null, "").forEach { assertFalse(BotRules.startAccepted(it), "$it") }
    }

    @Test fun degradacaoComChaveDesligadaOuServidorAntigo() {
        assertFalse(BotRules.shouldArm(isQuick = true, botOn = false, modeOk = true, practicing = false))
        assertFalse(BotRules.shouldArm(isQuick = true, botOn = true, modeOk = false, practicing = false), "cota esgotada")
        assertFalse(BotRules.shouldArm(isQuick = true, botOn = true, modeOk = true, practicing = true), "treino manual em curso")
        assertFalse(BotRules.shouldArm(isQuick = false, botOn = true, modeOk = true, practicing = false), "sala de amigo nunca")
        assertTrue(BotRules.shouldArm(isQuick = true, botOn = true, modeOk = true, practicing = false))
    }

    @Test fun esperaTemPisoETeto() {
        assertEquals(10_000L, BotRules.waitMillis(0))
        assertEquals(30_000L, BotRules.waitMillis(30))
        assertEquals(45_000L, BotRules.waitMillis(45))
        assertEquals(300_000L, BotRules.waitMillis(99_999))
    }

    @Test fun rankedSoContaQuandoOServidorAceitou() {
        assertTrue(BotRules.rankedCounts("ok"))
        listOf("too_short", "no_points", "repeat", "invalid", null).forEach { assertFalse(BotRules.rankedCounts(it)) }
    }

    @Test fun provocacoesSoVemDaListaFechada() {
        val random = Random(5)
        var seen = 0
        repeat(6_000) {
            BotRules.tauntAfterShot(random)?.let {
                seen++
                assertTrue(isKnownTaunt(it), it)
            }
        }
        assertTrue(seen in 100..800, "frequencia baixa e aleatoria: $seen em 6000")
        repeat(200) { BotRules.tauntOnFinish(random)?.let { assertEquals("GG", it) } }
    }

    // ---------------- personas ----------------

    @Test fun todasAsPersonasPassamPeloFiltroDeNomes() {
        BotPersonas.POOL.forEach { name ->
            assertEquals(NameFilter.Check.OK, NameFilter.validate(name), name)
            assertTrue(name.length <= 16, "cabe no nome da partida: $name")
        }
    }

    @Test fun poolTemQuarentaNomesDistintos() {
        assertEquals(40, BotPersonas.POOL.size)
        assertEquals(40, BotPersonas.POOL.map { it.lowercase() }.toSet().size)
    }

    @Test fun semRepetirASeguidaNemOProprioNome() {
        val random = Random(9)
        var last: String? = null
        repeat(300) {
            val p = BotPersonas.pick(random, last, "MarujoLeo")
            assertNotEquals(last, p)
            assertFalse(p.equals("MarujoLeo", ignoreCase = true))
            last = p
        }
    }

    // ---------------- IA com erro e a partida ----------------

    private fun boardWithHitAt(x: Int, y: Int): Board = Board().also { it.marks[Coord(x, y)] = Mark.HIT }

    @Test fun oErroDesviaDaPerseguicao() {
        fun followRate(chance: Double): Int {
            var follow = 0
            repeat(200) { seed ->
                val board = boardWithHitAt(4, 4)
                val ai = Ai(Random(seed), easy = false, errorChance = chance)
                ai.registerOutcome(ShotOutcome(Coord(4, 4), ShotResult.HIT, null), board)
                val shot = ai.nextShot(board)
                if (shot in listOf(Coord(3, 4), Coord(5, 4), Coord(4, 3), Coord(4, 5))) follow++
            }
            return follow
        }
        assertEquals(200, followRate(0.0), "sem erro a IA de sempre persegue o acerto")
        assertTrue(followRate(1.0) < 40, "erro certo = tiro aleatorio, quase nunca o vizinho")
    }

    @Test fun partidaInteiraContraOBotTerminaComRitmoEProvocacoesConhecidas() {
        val bot = BotProfile("MarujoLeo", BotLevel.MEDIUM)
        val m = Match(GameMode.CLASSIC, Opponent.AI, random = Random(21), bot = bot)
        m.confirmPlacement()
        assertEquals(Phase.BATTLE, m.phase)
        assertTrue(m.enemyThinkMillis() >= BotRules.MIN_THINK_MS)
        var guard = 0
        while (m.phase == Phase.BATTLE && guard++ < 1_000) {
            if (m.turnOwner == Side.PLAYER) m.fireRandom() else m.enemyTurn()
            m.lastTaunt?.let { assertTrue(isKnownTaunt(it.code), it.code) }
        }
        assertEquals(Phase.RESULT, m.phase)
        assertNotNull(m.winner)
        assertEquals("MarujoLeo", m.sideName(Side.ENEMY))
    }

    @Test fun semBotAEsperaContinuaUmSegundoEMeio() {
        val m = Match(GameMode.CLASSIC, Opponent.AI)
        assertNull(m.bot)
        assertEquals(1_500L, m.enemyThinkMillis())
    }
}
