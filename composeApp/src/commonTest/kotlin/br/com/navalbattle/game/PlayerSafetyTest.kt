package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Denunciar e bloquear (#58): o servidor depende destes códigos e destas respostas. */
class PlayerSafetyTest {

    @Test
    fun reasonCodesAreStable() {
        assertEquals(listOf("name", "abuse", "cheat", "other"), ReportReason.entries.map { it.code })
    }

    @Test
    fun contextCodesAreStableAndHaveNoChat() {
        assertEquals(listOf("ranking", "search", "friends", "match", "result"), ReportContext.entries.map { it.code })
    }

    @Test
    fun reportResultParses() {
        assertEquals(ReportOutcome.OK, parseReportResult("ok"))
        assertEquals(ReportOutcome.ALREADY, parseReportResult("already"))
        assertEquals(ReportOutcome.LIMIT, parseReportResult("limit"))
        assertEquals(ReportOutcome.SELF, parseReportResult("self"))
        assertEquals(ReportOutcome.INVALID, parseReportResult("invalid"))
        assertEquals(ReportOutcome.OK, parseReportResult(" ok "))
        assertEquals(ReportOutcome.ERROR, parseReportResult(null))
        assertEquals(ReportOutcome.ERROR, parseReportResult("qualquer coisa"))
    }

    @Test
    fun blockResultParses() {
        assertEquals(BlockOutcome.OK, parseBlockResult("ok"))
        assertEquals(BlockOutcome.SELF, parseBlockResult("self"))
        assertEquals(BlockOutcome.INVALID, parseBlockResult("invalid"))
        assertEquals(BlockOutcome.ERROR, parseBlockResult(""))
    }

    @Test
    fun usernameResultParses() {
        assertEquals(UsernameOutcome.OK, parseUsernameResult("ok"))
        assertEquals(UsernameOutcome.OFFENSIVE, parseUsernameResult("offensive"))
        assertEquals(UsernameOutcome.TAKEN, parseUsernameResult("taken"))
        assertEquals(UsernameOutcome.INVALID, parseUsernameResult("invalid"))
        assertEquals(UsernameOutcome.ERROR, parseUsernameResult(null))
    }

    @Test
    fun displayOtherMasksOffensiveNames() {
        assertEquals("Comandante", displayOther("Caralho", "Comandante"))
        assertEquals("Almirante", displayOther("Almirante", "Comandante"))
        assertFalse(displayOther("Fuck", "X") == "Fuck")
        assertTrue(displayOther("", "X") == "X")
    }
}
