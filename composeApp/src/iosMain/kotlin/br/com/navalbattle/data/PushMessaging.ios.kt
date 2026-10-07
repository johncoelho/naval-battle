package br.com.navalbattle.data

actual object PushMessaging {
    actual val platform: String = "ios"

    // APNs exige conta paga de desenvolvedor Apple; até lá o iPhone fica só com o
    // lembrete local do Diário de bordo
    actual suspend fun token(): String? = null
}
