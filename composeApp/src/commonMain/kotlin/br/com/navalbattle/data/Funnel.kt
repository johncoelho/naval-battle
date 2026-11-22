package br.com.navalbattle.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ações do funil que o jogo conta (#137, `supabase/funnel.sql`). Cada entrada é um par (evento, origem) do
 * catálogo fechado do servidor: o RPC `log_funnel_event` descarta o que não está lá. **Só contador diário**,
 * sem identificador de pessoa, hora ou texto livre; evento novo = entrada aqui + migração do catálogo
 * + linha no teste (`FunnelTest`) e, se mudar a natureza do dado, na política de privacidade.
 */
enum class FunnelAction(val event: String, val origin: String) {
    STORE_VIEWED_MENU("store_viewed", "menu"),
    STORE_VIEWED_HOOK("store_viewed", "hook"),
    STORE_VIEWED_POPUP("store_viewed", "popup"),
    STORE_VIEWED_OTHER("store_viewed", "outro"),

    INVITE_SHARED_REFERRAL_CARD("invite_shared", "referral_card"),
    INVITE_SHARED_HOOK("invite_shared", "hook"),
    INVITE_SHARED_SETTINGS_STORE("invite_shared", "settings_store"),

    FIRST_MATCH_BOT_WIN("first_match_end", "bot_win"),
    FIRST_MATCH_BOT_LOSS("first_match_end", "bot_loss"),
    FIRST_MATCH_BOT_ABANDON("first_match_end", "bot_abandon"),
    FIRST_MATCH_HUMAN_WIN("first_match_end", "human_win"),
    FIRST_MATCH_HUMAN_LOSS("first_match_end", "human_loss"),
    FIRST_MATCH_HUMAN_ABANDON("first_match_end", "human_abandon"),

    HOOK_SHOWN_WIN("hook_shown", "win"),
    HOOK_SHOWN_LOSS("hook_shown", "loss"),
    HOOK_REMATCH_WIN("hook_rematch", "win"),
    HOOK_REMATCH_LOSS("hook_rematch", "loss"),
    HOOK_INVITE_WIN("hook_invite", "win"),
    HOOK_INVITE_LOSS("hook_invite", "loss"),
    HOOK_CHALLENGE_WIN("hook_challenge", "win"),
    HOOK_CHALLENGE_LOSS("hook_challenge", "loss"),

    REVIEW_REQUESTED_WIN("review_requested", "win"),
    REVIEW_REQUESTED_RANKUP("review_requested", "rankup"),

    PUSH_OPENED_NEW_VERSION("push_opened", "new_version"),
    PUSH_OPENED_QUICK_MATCH_INVITE("push_opened", "quick_match_invite"),
    PUSH_OPENED_DAILY_INVITE("push_opened", "daily_invite"),
    PUSH_OPENED_FRIEND_ONLINE("push_opened", "friend_online"),
    PUSH_OPENED_MATCH_INVITE("push_opened", "match_invite"),
    PUSH_OPENED_OTHER("push_opened", "outro");

    companion object {
        /** Fim da 1ª partida: o resultado vira a origem (contra adversário virtual ou humano; ganhou, perdeu, saiu). */
        fun firstMatchEnd(vsBot: Boolean, won: Boolean, abandoned: Boolean): FunnelAction = when {
            vsBot && abandoned -> FIRST_MATCH_BOT_ABANDON
            vsBot && won -> FIRST_MATCH_BOT_WIN
            vsBot -> FIRST_MATCH_BOT_LOSS
            abandoned -> FIRST_MATCH_HUMAN_ABANDON
            won -> FIRST_MATCH_HUMAN_WIN
            else -> FIRST_MATCH_HUMAN_LOSS
        }

        /** Origem do `push_opened` pelo `kind` que o servidor manda no push; desconhecido = `outro`. */
        fun pushOpened(kind: String?): FunnelAction = when (kind) {
            "new_version" -> PUSH_OPENED_NEW_VERSION
            "quick_match_found", "quick_match_invite" -> PUSH_OPENED_QUICK_MATCH_INVITE
            "daily_invite" -> PUSH_OPENED_DAILY_INVITE
            "friend_online" -> PUSH_OPENED_FRIEND_ONLINE
            "match_invite" -> PUSH_OPENED_MATCH_INVITE
            else -> PUSH_OPENED_OTHER
        }
    }
}

object Funnel {
    /**
     * Conta uma ação sem esperar e sem relançar erro: instrumentação nunca derruba nem atrasa o jogo. Sem sessão
     * (convidado, aparelho offline) ou sem escopo, não faz nada e o evento é descartado. [send] é a chamada de rede
     * (no app, com renovação do token).
     */
    fun log(scope: CoroutineScope?, signedIn: Boolean, send: suspend () -> Unit) {
        if (scope == null || !signedIn) return
        scope.launch {
            try {
                send()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // evento perdido; nada a fazer
            }
        }
    }
}
