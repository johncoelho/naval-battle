package br.com.navalbattle.data

/** Identificador do app na App Store (Naval Battle Classic). */
const val APP_STORE_ID = "6827258132"

/**
 * Endereços da página do jogo na App Store, em ordem de tentativa: primeiro o esquema que abre
 * direto o app da loja; se o aparelho não tiver (simulador), o endereço web abre no Safari.
 */
fun appStoreUrls(): List<String> = listOf(
    "itms-apps://apps.apple.com/app/id$APP_STORE_ID",
    "https://apps.apple.com/app/id$APP_STORE_ID"
)
