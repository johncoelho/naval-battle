package br.com.navalbattle.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PasswordActionTest {

    @Test
    fun contaSoGoogleCriaSenha() {
        assertEquals(PasswordAction.CREATE, passwordAction(false))
    }

    @Test
    fun contaComSenhaTrocaSenha() {
        assertEquals(PasswordAction.CHANGE, passwordAction(true))
    }

    @Test
    fun semRespostaDoServidorSegueComoHoje() {
        // sem rede, erro ou ainda carregando: "Trocar senha", como antes da #9
        assertEquals(PasswordAction.CHANGE, passwordAction(null))
    }
}
