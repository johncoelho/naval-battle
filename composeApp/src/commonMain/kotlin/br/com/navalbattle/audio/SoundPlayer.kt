package br.com.navalbattle.audio

enum class Sfx {
    LAUNCH,
    MISS,
    HIT,
    SUNK,

    /** Alarme de colisão: soa só quando a SUA frota é atingida. */
    ALARM,

    /** Klaxon de emergência: soa quando você perde um navio. */
    ALARM_CRITICAL
}

/** Toca efeitos sonoros curtos de combate. Implementação nativa por plataforma. */
expect class SoundPlayer() {
    fun play(sfx: Sfx)
    fun release()
}
