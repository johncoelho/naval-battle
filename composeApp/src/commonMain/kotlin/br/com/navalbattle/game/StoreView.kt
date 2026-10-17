package br.com.navalbattle.game

/** O que a aba Dobrões mostra (#15): o Prêmio Beta, o Prêmio já pego hoje, a vitrine paga ou nada. */
enum class StoreView { PREMIO, PREMIO_DONE, PAID, CLOSED }

/**
 * Regra da aba Dobrões, pura para testar. Testador só vê o Prêmio (e nunca a vitrine
 * paga, mesmo com a venda ligada); com o Prêmio desligado, a aba de testador fica
 * fechada. Jogador comum vê a vitrine só com a venda real ligada.
 */
fun storeView(beta: Boolean, realEnabled: Boolean, premioEnabled: Boolean, premioClaimed: Boolean): StoreView =
    when {
        beta && premioEnabled && premioClaimed -> StoreView.PREMIO_DONE
        beta && premioEnabled -> StoreView.PREMIO
        beta -> StoreView.CLOSED
        realEnabled -> StoreView.PAID
        else -> StoreView.CLOSED
    }
