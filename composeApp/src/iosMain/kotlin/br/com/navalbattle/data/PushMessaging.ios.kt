package br.com.navalbattle.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Push no iPhone (#14): token do APNs, sem SDK novo. O registro (`registerForRemoteNotifications`)
 * vale com ou sem permissão de aviso; sem permissão o token chega igual e só o aviso não aparece.
 * O pedido de permissão continua sendo o do lembrete do Diário ([DailyReminder.requestPermission]),
 * que só pergunta enquanto a pessoa não respondeu: negou, não pergunta de novo a cada abertura.
 * O token volta pelo `AppDelegate` (Swift) via [PushBridge]. Sem assinatura (.ipa do Sideloadly) ou no
 * simulador o sistema recusa e o token é nulo.
 */
actual object PushMessaging {
    actual val platform: String = "ios"

    private const val TIMEOUT_MS = 10_000L

    actual suspend fun token(): String? {
        val waiting = PushBridge.expect()
        withContext(Dispatchers.Main) { PushBridge.register?.invoke() }
        val fresh = withTimeoutOrNull(TIMEOUT_MS) { waiting.await() }
        return (fresh ?: PushBridge.last)?.takeIf { isApnsToken(it) }
    }
}
