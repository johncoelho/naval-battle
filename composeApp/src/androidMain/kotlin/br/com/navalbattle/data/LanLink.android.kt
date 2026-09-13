package br.com.navalbattle.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import br.com.navalbattle.audio.AudioContextHolder
import java.io.BufferedReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

private const val SERVICE_TYPE = "_navalbattle._tcp."

/**
 * Rede local com as ferramentas do próprio Android: NSD anuncia e descobre a partida
 * (o mesmo mDNS do Bonjour, o que abre caminho para o iOS depois), e a conversa corre
 * num socket TCP direto entre os dois aparelhos. Sem biblioteca, sem servidor.
 */
actual class LanLink actual constructor() {

    private val nsd: NsdManager by lazy {
        AudioContextHolder.appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var writerThread: Thread? = null
    /**
     * Fila de saída, com uma única thread escrevendo em ordem. Antes, cada [send]
     * abria a própria thread para escrever — sem nenhuma garantia de que a primeira
     * chamada terminasse antes da segunda. Numa habilidade normal isso quase nunca
     * dava problema (só uma linha por vez), mas usar uma habilidade manda duas linhas
     * em sequência (`ABIL` armando o alvo, `ACT` com a coordenada); se a ordem
     * invertesse na rede, o aparelho que recebia aplicava um tiro comum em vez do
     * efeito da habilidade, e os dois lados passavam a discordar sobre de quem era a
     * vez dali em diante — exatamente o travamento relatado no tático em rede.
     */
    private val outbox = LinkedBlockingQueue<String>()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var alive = true

    // ------------------------------------------------------------------ hospedar

    actual fun host(name: String, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        alive = true
        outbox.clear()
        onState(LinkState.HOSTING)
        thread(name = "naval-host") {
            runCatching {
                val srv = ServerSocket(0)
                server = srv
                announce(name, srv.localPort)
                val client = srv.accept()
                socket = client
                onState(LinkState.CONNECTED)
                pump(client, onLine, onState)
            }.onFailure { if (alive) onState(LinkState.FAILED) }
        }
    }

    private fun announce(name: String, port: Int) {
        val info = NsdServiceInfo().apply {
            // o nome do serviço aparece na lista do outro aparelho
            serviceName = name.take(24).ifBlank { "Naval Battle" }
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    // -------------------------------------------------------------------- entrar

    actual fun search(onFound: (List<LanGame>) -> Unit, onState: (LinkState) -> Unit) {
        alive = true
        onState(LinkState.SEARCHING)
        val found = linkedMapOf<String, LanGame>()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) = onState(LinkState.FAILED)
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val address = resolved.host?.hostAddress ?: return
                        found[resolved.serviceName] =
                            LanGame(resolved.serviceName, address, resolved.port)
                        onFound(found.values.toList())
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                onFound(found.values.toList())
            }
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    actual fun join(game: LanGame, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        alive = true
        outbox.clear()
        onState(LinkState.CONNECTING)
        thread(name = "naval-join") {
            runCatching {
                val client = Socket(game.host, game.port)
                socket = client
                onState(LinkState.CONNECTED)
                pump(client, onLine, onState)
            }.onFailure { if (alive) onState(LinkState.FAILED) }
        }
    }

    // ------------------------------------------------------------------ conversa

    /** Lê linha a linha até a conexão cair. Roda na thread da conexão. */
    private fun pump(client: Socket, onLine: (String) -> Unit, onState: (LinkState) -> Unit) {
        val out = PrintWriter(client.getOutputStream(), true)
        writer = out
        writerThread = thread(name = "naval-writer") {
            while (alive) {
                val line = try {
                    outbox.take()
                } catch (e: InterruptedException) {
                    break
                }
                runCatching { out.println(line) }
            }
        }
        val reader: BufferedReader = client.getInputStream().bufferedReader()
        while (alive) {
            val line = reader.readLine() ?: break
            if (line.isNotBlank()) onLine(line)
        }
        if (alive) onState(LinkState.FAILED)
    }

    /** Só enfileira — quem escreve de verdade é a única thread nascida em [pump]. */
    actual fun send(line: String) {
        outbox.offer(line)
    }

    actual fun close() {
        alive = false
        writerThread?.interrupt()
        writerThread = null
        outbox.clear()
        runCatching { registration?.let { nsd.unregisterService(it) } }
        runCatching { discovery?.let { nsd.stopServiceDiscovery(it) } }
        registration = null
        discovery = null
        runCatching { socket?.close() }
        runCatching { server?.close() }
        socket = null
        server = null
        writer = null
    }
}
