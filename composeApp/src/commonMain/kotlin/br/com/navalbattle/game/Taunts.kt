package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/** Provocações da partida (rede local e online): só estas existem; qualquer outra coisa recebida é ignorada (#58). */
internal val TAUNT_EMOJIS = listOf("🔥", "💥", "😈", "😅", "🎯", "🏳️")

internal val TAUNT_PHRASES = listOf(
    "FIRE" to K.TAUNT_FIRE,
    "NICE" to K.TAUNT_NICE_SHOT,
    "GG" to K.TAUNT_GG,
    "MERCY" to K.TAUNT_MERCY,
    "LUCKY" to K.TAUNT_LUCKY,
    "INCOMING" to K.TAUNT_INCOMING
)

/** Texto de um grito de guerra conhecido, ou nulo se o código não existe. */
internal fun tauntPhraseOf(code: String): K? = TAUNT_PHRASES.firstOrNull { it.first == code }?.second

/** Emoji da lista ou grito de guerra conhecido; um cliente alterado não consegue mandar texto livre por aqui. */
internal fun isKnownTaunt(code: String): Boolean = code in TAUNT_EMOJIS || tauntPhraseOf(code) != null
