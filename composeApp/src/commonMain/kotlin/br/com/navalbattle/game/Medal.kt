package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/**
 * Condecorações da partida. Nenhuma delas guarda estado novo: todas saem do que o
 * combate já registra (precisão, navios afundados, turnos, sequência de vitórias),
 * e é justamente por isso que podem ser mostradas na tela de resultado sem mexer
 * na carreira gravada.
 *
 * As não conquistadas aparecem apagadas com o requisito escrito — é o que dá o que
 * perseguir na próxima partida, em vez de a tela só informar o que já aconteceu.
 */
enum class Medal(val key: K, val requirementKey: K) {
    FLEET_HUNTER(K.MEDAL_HUNTER, K.MEDAL_HUNTER_REQ),
    FIRST_SORTIE(K.MEDAL_FIRST, K.MEDAL_FIRST_REQ),
    SHARPSHOOTER(K.MEDAL_SHARPSHOOTER, K.MEDAL_SHARPSHOOTER_REQ),
    BLITZ(K.MEDAL_BLITZ, K.MEDAL_BLITZ_REQ),
    UNTOUCHED(K.MEDAL_UNTOUCHED, K.MEDAL_UNTOUCHED_REQ),
    THREE_IN_A_ROW(K.MEDAL_STREAK, K.MEDAL_STREAK_REQ);

    companion object {
        /** Precisão mínima, em porcentagem, para o atirador de elite. */
        const val SHARP_ACCURACY = 40

        /**
         * Quais medalhas a partida rendeu. [matchesBefore] e [streakAfter] vêm da
         * carreira: a estreia é a primeira partida de todas, e a sequência só conta
         * depois de o resultado ter sido creditado.
         */
        fun earnedIn(
            award: Award,
            ownShipsLeft: Int,
            fleetSize: Int,
            matchesBefore: Int,
            streakAfter: Int
        ): Set<Medal> = buildSet {
            if (award.shipsSunk >= fleetSize) add(FLEET_HUNTER)
            if (matchesBefore == 0) add(FIRST_SORTIE)
            if (award.precision >= SHARP_ACCURACY) add(SHARPSHOOTER)
            if (award.blitzBonus > 0) add(BLITZ)
            if (award.victory && ownShipsLeft >= fleetSize) add(UNTOUCHED)
            if (streakAfter >= 3) add(THREE_IN_A_ROW)
        }
    }
}
