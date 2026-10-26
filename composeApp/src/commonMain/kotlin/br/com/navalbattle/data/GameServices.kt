package br.com.navalbattle.data

import br.com.navalbattle.game.Achievement
import br.com.navalbattle.game.AchievementUpdate

/**
 * Conquistas e placar do Google Play Games (#48; Play Games Services v2 aprovado pelo John em 25/10).
 * Espelho: a pontuação real e o pódio continuam no Supabase e conquista não vale moeda.
 *
 * Só o Android faz algo. Inerte enquanto o app_id (`pgsAppId` no gradle.properties) ou os IDs
 * (`res/values/games-ids.xml`) estiverem vazios: [supported] fica falso, o SDK nem inicia, e toda
 * chamada vira nada. Nunca lança; o login é automático e, se o jogador recusar, o jogo segue.
 */
expect object GameServices {
    /** Verdadeiro só no Android com app_id e IDs preenchidos. */
    val supported: Boolean

    /** Inicia o SDK (uma vez). [onSignedIn] roda quando o jogador está autenticado, para a recuperação. */
    fun start(onSignedIn: () -> Unit)

    fun unlock(a: Achievement)

    fun increment(a: Achievement, steps: Int)

    fun setSteps(a: Achievement, steps: Int)

    /** Envia os pontos da temporada ranqueada; o Play Games guarda o melhor. */
    fun submitRanked(points: Long)

    /** Abre a lista de conquistas do Play Games. */
    fun showAchievements()
}

/** Aplica o resultado da regra pura de [br.com.navalbattle.game.achievementsFor]. */
fun GameServices.send(updates: List<AchievementUpdate>) {
    if (!supported) return
    updates.forEach {
        when (it) {
            is AchievementUpdate.Unlock -> unlock(it.achievement)
            is AchievementUpdate.Increment -> increment(it.achievement, it.steps)
            is AchievementUpdate.SetSteps -> setSteps(it.achievement, it.steps)
        }
    }
}
