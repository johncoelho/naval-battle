package br.com.navalbattle.game

import br.com.navalbattle.data.DailyCheckin
import br.com.navalbattle.data.DailyStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReturnReminderTest {
    private val today = 20000

    private fun plan(
        lastOpen: Int? = today,
        notice: Int? = null,
        bonus: Int? = null,
        on: Boolean = true,
        signedIn: Boolean = true,
        checkedIn: Boolean = false
    ) = ReturnReminder.plan(today, lastOpen, notice, bonus, on, signedIn, checkedIn)

    private fun kinds(p: List<ReminderSlot>) = p.map { it.dayOffset to it.kind }

    @Test
    fun openedTodayOnlyDaily() {
        val p = plan()
        assertEquals(listOf(0, 1), p.filter { it.kind == ReminderKind.DAILY }.map { it.dayOffset })
        // a partir do 2º dia sem abrir o texto muda
        assertEquals(ReminderKind.DAILY_AWAY, p.first { it.dayOffset == 2 }.kind)
        assertTrue(p.all { it.kind != ReminderKind.RETURN_NO_PRIZE })
    }

    @Test
    fun checkedInTodaySkipsToday() {
        assertEquals(1, plan(checkedIn = true).first().dayOffset)
    }

    @Test
    fun twoDaysAwayChangesDailyText() {
        val p = plan(lastOpen = today - 2)
        assertEquals(listOf(0 to ReminderKind.DAILY_AWAY, 1 to ReminderKind.DAILY_AWAY, 2 to ReminderKind.DAILY_AWAY), kinds(p).take(3))
        assertTrue(p.all { it.hour == 19 || it.kind == ReminderKind.RETURN })
    }

    @Test
    fun fiveDaysAwayReturnNoticeAtNoonWithoutDailyThatDay() {
        val p = plan(lastOpen = today - 5)
        assertEquals(listOf(ReminderSlot(0, 12, ReminderKind.RETURN)), p)
    }

    @Test
    fun returnNoticeFromOpenDayFiveAhead() {
        val p = plan()
        val ret = p.filter { it.kind == ReminderKind.RETURN }
        assertEquals(listOf(ReminderSlot(5, 12, ReminderKind.RETURN)), ret)
        assertTrue(p.none { it.dayOffset == 5 && it.kind != ReminderKind.RETURN })
        // nada depois do dia 5
        assertTrue(p.none { it.dayOffset > 5 })
    }

    @Test
    fun nothingAfterDayFive() {
        assertEquals(emptyList(), plan(lastOpen = today - 6))
        assertEquals(emptyList(), plan(lastOpen = today - 12))
    }

    @Test
    fun noticeThreeDaysAgoBlocksReturn() {
        val p = plan(lastOpen = today - 5, notice = today - 3)
        assertEquals(listOf(ReminderSlot(0, 19, ReminderKind.DAILY_AWAY)), p)
        // 7 dias depois do último já pode
        val ok = plan(lastOpen = today - 5, notice = today - 7)
        assertEquals(ReminderKind.RETURN, ok.single().kind)
    }

    @Test
    fun bonusTenDaysAgoNoPrize() {
        val p = plan(lastOpen = today - 5, bonus = today - 10)
        assertEquals(listOf(ReminderSlot(0, 12, ReminderKind.RETURN_NO_PRIZE)), p)
        // carência de 30 dias vencida: promete de novo
        assertEquals(ReminderKind.RETURN, plan(lastOpen = today - 5, bonus = today - 30).single().kind)
    }

    @Test
    fun offOrSignedOutIsEmpty() {
        assertEquals(emptyList(), plan(on = false))
        assertEquals(emptyList(), plan(signedIn = false))
    }

    @Test
    fun noticeSettledOnlyWhenDayArrived() {
        assertTrue(ReturnReminder.noticeSettled(100, 100))
        assertTrue(ReturnReminder.noticeSettled(100, 103))
        assertEquals(false, ReturnReminder.noticeSettled(101, 100))
        assertEquals(false, ReturnReminder.noticeSettled(0, 100))
    }

    @Test
    fun epochDayOfKnownDates() {
        assertEquals(0, epochDayOf("1970-01-01"))
        assertEquals(19723, epochDayOf("2024-01-01"))
        assertEquals(epochDayOf("2026-02-28")!! + 1, epochDayOf("2026-03-01"))
        assertEquals(epochDayOf("2024-02-29")!! + 1, epochDayOf("2024-03-01"))
        assertNull(epochDayOf("lixo"))
        assertNull(epochDayOf("2026-13-01"))
    }

    @Test
    fun oldServerResponseDefaultsToNoReturn() {
        val checkin = DailyCheckin(streak = 1, doubloons = 25, weekCompleted = false)
        assertEquals(0, checkin.returnDoubloons)
        assertEquals(0, checkin.returnMiles)
        val status = DailyStatus("2026-10-26", 0, false, 25, 300, "PLAY_1", 1, false, 50, 100)
        assertEquals(false, status.returnReady)
        assertEquals(0, status.returnDoubloons)
    }
}
