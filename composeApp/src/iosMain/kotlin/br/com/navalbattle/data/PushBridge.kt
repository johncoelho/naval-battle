package br.com.navalbattle.data

import kotlinx.coroutines.CompletableDeferred

/**
 * Ponte entre o `AppDelegate` (Swift, iosApp/iOSApp.swift) e o Kotlin: o iOS devolve o token do APNs
 * num callback do delegate, não como retorno de função (#14). O Swift chama [onToken]/[onError]; o
 * [PushMessaging.token] espera o resultado por [expect].
 */
object PushBridge {
    private var waiting: CompletableDeferred<String?>? = null

    /**
     * Pede ao sistema o registro no APNs. Quem define é o `AppDelegate` (Swift): o cinterop do Kotlin
     * não expõe `UIApplication.registerForRemoteNotifications`. Nulo = sem Swift por trás (testes).
     */
    var register: (() -> Unit)? = null

    /** Último token válido recebido (fica de reserva se o próximo pedido demorar). */
    var last: String? = null
        private set

    /** Token do APNs em hexadecimal minúsculo, como o servidor guarda. */
    fun onToken(hex: String) {
        if (!isApnsToken(hex)) {
            waiting?.complete(null)
            return
        }
        last = hex
        waiting?.complete(hex)
    }

    /** O sistema recusou o registro (sem entitlement, simulador sem push, rede). Sem token, sem erro. */
    fun onError(@Suppress("UNUSED_PARAMETER") message: String) {
        waiting?.complete(null)
    }

    fun expect(): CompletableDeferred<String?> {
        val d = CompletableDeferred<String?>()
        waiting = d
        return d
    }
}
