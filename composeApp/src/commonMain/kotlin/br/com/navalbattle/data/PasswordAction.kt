package br.com.navalbattle.data

/** O que o Perfil oferece para a senha da conta logada. */
enum class PasswordAction { CHANGE, CREATE }

/**
 * "Trocar senha" (pede a senha atual) ou "Criar senha" (conta que entrou só com o
 * Google, #9). [hasPassword] vem da RPC `account_has_password`; nulo (ainda não
 * carregou, sem rede ou erro) conta como "tem senha", o comportamento de antes.
 */
fun passwordAction(hasPassword: Boolean?): PasswordAction =
    if (hasPassword == false) PasswordAction.CREATE else PasswordAction.CHANGE
