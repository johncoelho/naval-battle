package br.com.navalbattle.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResultLayoutTest {
    @Test
    fun onlineTrazOComparativoLogoAposAManchete() {
        val s = resultSections(local = false, online = true)
        assertEquals(ResultSection.HEADLINE, s[0])
        assertEquals(1, s.indexOf(ResultSection.COMPARISON))
        assertTrue(s.indexOf(ResultSection.COMPARISON) < s.indexOf(ResultSection.CHART))
    }

    @Test
    fun localTemMapaAntesDasEstatisticasESemComparativo() {
        val s = resultSections(local = true, online = false)
        assertTrue(s.indexOf(ResultSection.CHART) < s.indexOf(ResultSection.LOCAL_STATS))
        assertFalse(ResultSection.COMPARISON in s)
    }

    @Test
    fun soloNaoTemMapaNemComparativo() {
        val s = resultSections(local = false, online = false)
        assertFalse(ResultSection.CHART in s)
        assertFalse(ResultSection.COMPARISON in s)
    }
}
