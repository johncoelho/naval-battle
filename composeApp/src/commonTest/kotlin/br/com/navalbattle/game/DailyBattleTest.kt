package br.com.navalbattle.game

import br.com.navalbattle.data.dailyBattleStoreLink
import br.com.navalbattle.data.platformName
import br.com.navalbattle.i18n.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyBattleTest {
    private val hour = 3_600_000L

    /** 23/11/2026 00:00 em Brasília = 03:00 UTC. */
    private val firstDayMidnightBrt = 20_780L * 24 * hour + 3 * hour

    private fun enemyFleet(day: Long): List<Ship> =
        Match(GameMode.CLASSIC, Opponent.AI, random = DailyBattle.random(day)).enemyBoard.ships.toList()

    @Test
    fun diaViraAMeiaNoiteDeBrasiliaNaoAMeiaNoiteUtc() {
        assertEquals(20_779L, DailyBattle.dayOf(firstDayMidnightBrt - 1))
        assertEquals(20_780L, DailyBattle.dayOf(firstDayMidnightBrt))
        // 23:59 de Brasília ainda é o mesmo dia, embora em UTC já seja o dia seguinte
        assertEquals(20_780L, DailyBattle.dayOf(firstDayMidnightBrt + 23 * hour + 59 * 60_000L))
        assertEquals(20_781L, DailyBattle.dayOf(firstDayMidnightBrt + 24 * hour))
    }

    @Test
    fun numeroDoDiaComecaEmUm() {
        assertEquals(1, DailyBattle.number(20_780L))
        assertEquals(8, DailyBattle.number(20_787L))
        assertEquals(1, DailyBattle.number(20_000L))
    }

    @Test
    fun mesmoDiaMesmoMarEmDuasPartidas() {
        val day = 20_790L
        assertEquals(enemyFleet(day), enemyFleet(day))
        assertEquals(ShipClass.fleet.size, enemyFleet(day).size)
    }

    @Test
    fun diaSeguinteMudaOMar() {
        assertNotEquals(enemyFleet(20_790L), enemyFleet(20_791L))
    }

    @Test
    fun mesmoMarMesmoQueOJogadorMexaNaPropriaFrota() {
        val day = 20_795L
        val a = Match(GameMode.CLASSIC, Opponent.AI, random = DailyBattle.random(day))
        val b = Match(GameMode.CLASSIC, Opponent.AI, random = DailyBattle.random(day))
        repeat(3) { b.randomizePlacingFleet() }
        assertEquals(a.enemyBoard.ships.toList(), b.enemyBoard.ships.toList())
    }

    @Test
    fun sorteioProprioNaoMudaEntreVersoes() {
        // valor travado: se mudar, o mar de um dia deixa de ser igual entre aparelhos com versões diferentes
        val r = DailyBattle.random(20_780L)
        val first = List(5) { r.nextInt(100) }
        assertEquals(DailyBattle.seed(20_780L), DailyBattle.seed(20_780L))
        assertNotEquals(DailyBattle.seed(20_780L), DailyBattle.seed(20_781L))
        assertEquals(GOLDEN_FIRST_FIVE, first)
    }

    @Test
    fun jaJogouHojeSoNoMesmoDia() {
        assertFalse(DailyBattle.playedToday(null, 20_780L))
        assertTrue(DailyBattle.playedToday(DailyBattle.started(20_780L), 20_780L))
        assertFalse(DailyBattle.playedToday(DailyBattle.finished(20_779L, true, 40), 20_780L))
    }

    @Test
    fun registroIdaEVoltaNoAparelho() {
        listOf(
            DailyBattle.started(20_780L),
            DailyBattle.finished(20_780L, true, 37),
            DailyBattle.finished(20_781L, false, 60)
        ).forEach { assertEquals(it, DailyBattle.decode(DailyBattle.encode(it))) }
        assertNull(DailyBattle.decode(""))
        assertNull(DailyBattle.decode("abc"))
        assertNull(DailyBattle.decode("20780:2:10"))
        assertNull(DailyBattle.decode("20780:1:-3"))
    }

    @Test
    fun registroComSequenciaIdaEVolta() {
        listOf(
            DailyBattle.finished(20_780L, true, 5, "MHHSM"),
            DailyBattle.finished(20_781L, false, 3, "MMH"),
            DailyBattle.finished(20_782L, true, 0, "")
        ).forEach { assertEquals(it, DailyBattle.decode(DailyBattle.encode(it))) }
        assertEquals("20780:1:5:MHHSM", DailyBattle.encode(DailyBattle.finished(20_780L, true, 5, "MHHSM")))
        // registro antigo (0.48.1 a 0.48.3), de 3 partes: continua valendo, sem sequência
        assertEquals(DailyBattle.Record(20_780L, true, 37, null), DailyBattle.decode("20780:1:37"))
        assertEquals("20780:1:37", DailyBattle.encode(DailyBattle.finished(20_780L, true, 37)))
        // sequência estragada (letra estranha ou tamanho diferente dos tiros): o resultado fica, a sequência some
        assertEquals(DailyBattle.Record(20_780L, true, 3, null), DailyBattle.decode("20780:1:3:MXH"))
        assertEquals(DailyBattle.Record(20_780L, true, 3, null), DailyBattle.decode("20780:1:3:MH"))
        // lixo continua nulo
        assertNull(DailyBattle.decode("20780:1:3:MHS:extra"))
        assertNull(DailyBattle.decode("20780:2:3:MHS"))
        assertNull(DailyBattle.decode("abc:1:3:MHS"))
    }

    @Test
    fun sequenciaDosTirosNaOrdem() {
        assertEquals(
            "MHSHM",
            DailyBattle.sequenceOf(listOf(ShotResult.MISS, ShotResult.HIT, ShotResult.SUNK, ShotResult.HIT, ShotResult.MISS))
        )
        // tiro repetido nunca vira tiro na sequência
        assertEquals("MH", DailyBattle.sequenceOf(listOf(ShotResult.MISS, ShotResult.ALREADY_FIRED, ShotResult.HIT)))
        assertEquals("", DailyBattle.sequenceOf(emptyList()))
    }

    @Test
    fun placarSoComDiaResultadoETiros() {
        val win = DailyBattle.finished(20_782L, true, 41)
        val pt = assertNotNull(DailyBattle.shareText(win, Lang.PT))
        assertTrue("#3" in pt && "41 tiros" in pt, pt)
        assertTrue("Daily Battle #3" in assertNotNull(DailyBattle.shareText(win, Lang.EN)))
        assertTrue("Batalla del Día #3" in assertNotNull(DailyBattle.shareText(win, Lang.ES)))
        assertTrue("resistiu" in assertNotNull(DailyBattle.shareText(DailyBattle.finished(20_782L, false, 55), Lang.PT)))
        // registro antigo, sem sequência: o texto de antes, sem linha de emoji, sem link nem placeholder cru
        Lang.entries.forEach { lang ->
            val text = assertNotNull(DailyBattle.shareText(win, lang))
            assertFalse("http" in text || "%" in text, text)
            assertFalse(DailyBattle.SHOT_EMOJIS.any { it in text }, text)
        }
        // começou e não terminou: nada a compartilhar
        assertNull(DailyBattle.shareText(DailyBattle.started(20_782L), Lang.PT))
    }

    @Test
    fun placarComUmEmojiPorTiroNaOrdemSemCoordenada() {
        val seq = "MMHHSMHHHSMMMHS"
        val win = DailyBattle.finished(20_782L, true, seq.length, seq)
        val expected = seq.map { DailyBattle.emojiOf(it) }
        Lang.entries.forEach { lang ->
            val text = assertNotNull(DailyBattle.shareText(win, lang))
            val base = assertNotNull(DailyBattle.shareText(DailyBattle.finished(20_782L, true, seq.length), lang))
            // o texto de antes continua no começo; a sequência vem depois, separada por linha em branco
            assertTrue(text.startsWith("$base\n\n"), text)
            val grid = text.removePrefix("$base\n\n")
            val lines = grid.split('\n')
            // quebra a cada 10 tiros só para ler melhor
            assertEquals(listOf(10, 5), lines.map { emojisIn(it).size }, text)
            assertEquals(expected, lines.flatMap { emojisIn(it) }, text)
            // na grade não há letra nem número de casa (sem coordenada, sem posição de navio)
            assertTrue(grid.none { it.isLetterOrDigit() }, grid)
            assertFalse("http" in text || "%" in text || "@" in text, text)
        }
        // três símbolos diferentes: erro, acerto e afundado não se confundem
        assertEquals(3, DailyBattle.SHOT_EMOJIS.toSet().size)
        // a tela de resultado mostra exatamente a sequência do texto compartilhado (#138)
        Lang.entries.forEach { lang ->
            val text = assertNotNull(DailyBattle.shareText(win, lang))
            assertTrue(text.endsWith("\n\n" + assertNotNull(DailyBattle.emojiLines(win))), text)
        }
        // sem sequência (registro antigo) ou sem fim de partida: nada a mostrar
        assertNull(DailyBattle.emojiLines(DailyBattle.finished(20_782L, true, seq.length)))
        assertNull(DailyBattle.emojiLines(DailyBattle.started(20_782L)))
        // derrota também leva a sequência
        val loss = assertNotNull(DailyBattle.shareText(DailyBattle.finished(20_782L, false, 3, "MHM"), Lang.PT))
        assertTrue(loss.endsWith(listOf('M', 'H', 'M').joinToString("") { DailyBattle.emojiOf(it) }), loss)
    }

    @Test
    fun placarComLinkDaLojaTerminaNeleNasTresLinguas() {
        val link = "https://example.test/loja?referrer=utm_source%3Dbatalha_do_dia"
        val seq = "MMHHSMHHHSMMMHS"
        val records = listOf(
            DailyBattle.finished(20_782L, true, seq.length, seq),
            DailyBattle.finished(20_782L, false, 3, "MHM"),
            DailyBattle.finished(20_782L, true, 41) // registro antigo, sem sequência
        )
        Lang.entries.forEach { lang ->
            records.forEach { r ->
                val sem = assertNotNull(DailyBattle.shareText(r, lang))
                val com = assertNotNull(DailyBattle.shareText(r, lang, link))
                // sem link o texto é o de antes; com link só acrescenta uma linha em branco e o link no fim
                assertEquals(sem, assertNotNull(DailyBattle.shareText(r, lang, "")))
                assertEquals("$sem\n\n$link", com, lang.code)
                assertTrue(com.endsWith(link), com)
                // o emoji de cada tiro segue igual
                assertEquals(sem.count { it in DailyBattle.SHOT_EMOJIS.joinToString("") }, com.count { it in DailyBattle.SHOT_EMOJIS.joinToString("") })
            }
        }
        // começou e não terminou: nada a compartilhar, nem o link sozinho
        assertNull(DailyBattle.shareText(DailyBattle.started(20_782L), Lang.PT, link))
    }

    @Test
    fun linkDoPlacarDaBatalhaDoDiaTemAOrigemEFicaCurto() {
        if (platformName == "ios") {
            // o binário do iPhone não leva link da Play (#174): o placar sai sem link
            assertEquals("", dailyBattleStoreLink)
            return
        }
        assertEquals(
            "https://play.google.com/store/apps/details?id=aigamesfactory.navalbattleclassic" +
                "&referrer=utm_source%3Dbatalha_do_dia%26utm_medium%3Dorganic%26utm_campaign%3Dbatalha_do_dia",
            dailyBattleStoreLink
        )
        // o `&` do referrer vai codificado: um `&` cru cortaria a origem na Play
        assertFalse("&utm_" in dailyBattleStoreLink)
        // mensagem de WhatsApp: o texto sem a grade de emojis, com o link, continua curto
        Lang.entries.forEach { lang ->
            val text = assertNotNull(DailyBattle.shareText(DailyBattle.finished(20_782L, true, 41), lang, dailyBattleStoreLink))
            assertTrue(text.length <= 280, "${lang.code}: ${text.length}")
        }
    }

    @Test
    fun batalhaDoDiaNaoContaNaCarreira() {
        assertFalse(DailyBattle.countsForCareer(daily = true))
        assertTrue(DailyBattle.countsForCareer(daily = false))
    }

    /** Separa uma linha da grade em emojis (cada um é um par substituto em UTF-16). */
    private fun emojisIn(line: String): List<String> =
        DailyBattle.SHOT_EMOJIS.let { all ->
            val out = mutableListOf<String>()
            var rest = line
            while (rest.isNotEmpty()) {
                val e = all.firstOrNull { rest.startsWith(it) } ?: error("símbolo inesperado em '$line'")
                out += e
                rest = rest.removePrefix(e)
            }
            out
        }

    private companion object {
        val GOLDEN_FIRST_FIVE: List<Int> = listOf(20, 42, 36, 15, 67)
    }
}
