package br.com.navalbattle.ui

import kotlin.math.PI
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** #22: o borrifo pré-calculado é o mesmo que o desenho sorteava a cada quadro. */
class SeaWaveSprayTest {

    /** Sequência do código antigo: Random(foamSeed + i * 31), cinco sorteios por gota nesta ordem. */
    private fun legacy(seed: Int): List<SprayParticle> {
        val spray = Random(seed)
        return List(18) {
            val fx = spray.nextFloat()
            val angle = (-90f + (spray.nextFloat() - 0.5f) * 140f) * (PI.toFloat() / 180f)
            val reachFrac = 0.3f + spray.nextFloat() * 0.7f
            val r = 1f + spray.nextFloat() * 1.8f
            val a0 = 0.45f + spray.nextFloat() * 0.4f
            SprayParticle(fx, angle, reachFrac, r, a0)
        }
    }

    @Test
    fun mesmasGotasDoSorteioAntigo() {
        for (seed in listOf(0, 1, -7, 123456789, Int.MAX_VALUE, Int.MIN_VALUE)) {
            assertEquals(legacy(seed), sprayParticles(seed))
        }
    }

    @Test
    fun cadaNavioUsaASementeDaOndaMais31PorIndice() {
        val wave = SeaWave(startMs = 0L, durationMs = 15000L, tilt = 0.05f, seed = 42)
        for (i in 0..4) {
            assertEquals(legacy(wave.foamSeed + i * 31), wave.spray(i))
        }
    }

    @Test
    fun borrifoNaoMudaEntreQuadros() {
        val wave = SeaWave(startMs = 0L, durationMs = 15000L, tilt = 0f, seed = 7)
        assertSame(wave.spray(2), wave.spray(2))
        assertEquals(18, wave.spray(2).size)
    }
}
