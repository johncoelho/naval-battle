package br.com.navalbattle.data

import br.com.navalbattle.game.Achievement

/** iPhone: sem conquistas por enquanto (Game Center fica para depois da App Store). */
actual object GameServices {
    actual val supported: Boolean get() = false

    actual fun start(onSignedIn: () -> Unit) {}

    actual fun unlock(a: Achievement) {}

    actual fun increment(a: Achievement, steps: Int) {}

    actual fun setSteps(a: Achievement, steps: Int) {}

    actual fun submitRanked(points: Long) {}

    actual fun showAchievements() {}
}
