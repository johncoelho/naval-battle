package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #131: o app abre no idioma do aparelho; pt, en e es direto, o resto em inglês. */
class DeviceLanguageTest {

    @Test
    fun portuguesEmQualquerRegiao() {
        assertEquals(Lang.PT, Lang.fromDevice("pt"))
        assertEquals(Lang.PT, Lang.fromDevice("pt-BR"))
        assertEquals(Lang.PT, Lang.fromDevice("pt-PT"))
        assertEquals(Lang.PT, Lang.fromDevice("pt_BR"))
    }

    @Test
    fun inglesDireto() {
        assertEquals(Lang.EN, Lang.fromDevice("en"))
        assertEquals(Lang.EN, Lang.fromDevice("en-US"))
        assertEquals(Lang.EN, Lang.fromDevice("en-GB"))
    }

    @Test
    fun espanholEmQualquerRegiao() {
        assertEquals(Lang.ES, Lang.fromDevice("es"))
        assertEquals(Lang.ES, Lang.fromDevice("es-MX"))
        assertEquals(Lang.ES, Lang.fromDevice("es-419"))
        assertEquals(Lang.ES, Lang.fromDevice("es-ES"))
        assertEquals(Lang.ES, Lang.fromDevice("es-Latn-MX"))
        assertEquals(Lang.ES, Lang.fromDevice("ES_mx"))
    }

    @Test
    fun outroIdiomaVirdaIngles() {
        assertEquals(Lang.EN, Lang.fromDevice("fr"))
        assertEquals(Lang.EN, Lang.fromDevice("fr-CA"))
        assertEquals(Lang.EN, Lang.fromDevice("de-DE"))
        assertEquals(Lang.EN, Lang.fromDevice("zh-Hans-CN"))
        assertEquals(Lang.EN, Lang.fromDevice("und"))
    }

    @Test
    fun vazioOuNuloVirdaIngles() {
        assertEquals(Lang.EN, Lang.fromDevice(""))
        assertEquals(Lang.EN, Lang.fromDevice("   "))
        assertEquals(Lang.EN, Lang.fromDevice(null))
    }

    @Test
    fun idiomaGravadoMandaSobreoAparelho() {
        assertEquals(Lang.PT, Lang.initial("pt", "en-US"))
        assertEquals(Lang.ES, Lang.initial("es", "en-US"))
        assertEquals(Lang.EN, Lang.initial("en", "pt-BR"))
    }

    @Test
    fun semIdiomaGravadoUsaOAparelho() {
        assertEquals(Lang.EN, Lang.initial("", "en-US"))
        assertEquals(Lang.ES, Lang.initial("", "es-MX"))
        assertEquals(Lang.PT, Lang.initial("", "pt-BR"))
        assertEquals(Lang.EN, Lang.initial("", "fr-FR"))
        assertEquals(Lang.EN, Lang.initial("", null))
    }
}
