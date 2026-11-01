package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/** Prêmio Beta na aba Dobrões (#28): escondido, pronto para pegar ou já pego hoje. */
enum class PremioState { ESCONDIDO, PEGAR, JA_PEGOU }

/**
 * Regra do Prêmio Beta, pura para testar. Só testador, e só com o interruptor
 * `premio_beta_enabled` ligado no servidor. O Prêmio não passa pela Play.
 */
fun premioState(beta: Boolean, premioEnabled: Boolean, premioClaimed: Boolean): PremioState =
    when {
        !beta || !premioEnabled -> PremioState.ESCONDIDO
        premioClaimed -> PremioState.JA_PEGOU
        else -> PremioState.PEGAR
    }

/** O que a aba Dobrões mostra (#62), em ordem de prioridade. */
enum class DoubloonAisleView { SIGN_IN, LOADING, FAILED, CLOSED, PREMIO, JA_PEGOU, VITRINE }

/**
 * Decide o estado da aba Dobrões, pura para testar. Sem a situação da conta ([statusLoaded] falso):
 * carregando, ou falha de rede ([loadFailed]) com botão de tentar de novo; nunca tela vazia (#62).
 * Sem Prêmio nem vitrine (iPhone de quem não é testador): "chega em breve".
 */
fun doubloonAisleView(
    signedIn: Boolean,
    statusLoaded: Boolean,
    loadFailed: Boolean,
    premio: PremioState,
    vitrine: Boolean
): DoubloonAisleView = when {
    !signedIn -> DoubloonAisleView.SIGN_IN
    !statusLoaded -> if (loadFailed) DoubloonAisleView.FAILED else DoubloonAisleView.LOADING
    premio == PremioState.PEGAR -> DoubloonAisleView.PREMIO
    premio == PremioState.JA_PEGOU -> DoubloonAisleView.JA_PEGOU
    vitrine -> DoubloonAisleView.VITRINE
    else -> DoubloonAisleView.CLOSED
}

/**
 * A vitrine paga aparece só onde a Play vende ([vendaPelaPlay], falso no iPhone), e então com a
 * venda real ligada (todos) ou com a vitrine beta ligada (`beta_store_enabled`, só para testador;
 * o servidor já devolve a flag só para conta de testador). No iPhone nunca aparece (#30).
 */
fun vitrineVisivel(vendaPelaPlay: Boolean, realEnabled: Boolean, vitrineBeta: Boolean): Boolean =
    vendaPelaPlay && (realEnabled || vitrineBeta)

/**
 * Aviso de uma compra da vitrine conforme o status do servidor. Null: nada a dizer (o saldo
 * entra por `dobroes_pending`, com o próprio aviso de "dobrões entraram").
 */
fun avisoCompra(status: String): K? = when (status) {
    "credited", "premio", "already" -> null
    "premio_done" -> K.PREMIO_DONE_NOTICE
    "billing_off", "premio_off", "premio_only" -> K.DOUBLOON_CLOSED
    else -> K.DOUBLOON_BUY_FAILED
}

/** Aviso do resgate do Prêmio Beta (RPC `claim_premio_beta`): premio | already | off | not_beta. */
fun avisoPremio(status: String): K? = when (status) {
    "premio" -> null
    "already" -> K.PREMIO_DONE_NOTICE
    "off", "not_beta" -> K.PREMIO_UNAVAILABLE
    else -> K.DOUBLOON_BUY_FAILED
}
