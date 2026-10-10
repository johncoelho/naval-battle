package br.com.navalbattle.design

import br.com.navalbattle.game.ShipClass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #21: a superestrutura da linha de casco substitui a da classe (não empilha), o navio
 * tem no máximo um mastro e a verga fica dentro do casco. Testa o plano que o desenho
 * segue, nas 20 combinações (5 classes x 4 linhas).
 */
class ShipLayoutTest {

    private val combos = ShipClass.entries.flatMap { type -> FleetLine.all.map { type to it } }

    private fun near(expected: Float, actual: Float) = abs(expected - actual) < 0.001f

    @Test
    fun noMaximoUmMastroEVergaDentroDoCasco() {
        assertEquals(20, combos.size)
        combos.forEach { (type, line) ->
            val plan = superstructurePlan(type, line)
            assertTrue(plan.mastCount <= 1, "$type/${line.id}: mais de um mastro")
            assertTrue(
                plan.yardHalf <= hullHalf(type) * 0.8f,
                "$type/${line.id}: verga ${plan.yardHalf} passa de 0,8 da meia-boca ${hullHalf(type)}"
            )
        }
    }

    @Test
    fun torreFacetadaESubmarinoSemMastro() {
        combos.filter { (type, line) -> type == ShipClass.SUBMARINE || (line.tower == Tower.FACETADA && type != ShipClass.CARRIER) }
            .forEach { (type, line) -> assertNull(superstructurePlan(type, line).mastX, "$type/${line.id}") }
    }

    @Test
    fun portaAvioesESubmarinoNaoGanhamTorreDaLinha() {
        FleetLine.all.forEach { line ->
            listOf(ShipClass.CARRIER, ShipClass.SUBMARINE).forEach { type ->
                val plan = superstructurePlan(type, line)
                assertEquals(Tower.PADRAO, plan.bridge, "$type/${line.id}")
                assertEquals(superstructurePlan(type, FleetLine.STANDARD), plan, "$type/${line.id}: linha mudou a superestrutura")
            }
        }
    }

    @Test
    fun torreDaLinhaSubstituiAIlhaDaClasseNoMesmoLugar() {
        val gunships = listOf(ShipClass.BATTLESHIP, ShipClass.CRUISER, ShipClass.DESTROYER)
        gunships.forEach { type ->
            val std = superstructurePlan(type, FleetLine.STANDARD)
            FleetLine.all.forEach { line ->
                val plan = superstructurePlan(type, line)
                assertEquals(line.tower, plan.bridge, "$type/${line.id}: ilha errada")
                assertTrue(near(std.bridgeCx, plan.bridgeCx), "$type/${line.id}: torre fora do centro da ilha")
                assertTrue(near(std.bridgeLength, plan.bridgeLength), "$type/${line.id}: torre maior que a ilha")
                assertTrue(near(std.bridgeHalfBeam, plan.bridgeHalfBeam), "$type/${line.id}: torre mais larga que a ilha")
                plan.mastX?.let { x ->
                    assertTrue(abs(x - plan.bridgeCx) <= plan.bridgeLength / 2f + 12.5f, "$type/${line.id}: mastro longe da ilha")
                }
            }
        }
    }

    @Test
    fun chamineDaLinhaSubstituiADaClasseSemEncavalar() {
        combos.forEach { (type, line) ->
            val plan = superstructurePlan(type, line)
            if (line.funnels > 0 && type != ShipClass.CARRIER && type != ShipClass.SUBMARINE) {
                assertEquals(line.funnels, plan.funnels.size, "$type/${line.id}: chaminés empilhadas")
            }
            val f = plan.funnels.sortedBy { it.x }
            f.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    b.x - a.x >= 2f * maxOf(a.rx, b.rx),
                    "$type/${line.id}: chaminés em ${a.x} e ${b.x} encavaladas"
                )
            }
        }
    }

    @Test
    fun linhaSemChamineFicaComAsDaClasse() {
        ShipClass.entries.forEach { type ->
            assertEquals(
                superstructurePlan(type, FleetLine.STANDARD).funnels,
                superstructurePlan(type, FleetLine.GHOST).funnels,
                "$type: Fantasma deveria manter as chaminés da classe"
            )
        }
    }

    /** Regressão: a linha Padrão continua com a ilha, o mastro e as chaminés de antes. */
    @Test
    fun padraoIgualAoDeAntes() {
        fun check(type: ShipClass, cx: Float, mast: Float, funnelXs: List<Float>) {
            val plan = superstructurePlan(type, FleetLine.STANDARD)
            assertEquals(Tower.PADRAO, plan.bridge)
            assertTrue(near(cx, plan.bridgeCx), "$type: ilha mudou de lugar (${plan.bridgeCx})")
            assertTrue(near(mast, plan.mastX!!), "$type: mastro mudou de lugar (${plan.mastX})")
            assertEquals(funnelXs.size, plan.funnels.size)
            funnelXs.zip(plan.funnels).forEach { (x, f) -> assertTrue(near(x, f.x), "$type: chaminé em ${f.x}, era $x") }
        }
        // bowX = tamanho * 50 - 5
        check(ShipClass.BATTLESHIP, 97.5f, 109.5f, listOf(117.5f, 71.5f))
        check(ShipClass.CRUISER, 63.8f, 72.8f, listOf(49.8f, 37.8f))
        check(ShipClass.DESTROYER, 47.5f, 53.5f, listOf(36.5f, 26.5f))
        val carrier = superstructurePlan(ShipClass.CARRIER, FleetLine.STANDARD)
        assertEquals(1, carrier.funnels.size)
        assertTrue(near(171f + 20f, carrier.funnels[0].x))
        assertNull(carrier.mastX)
    }
}
