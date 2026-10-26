package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Faixa etária da Play (#49, ECA Digital): conversão dos inteiros crus da API e o tipo de
 * anúncio de cada faixa. Menor e idade desconhecida nunca recebem anúncio personalizado.
 */
class AgeRulesTest {

    private val shared = AgeRules.STATUS_SHARED

    private fun band(lower: Int?, upper: Int?, status: Int? = shared, error: Boolean = false) =
        AgeRules.band(status, lower, upper, error)

    @Test
    fun faixaZeroADozeEhCrianca() = assertEquals(AgeBand.CHILD, band(0, 12))

    @Test
    fun faixaPersonalizadaDezADozeEhCrianca() = assertEquals(AgeBand.CHILD, band(10, 12))

    @Test
    fun treze_a_quinzeEDezesseisADezesseteSaoAdolescente() {
        assertEquals(AgeBand.TEEN, band(13, 15))
        assertEquals(AgeBand.TEEN, band(16, 17))
    }

    @Test
    fun dezoitoSemLimiteSuperiorEhAdulto() {
        assertEquals(AgeBand.ADULT, band(18, null))
        assertEquals(AgeBand.ADULT, band(18, 24))
    }

    @Test
    fun naoCompartilhouEVerificacaoPendenteSaoDesconhecidos() {
        assertEquals(AgeBand.UNKNOWN, band(18, null, status = 2))
        assertEquals(AgeBand.UNKNOWN, band(18, null, status = 3))
        assertEquals(AgeBand.UNKNOWN, band(0, 12, status = null))
    }

    @Test
    fun semLimitesOuComErroEhDesconhecido() {
        assertEquals(AgeBand.UNKNOWN, band(null, null))
        assertEquals(AgeBand.UNKNOWN, band(18, null, error = true))
        assertEquals(AgeBand.UNKNOWN, band(null, 20))
    }

    @Test
    fun criancaPedeNaoPersonalizadoComMarcaInfantilEClassificacaoG() {
        val a = AgeRules.audience(AgeBand.CHILD)
        assertFalse(a.personalized)
        assertTrue(a.underAgeOfConsent)
        assertEquals(AdAgeTreatment.CHILD, a.treatment)
        assertEquals(AdRating.G, a.maxRating)
    }

    @Test
    fun adolescenteEDesconhecidoSaoIguais() {
        val teen = AgeRules.audience(AgeBand.TEEN)
        assertEquals(teen, AgeRules.audience(AgeBand.UNKNOWN))
        assertFalse(teen.personalized)
        assertTrue(teen.underAgeOfConsent)
        assertEquals(AdAgeTreatment.TEEN, teen.treatment)
        assertEquals(AdRating.PG, teen.maxRating)
    }

    @Test
    fun adultoRecebeAnuncioPersonalizadoSemMarcas() {
        val a = AgeRules.audience(AgeBand.ADULT)
        assertTrue(a.personalized)
        assertFalse(a.underAgeOfConsent)
        assertEquals(AdAgeTreatment.UNSPECIFIED, a.treatment)
        assertEquals(AdRating.NONE, a.maxRating)
    }

    @Test
    fun cadaFaixaTemOTratamentoDoAdMob() {
        val esperado = mapOf(
            AgeBand.ADULT to AdAgeTreatment.UNSPECIFIED,
            AgeBand.TEEN to AdAgeTreatment.TEEN,
            AgeBand.UNKNOWN to AdAgeTreatment.TEEN,
            AgeBand.CHILD to AdAgeTreatment.CHILD
        )
        for (band in AgeBand.entries) {
            val a = AgeRules.audience(band)
            assertEquals(esperado.getValue(band), a.treatment, "tratamento de $band")
            // A marca do UMP nunca contradiz o tratamento: só o adulto fica sem marca.
            assertEquals(band != AgeBand.ADULT, a.underAgeOfConsent, "UMP de $band")
            assertEquals(band == AgeBand.ADULT, a.personalized, "personalizado de $band")
        }
    }

    @Test
    fun leituraVale24Horas() {
        val h = 60L * 60 * 1000
        val t0 = 1_000_000_000L
        assertFalse(AgeRules.checkDue(t0, t0 + 23 * h))
        assertTrue(AgeRules.checkDue(t0, t0 + 25 * h))
        assertTrue(AgeRules.checkDue(0L, t0))
        assertTrue(AgeRules.checkDue(t0, t0 - h)) // relógio voltou: lê de novo
    }

    @Test
    fun falhaNaoEhGuardadaMasRespostaDaPlayE() {
        val falha = AgeRules.reading(null, null, null, error = true)
        assertEquals(AgeBand.UNKNOWN, falha.band)
        assertFalse(AgeRules.shouldCache(falha))
        val naoCompartilhou = AgeRules.reading(2, null, null, error = false)
        assertEquals(AgeBand.UNKNOWN, naoCompartilhou.band)
        assertTrue(AgeRules.shouldCache(naoCompartilhou))
        val teen = AgeRules.reading(AgeRules.STATUS_SHARED, 13, 15, error = false)
        assertEquals(AgeBand.TEEN, teen.band)
        assertTrue(AgeRules.shouldCache(teen))
    }

    @Test
    fun faixaGuardadaInvalidaVoltaParaDesconhecido() {
        assertEquals(AgeBand.UNKNOWN, AgeRules.parse(""))
        assertEquals(AgeBand.UNKNOWN, AgeRules.parse("xyz"))
        assertEquals(AgeBand.TEEN, AgeRules.parse(AgeBand.TEEN.name))
    }
}
