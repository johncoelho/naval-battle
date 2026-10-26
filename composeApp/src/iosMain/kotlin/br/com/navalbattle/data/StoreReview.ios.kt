package br.com.navalbattle.data

// O .ipa vem do Sideloadly, sem App Store: não há avaliação para pedir. Fica para o TestFlight/App Store.
actual object StoreReview {
    actual val supported: Boolean = false
    actual fun request(onDone: () -> Unit) = onDone()
}
