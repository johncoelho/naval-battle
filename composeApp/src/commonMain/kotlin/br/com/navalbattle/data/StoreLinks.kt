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

/**
 * Se há link de loja para compartilhar neste aparelho (#166). Android: a ficha da Play. iPhone: ainda não (o jogo sai
 * pelo TestFlight e o `shareStoreListing` do iOS é vazio), então quem pergunta esconde o botão em vez de deixá-lo mudo.
 * Quando a App Store tiver o jogo: ligar o "ios" aqui e implementar o `actual` com [appStoreUrls].
 */
object StoreLink {
    fun available(platform: String): Boolean = platform == "android"
}
