package br.com.navalbattle.game

import br.com.navalbattle.data.PrefsBatch

/**
 * Chaves do [br.com.navalbattle.data.Prefs] do aparelho (SharedPreferences / NSUserDefaults), num lugar só para o
 * teste enxergar (#64). O que **fica** quando a conta sai é uma lista de permissões ([KEEP_INTS], [KEEP_STRINGS]):
 * chave nova de carreira é apagada por padrão, e só o que é do aparelho (idioma, som, avisos já vistos) é listado.
 */
internal object ProfileKeys {
    const val K_NAME = "name"
    const val K_INSIGNIA = "insignia"
    const val K_XP = "xp"
    const val K_CREDITS = "credits"
    const val K_WALLET = "wallet_for"
    const val K_MATCHES = "matches"
    const val K_WINS = "wins"
    const val K_SHOTS = "shots"
    const val K_HITS = "hits"
    const val K_SUNK = "sunk"
    const val K_STREAK = "streak"
    const val K_BEST_STREAK = "best_streak"
    const val K_OWNED = "owned"
    const val K_EQUIPPED = "equipped"
    const val K_FLEETS = "fleets"
    const val K_FLEET = "fleet"
    const val K_ABILITY_CHARGES = "ability_charges"
    const val K_UID = "uid"
    const val K_EMAIL = "email"
    const val K_TOKEN = "token"
    const val K_REFRESH = "refresh"
    const val K_MUSIC = "music"
    const val K_SFX = "sfx"
    const val K_CURRENCY_TIP = "currency_tip_seen"
    const val K_REMINDER = "daily_reminder"
    const val K_LAST_OPEN_DAY = "last_open_day"
    const val K_RETURN_NOTICE_DAY = "last_return_notice_day"
    const val K_RETURN_BONUS_DAY = "last_return_bonus_day"
    const val K_RETURN_PENDING_DAY = "return_pending_day"
    const val K_LAST_MODE = "last_mode"
    const val K_DAILY_DAY = "daily_day"
    const val K_DAILY_PLAYED = "daily_played"
    const val K_DAILY_WON = "daily_won"
    const val K_DAILY_SUNK = "daily_sunk"
    const val K_DAILY_ABILITIES = "daily_abilities"
    const val K_DAILY_BEST_ACCURACY = "daily_best_accuracy"
    const val K_LANG = "lang"
    const val K_AVATAR = "avatar"
    const val K_WELCOME_DONE = "welcome_done"
    const val K_GUIDED_DONE = "guided_done"
    const val K_WELCOME_PENDING = "welcome_pending"
    const val K_THEME_TRACK = "theme_track"
    const val K_SEASON_ACCEPTED = "season_accepted"
    const val K_FEEDBACK_OPT_OUT = "feedback_opt_out"
    const val K_FEEDBACK_NEXT_AT = "feedback_next_at"
    const val K_QUICK_OFFER = "quick_offer"
    const val K_ACCEPT_INVITES = "accept_invites"
    const val K_FRIEND_ONLINE_PUSH = "friend_online_push"
    const val K_QUICK_OFFER_KIND = "quick_offer_kind"
    const val K_ONLINE_REWARDS = "online_rewards"
    const val K_DEVICE_CHECK = "device_check_sent"

    // Chaves gravadas fora do Profile (App.kt) mas que são do aparelho e não podem morrer ao sair da conta.
    /** Tokens de compra da Play já somados (#15): são da loja do aparelho, não da carreira. */
    const val DOBROES_DELIVERED = "dobroes_delivered"

    /** Faixa etária da Play e hora da leitura (#49): nunca sai do aparelho, só escolhe o tipo de anúncio. */
    const val AGE_BAND = "age_band"
    const val AGE_CHECKED_AT = "age_checked_at"

    /** Pedido de avaliação na Play (#45): a cota é do aparelho. */
    const val REVIEW_LAST_ASK = "review_last_ask"
    const val REVIEW_ASK_COUNT = "review_ask_count"

    /** Aviso de lançamento já visto pelo convidado ([launchNoticeKey] com id nulo): não volta ao sair. */
    const val LAUNCH_NOTICE_GUEST = "launch_notice_seen_guest"

    /** O que fica como inteiro ao voltar a convidado. */
    val KEEP_INTS: List<String> = listOf(
        K_MUSIC, K_SFX, K_REMINDER, K_THEME_TRACK, K_CURRENCY_TIP, K_GUIDED_DONE, K_WELCOME_DONE,
        K_LAST_OPEN_DAY, K_RETURN_NOTICE_DAY, K_RETURN_BONUS_DAY, K_RETURN_PENDING_DAY,
        K_FEEDBACK_OPT_OUT, K_FEEDBACK_NEXT_AT, K_QUICK_OFFER, K_ACCEPT_INVITES, K_FRIEND_ONLINE_PUSH,
        REVIEW_ASK_COUNT, LAUNCH_NOTICE_GUEST
    )

    /** O que fica como texto ao voltar a convidado. */
    val KEEP_STRINGS: List<String> = listOf(
        K_LANG, K_LAST_MODE, K_QUICK_OFFER_KIND, K_DEVICE_CHECK,
        DOBROES_DELIVERED, AGE_BAND, AGE_CHECKED_AT, REVIEW_LAST_ASK
    )

    /** Tudo o que fica quando a conta sai (a permissão; o resto é carreira e vai embora). */
    val KEEP_ON_SIGN_OUT: Set<String> = (KEEP_INTS + KEEP_STRINGS).toSet()

    private const val ABSENT_INT = Int.MIN_VALUE

    /**
     * Lê do aparelho o que fica ([KEEP_ON_SIGN_OUT]) num lote, para regravar depois do `clear()`. Chave ausente
     * não entra (nem vira 0 nem texto vazio): continua ausente e assume o padrão de sempre.
     */
    fun keptOnSignOut(getString: (String, String) -> String, getInt: (String, Int) -> Int): PrefsBatch {
        val batch = PrefsBatch()
        KEEP_STRINGS.forEach { key ->
            val value = getString(key, "")
            if (value.isNotEmpty()) batch.putString(key, value)
        }
        KEEP_INTS.forEach { key ->
            val value = getInt(key, ABSENT_INT)
            if (value != ABSENT_INT) batch.putInt(key, value)
        }
        return batch
    }
}
