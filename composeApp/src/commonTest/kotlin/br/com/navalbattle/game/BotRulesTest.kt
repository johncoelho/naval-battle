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
        // fora das pausas longas ocasionais (#112) ninguem passa de 7 s; as pausas existem mas sao raras
        val longPauses = plain.count { it > 6_000L + 1_000L }
        assertTrue(plain.all { it <= 15_000L })
        assertTrue(longPauses in 5..80, "pausas longas: $longPauses de 400")
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

    @Test fun esperaSorteiaEntre3e6SegundosSejaQualForOValorDoServidor() {
        val r = Random(7)
        val seen = mutableSetOf<Long>()
        repeat(500) {
            // 30 e 45 s (padroes antigos) e valores absurdos: todos caem no mesmo intervalo de 3 a 6 s
            for (cfg in listOf(30, 45, 99_999, 6)) {
                val ms = BotRules.waitMillis(cfg, r)
                assertTrue(ms in 3_000L..6_000L, "espera fora de 3 a 6 s: $ms")
                seen += ms / 1000
            }
        }
        assertTrue(seen.size >= 3, "o sorteio varia: $seen")
    }

    @Test fun esperaConfiguradaMenorSoEncurtaComPisoDe3s() {
        val r = Random(3)
        repeat(200) {
            assertTrue(BotRules.waitMillis(5, r) in 3_000L..5_000L)
            assertTrue(BotRules.waitMillis(3, r) == 3_000L)
            assertTrue(BotRules.waitMillis(0, r) == 3_000L, "piso de 3 s: nunca instantaneo")
            assertTrue(BotRules.waitMillis(-3, r) == 3_000L)
        }
    }

    @Test fun fichaDaPersonaEPlausivelEEstavel() {
        val a = BotPersonas.look("MarujoLeo", BotLevel.MEDIUM, 1_000, 1_000)
        assertEquals(a, BotPersonas.look("MarujoLeo", BotLevel.MEDIUM, 1_000, 1_000), "mesmo nome, mesma ficha")
        BotPersonas.POOL.forEach { name ->
            BotLevel.entries.forEach { lvl ->
                val l = BotPersonas.look(name, lvl, 900, 1_050)
                assertTrue(Avatar.entries.any { it.id == l.avatarId })
                assertTrue(l.xp in 60..(Rank.ALMIRANTE.xp + 3_000))
                assertTrue(l.rating in 960..1_140, "perto do jogador: ${l.rating}")
            }
        }
        // o facil fica abaixo do dificil
        val easy = BotPersonas.look("Kaique77", BotLevel.EASY, 2_000, 1_000)
        val hard = BotPersonas.look("Kaique77", BotLevel.HARD, 2_000, 1_000)
        assertTrue(easy.rating < hard.rating && easy.xp < hard.xp)
        // jogador sem xp nem pontos: ficha ainda valida
        val zero = BotPersonas.look("Ancora22", BotLevel.EASY, 0, 0)
        assertTrue(zero.rating >= 0 && zero.xp >= 60)
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
        assertEquals(Phase.PLACEMENT, m.phase, "o adversario ainda posiciona (2 a 6 s)")
        m.beginBotBattle()
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

    // ---------------- ajuste suave de dificuldade (#112) ----------------

    /** Simula [games] partidas: o "humano" e a IA de caca com [humanError] de tiro aleatorio; devolve a taxa de vitoria dele. */
    private fun humanWinRate(level: BotLevel, humanError: Double, games: Int, seed: Int): Double {
        var wins = 0
        for (g in 0 until games) {
            val rnd = Random(seed * 100_003 + g)
            val m = Match(GameMode.CLASSIC, Opponent.AI, random = rnd, bot = BotProfile("MarujoLeo", level))
            m.confirmPlacement()
            m.beginBotBattle()
            val human = Ai(Random(seed * 7_919 + g), easy = false, errorChance = humanError)
            var guard = 0
            while (m.phase == Phase.BATTLE && guard++ < 2_000) {
                if (m.turnOwner == Side.PLAYER) {
                    val shot = human.nextShot(m.enemyBoard)
                    val outcome = m.act(shot)
                    if (outcome != null) human.registerOutcome(outcome, m.enemyBoard)
                } else m.enemyTurn()
            }
            if (m.winner == Side.PLAYER) wins++
        }
        return wins.toDouble() / games
    }

    @Test fun ajusteDeDificuldadeSoAtuaQuandoOJogadorEstaAtras() {
        assertEquals(BotRules.EVEN_ERROR, BotRules.catchUpError(5, 5), 1e-9)
        assertEquals(0.0, BotRules.catchUpError(3, 5), "jogador adiante: mira normal")
        assertEquals(BotRules.EVEN_ERROR + BotRules.CATCH_UP_STEP, BotRules.catchUpError(5, 4), 1e-9)
        assertEquals(BotRules.CATCH_UP_MAX, BotRules.catchUpError(5, 1), 1e-9, "teto: nunca vira jogada absurda")
    }

    @Test fun jogadorDoNivelVenceEntre55e70PorCento() {
        // o servidor sorteia o nivel pela forca do jogador: facil p/ quem erra muito, dificil p/ quem joga bem
        val pairs = listOf(BotLevel.EASY to 0.55, BotLevel.MEDIUM to 0.35, BotLevel.HARD to 0.15)
        val SEED = 5
        val rates = pairs.map { (lvl, err) -> "${lvl.code}=" + humanWinRate(lvl, err, 700, SEED) }
        pairs.forEachIndexed { i, (lvl, err) ->
            val rate = humanWinRate(lvl, err, 700, SEED)
            assertTrue(rate in 0.55..0.72, "RATES $rates ${lvl.code}: vitoria = $rate")
        }
    }

    @Test fun oBotNuncaGanhaDeGraca() {
        // jogador fraco (so atira ao acaso): o ajuste ajuda, mas nao entrega a partida
        val rate = humanWinRate(BotLevel.HARD, 1.0, 400, 9)
        assertTrue(rate < 0.5, "RATE $rate jogador sem nenhuma tecnica nao deve ganhar mais da metade: $rate")
    }

    @Test fun cancelarDuranteAChamadaNaoAbreAPartida() {
        assertTrue(BotRules.openAfterStart(stillSearching = true))
        assertFalse(BotRules.openAfterStart(stillSearching = false))
    }

    @Test fun respostaDoAdversarioSoUsaAListaFechada() {
        val r = Random(4)
        val replies = List(400) { BotRules.replyTo("FIRE", r) }
        assertTrue(replies.count { it != null } in 100..260, "responde so de vez em quando")
        replies.filterNotNull().forEach { assertTrue(isKnownTaunt(it), it); assertNotEquals("MERCY", it) }
        repeat(100) { BotRules.replyTo("GG", r)?.let { assertEquals("GG", it) } }
    }

    @Test fun fichaComRatingDoServidorEPersonaBloqueadaNaoVolta() {
        assertEquals(1_234, BotPersonas.look("MarujoLeo", BotLevel.HARD, 500, 1_234, exact = true).rating)
        val blocked = BotPersonas.POOL.drop(1).toSet()
        repeat(50) { assertEquals(BotPersonas.POOL.first(), BotPersonas.pick(Random(it), blocked = blocked)) }
    }

    @Test fun posicionamentoEEsperaDeRevanchePareceGente() {
        val r = Random(2)
        repeat(100) {
            assertTrue(BotRules.placementMillis(r) in 2_000L..6_000L)
            assertTrue(BotRules.rematchWaitMillis(r) in 2_000L..6_000L)
        }
    }
}
