package br.com.navalbattle.data

import android.app.Activity
import android.content.Context
import br.com.navalbattle.ActivityHolder
import br.com.navalbattle.R
import br.com.navalbattle.audio.AudioContextHolder
import br.com.navalbattle.game.Achievement
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk

actual object GameServices {
    private var started = false

    private fun ctx(): Context? = runCatching { AudioContextHolder.appContext }.getOrNull()

    private fun text(context: Context, id: Int): String = runCatching { context.getString(id) }.getOrDefault("")

    /** Inerte enquanto o app_id ou o ID da primeira conquista estiverem vazios. */
    actual val supported: Boolean
        get() {
            val c = ctx() ?: return false
            return text(c, R.string.game_services_project_id).isNotBlank() &&
                text(c, R.string.achievement_primeira_vitoria).isNotBlank()
        }

    private fun id(a: Achievement, c: Context): String = text(
        c,
        when (a) {
            Achievement.PrimeiraVitoria -> R.string.achievement_primeira_vitoria
            Achievement.PrimeiraOnline -> R.string.achievement_primeira_online
            Achievement.FrotaIntacta -> R.string.achievement_frota_intacta
            Achievement.Diario7 -> R.string.achievement_diario_7
            Achievement.Cacador100 -> R.string.achievement_cacador_100
            Achievement.Ranqueada10 -> R.string.achievement_ranqueada_10
            Achievement.CapitaoCorveta -> R.string.achievement_capitao_corveta
        }
    )

    private fun activity(): Activity? = ActivityHolder.current

    actual fun start(onSignedIn: () -> Unit) {
        if (started || !supported) return
        val act = activity() ?: return
        started = true
        try {
            PlayGamesSdk.initialize(act.applicationContext)
            PlayGames.getGamesSignInClient(act).isAuthenticated.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result.isAuthenticated) onSignedIn()
            }
        } catch (_: Throwable) {
            // sem Play Games o jogo segue normalmente
        }
    }

    private inline fun withAchievement(a: Achievement, block: (Activity, String) -> Unit) {
        if (!started) return
        val act = activity() ?: return
        val id = id(a, act).ifBlank { return }
        try {
            block(act, id)
        } catch (_: Throwable) {
        }
    }

    actual fun unlock(a: Achievement) =
        withAchievement(a) { act, id -> PlayGames.getAchievementsClient(act).unlock(id) }

    actual fun increment(a: Achievement, steps: Int) {
        if (steps <= 0) return
        withAchievement(a) { act, id -> PlayGames.getAchievementsClient(act).increment(id, steps) }
    }

    actual fun setSteps(a: Achievement, steps: Int) {
        if (steps <= 0) return
        withAchievement(a) { act, id -> PlayGames.getAchievementsClient(act).setSteps(id, steps) }
    }

    actual fun submitRanked(points: Long) {
        if (!started) return
        val act = activity() ?: return
        val id = text(act, R.string.leaderboard_ranqueada).ifBlank { return }
        try {
            PlayGames.getLeaderboardsClient(act).submitScore(id, points)
        } catch (_: Throwable) {
        }
    }

    actual fun showAchievements() {
        if (!started) return
        val act = activity() ?: return
        try {
            PlayGames.getAchievementsClient(act).achievementsIntent.addOnSuccessListener { act.startActivityForResult(it, 9003) }
        } catch (_: Throwable) {
        }
    }
}
