package br.com.navalbattle.data

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Vínculo de logins na mesma conta (#63): Apple ID e Google. Regras puras, para testar sem aparelho.
 * O app nunca vincula só por e-mail nem junta duas contas com histórico: o vínculo manual exige a
 * sessão da conta de destino e um token novo do provedor (ver [CloudApi.linkIdentity]).
 */
enum class LinkOption { APPLE, GOOGLE }

/**
 * Botões "Vincular ..." do Perfil. Só o iPhone ([isIos]) vincula (a Apple não existe no Android, decisão de
 * 26/10), e só o que ainda falta na conta. Lista de provedores vazia = ainda não carregou: não oferece nada.
 */
fun linkOptions(providers: List<String>, isIos: Boolean): List<LinkOption> {
    if (!isIos || providers.isEmpty()) return emptyList()
    val has = providers.map { it.trim().lowercase() }.toSet()
    return buildList {
        if ("apple" !in has) add(LinkOption.APPLE)
        if ("google" !in has) add(LinkOption.GOOGLE)
    }
}

/**
 * Pergunta "Já joga Naval Battle com Google?": só na primeira autorização da Apple (a Apple manda o
 * e-mail uma vez só) e sem conta aberta. Logado, o jogador usa "Vincular Apple ID" do Perfil.
 */
fun offerExistingAccount(firstAuthorization: Boolean, signedIn: Boolean): Boolean =
    firstAuthorization && !signedIn

/** Nomes dos logins da conta para a linha "Logins da conta": sem repetir, sem provedor desconhecido. */
fun providerLabels(providers: List<String>): List<String> =
    providers.map { it.trim().lowercase() }
        .mapNotNull {
            when (it) {
                "google" -> "Google"
                "apple" -> "Apple"
                "email" -> t(K.LINK_EMAIL)
                else -> null
            }
        }
        .distinct()

/**
 * Texto para a falha do vínculo, pelo `error_code` do GoTrue ([CloudResult.Fail.code]). Sem código
 * (rede, servidor fora) cai no texto geral.
 */
fun linkFailureText(code: String?): K = when (code?.trim()?.lowercase()) {
    "identity_already_exists" -> K.LINK_ALREADY_USED
    "manual_linking_disabled" -> K.LINK_UNAVAILABLE
    "bad_jwt", "bad_id_token", "validation_failed", "invalid_credentials", "token_expired" -> K.LINK_EXPIRED
    else -> K.LINK_FAIL
}
