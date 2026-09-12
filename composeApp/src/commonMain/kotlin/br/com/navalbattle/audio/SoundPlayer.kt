package br.com.navalbattle.audio

enum class Sfx { MISS, HIT, SUNK }

/** Toca efeitos sonoros curtos de combate. Implementação nativa por plataforma. */
expect class SoundPlayer() {
    fun play(sfx: Sfx)
    fun release()
}
