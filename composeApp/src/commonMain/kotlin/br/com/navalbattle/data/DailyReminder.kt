package br.com.navalbattle.data

/**
 * Lembrete diário do Diário de bordo — notificação LOCAL, agendada pelo próprio
 * aparelho (sem servidor de push). Sai às [REMINDER_HOUR]h; com o check-in de hoje
 * feito, o próximo fica para amanhã ([schedule] com `skipToday`). Continua saindo
 * nos dias seguintes mesmo sem abrir o app (Android reagenda ao disparar; iOS deixa
 * a semana agendada), e é refeito toda vez que o app abre.
 */
expect object DailyReminder {
    /** Pede a permissão de notificação (Android 13+ e iOS) — o sistema só pergunta uma vez. */
    fun requestPermission()

    fun schedule(title: String, body: String, skipToday: Boolean)

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
