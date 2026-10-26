package br.com.navalbattle.game

import br.com.navalbattle.data.REMINDER_HOUR

/** Que texto sai num horário do plano de lembretes. */
enum class ReminderKind {
    /** Lembrete diário das 19h de sempre. */
    DAILY,

    /** Lembrete das 19h depois de 2 dias sem abrir: "seu diário de bordo está esperando". */
    DAILY_AWAY,

    /** Aviso de retorno (5 dias sem abrir), às 12h, prometendo o prêmio. */
    RETURN,

    /** Aviso de retorno sem prometer prêmio (resgatado há menos de 30 dias). */
    RETURN_NO_PRIZE
}

/** Um aviso do plano: [dayOffset] dias a partir de hoje, na [hour] do relógio do aparelho. */
data class ReminderSlot(val dayOffset: Int, val hour: Int, val kind: ReminderKind)

private const val AWAY_FROM_DAYS = 2
private const val RETURN_AT_DAYS = 5
private const val RETURN_NOTICE_HOUR = 12
private const val NOTICE_GAP_DAYS = 7
private const val BONUS_COOLDOWN_DAYS = 30
private const val PLAN_DAYS = 7

/**
 * Plano de notificações locais do Diário de bordo (#42), sem servidor de push. Puro: o dia é o
 * número de dias da data de Brasília (ver [epochDayOf]) e o horário é o do aparelho.
 *
 * - Aberto há menos de 2 dias: só o lembrete das 19h.
 * - 2 a 4 dias sem abrir: o lembrete das 19h troca de texto.
 * - 5 dias sem abrir: um aviso de retorno às 12h e o das 19h daquele dia não sai.
 * - Depois disso nada sai até o jogador abrir o app. No máximo 1 aviso de retorno a cada 7 dias.
 * - Sem conta ou com o lembrete desligado: plano vazio.
 */
object ReturnReminder {
    fun plan(
        today: Int,
        lastOpenDay: Int?,
        lastReturnNoticeDay: Int?,
        lastReturnBonusDay: Int?,
        reminderOn: Boolean,
        signedIn: Boolean,
        checkedInToday: Boolean
    ): List<ReminderSlot> {
        if (!reminderOn || !signedIn) return emptyList()
        val opened = lastOpenDay ?: today
        val slots = mutableListOf<ReminderSlot>()
        for (offset in 0 until PLAN_DAYS) {
            val day = today + offset
            val away = (day - opened).coerceAtLeast(0)
            val slot = when {
                offset == 0 && checkedInToday -> null
                away < AWAY_FROM_DAYS -> ReminderSlot(offset, REMINDER_HOUR, ReminderKind.DAILY)
                away < RETURN_AT_DAYS -> ReminderSlot(offset, REMINDER_HOUR, ReminderKind.DAILY_AWAY)
                away == RETURN_AT_DAYS -> {
                    val noticeFree = lastReturnNoticeDay == null || day - lastReturnNoticeDay >= NOTICE_GAP_DAYS
                    val prize = lastReturnBonusDay == null || day - lastReturnBonusDay >= BONUS_COOLDOWN_DAYS
                    when {
                        !noticeFree -> ReminderSlot(offset, REMINDER_HOUR, ReminderKind.DAILY_AWAY)
                        prize -> ReminderSlot(offset, RETURN_NOTICE_HOUR, ReminderKind.RETURN)
                        else -> ReminderSlot(offset, RETURN_NOTICE_HOUR, ReminderKind.RETURN_NO_PRIZE)
                    }
                }
                else -> null
            }
            if (slot != null) slots += slot
        }
        return slots
    }

    /** O aviso de retorno agendado para [pendingDay] já passou ou foi substituído por uma abertura? */
    fun noticeSettled(pendingDay: Int, today: Int): Boolean = pendingDay in 1..today
}

/** Dias desde 1970-01-01 de uma data "AAAA-MM-DD" (a de Brasília, vinda do servidor); null se inválida. */
fun epochDayOf(date: String): Int? {
    val parts = date.split("-")
    if (parts.size != 3) return null
    val y0 = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}
