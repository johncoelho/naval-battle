package br.com.navalbattle.data

import br.com.navalbattle.game.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Partida pela internet: mesma ideia da [LanLink] (host/join, mensagens de texto do
 * [Protocol]), só que o transporte é a REST do Supabase em vez de um socket na rede
 * local — cada lado grava a própria jogada como uma linha em `online_messages` e
 * consulta por linhas novas a cada intervalo curto (ver `supabase/online.sql`).
 *
 * Puro `commonMain`: não precisa de `expect`/`actual` por plataforma porque o
 * [CloudApi] já resolve a chamada HTTP nos dois lados — só o polling em si (laço com
 * `delay`) é código comum de corrotina.
 *
 * [onState] carrega também o lado que o comandante deste aparelho acabou assumindo —
 * na sala de amigo e na hospedagem de partida rápida ele é sempre [Side.PLAYER]; ao
 * entrar (por código ou emparelhado numa partida rápida alheia) é sempre
 * [Side.ENEMY]. Esse valor só importa quando o estado é [LinkState.CONNECTED].
 */
class OnlineLink(private val cloud: CloudApi, private val scope: CoroutineScope) {
    private var pollJob: Job? = null
    private var session: Session? = null

    companion object {
        private const val QUICK_MATCH_SEARCH_ATTEMPTS = 5
        private const val QUICK_MATCH_SEARCH_INTERVAL_MS = 500L
    }

    var matchId: String? = null
        private set

    /** Código da sala, quando for uma sala de amigo (nulo em partida rápida). */
    var inviteCode: String? = null
        private set

    /** Se a sala conectada é ranqueada — soma pontos no placar ao fim da partida. */
    var ranked: Boolean = false
        private set

    /** Id e nome de quem está do outro lado da sala — nulos até a conexão fechar. */
    var opponentId: String? = null
        private set
    var opponentName: String? = null
        private set

    fun close() {
        pollJob?.cancel()
        pollJob = null
        matchId = null
        inviteCode = null
        ranked = false
        opponentId = null
        opponentName = null
        session = null
    }

    /** Descobre quem é o adversário comparando os dois lados da sala com o próprio id. */
    private fun captureOpponent(match: OnlineMatch, session: Session) {
        if (match.hostId == session.userId) {
            opponentId = match.guestId
            opponentName = match.guestName
        } else {
            opponentId = match.hostId
            opponentName = match.hostName
        }
    }

    /**
     * Cria uma sala de amigo: gera um código e espera alguém entrar. Com
     * [invitedId], a sala é mirada num amigo específico — só ele consegue entrar
     * (trava no [join_online_match] do banco), e é ele quem recebe o banner de
     * convite fora da tela Online; sem [invitedId], o código sozinho já basta,
     * igual antes.
     */
    fun createRoom(
        session: Session,
        mode: String,
        invitedId: String? = null,
        onState: (LinkState, Side) -> Unit,
        onCode: (String) -> Unit,
        onLine: (String) -> Unit
    ) {
        this.session = session
        pollJob?.cancel()
        onState(LinkState.HOSTING, Side.PLAYER)
        pollJob = scope.launch {
            val code = randomCode()
            val result = cloud.createOnlineMatch(
                session, mode, quick = false, inviteCode = code, hostName = session.username, invitedId = invitedId
            )
            val match = (result as? CloudResult.Ok)?.value
            if (match == null) {
                onState(LinkState.FAILED, Side.PLAYER)
                return@launch
            }
            matchId = match.id
            inviteCode = match.inviteCode ?: code
            onCode(inviteCode!!)
            waitForGuest(session, match.id, onState, onLine)
        }
    }

    /** Aceita um convite mirado direto pelo id da sala — sem precisar digitar código. */
    fun acceptInvite(session: Session, matchId: String, onState: (LinkState, Side) -> Unit, onLine: (String) -> Unit) {
        this.session = session
        pollJob?.cancel()
        onState(LinkState.CONNECTING, Side.ENEMY)
        pollJob = scope.launch {
            val joined = (cloud.joinOnlineMatch(session, matchId, session.username) as? CloudResult.Ok)?.value
            if (joined == null) {
                onState(LinkState.FAILED, Side.ENEMY)
                return@launch
            }
            this@OnlineLink.matchId = joined.id
            this@OnlineLink.ranked = joined.ranked
            captureOpponent(joined, session)
            onState(LinkState.CONNECTED, Side.ENEMY)
            startMessagePolling(session, joined.id, onLine)
        }
    }

    /**
     * Procura uma sala de partida rápida aberta; se não achar, hospeda a própria.
     * A busca tenta algumas vezes antes de desistir e virar anfitrião — uma tentativa
     * só perdia pra corrida quando dois comandantes clicavam quase juntos (nenhum via
     * a sala do outro a tempo, os dois hospedavam e ficavam esperando pra sempre).
     * [ranked] só pareia com outra sala ranqueada — a ladder de verdade é sempre
     * matchmaking anônimo, nunca desafio combinado com amigo (mesmo padrão de
     * Clash Royale e afins: convite de amigo é sempre casual).
     */
    fun quickMatch(
        session: Session,
        mode: String,
        ranked: Boolean,
        onState: (LinkState, Side) -> Unit,
        onLine: (String) -> Unit
    ) {
        this.session = session
        pollJob?.cancel()
        this.ranked = ranked
        onState(LinkState.SEARCHING, Side.PLAYER)
        pollJob = scope.launch {
            repeat(QUICK_MATCH_SEARCH_ATTEMPTS) { attempt ->
                val found = (cloud.findQuickMatch(session, mode, ranked) as? CloudResult.Ok)?.value
                if (found != null) {
                    val joined = (cloud.joinOnlineMatch(session, found.id, session.username) as? CloudResult.Ok)?.value
                    if (joined != null) {
                        matchId = joined.id
                        this@OnlineLink.ranked = joined.ranked
                        captureOpponent(joined, session)
                        onState(LinkState.CONNECTED, Side.ENEMY)
                        startMessagePolling(session, joined.id, onLine)
                        return@launch
                    }
                    // perdeu a corrida para outro jogador que entrou primeiro nessa sala —
                    // tenta achar outra em vez de já desistir e hospedar a própria
                }
                if (attempt < QUICK_MATCH_SEARCH_ATTEMPTS - 1) delay(QUICK_MATCH_SEARCH_INTERVAL_MS)
            }
            val created = (cloud.createOnlineMatch(
                session, mode, quick = true, inviteCode = null, hostName = session.username, ranked = ranked
            ) as? CloudResult.Ok)?.value
            if (created == null) {
                onState(LinkState.FAILED, Side.PLAYER)
                return@launch
            }
            matchId = created.id
            waitForGuest(session, created.id, onState, onLine)
        }
    }

    /** Entra numa sala de amigo a partir do código compartilhado por fora do jogo. */
    fun joinByCode(session: Session, code: String, onState: (LinkState, Side) -> Unit, onLine: (String) -> Unit) {
        this.session = session
        pollJob?.cancel()
        onState(LinkState.CONNECTING, Side.ENEMY)
        pollJob = scope.launch {
            val found = (cloud.findMatchByCode(session, code.trim().uppercase()) as? CloudResult.Ok)?.value
            if (found == null) {
                onState(LinkState.FAILED, Side.ENEMY)
                return@launch
            }
            val joined = (cloud.joinOnlineMatch(session, found.id, session.username) as? CloudResult.Ok)?.value
            if (joined == null) {
                onState(LinkState.FAILED, Side.ENEMY)
                return@launch
            }
            matchId = joined.id
            this@OnlineLink.ranked = joined.ranked
            captureOpponent(joined, session)
            onState(LinkState.CONNECTED, Side.ENEMY)
            startMessagePolling(session, joined.id, onLine)
        }
    }

    /** Hospedando: consulta até o convidado entrar, depois passa a trocar jogadas. */
    private suspend fun waitForGuest(
        session: Session,
        matchId: String,
        onState: (LinkState, Side) -> Unit,
        onLine: (String) -> Unit
    ) {
        while (true) {
            delay(1500)
            val match = (cloud.getOnlineMatch(session, matchId) as? CloudResult.Ok)?.value ?: continue
            if (match.guestId != null) {
                captureOpponent(match, session)
                onState(LinkState.CONNECTED, Side.PLAYER)
                startMessagePolling(session, matchId, onLine)
                return
            }
        }
    }

    private fun startMessagePolling(session: Session, matchId: String, onLine: (String) -> Unit) {
        pollJob = scope.launch {
            var lastId = 0L
            while (isActive) {
                delay(1200)
                val messages = (cloud.pollOnlineMessages(session, matchId, lastId) as? CloudResult.Ok)?.value ?: continue
                for (message in messages) {
                    lastId = message.id
                    if (message.senderId != session.userId) onLine(message.body)
                }
            }
        }
    }

    fun send(line: String) {
        val s = session ?: return
        val id = matchId ?: return
        scope.launch { cloud.sendOnlineMessage(s, id, line) }
    }

    /** Marca a sala como encerrada — chamado ao sair ou terminar a partida. */
    fun finish(status: String) {
        val s = session ?: return
        val id = matchId ?: return
        scope.launch { cloud.closeOnlineMatch(s, id, status) }
    }

    private fun randomCode(): String {
        // sem O/0/I/1: parecidos demais para ditar por mensagem sem confundir
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { alphabet.random() }.joinToString("")
    }
}
