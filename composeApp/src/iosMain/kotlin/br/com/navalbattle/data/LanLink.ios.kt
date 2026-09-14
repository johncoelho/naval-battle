package br.com.navalbattle.data

/**
 * Rede local ainda não está portada para o iOS (precisa de `NetService`/
 * `NetServiceBrowser`, o Bonjour do Foundation, no lugar do NSD do Android — mesmo
 * protocolo mDNS, então dá para reaproveitar a ideia; ver docs/BUILD.md). Por ora
 * qualquer tentativa de hospedar, procurar ou entrar falha na hora, para a tela avisar
 * em vez de ficar esperando uma conexão que nunca chega.
 */
actual class LanLink actual constructor() {
    actual fun host(name: String, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        onState(LinkState.FAILED)
    }

    actual fun search(onFound: (List<LanGame>) -> Unit, onState: (LinkState) -> Unit) {
        onState(LinkState.FAILED)
    }

    actual fun join(game: LanGame, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        onState(LinkState.FAILED)
    }

    actual fun send(line: String) {}

    actual fun close() {}
}
