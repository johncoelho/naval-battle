package br.com.navalbattle.game

/**
 * Replay da mira do adversario virtual (#139): usa so os tiros que a IA ja deu e ficaram guardados na partida
 * ([Match.aiShotLog]); nao muda a IA, a dificuldade nem os pontos. Regras puras, testadas em commonTest.
 */
object BotReplay {
    /** Selo "Adversario virtual": so contra o bot (nunca humano, treino da IA sem persona nem rede). */
    fun showBadge(match: Match): Boolean = match.opponent == Opponent.AI && match.bot != null

    /** Replay so em partida terminada de verdade contra o bot, com ao menos um tiro e sem abandono. */
    fun offered(match: Match): Boolean =
        showBadge(match) && match.phase == Phase.RESULT && !match.forfeitedBySelf && !match.wonByAbsence &&
            !match.endedByWalkover && match.aiShotLog.isNotEmpty()

    /** Frota do jogador com so os [step] primeiros tiros da IA aplicados (0 = frota intocada). */
    fun boardAt(fleet: List<Ship>, shots: List<ShotOutcome>, step: Int): Board {
        val b = Board()
        fleet.forEach { b.place(it) }
        shots.take(step.coerceIn(0, shots.size)).forEach { b.fireAt(it.coord) }
        return b
    }
}
