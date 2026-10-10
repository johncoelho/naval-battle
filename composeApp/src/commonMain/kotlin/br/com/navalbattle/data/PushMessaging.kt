package br.com.navalbattle.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pushes que chegam com o jogo aberto (o sistema não mostra a notificação nesse caso).
 * Carrega só o `kind` do aviso, para a tela certa reler o que mudou no servidor —
 * sem isso o pedido de amizade aceito só aparecia depois de sair e voltar.
 */
object PushInbox {
    private val flow = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = flow

    fun post(kind: String) {
        flow.tryEmit(kind)
    }

    /**
     * Push tocado pelo jogador (o sistema abriu o app com o `kind` nos extras, #19).
     * Fica guardado até o jogo sair da abertura e levar à tela certa ([consumeOpened]).
     */
    private val openedFlow = MutableStateFlow<String?>(null)
    val opened: StateFlow<String?> = openedFlow

    fun open(kind: String) {
        if (kind.isNotBlank()) openedFlow.value = kind
    }

    fun consumeOpened() {
        openedFlow.value = null
    }
}

/**
 * Push de verdade: convites de amigo, pedidos de amizade e adversário encontrado
 * chegam com o app fechado. Android via Firebase Cloud Messaging; o envio sai do
 * Supabase (supabase/push.sql + Edge Function `push`). No iPhone ainda não existe
 * (precisa de conta paga da Apple para o APNs) e [token] devolve nulo.
 */
expect object PushMessaging {
    /** "android" ou "ios" — vai junto com o token para o servidor. */
    val platform: String

    /** Token deste aparelho, ou nulo quando o push não está disponível. */
    suspend fun token(): String?
}
