package br.com.navalbattle.audio

/**
 * Faixas da trilha. As três primeiras formam a playlist do deque de comando — a cada
 * abertura do app toca uma delas, em rodízio (ver [br.com.navalbattle.game.Profile.rollThemeTrack]);
 * a lista cresce aos poucos, conforme mais músicas forem entrando.
 */
enum class Music {
    /** Faixa original, em laço curto cortado em número inteiro de compassos. */
    THEME_1,

    /** Composições completas, geradas com IA — tocam a faixa inteira antes de repetir. */
    THEME_2,
    THEME_3,

    /** Combate: mais contido, para não brigar com os efeitos de tiro. */
    BATTLE
}

/** Faixas do deque de comando, na ordem do rodízio. */
val THEME_PLAYLIST = listOf(Music.THEME_1, Music.THEME_2, Music.THEME_3)

/**
 * Trilha de fundo em laço. Trocar de faixa faz a transição sozinho; [stop] silencia
 * sem soltar os recursos, para o comandante poder ligar de novo no menu.
 */
expect class MusicPlayer() {
    fun play(track: Music)
    fun stop()
    fun release()
}
