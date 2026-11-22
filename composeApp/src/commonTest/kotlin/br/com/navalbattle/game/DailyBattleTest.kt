package br.com.navalbattle.game

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
    fun placarSoComDiaResultadoETiros() {
        val win = DailyBattle.finished(20_782L, true, 41)
        val pt = assertNotNull(DailyBattle.shareText(win, Lang.PT))
        assertTrue("#3" in pt && "41 tiros" in pt, pt)
        assertTrue("Daily Battle #3" in assertNotNull(DailyBattle.shareText(win, Lang.EN)))
        assertTrue("Batalla del Día #3" in assertNotNull(DailyBattle.shareText(win, Lang.ES)))
        assertTrue("resistiu" in assertNotNull(DailyBattle.shareText(DailyBattle.finished(20_782L, false, 55), Lang.PT)))
        // sem link, sem grade, sem placeholder cru
        Lang.entries.forEach { lang ->
            val text = assertNotNull(DailyBattle.shareText(win, lang))
            assertFalse("http" in text || "%" in text || "🟦" in text, text)
        }
        // começou e não terminou: nada a compartilhar
        assertNull(DailyBattle.shareText(DailyBattle.started(20_782L), Lang.PT))
    }

    private companion object {
        val GOLDEN_FIRST_FIVE: List<Int> = listOf(20, 42, 36, 15, 67)
    }
}
