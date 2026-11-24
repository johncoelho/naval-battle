package br.com.navalbattle.game

import br.com.navalbattle.i18n.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountriesTest {
    @Test
    fun codigosUnicosDeDuasLetrasMaiusculasENomesNosTresIdiomas() {
        val codes = Countries.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.matches(Regex("[A-Z]{2}")) })
        assertTrue(Countries.all.size in 60..80, "primeiro corte entre 60 e 80 países")
        Countries.all.forEach { c -> Lang.entries.forEach { assertTrue(c.name(it).isNotBlank(), c.code) } }
    }

    @Test
    fun foraTerritoriosDisputados() {
        listOf("TW", "XK", "EH", "HK", "PR", "GF").forEach { assertFalse(Countries.isValid(it), it) }
        assertTrue(Countries.isValid("PS"))
        assertTrue(Countries.isValid("VA"))
    }

    @Test
    fun bandeiraEmojiPelosIndicadoresRegionais() {
        assertEquals("\uD83C\uDDE7\uD83C\uDDF7", Countries.flagOf("BR"))
        assertEquals("\uD83C\uDDE6\uD83C\uDDF7", Countries.flagOf("AR"))
        assertEquals("", Countries.flagOf("br"))
        assertEquals("", Countries.flagOf("BRA"))
    }

    @Test
    fun buscaPorNomeSemAcentoEPorCodigo() {
        assertEquals(listOf("AR"), Countries.search("arg", Lang.PT).map { it.code })
        assertTrue("MX" in Countries.search("mexi", Lang.PT).map { it.code })
        assertTrue("BR" in Countries.search("br", Lang.EN).map { it.code })
        assertEquals(Countries.all.size, Countries.search("", Lang.ES).size)
        assertTrue(Countries.search("zzz", Lang.PT).isEmpty())
    }

    @Test
    fun sugestaoPelaRegiaoDoIdiomaDoAparelho() {
        assertEquals("BR", Countries.regionOf("pt-BR"))
        assertEquals("MX", Countries.regionOf("es-MX"))
        assertEquals("CN", Countries.regionOf("zh-Hans-CN"))
        assertNull(Countries.regionOf("es-419"))
        assertNull(Countries.regionOf("en"))
        assertNull(Countries.regionOf(null))
        assertEquals("BR", Countries.suggestion(Countries.regionOf("en-BR"))?.code)
        // região fora da lista: sem sugestão
        assertNull(Countries.suggestion("TW"))
    }

    @Test
    fun escolhaNoAparelhoENaNuvem() {
        // nunca escolheu: não manda o campo (não apaga a bandeira da nuvem)
        assertNull(CountryChoice.toCloud(CountryChoice.UNSET))
        // prefere não mostrar: manda vazio (o servidor grava nulo)
        assertEquals("", CountryChoice.toCloud(CountryChoice.NONE))
        assertEquals("AR", CountryChoice.toCloud("AR"))
        assertNull(CountryChoice.toCloud("XX"))
        // da nuvem: código válido vale; nulo mantém a escolha do aparelho
        assertEquals("BR", CountryChoice.fromCloud(CountryChoice.UNSET, "BR"))
        assertEquals(CountryChoice.NONE, CountryChoice.fromCloud(CountryChoice.NONE, null))
        assertEquals("AR", CountryChoice.fromCloud("AR", "xx"))
        assertNull(CountryChoice.shown(CountryChoice.NONE))
        assertEquals("CL", CountryChoice.shown("CL"))
    }

    @Test
    fun personasComBandeiraCoerenteEUmaParteSem() {
        val flags = BotPersonas.POOL.map { BotPersonas.countryOf(it) }
        assertTrue(flags.filterNotNull().all { Countries.isValid(it) })
        val without = flags.count { it == null }
        assertTrue(without in 6..16, "parte das personas sem bandeira, como os humanos: $without")
        assertTrue(flags.filterNotNull().toSet().size >= 3, "bandeiras variam")
        assertEquals("BR", BotPersonas.countryOf("MarujoLeo"))
        assertNull(BotPersonas.countryOf("NomeQueNaoExiste"))
    }
}
