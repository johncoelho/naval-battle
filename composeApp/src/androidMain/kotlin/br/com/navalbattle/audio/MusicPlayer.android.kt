package br.com.navalbattle.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import br.com.navalbattle.R

actual class MusicPlayer actual constructor() {

    private var player: MediaPlayer? = null
    private var current: Music? = null

    private fun resOf(track: Music) = when (track) {
        Music.THEME_1 -> R.raw.music_theme
        Music.THEME_2 -> R.raw.music_theme_2
        Music.THEME_3 -> R.raw.music_theme_3
        Music.BATTLE -> R.raw.music_battle
    }

    private fun volumeOf(track: Music) = when (track) {
        Music.THEME_1, Music.THEME_2, Music.THEME_3 -> 0.55f
        // no combate a trilha recua para os tiros e alarmes ficarem à frente
        Music.BATTLE -> 0.34f
    }

    actual fun play(track: Music) {
        if (current == track && player?.isPlaying == true) return
        release()
        current = track
        // create() já entrega o player preparado; atributos de áudio vão no próprio create
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        player = MediaPlayer.create(
            AudioContextHolder.appContext,
            resOf(track),
            attrs,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )?.apply {
            isLooping = true
            setVolume(volumeOf(track), volumeOf(track))
            start()
        }
    }

    actual fun stop() {
        release()
    }

    actual fun release() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            p.release()
        }
        player = null
        current = null
    }
}
