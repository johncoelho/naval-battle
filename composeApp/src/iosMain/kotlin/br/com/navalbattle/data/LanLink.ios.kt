package br.com.navalbattle.data

import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.addrinfo
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.htons
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket

private const val SERVICE_TYPE = "_navalbattle._tcp."
// Porta fixa: mais simples e robusto do que descobrir a porta que o SO escolheu
// (bind(0) + getsockname) — os dois lados sempre sabem de antemão qual usar.
private const val FIXED_PORT: UShort = 53421u

/**
 * Rede local no iOS: Bonjour (`NSNetService`/`NSNetServiceBrowser`, o Foundation por
 * trás do mesmo mDNS que o NSD do Android já usa) para anunciar e descobrir a
 * partida, e um socket POSIX puro (`platform.posix`) para a conversa linha a linha —
 * o mesmo protocolo de texto do `LanLink.android.kt`, só a camada de transporte muda.
 * Precisa de `NSLocalNetworkUsageDescription` e `NSBonjourServices` no `Info.plist`
 * (iOS 14+ exige os dois, senão a descoberta falha calada).
 */
@OptIn(ExperimentalForeignApi::class)
actual class LanLink actual constructor() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private var listenFd = -1
    private var connFd = -1
    private var netService: NSNetService? = null
    private var browser: NSNetServiceBrowser? = null
    private var browserDelegate: NSObject? = null
    private val resolveDelegates = mutableMapOf<NSNetService, NSObject>()
    private val foundServices = linkedMapOf<String, NSNetService>()
    private val resolvedGames = linkedMapOf<String, LanGame>()

    @Volatile private var alive = true

    // ------------------------------------------------------------------ hospedar

    actual fun host(name: String, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        alive = true
        onState(LinkState.HOSTING)
        scope.launch {
            val fd = openListenSocket()
            if (fd < 0) {
                onState(LinkState.FAILED)
                return@launch
            }
            listenFd = fd
            publish(name)
            val client = accept(fd, null, null)
            if (client < 0) {
                if (alive) onState(LinkState.FAILED)
                return@launch
            }
            connFd = client
            onState(LinkState.CONNECTED)
            pump(client, onLine, onState)
        }
    }

    private fun openListenSocket(): Int {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return -1
        val bound = memScoped {
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_addr.s_addr = INADDR_ANY.convert()
            addr.sin_port = htons(FIXED_PORT)
            bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
        }
        if (!bound || listen(fd, 1) != 0) {
            close(fd)
            return -1
        }
        return fd
    }

    private fun publish(name: String) {
        val service = NSNetService(
            domain = "",
            type = SERVICE_TYPE,
            name = name.take(24).ifBlank { "Naval Battle" },
            port = FIXED_PORT.toInt()
        )
        service.publish()
        netService = service
    }

    // -------------------------------------------------------------------- entrar

    actual fun search(onFound: (List<LanGame>) -> Unit, onState: (LinkState) -> Unit) {
        alive = true
        onState(LinkState.SEARCHING)
        foundServices.clear()
        resolvedGames.clear()

        val delegate = object : NSObject(), NSNetServiceBrowserDelegateProtocol {
            override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
                foundServices[didFindService.name] = didFindService
                val resolveDelegate = object : NSObject(), NSNetServiceDelegateProtocol {
                    override fun netServiceDidResolveAddress(sender: NSNetService) {
                        val host = sender.hostName ?: return
                        resolvedGames[sender.name] = LanGame(sender.name, host, sender.port.toInt())
                        onFound(resolvedGames.values.toList())
                    }
                }
                resolveDelegates[didFindService] = resolveDelegate
                didFindService.delegate = resolveDelegate
                didFindService.resolveWithTimeout(5.0)
            }

            override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
                foundServices.remove(didRemoveService.name)
                resolvedGames.remove(didRemoveService.name)
                onFound(resolvedGames.values.toList())
            }

            override fun netServiceBrowser(browser: NSNetServiceBrowser, didNotSearch: Map<Any?, *>) {
                onState(LinkState.FAILED)
            }
        }
        browserDelegate = delegate

        val b = NSNetServiceBrowser()
        b.delegate = delegate
        b.searchForServicesOfType(SERVICE_TYPE, inDomain = "")
        browser = b
    }

    actual fun join(game: LanGame, onState: (LinkState) -> Unit, onLine: (String) -> Unit) {
        alive = true
        onState(LinkState.CONNECTING)
        scope.launch {
            val fd = resolveAndConnect(game.host, game.port)
            if (fd < 0) {
                if (alive) onState(LinkState.FAILED)
                return@launch
            }
            connFd = fd
            onState(LinkState.CONNECTED)
            pump(fd, onLine, onState)
        }
    }

    private fun resolveAndConnect(host: String, port: Int): Int = memScoped {
        val hints = alloc<addrinfo>()
        hints.ai_family = AF_INET
        hints.ai_socktype = SOCK_STREAM
        val resultPtr = alloc<CPointerVar<addrinfo>>()
        val rc = getaddrinfo(host, port.toString(), hints.ptr, resultPtr.ptr)
        val info = resultPtr.value
        if (rc != 0 || info == null) return -1
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) {
            freeaddrinfo(info)
            return -1
        }
        val connected = connect(fd, info.pointed.ai_addr, info.pointed.ai_addrlen) == 0
        freeaddrinfo(info)
        if (!connected) {
            close(fd)
            return -1
        }
        fd
    }

    // ------------------------------------------------------------------ conversa

    private fun pump(fd: Int, onLine: (String) -> Unit, onState: (LinkState) -> Unit) {
        val pending = StringBuilder()
        val chunk = ByteArray(4096)
        while (alive) {
            val newlineAt = pending.indexOf("\n")
            if (newlineAt >= 0) {
                val line = pending.substring(0, newlineAt).trimEnd('\r')
                pending.deleteRange(0, newlineAt + 1)
                if (line.isNotBlank()) onLine(line)
                continue
            }
            val n = chunk.usePinned { pinned -> recv(fd, pinned.addressOf(0), chunk.size.convert(), 0) }
            if (n <= 0) break
            pending.append(chunk.decodeToString(0, n.toInt()))
        }
        if (alive) onState(LinkState.FAILED)
    }

    actual fun send(line: String) {
        val fd = connFd
        if (fd < 0) return
        val bytes = (line + "\n").encodeToByteArray()
        bytes.usePinned { pinned -> send(fd, pinned.addressOf(0), bytes.size.convert(), 0) }
    }

    actual fun close() {
        alive = false
        runCatching { netService?.stop() }
        runCatching { browser?.stop() }
        netService = null
        browser = null
        browserDelegate = null
        resolveDelegates.clear()
        if (connFd >= 0) runCatching { close(connFd) }
        if (listenFd >= 0) runCatching { close(listenFd) }
        connFd = -1
        listenFd = -1
    }
}
