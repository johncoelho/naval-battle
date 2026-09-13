package br.com.navalbattle.data

/** Uma partida anunciada na rede local, pronta para receber um adversário. */
data class LanGame(val name: String, val host: String, val port: Int)

/** Em que pé está a ligação entre os dois aparelhos. */
enum class LinkState { IDLE, HOSTING, SEARCHING, CONNECTING, CONNECTED, FAILED }

/**
 * Ligação direta entre dois celulares na mesma rede sem fio: um anuncia a partida,
 * o outro encontra e conecta. Não passa por servidor — o jogo funciona sem internet,
 * só com os dois aparelhos no mesmo Wi-Fi.
 *
 * As mensagens são linhas de texto simples (ver [Protocol]).
 */
expect class LanLink() {
    /** Anuncia uma partida na rede e espera o adversário. */
    fun host(name: String, onState: (LinkState) -> Unit, onLine: (String) -> Unit)

    /** Procura partidas anunciadas; chama [onFound] a cada lista atualizada. */
    fun search(onFound: (List<LanGame>) -> Unit, onState: (LinkState) -> Unit)

    /** Entra numa partida encontrada. */
    fun join(game: LanGame, onState: (LinkState) -> Unit, onLine: (String) -> Unit)

    fun send(line: String)

    fun close()
}

/**
 * Protocolo de linha. Formato: `TIPO|campo|campo`. Texto puro para caber em qualquer
 * transporte e ser legível em depuração.
 */
object Protocol {
    const val HELLO = "HELLO"
    const val FLEET = "FLEET"
    const val ACT = "ACT"
    const val ABILITY = "ABIL"
    const val QUIT = "QUIT"

    fun hello(name: String, mode: String) = "$HELLO|$name|$mode"

    /** Frota inteira: `FLEET|CARRIER,3,4,H;BATTLESHIP,0,0,V;...` */
    fun fleet(ships: List<String>) = "$FLEET|${ships.joinToString(";")}"

    fun act(x: Int, y: Int) = "$ACT|$x|$y"

    fun ability(code: String) = "$ABILITY|$code"

    fun parts(line: String): List<String> = line.split("|")
}
