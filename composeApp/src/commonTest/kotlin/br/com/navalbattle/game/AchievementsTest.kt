package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {
    private fun unlocked(list: List<AchievementUpdate>) =
        list.filterIsInstance<AchievementUpdate.Unlock>().map { it.achievement }.toSet()

    private fun end(victory: Boolean, online: Boolean = false, lost: Int = 2, sunk: Int = 0) =
        AchievementEvent.MatchEnd(victory, online, lost, sunk)

    @Test
    fun primeiraVitoriaContraIa() {
        val got = unlocked(achievementsFor(end(victory = true)))
        assertTrue(Achievement.PrimeiraVitoria in got)
        assertTrue(Achievement.PrimeiraOnline !in got)
    }

    @Test
    fun vitoriaOnlineDaTambemBatismoDeFogo() {
        val got = unlocked(achievementsFor(end(victory = true, online = true)))
        assertEquals(setOf(Achievement.PrimeiraVitoria, Achievement.PrimeiraOnline), got)
    }

    @Test
    fun vitoriaSemPerderNavioDaFrotaIntacta() {
        assertTrue(Achievement.FrotaIntacta in unlocked(achievementsFor(end(victory = true, lost = 0))))
        assertTrue(Achievement.FrotaIntacta !in unlocked(achievementsFor(end(victory = true, lost = 1))))
    }

    @Test
    fun derrotaNaoDesbloqueiaNada() {
        assertTrue(unlocked(achievementsFor(end(victory = false, lost = 0))).isEmpty())
    }

    @Test
    fun naviosAfundadosViramPassos() {
        val got = achievementsFor(end(victory = false, sunk = 3))
        assertEquals(listOf<AchievementUpdate>(AchievementUpdate.Increment(Achievement.Cacador100, 3)), got)
        assertTrue(achievementsFor(end(victory = false, sunk = 0)).isEmpty())
    }

    @Test
    fun vitoriaRanqueadaSomaUmPasso() {
        assertEquals(
            listOf<AchievementUpdate>(AchievementUpdate.Increment(Achievement.Ranqueada10, 1)),
            achievementsFor(AchievementEvent.RankedWin)
        )
    }

    @Test
    fun sequenciaSeteDesbloqueiaDiario() {
        assertTrue(achievementsFor(AchievementEvent.CheckIn(6)).isEmpty())
        assertEquals(setOf(Achievement.Diario7), unlocked(achievementsFor(AchievementEvent.CheckIn(7))))
    }

    @Test
    fun patenteCapitaoDeCorvetaDesbloqueia() {
        assertTrue(achievementsFor(AchievementEvent.RankUp(Rank.TENENTE)).isEmpty())
        assertEquals(setOf(Achievement.CapitaoCorveta), unlocked(achievementsFor(AchievementEvent.RankUp(Rank.CAPITAO_CORVETA))))
        assertEquals(setOf(Achievement.CapitaoCorveta), unlocked(achievementsFor(AchievementEvent.RankUp(Rank.ALMIRANTE))))
    }

    @Test
    fun backfillDeCarreiraComCentoEVinteNaviosMandaCemPassos() {
        val got = backfill(AchievementCareer(wins = 5, shipsSunk = 120, rank = Rank.CABO, checkInStreak = 2))
        assertTrue(AchievementUpdate.SetSteps(Achievement.Cacador100, 100) in got)
        assertTrue(got.none { it is AchievementUpdate.SetSteps && it.steps > 100 })
        assertTrue(got.none { it is AchievementUpdate.Increment })
        assertEquals(setOf(Achievement.PrimeiraVitoria), unlocked(got))
    }

    @Test
    fun backfillDeCarreiraVaziaNaoMandaNada() {
        assertTrue(backfill(AchievementCareer(0, 0, Rank.RECRUTA, 0)).isEmpty())
    }

    @Test
    fun backfillDeVeteranoDesbloqueiaPatenteEDiario() {
        val got = unlocked(backfill(AchievementCareer(40, 30, Rank.CAPITAO_FRAGATA, 9)))
        assertEquals(setOf(Achievement.PrimeiraVitoria, Achievement.Diario7, Achievement.CapitaoCorveta), got)
    }

    @Test
    fun placarFicaNoLimiteDoConsole() {
        assertEquals(0L, leaderboardScore(-5))
        assertEquals(1234L, leaderboardScore(1234))
        assertEquals(100_000L, leaderboardScore(250_000))
    }
}
