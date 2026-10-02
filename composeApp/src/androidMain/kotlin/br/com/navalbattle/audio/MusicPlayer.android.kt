package br.com.navalbattle.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import br.com.navalbattle.R

actual class MusicPlayer actual constructor() {

    private var player: MediaPlayer? = null
    private var current: Music? = null
    private var sea: MediaPlayer? = null

    private fun resOf(track: Music) = when (track) {
        Music.THEME_1 -> R.raw.music_theme
        Music.THEME_2 -> R.raw.music_theme_2
        Music.THEME_3 -> R.raw.music_theme_3
        Music.BATTLE -> R.raw.music_battle
        Music.BATTLE_INTENSE -> R.raw.music_battle_intense
    }

    private fun volumeOf(track: Music) = when (track) {
        Music.THEME_1, Music.THEME_2, Music.THEME_3 -> 0.55f
        // no combate a trilha recua para os tiros e alarmes ficarem à frente
        Music.BATTLE, Music.BATTLE_INTENSE -> 0.17f
    }

    private fun create(res: Int): MediaPlayer? {
        // create() já entrega o player preparado; atributos de áudio vão no próprio create
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        return MediaPlayer.create(
            AudioContextHolder.appContext,
            res,
            attrs,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    actual fun play(track: Music) {
        if (current == track && player?.isPlaying == true) return
        // entre as duas faixas de combate, a nova segue do mesmo ponto do compasso
        val resumeAt = if (current?.isBattle() == true && track.isBattle()) {
            runCatching { player?.currentPosition }.getOrNull() ?: 0
        } else 0
        releaseMusic()
        current = track
        player = create(resOf(track))?.apply {
            isLooping = true
            setVolume(volumeOf(track), volumeOf(track))
            if (resumeAt > 0) seekTo(resumeAt)
            start()
        }
    }

    actual fun ambient(on: Boolean) {
        if (!on) {
            releaseSea()
            return
        }
        if (sea?.isPlaying == true) return
        releaseSea()
        sea = create(R.raw.ambient_sea)?.apply {
            isLooping = true
            setVolume(SEA_VOLUME, SEA_VOLUME)
            start()
        }
    }

    actual fun stop() {
        release()
    }

    actual fun release() {
        releaseMusic()
        releaseSea()
    }

    private fun releaseMusic() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            p.release()
        }
        player = null
        current = null
    }

    private fun releaseSea() {
        sea?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            p.release()
        }
        sea = null
    }

    private companion object {
        const val SEA_VOLUME = 0.28f
    }
}
