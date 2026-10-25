package br.com.navalbattle.game

/**
 * Aviso de lançamento (#36): popup único na entrada do jogo, só no Android. A decisão é pura —
 * o estado vem do servidor (RPC `launch_notice_status`) e a marca local do [br.com.navalbattle.data.Prefs].
 */
data class LaunchNoticeStatus(val enabled: Boolean, val variant: String, val seen: Boolean)

/** Variação do texto: agradecimento ao testador do beta ou boas-vindas ao jogador novo. */
enum class LaunchVariant { TESTER, NEW }

sealed interface LaunchNoticeDecision {
    /** Nada aparece. */
    data object Hide : LaunchNoticeDecision

    /** Mostra o popup com a variação indicada. */
    data class Show(val variant: LaunchVariant) : LaunchNoticeDecision

    /** Já visto neste aparelho mas o servidor ainda não sabe: reenvia o ack em silêncio. */
    data object AckOnly : LaunchNoticeDecision
}

/** Chave da marca local: por conta, ou `guest` para convidado. */
fun launchNoticeKey(userId: String?): String = "launch_notice_seen_${userId ?: "guest"}"

/**
 * Sem status (falha de rede, iPhone) = [LaunchNoticeDecision.Hide]: o aviso nunca trava a entrada.
 * Variante desconhecida do servidor cai em [LaunchVariant.NEW].
 */
fun launchNoticeDecision(status: LaunchNoticeStatus?, signedIn: Boolean, localSeen: Boolean): LaunchNoticeDecision {
    if (status == null || !status.enabled) return LaunchNoticeDecision.Hide
    if (status.seen) return LaunchNoticeDecision.Hide
    if (localSeen) return if (signedIn) LaunchNoticeDecision.AckOnly else LaunchNoticeDecision.Hide
    val variant = if (signedIn && status.variant == "tester") LaunchVariant.TESTER else LaunchVariant.NEW
    return LaunchNoticeDecision.Show(variant)
}
