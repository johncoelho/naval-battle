package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreLinksTest {
    @Test
    fun doisEnderecosPrimeiroDaLojaDepoisWeb() {
        val urls = appStoreUrls()
        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("itms-apps://"))
        assertTrue(urls[1].startsWith("https://apps.apple.com/"))
    }

    @Test
    fun ambosApontamParaOMesmoApp() {
        assertTrue(appStoreUrls().all { it.endsWith("id6827258132") })
    }
}
