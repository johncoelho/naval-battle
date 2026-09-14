package br.com.navalbattle.audio

/** Mesma pendência do [SoundPlayer]: trilha ainda não empacotada para o iOS. */
actual class MusicPlayer actual constructor() {
    actual fun play(track: Music) {}
    actual fun stop() {}
    actual fun release() {}
}
