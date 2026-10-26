package br.com.navalbattle.game

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang

/**
 * Convide um amigo (#44). O servidor decide e paga (`supabase/referral.sql`); aqui ficam só a forma do
 * código, o texto do compartilhamento e a tradução da resposta do servidor em mensagem.
 */

private const val CODE_LENGTH = 6
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val STORE_LINK = "https://play.google.com/store/apps/details?id=aigamesfactory.navalbattleclassic"

/** Valores só para MOSTRAR antes do resgate; quem paga é o servidor (`referral_doubloons_*`, `referral_total_limit`). */
internal const val REFERRAL_SHOWN_DOUBLOONS = 100
internal const val REFERRAL_SHOWN_TOTAL_LIMIT = 10

/** Maiúsculas, sem espaço nem hífen: o que o comandante digitou ou colou vira o formato do servidor. */
fun normalizeReferralCode(text: String): String =
    text.filter { !it.isWhitespace() && it != '-' }.uppercase()

/** Forma válida (6 símbolos do alfabeto sem 0/O/1/I): código fora disso nem vai à rede. */
fun isValidReferralCodeShape(text: String): Boolean {
    val c = normalizeReferralCode(text)
    return c.length == CODE_LENGTH && c.all { it in CODE_ALPHABET }
}

/** Texto do seletor de compartilhamento: convite, link da loja e o código. */
fun referralShareText(code: String, lang: Lang): String {
    val key = K.REFERRAL_SHARE_TEXT
    val raw = when (lang) {
        Lang.PT -> key.pt
        Lang.EN -> key.en
        Lang.ES -> key.es
    }
    return raw.replace("%1", STORE_LINK).replace("%2", code)
}

/** Resposta de `referral_redeem`. */
data class ReferralRedeem(val status: String, val inviterName: String)

/** Resposta de `referral_settle`: `paid` é o único que paga; `pending`/`none`/`already` seguem em silêncio. */
data class ReferralSettle(val status: String, val doubloons: Int, val balance: Int)

/** Um convite que eu fiz (`referral_status`): state = waiting | paid | pending | over_limit | qa. */
data class ReferralInvite(val name: String, val state: String, val amount: Int, val unseen: Boolean)

enum class ReferralOutcome { OK, INVALID, SELF, ALREADY, TOO_OLD, NOT_ELIGIBLE, DISABLED, TOO_MANY, OFFLINE }

fun referralOutcome(r: ReferralRedeem?): ReferralOutcome = when (r?.status) {
    null -> ReferralOutcome.OFFLINE
    "ok" -> ReferralOutcome.OK
    "invalid" -> ReferralOutcome.INVALID
    "self" -> ReferralOutcome.SELF
    "already" -> ReferralOutcome.ALREADY
    "too_old" -> ReferralOutcome.TOO_OLD
    "not_eligible" -> ReferralOutcome.NOT_ELIGIBLE
    "disabled" -> ReferralOutcome.DISABLED
    "too_many" -> ReferralOutcome.TOO_MANY
    else -> ReferralOutcome.OFFLINE
}

/** Mensagem do resgate que não deu certo (o OK abre o popup, não usa esta). */
fun referralFailureText(outcome: ReferralOutcome): K = when (outcome) {
    ReferralOutcome.INVALID -> K.REFERRAL_ERR_INVALID
    ReferralOutcome.SELF -> K.REFERRAL_ERR_SELF
    ReferralOutcome.ALREADY -> K.REFERRAL_ERR_ALREADY
    ReferralOutcome.TOO_OLD -> K.REFERRAL_ERR_TOO_OLD
    ReferralOutcome.NOT_ELIGIBLE -> K.REFERRAL_ERR_NOT_ELIGIBLE
    ReferralOutcome.DISABLED -> K.REFERRAL_ERR_DISABLED
    ReferralOutcome.TOO_MANY -> K.REFERRAL_ERR_TOO_MANY
    else -> K.REFERRAL_ERR_OFFLINE
}

/** Soma dos prêmios que quem convidou ainda não viu (alimenta o popup "Seu convite rendeu"). */
fun referralUnseenTotal(invites: List<ReferralInvite>): Int =
    invites.filter { it.unseen && it.state == "paid" }.sumOf { it.amount }

/** Quantos convites já renderam, para o contador "x de 10" do cartão. */
fun referralPaidCount(invites: List<ReferralInvite>): Int = invites.count { it.state == "paid" }
