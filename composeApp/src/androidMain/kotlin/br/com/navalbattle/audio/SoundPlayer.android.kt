package br.com.navalbattle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import br.com.navalbattle.R

/** Contexto de aplicação registrado por [br.com.navalbattle.MainActivity] na inicialização. */
object AudioContextHolder {
    lateinit var appContext: Context
}

actual class SoundPlayer actual constructor() {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Sfx, Int> = mapOf(
        Sfx.LAUNCH to pool.load(AudioContextHolder.appContext, R.raw.sfx_launch, 1),
        Sfx.MISS to pool.load(AudioContextHolder.appContext, R.raw.sfx_miss, 1),
        Sfx.HIT to pool.load(AudioContextHolder.appContext, R.raw.sfx_hit, 1),
        Sfx.SUNK to pool.load(AudioContextHolder.appContext, R.raw.sfx_sunk, 1)
    )

    actual fun play(sfx: Sfx) {
        val id = ids[sfx] ?: return
        val volume = when (sfx) {
            Sfx.SUNK -> 1f
            Sfx.LAUNCH -> 0.6f
            else -> 0.9f
        }
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    actual fun release() {
        pool.release()
    }
}
