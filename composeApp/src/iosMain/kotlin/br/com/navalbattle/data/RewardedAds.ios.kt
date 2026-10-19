package br.com.navalbattle.data

import br.com.navalbattle.game.AdShowOutcome

/**
 * iPhone: sem anúncio por enquanto (#29 fica no Android; o iPhone precisa de ATT, SKAdNetwork e
 * do SDK no Xcode, issue própria). O servidor também não libera anúncio para "ios".
 */
actual class RewardedAds actual constructor() {
    actual val supported: Boolean get() = false

    actual val buildUsesTestAds: Boolean get() = true

    actual suspend fun show(adUnitId: String, userId: String, customData: String): AdShowOutcome =
        AdShowOutcome.UNAVAILABLE

    actual suspend fun openPrivacyOptions(): Boolean = false
}
