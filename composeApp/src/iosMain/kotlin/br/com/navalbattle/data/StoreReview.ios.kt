package br.com.navalbattle.data

// O iPhone é instalado pelo TestFlight, sem avaliação da App Store para pedir. Fica para a App Store.
actual object StoreReview {
    actual val supported: Boolean = false
    actual fun request(onDone: () -> Unit) = onDone()
}
