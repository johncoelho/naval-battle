package br.com.navalbattle.data

import br.com.navalbattle.game.ReminderKind
import br.com.navalbattle.game.ReminderSlot

/** Título e texto de uma notificação. */
data class ReminderMessage(val title: String, val body: String)

/** Os textos de cada tipo do plano ([ReturnReminder.plan]), já na língua do jogador. */
data class ReminderTexts(
    val daily: ReminderMessage,
    val away: ReminderMessage,
    val returnPrize: ReminderMessage,
    val returnNoPrize: ReminderMessage
) {
    fun of(kind: ReminderKind): ReminderMessage = when (kind) {
        ReminderKind.DAILY -> daily
        ReminderKind.DAILY_AWAY -> away
        ReminderKind.RETURN -> returnPrize
        ReminderKind.RETURN_NO_PRIZE -> returnNoPrize
    }
}

/**
 * Lembrete do Diário de bordo — notificação LOCAL, agendada pelo próprio aparelho
 * (sem servidor de push). O app calcula o plano dos próximos dias (lembrete das
 * [REMINDER_HOUR]h, com o texto trocado depois de 2 dias sem abrir, e um aviso de
 * retorno às 12h aos 5 dias; ver `ReturnReminder`) e o entrega aqui; é refeito toda
 * vez que o app abre, e o que sobra do plano continua saindo sem abrir o app.
 */
expect object DailyReminder {
    /** Pede a permissão de notificação (Android 13+ e iOS) — o sistema só pergunta uma vez. */
    fun requestPermission()

    /** Troca o que estava agendado pelo plano [slots] (vazio = cancela tudo). */
    fun schedule(slots: List<ReminderSlot>, texts: ReminderTexts)

    fun cancel()

    /** Dispara uma notificação de teste daqui a [TEST_DELAY_SECONDS]s (Ajustes → Testar notificação). */
    fun sendTest(title: String, body: String)

    /** Consulta se o sistema deixa o app notificar (permissão dada e notificações ligadas). */
    fun checkAllowed(onResult: (Boolean) -> Unit)

    /** Abre a tela de notificações do app nos ajustes do sistema. */
    fun openSystemSettings()
}

const val TEST_DELAY_SECONDS = 5

const val REMINDER_HOUR = 19
