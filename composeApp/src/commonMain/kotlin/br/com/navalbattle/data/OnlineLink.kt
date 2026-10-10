package br.com.navalbattle.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 *
 * Estabilidade (0.16.0): a sessão é renovada sozinha quando o token vence no meio da
 * partida ([renewSession]) — antes tudo passava a falhar calado depois de 1h e o
 * jogo congelava; cada jogada fica numa fila e é reenviada até o servidor confirmar
 * (numerada, sem duplicar); e os dois lados batem o ponto de presença a cada poucos
 * segundos, o que alimenta [selfOffline] e [opponentAwaySeconds] para a tela avisar.
 */
class OnlineLink(
    private val cloud: CloudApi,
    private val scope: CoroutineScope,
    private val renewSession: suspend () -> Session?
) {
    private var pollJob: Job? = null
    private var heartbeatJob: Job? = null
    private var sendJob: Job? = null
    private var session: Session? = null

    /** Jogadas ainda não confirmadas pelo servidor, na ordem em que foram feitas. */
    private val outbox = ArrayDeque<Pair<Int, String>>()
    private var nextSeq = 0

    /** Última resposta boa do servidor (envio, consulta ou presença). */
    private var lastOkMillis = 0L

    /** Instante estimado em que o adversário bateu o ponto pela última vez. */
    private var opponentSeenAtMillis: Long? = null

    /** Este aparelho está sem falar com o servidor há alguns segundos. */
    var selfOffline by mutableStateOf(false)
        private set

    /**
     * Há quantos segundos o adversário não aparece — nulo enquanto não houver
     * presença dele (app antigo) ou enquanto este aparelho estiver sem conexão
     * (aí não dá para saber quem caiu). Atualizado a cada segundo.
     */
    var opponentAwaySeconds by mutableStateOf<Int?>(null)
        private set

    companion object {
        private const val QUICK_MATCH_SEARCH_ATTEMPTS = 5
        private const val QUICK_MATCH_SEARCH_INTERVAL_MS = 500L
        private const val HEARTBEAT_INTERVAL_MS = 3000L
        private const val OFFLINE_AFTER_MS = 6000L
        private const val RESEND_MAX_DELAY_MS = 4000L
    }

    /**
     * Roda [block] com a sessão da sala e, se o token tiver vencido, renova e repete
     * uma vez — a partida pode durar mais que a hora de vida do token.
     */
    private suspend fun <T> withSession(block: suspend (Session) -> CloudResult<T>): CloudResult<T> {
        val s = session ?: return CloudResult.Fail("sem sessão")
        val first = block(s)
        if (first !is CloudResult.Fail || !first.expired) {
            if (first is CloudResult.Ok) lastOkMillis = nowMillis()
            return first
        }
        val renewed = renewSession() ?: return first
        session = renewed
        val second = block(renewed)
        if (second is CloudResult.Ok) lastOkMillis = nowMillis()
        return second
    }

    var matchId: String? = null
        private set

    /** Código da sala, quando for uma sala de amigo (nulo em partida rápida). */
    var inviteCode: String? = null
        private set

    /** Se a sala conectada é ranqueada — soma pontos no placar ao fim da partida. */
    var ranked: Boolean = false
        private set

    /** O amigo convidado recusou a sala (ela virou `abandoned` antes de alguém entrar). */
    var inviteDeclined: Boolean = false
        private set

    /** Id e nome de quem está do outro lado da sala — nulos até a conexão fechar. */
    var opponentId: String? = null
        private set
    var opponentName: String? = null
        private set

    fun close() {
        // o que ficou na fila (o QUIT de quem está saindo, quase sempre) ainda vai,
        // uma tentativa por linha, com a sala e a sessão de agora
        val s = session
        val id = matchId
        val pending = outbox.toList()
        if (s != null && id != null && pending.isNotEmpty()) {
            scope.launch {
                var current: Session = s
                for ((seq, line) in pending) {
                    val r = cloud.sendOnlineMessage(current, id, line, seq)
                    if (r is CloudResult.Fail && r.expired) {
                        current = renewSession() ?: return@launch
                        cloud.sendOnlineMessage(current, id, line, seq)
                    }
                }
            }
        }
        pollJob?.cancel()
        pollJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        sendJob?.cancel()
        sendJob = null
        outbox.clear()
        nextSeq = 0
        opponentSeenAtMillis = null
        selfOffline = false
        opponentAwaySeconds = null
        matchId = null
        inviteCode = null
        ranked = false
        opponentId = null
        opponentName = null
        roomMode = null
        session = null
    }

    /**
     * Modo da sala conectada ("CLASSIC"/"TACTICAL") — quem entra por convite,
     * código ou balão de partida rápida joga no modo de quem abriu a sala, não no
     * que estava escolhido no próprio menu.
     */
    var roomMode: String? = null
        private set

    /** Descobre quem é o adversário comparando os dois lados da sala com o próprio id. */
    private fun captureOpponent(match: OnlineMatch, session: Session) {
        roomMode = match.mode
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
        ranked: Boolean = false,
        onState: (LinkState, Side) -> Unit,
        onCode: (String) -> Unit,
        onLine: (String) -> Unit
    ) {
        this.session = session
        this.ranked = ranked
        inviteDeclined = false
        pollJob?.cancel()
        onState(LinkState.HOSTING, Side.PLAYER)
        pollJob = scope.launch {
            val code = randomCode()
            val result = cloud.createOnlineMatch(
                session, mode, quick = false, inviteCode = code, hostName = session.username,
                invitedId = invitedId, ranked = ranked
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

    /**
     * Revanche online: quem abriu a sala atual cria uma sala nova (de código, sem
     * convite mirado — assim não dispara push nem balão de convite), avisa o id pela
     * sala antiga com [Protocol.ROOM], fecha a antiga e espera o adversário entrar.
     * Sala nova = milha cobrada de novo e ranqueada pontuada de novo (o servidor só
     * aceita um relato por sala).
     */
    fun openRematchRoom(mode: String, onState: (LinkState, Side) -> Unit, onLine: (String) -> Unit) {
        val s = session ?: return
        val oldId = matchId ?: return
        val wasRanked = ranked
        stopRoomJobs()
        onState(LinkState.HOSTING, Side.PLAYER)
        pollJob = scope.launch {
            val code = randomCode()
            val created = (withSession {
                cloud.createOnlineMatch(it, mode, quick = false, inviteCode = code, hostName = it.username, ranked = wasRanked)
            } as? CloudResult.Ok)?.value
            if (created == null) {
                onState(LinkState.FAILED, Side.PLAYER)
                return@launch
            }
            // direto, fora da fila: a fila é da sala antiga e vai ser descartada
            var tries = 0
            while (tries < 5 && withSession { cloud.sendOnlineMessage(it, oldId, Protocol.room(created.id), nextSeq++) } !is CloudResult.Ok) {
                tries++
                delay(800)
            }
            withSession { cloud.closeOnlineMatch(it, oldId, "finished") }
            matchId = created.id
            inviteCode = created.inviteCode ?: code
            waitForGuest(s, created.id, onState, onLine)
        }
    }

    /** Para polling, batimento e fila da sala atual antes de trocar de sala. */
    private fun stopRoomJobs() {
        pollJob?.cancel()
        heartbeatJob?.cancel()
        sendJob?.cancel()
        outbox.clear()
    }

    /** Aceita um convite mirado direto pelo id da sala — sem precisar digitar código. */
    fun acceptInvite(session: Session, matchId: String, onState: (LinkState, Side) -> Unit, onLine: (String) -> Unit) {
        this.session = session
        stopRoomJobs()
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
     * Aceita, de qualquer tela, uma sala de partida rápida alheia que apareceu no
     * balão de [CloudApi.findQuickOffer] — mesmo caminho do convidado em
     * [quickMatch] (entrada atômica, captura do adversário, polling de jogadas),
     * só que sem procurar: a sala já veio escolhida. Do outro lado nada muda — o
     * anfitrião continua no [waitForGuest] dele e conecta ao ver o guest_id
     * preenchido. Se outra pessoa entrou primeiro, [onTaken] avisa e o link volta
     * a ficar parado, sem virar anfitrião de nada.
     */
    fun joinQuickOffer(
        session: Session,
        room: OnlineMatch,
        onState: (LinkState, Side) -> Unit,
        onTaken: () -> Unit,
        onLine: (String) -> Unit
    ) {
        this.session = session
        pollJob?.cancel()
        onState(LinkState.CONNECTING, Side.ENEMY)
        pollJob = scope.launch {
            val joined = (cloud.joinOnlineMatch(session, room.id, session.username) as? CloudResult.Ok)?.value
            if (joined == null) {
                onState(LinkState.IDLE, Side.ENEMY)
                onTaken()
                return@launch
            }
            matchId = joined.id
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
            // o convidado recusou (decline_online_invite fecha a sala): para de esperar
            if (match.status == "abandoned") {
                inviteDeclined = match.invitedId != null
                onState(LinkState.FAILED, Side.PLAYER)
                return
            }
        }
    }

    private fun startMessagePolling(session: Session, matchId: String, onLine: (String) -> Unit) {
        lastOkMillis = nowMillis()
        pollJob = scope.launch {
            // lastId só avança com o que chegou: depois de uma queda, a primeira
            // consulta que der certo traz tudo o que o adversário jogou nesse meio-tempo
            var lastId = 0L
            while (isActive) {
                delay(1200)
                val messages = (withSession { cloud.pollOnlineMessages(it, matchId, lastId) } as? CloudResult.Ok)
                    ?.value ?: continue
                for (message in messages) {
                    lastId = message.id
                    if (message.senderId != session.userId) onLine(message.body)
                }
            }
        }
        startHeartbeat(matchId)
        flushOutbox()
    }

    private fun startHeartbeat(matchId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var sinceBeat = HEARTBEAT_INTERVAL_MS
            while (isActive) {
                if (sinceBeat >= HEARTBEAT_INTERVAL_MS) {
                    sinceBeat = 0L
                    val beat = withSession { cloud.onlineHeartbeat(it, matchId) }
                    if (beat is CloudResult.Ok) {
                        opponentSeenAtMillis = beat.value?.let { secs -> nowMillis() - secs * 1000L }
                    }
                }
                val now = nowMillis()
                selfOffline = now - lastOkMillis > OFFLINE_AFTER_MS
                // sem conexão aqui não dá para culpar o outro lado
                opponentAwaySeconds = if (selfOffline) null
                else opponentSeenAtMillis?.let { ((now - it) / 1000L).toInt() }
                delay(1000)
                sinceBeat += 1000L
            }
        }
    }

    /**
     * Põe a jogada na fila e garante que alguém está esvaziando ela. A fila só anda
     * quando o servidor confirma: com a internet oscilando, a jogada espera e vai
     * assim que der, na ordem certa — antes ela se perdia e os dois aparelhos
     * passavam a discordar da partida sem aviso nenhum.
     */
    fun send(line: String) {
        if (session == null || matchId == null) return
        outbox.addLast(nextSeq++ to line)
        flushOutbox()
    }

    private fun flushOutbox() {
        if (sendJob?.isActive == true) return
        val id = matchId ?: return
        sendJob = scope.launch {
            var wait = 500L
            while (isActive && outbox.isNotEmpty()) {
                val (seq, line) = outbox.first()
                val sent = withSession { cloud.sendOnlineMessage(it, id, line, seq) }
                if (sent is CloudResult.Ok) {
                    outbox.removeFirst()
                    wait = 500L
                } else {
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(RESEND_MAX_DELAY_MS)
                }
            }
        }
    }

    /** Marca a sala como encerrada — chamado ao sair ou terminar a partida. */
    fun finish(status: String) {
        // sessão e sala capturadas agora: quem chama costuma fechar o link logo depois
        val s = session ?: return
        val id = matchId ?: return
        scope.launch {
            val r = cloud.closeOnlineMatch(s, id, status)
            if (r is CloudResult.Fail && r.expired) renewSession()?.let { cloud.closeOnlineMatch(it, id, status) }
        }
    }

    private fun randomCode(): String {
        // sem O/0/I/1: parecidos demais para ditar por mensagem sem confundir
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..5).map { alphabet.random() }.joinToString("")
    }
}
