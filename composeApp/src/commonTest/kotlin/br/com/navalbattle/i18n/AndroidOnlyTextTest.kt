package br.com.navalbattle.i18n

import br.com.navalbattle.data.platformName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #118: os textos de Google Play/beta saem do binario do iPhone. No iPhone todos devolvem vazio; no Android todos
 * existem nos tres idiomas (o conteudo exato esta travado em AndroidOnlyTextAndroidTest).
 */
class AndroidOnlyTextTest {
    @Test
    fun vazioNoIphoneEPreenchidoNoAndroidNosTresIdiomas() {
        val original = I18n.lang
        try {
            Lang.entries.forEach { lang ->
                I18n.lang = lang
                AndroidOnlyKey.entries.forEach { key ->
                    val texto = androidOnlyText(key)
                    if (platformName == "ios") assertEquals("", texto, "$lang $key deveria ser vazio no iPhone")
                    else assertTrue(texto.isNotBlank(), "$lang $key vazio no Android")
                }
            }
        } finally {
            I18n.lang = original
        }
    }

    @Test
    fun valorSubstituiOPorcentoS() {
        if (platformName == "ios") return
        val texto = androidOnlyText(AndroidOnlyKey.PREMIO_TAKEN, "2h")
        assertTrue("2h" in texto)
        assertTrue("%s" !in texto)
    }
}
