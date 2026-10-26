package br.com.navalbattle.game

/**
 * Conquistas do Google Play Games (#48). Só o Android envia (ver `data/GameServices`); aqui fica a
 * regra pura de quando cada uma é desbloqueada, para testar sem Play Games. Conquista não vale
 * moeda nem pontos de jogo: é espelho da carreira, que continua no servidor.
 */
enum class Achievement {
    PrimeiraVitoria,
    PrimeiraOnline,
    FrotaIntacta,
    Diario7,
    Cacador100,
    Ranqueada10,
    CapitaoCorveta
}

/** O que o app manda ao Play Games: desbloquear, somar passos ou fixar o total de passos. */
sealed interface AchievementUpdate {
    val achievement: Achievement

    data class Unlock(override val achievement: Achievement) : AchievementUpdate

    /** Soma [steps] ao progresso (conquista incremental). */
    data class Increment(override val achievement: Achievement, val steps: Int) : AchievementUpdate

    /** Fixa o progresso em [steps] (o Play Games guarda o maior; seguro repetir). */
    data class SetSteps(override val achievement: Achievement, val steps: Int) : AchievementUpdate
}

/** O que aconteceu no jogo e pode render conquista. */
sealed interface AchievementEvent {
    /**
     * Fim de uma partida que conta para a carreira (contra a IA ou online). [shipsLost] são os navios
     * da própria frota afundados; [shipsSunk] os do adversário. A vitória ranqueada tem evento
     * próprio ([RankedWin]), porque quem a confirma é o servidor.
     */
    data class MatchEnd(
        val victory: Boolean,
        val online: Boolean,
        val shipsLost: Int,
        val shipsSunk: Int
    ) : AchievementEvent

    /** Vitória ranqueada confirmada pelo servidor. */
    data object RankedWin : AchievementEvent

    /** Check-in do diário; [streak] é a sequência devolvida pelo servidor. */
    data class CheckIn(val streak: Int) : AchievementEvent

    /** Subida de patente. */
    data class RankUp(val rank: Rank) : AchievementEvent
}

/** O que a carreira já tem, para quem já jogava antes das conquistas existirem. */
data class AchievementCareer(
    val wins: Int,
    val shipsSunk: Int,
    val rank: Rank,
    val checkInStreak: Int
)

private const val DIARY_GOAL = 7
private const val HUNTER_GOAL = 100
private val RANK_GOAL = Rank.CAPITAO_CORVETA

/** Conquistas que o [event] rende. Derrota não rende vitória, mas navio afundado conta. */
fun achievementsFor(event: AchievementEvent): List<AchievementUpdate> = when (event) {
    is AchievementEvent.MatchEnd -> buildList {
        if (event.victory) {
            add(AchievementUpdate.Unlock(Achievement.PrimeiraVitoria))
            if (event.online) add(AchievementUpdate.Unlock(Achievement.PrimeiraOnline))
            if (event.shipsLost == 0) add(AchievementUpdate.Unlock(Achievement.FrotaIntacta))
        }
        if (event.shipsSunk > 0) add(AchievementUpdate.Increment(Achievement.Cacador100, event.shipsSunk))
    }

    AchievementEvent.RankedWin -> listOf(AchievementUpdate.Increment(Achievement.Ranqueada10, 1))

    is AchievementEvent.CheckIn ->
        if (event.streak >= DIARY_GOAL) listOf(AchievementUpdate.Unlock(Achievement.Diario7)) else emptyList()

    is AchievementEvent.RankUp ->
        if (event.rank >= RANK_GOAL) listOf(AchievementUpdate.Unlock(Achievement.CapitaoCorveta)) else emptyList()
}

/**
 * Recuperação ao entrar no Play Games: manda o que a carreira já tem. Idempotente (só desbloqueios
 * e [AchievementUpdate.SetSteps]), então repetir não soma duas vezes. Vitória online, frota intacta
 * e vitórias ranqueadas não ficam guardadas no aparelho: essas começam a contar daqui.
 */
fun backfill(career: AchievementCareer): List<AchievementUpdate> = buildList {
    if (career.wins > 0) add(AchievementUpdate.Unlock(Achievement.PrimeiraVitoria))
    if (career.checkInStreak >= DIARY_GOAL) add(AchievementUpdate.Unlock(Achievement.Diario7))
    if (career.rank >= RANK_GOAL) add(AchievementUpdate.Unlock(Achievement.CapitaoCorveta))
    if (career.shipsSunk > 0) {
        add(AchievementUpdate.SetSteps(Achievement.Cacador100, minOf(career.shipsSunk, HUNTER_GOAL)))
    }
}

/** Pontos aceitos pelo placar do Play Games (limite 0 a 100 000 configurado no console). */
fun leaderboardScore(seasonPoints: Int): Long = seasonPoints.toLong().coerceIn(0L, 100_000L)
