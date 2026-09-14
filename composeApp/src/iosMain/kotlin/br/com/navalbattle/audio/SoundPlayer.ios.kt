package br.com.navalbattle.audio

/**
 * Ainda não toca nada de verdade no iOS: falta empacotar os efeitos no bundle do
 * `iosApp` e trocar por `AVAudioPlayer` — ver docs/BUILD.md, seção iOS. Fica mudo em
 * vez de travar o app, para o resto do jogo funcionar enquanto isso não é feito.
 */
actual class SoundPlayer actual constructor() {
    actual fun play(sfx: Sfx) {}
    actual fun release() {}
}
