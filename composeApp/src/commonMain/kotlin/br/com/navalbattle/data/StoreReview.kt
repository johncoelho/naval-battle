package br.com.navalbattle.data

/**
 * Pedido de avaliação dentro do jogo (#45). No Android, a Play In-App Review API; no iPhone não há
 * pedido (o .ipa não vem da App Store). O jogo nunca sabe se a pessoa avaliou: a Google aplica a
 * cota própria e pode não mostrar nada. [request] nunca lança e sempre chama [onDone] (no máximo uma vez).
 */
expect object StoreReview {
    val supported: Boolean
    fun request(onDone: () -> Unit)
}
