package br.com.navalbattle.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import br.com.navalbattle.R
import kotlin.random.Random

/** Contexto de aplicação registrado por [br.com.navalbattle.MainActivity] na inicialização. */
object AudioContextHolder {
    lateinit var appContext: Context
}

actual class SoundPlayer actual constructor() {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private fun load(resId: Int) = pool.load(AudioContextHolder.appContext, resId, 1)

    /**
     * O tiro na água é o som mais frequente da partida: três gravações diferentes,
     * sorteadas a cada disparo, evitam a sensação de repetição.
     */
    private val variants: Map<Sfx, List<Int>> = mapOf(
        Sfx.LAUNCH to listOf(load(R.raw.sfx_launch)),
        Sfx.MISS to listOf(load(R.raw.sfx_miss1), load(R.raw.sfx_miss2), load(R.raw.sfx_miss3)),
        Sfx.HIT to listOf(load(R.raw.sfx_hit)),
        Sfx.SUNK to listOf(load(R.raw.sfx_sunk)),
        Sfx.ALARM to listOf(load(R.raw.sfx_alarm)),
        Sfx.ALARM_CRITICAL to listOf(load(R.raw.sfx_alarm_critical))
    )

    actual fun play(sfx: Sfx) {
        val ids = variants[sfx] ?: return
        val id = ids[Random.nextInt(ids.size)]
        val volume = when (sfx) {
            Sfx.SUNK -> 1f
            Sfx.ALARM_CRITICAL -> 1f
            Sfx.HIT -> 0.95f
            Sfx.ALARM -> 0.9f
            Sfx.MISS -> 0.7f
            Sfx.LAUNCH -> 0.55f
        }
        // alarmes precisam soar sempre iguais para serem reconhecíveis; o resto varia de afinação
        val rate = if (sfx == Sfx.ALARM || sfx == Sfx.ALARM_CRITICAL) {
            1f
        } else {
            0.94f + Random.nextFloat() * 0.12f
        }
        pool.play(id, volume, volume, 1, 0, rate)
    }

    actual fun release() {
        pool.release()
    }
}
