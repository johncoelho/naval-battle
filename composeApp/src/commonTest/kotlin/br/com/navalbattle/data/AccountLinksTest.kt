package br.com.navalbattle.data

import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Vincular Apple ID e Google na mesma conta (#63). */
class AccountLinksTest {

    @Test
    fun iphoneComSoGoogleOfereceAApple() {
        assertEquals(listOf(LinkOption.APPLE), linkOptions(listOf("google"), isIos = true))
    }

    @Test
    fun iphoneComSoApplePedeOGoogle() {
        assertEquals(listOf(LinkOption.GOOGLE), linkOptions(listOf("apple"), isIos = true))
    }

    @Test
    fun contaSoDeEmailOfereceOsDois() {
        assertEquals(listOf(LinkOption.APPLE, LinkOption.GOOGLE), linkOptions(listOf("email"), isIos = true))
    }

    @Test
    fun appleMaisGoogleNaoOfereceNada() {
        assertEquals(emptyList(), linkOptions(listOf("google", "apple"), isIos = true))
        assertEquals(emptyList(), linkOptions(listOf("Apple", " GOOGLE "), isIos = true))
    }

    @Test
    fun androidNuncaOfereceVinculo() {
        assertEquals(emptyList(), linkOptions(listOf("google"), isIos = false))
        assertEquals(emptyList(), linkOptions(listOf("email"), isIos = false))
    }

    @Test
    fun provedoresAindaNaoCarregadosNaoOferecemNada() {
        assertEquals(emptyList(), linkOptions(emptyList(), isIos = true))
    }

    @Test
    fun primeiraAutorizacaoSemContaPergunta() {
        assertTrue(offerExistingAccount(firstAuthorization = true, signedIn = false))
    }

    @Test
    fun logadoOuNaoSendoAPrimeiraNaoPergunta() {
        assertFalse(offerExistingAccount(firstAuthorization = true, signedIn = true))
        assertFalse(offerExistingAccount(firstAuthorization = false, signedIn = false))
        assertFalse(offerExistingAccount(firstAuthorization = false, signedIn = true))
    }

    @Test
    fun linhaDeLoginsSemRepetirNemDesconhecidos() {
        assertEquals(listOf("Google", "Apple"), providerLabels(listOf("google", "apple")))
        assertEquals(listOf("E-mail", "Google"), providerLabels(listOf("email", "Google", "google", "phone")))
        assertEquals(emptyList(), providerLabels(emptyList()))
    }

    @Test
    fun cadaCodigoDoAuthTemSeuTexto() {
        assertEquals(K.LINK_ALREADY_USED, linkFailureText("identity_already_exists"))
        assertEquals(K.LINK_UNAVAILABLE, linkFailureText("manual_linking_disabled"))
        assertEquals(K.LINK_EXPIRED, linkFailureText("bad_jwt"))
        assertEquals(K.LINK_EXPIRED, linkFailureText("validation_failed"))
        assertEquals(K.LINK_FAIL, linkFailureText("different_user"))
        // rede ou servidor fora: sem código
        assertEquals(K.LINK_FAIL, linkFailureText(null))
        assertEquals(K.LINK_FAIL, linkFailureText("unexpected_failure"))
    }

    @Test
    fun contaComAppleVinculadaRevogaNaExclusao() {
        assertTrue(shouldRevokeApple(listOf("google", "apple")))
        assertFalse(shouldRevokeApple(listOf("google")))
    }
}
