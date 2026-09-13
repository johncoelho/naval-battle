package br.com.navalbattle.audio

/** Faixas da trilha. Cada uma é um laço fechado em número inteiro de compassos. */
enum class Music {
    /** Tema de abertura e do deque de comando: rock naval, banda completa. */
    THEME,

    /** Combate: mais contido, para não brigar com os efeitos de tiro. */
    BATTLE
}

/**
 * Trilha de fundo em laço. Trocar de faixa faz a transição sozinho; [stop] silencia
 * sem soltar os recursos, para o comandante poder ligar de novo no menu.
 */
expect class MusicPlayer() {
    fun play(track: Music)
    fun stop()
    fun release()
}
