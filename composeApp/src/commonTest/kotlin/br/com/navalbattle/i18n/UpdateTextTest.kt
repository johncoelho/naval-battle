package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #81: o aviso de atualização do iPhone fala da App Store, não de .ipa, TestFlight ou site. */
class UpdateTextTest {
    private fun textos(key: K) = listOf(key.pt, key.en, key.es)

    @Test
    fun textosDoAvisoNaoCitamIpaTestFlightNemSite() {
        listOf(K.UPDATE_POPUP_IOS_HINT, K.UPDATE_POPUP_STORE).forEach { key ->
            textos(key).forEach { texto ->
                assertTrue(texto.isNotBlank(), "$key em branco")
                listOf("ipa", "testflight", "site").forEach { proibido ->
                    assertFalse(texto.contains(proibido, ignoreCase = true), "$key cita $proibido: $texto")
                }
            }
        }
    }

    @Test
    fun dicaDoIphoneMandaParaAAppStore() {
        textos(K.UPDATE_POPUP_IOS_HINT).forEach { assertTrue(it.contains("App Store"), it) }
    }
}
