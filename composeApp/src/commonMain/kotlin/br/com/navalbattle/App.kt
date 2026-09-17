package br.com.navalbattle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import br.com.navalbattle.audio.Music
import br.com.navalbattle.audio.MusicPlayer
import br.com.navalbattle.audio.THEME_PLAYLIST
import br.com.navalbattle.data.CloudApi
import br.com.navalbattle.data.CloudResult
import br.com.navalbattle.data.CommanderHit
import br.com.navalbattle.data.Friendship
import br.com.navalbattle.data.GoogleAuth
import br.com.navalbattle.data.GoogleAuthResult
import br.com.navalbattle.data.LanGame
import br.com.navalbattle.data.LanLink
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.data.OnlineLink
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Protocol
import br.com.navalbattle.data.Session
import br.com.navalbattle.data.checkUpdateAvailable
import br.com.navalbattle.data.openStoreListing
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.FleetCodec
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.isNetwork
import br.com.navalbattle.game.Profile
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.LanScreen
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.HandoffScreen
import br.com.navalbattle.ui.MenuScreen
import br.com.navalbattle.ui.NamesScreen
import br.com.navalbattle.ui.OnlineScreen
import br.com.navalbattle.ui.PlacementScreen
import br.com.navalbattle.ui.ProfileScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.ShipyardScreen
import br.com.navalbattle.ui.StoreScreen
import br.com.navalbattle.ui.SplashScreen
import br.com.navalbattle.ui.WelcomeScreen

enum class Screen { SPLASH, WELCOME, MENU, SHIPYARD, STORE, PROFILE, LAN, ONLINE, NAMES, PLACEMENT, HANDOFF, BATTLE, RESULT }

class AppState(val profile: Profile, private val cloud: CloudApi) {
    var screen by mutableStateOf(Screen.SPLASH)
    var mode by mutableStateOf(GameMode.CLASSIC)
    var match by mutableStateOf<Match?>(null)

    /** O visual em uso — linha de casco e camuflagem — vem do perfil gravado no aparelho. */
    val skin: Skin
        get() = Skin(Paint.of(profile.equipped), FleetLine.of(profile.equippedFleet))

    /** Quem deve pegar o aparelho para posicionar a própria frota. */
    var handoffSide by mutableStateOf(Side.PLAYER)

    /** Trilha ligada. Os efeitos de combate continuam tocando de qualquer jeito. */
    var musicOn by mutableStateOf(profile.musicOn)

    /** Já existe uma versão mais nova publicada na faixa em que o comandante está. */
    var updateAvailable by mutableStateOf(false)

    /**
     * Para onde ir depois da abertura: direto ao deque para quem já tem conta ou já
     * escolheu jogar como convidado; senão, pergunta uma vez na tela de boas-vindas.
     */
    fun afterSplash() {
        screen = if (profile.signedIn || profile.welcomeDone) Screen.MENU else Screen.WELCOME
    }

    fun newMatch(opponent: Opponent) {
        match = Match(mode, opponent)
        // no modo local os dois se identificam antes de posicionar as frotas
        screen = if (opponent == Opponent.LOCAL) Screen.NAMES else Screen.PLACEMENT
    }

    /**
     * Única troca de mãos do jogo: cobre a tela entre o posicionamento de um
     * comandante e o do outro. A batalha em si corre toda na mesma tela.
     */
    fun handoffToPlacement(side: Side) {
        handoffSide = side
        screen = Screen.HANDOFF
    }

    fun quitToMenu() {
        // avisa o outro aparelho antes de fechar, para ele não ficar esperando a
        // vez de alguém que já saiu — quem ficou leva a vitória na hora
        when (match?.opponent) {
            Opponent.LAN -> {
                link.send(Protocol.QUIT)
                // send() escreve numa thread à parte; um respiro curto garante que o
                // aviso saia no fio antes de fecharmos o socket embaixo dele
                val scope = uiScope
                if (scope != null) {
                    scope.launch { delay(200); closeLink() }
                } else {
                    closeLink()
                }
            }

            Opponent.ONLINE -> {
                onlineLink.send(Protocol.QUIT)
                onlineLink.finish("abandoned")
                closeOnline()
            }

            else -> Unit
        }
        match = null
        screen = Screen.MENU
    }

    // ---------------- partida na rede local ----------------

    /** Corrotina da interface, para trazer as mensagens da rede para a thread da tela. */
    var uiScope: CoroutineScope? = null

    var linkState by mutableStateOf(LinkState.IDLE)
        private set
    var foundGames by mutableStateOf<List<LanGame>>(emptyList())
        private set

    /** Revanche em rede: os dois lados precisam pedir antes de a partida recomeçar. */
    var rematchRequestedByMe by mutableStateOf(false)
        private set
    var rematchRequestedByOpponent by mutableStateOf(false)
        private set

    private val link = LanLink()

    private fun onMain(block: () -> Unit) {
        val scope = uiScope
        if (scope == null) block() else scope.launch { block() }
    }

    /**
     * Anuncia a partida no Wi-Fi e espera alguém entrar. Quem hospeda joga primeiro.
     * [gameName] é só o rótulo que aparece na busca do outro aparelho — o nome do
     * comandante em si viaja à parte, no aperto de mão inicial.
     */
    fun hostGame(gameName: String) {
        link.close()
        linkState = LinkState.HOSTING
        val label = gameName.trim().take(24).ifBlank { t(K.LAN_DEFAULT_NAME, profile.displayName) }
        link.host(
            name = label,
            onState = { s -> onMain { onLinkState(s, Side.PLAYER) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun searchGames() {
        link.close()
        foundGames = emptyList()
        linkState = LinkState.SEARCHING
        link.search(
            onFound = { list -> onMain { foundGames = list } },
            onState = { s -> onMain { linkState = s } }
        )
    }

    fun joinGame(game: LanGame) {
        link.join(
            game = game,
            onState = { s -> onMain { onLinkState(s, Side.ENEMY) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun closeLink() {
        link.close()
        linkState = LinkState.IDLE
        foundGames = emptyList()
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
    }

    /** Conectou: abre a partida deste lado e se apresenta ao adversário. */
    private fun onLinkState(state: LinkState, side: Side) {
        linkState = state
        if (state != LinkState.CONNECTED) return
        startLanMatch(side)
    }

    /** Abre uma partida em rede — na primeira ligação e em toda revanche seguinte. */
    private fun startLanMatch(side: Side) {
        val m = Match(mode, Opponent.LAN, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        link.send(Protocol.hello(profile.displayName, mode.name))
        screen = Screen.PLACEMENT
    }

    // ---------------- partida online (internet) ----------------

    var onlineLinkState by mutableStateOf(LinkState.IDLE)
        private set

    /** Código da sala de amigo, para mostrar na tela enquanto espera alguém entrar. */
    var onlineCode by mutableStateOf<String?>(null)
        private set

    var friendResults by mutableStateOf<List<CommanderHit>>(emptyList())
        private set
    var friendships by mutableStateOf<List<Friendship>>(emptyList())
        private set

    private val onlineLink: OnlineLink by lazy { OnlineLink(cloud, uiScope!!) }

    /** Cria uma sala de amigo — quem cria sempre joga primeiro. */
    fun createOnlineRoom() {
        val session = profile.currentSession() ?: return
        onlineLink.close()
        onlineCode = null
        onlineLink.createRoom(
            session = session,
            mode = mode.name,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onCode = { code -> onMain { onlineCode = code } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /** Procura uma partida rápida aberta; se não achar, fica esperando a própria. */
    fun startQuickMatchOnline() {
        val session = profile.currentSession() ?: return
        onlineLink.close()
        onlineCode = null
        onlineLink.quickMatch(
            session = session,
            mode = mode.name,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /** Entra numa sala de amigo pelo código que ele compartilhou por fora do jogo. */
    fun joinOnlineByCode(code: String) {
        val session = profile.currentSession() ?: return
        onlineLink.close()
        onlineLink.joinByCode(
            session = session,
            code = code,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun closeOnline() {
        onlineLink.close()
        onlineLinkState = LinkState.IDLE
        onlineCode = null
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
    }

    private fun onOnlineState(state: LinkState, side: Side) {
        onlineLinkState = state
        if (state != LinkState.CONNECTED) return
        startOnlineMatch(side)
    }

    private fun startOnlineMatch(side: Side) {
        val m = Match(mode, Opponent.ONLINE, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        onlineLink.send(Protocol.hello(profile.displayName, mode.name))
        screen = Screen.PLACEMENT
    }

    /** Procura comandantes pelo início do nome, para mandar pedido de amizade. */
    suspend fun searchCommander(query: String) {
        if (query.isBlank()) {
            friendResults = emptyList()
            return
        }
        val session = profile.currentSession() ?: return
        val r = cloud.searchCommander(session, query)
        friendResults = (r as? CloudResult.Ok)?.value.orEmpty()
    }

    suspend fun sendFriendRequest(hit: CommanderHit) {
        val session = profile.currentSession() ?: return
        cloud.sendFriendRequest(session, hit.id, profile.displayName, hit.username)
        refreshFriendships()
    }

    suspend fun respondFriendRequest(friendship: Friendship, accept: Boolean) {
        val session = profile.currentSession() ?: return
        cloud.respondFriendRequest(session, friendship.id, accept)
        refreshFriendships()
    }

    suspend fun removeFriendship(friendship: Friendship) {
        val session = profile.currentSession() ?: return
        cloud.removeFriendship(session, friendship.id)
        refreshFriendships()
    }

    suspend fun refreshFriendships() {
        val session = profile.currentSession() ?: return
        val r = cloud.listFriendships(session)
        friendships = (r as? CloudResult.Ok)?.value.orEmpty()
    }

    // ---------------- ações compartilhadas entre LAN e online ----------------

    private fun sendToOpponent(line: String) {
        when (match?.opponent) {
            Opponent.LAN -> link.send(line)
            Opponent.ONLINE -> onlineLink.send(line)
            else -> Unit
        }
    }

    /** Uma linha chegou do outro aparelho — mesmo protocolo para LAN e online. */
    private fun onLine(line: String) {
        val m = match ?: return
        val parts = Protocol.parts(line)
        when (parts.firstOrNull()) {
            Protocol.HELLO -> parts.getOrNull(1)?.let { m.setName(m.mySide.other(), it) }

            Protocol.FLEET -> parts.getOrNull(1)
                ?.let { m.applyRemoteFleet(FleetCodec.decode(it)) }

            Protocol.ABILITY -> parts.getOrNull(1)
                ?.let { code -> Ability.entries.firstOrNull { it.code == code } }
                ?.let { m.selectAbility(it, ignoreCooldown = parts.getOrNull(2) == "1") }

            Protocol.ACT -> {
                val x = parts.getOrNull(1)?.toIntOrNull() ?: return
                val y = parts.getOrNull(2)?.toIntOrNull() ?: return
                m.act(Coord(x, y))
            }

            Protocol.TAUNT -> parts.getOrNull(1)?.let { m.sendTaunt(m.mySide.other(), it) }

            Protocol.REMATCH -> {
                rematchRequestedByOpponent = true
                maybeStartRematch()
            }

            Protocol.QUIT -> {
                m.abandon(m.mySide)
                if (m.opponent == Opponent.LAN) closeLink() else closeOnline()
            }
        }
    }

    /** Dispara e conta ao adversário — os dois aparelhos resolvem o mesmo tiro. */
    fun fireShared(coord: Coord) {
        val m = match ?: return
        if (m.opponent.isNetwork()) sendToOpponent(Protocol.act(coord.x, coord.y))
        m.act(coord)
    }

    fun useAbilityShared(ability: Ability, ignoreCooldown: Boolean = false) {
        val m = match ?: return
        if (m.opponent.isNetwork()) sendToOpponent(Protocol.ability(ability.code, ignoreCooldown))
        m.selectAbility(ability, ignoreCooldown)
    }

    /** Manda a própria frota assim que ela é confirmada. */
    fun sendFleet() {
        val m = match ?: return
        if (!m.opponent.isNetwork()) return
        sendToOpponent(Protocol.fleet(FleetCodec.encode(m.board(m.mySide).ships)))
    }

    /** Emoji ou grito de guerra: decoração pura, não passa pela lógica da partida. */
    fun sendTaunt(code: String) {
        val m = match ?: return
        if (!m.opponent.isNetwork()) return
        sendToOpponent(Protocol.taunt(code))
        m.sendTaunt(m.mySide, code)
    }

    /**
     * Pede revanche na mesma ligação: os dois lados precisam pedir para a partida
     * recomeçar, senão um dos dois ficaria esperando sem saber que o outro já saiu
     * da tela de resultado.
     */
    fun requestRematch() {
        val m = match ?: return
        val connected = when (m.opponent) {
            Opponent.LAN -> linkState == LinkState.CONNECTED
            Opponent.ONLINE -> onlineLinkState == LinkState.CONNECTED
            else -> false
        }
        if (!connected) return
        rematchRequestedByMe = true
        sendToOpponent(Protocol.REMATCH)
        maybeStartRematch()
    }

    private fun maybeStartRematch() {
        if (!rematchRequestedByMe || !rematchRequestedByOpponent) return
        val old = match ?: return
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
        when (old.opponent) {
            Opponent.LAN -> startLanMatch(old.mySide)
            Opponent.ONLINE -> startOnlineMatch(old.mySide)
            else -> Unit
        }
    }

    // ---------------- conta e sincronização ----------------

    /** Cria a conta e já sobe a carreira que existir neste aparelho. */
    suspend fun createAccount(email: String, password: String, username: String): Pair<Boolean, String> =
        when (val r = cloud.signUp(email, password, username)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                profile.rename(username)
                cloud.saveProfile(r.value, profile.snapshot())
                true to t(K.AUTH_CREATED)
            }

            is CloudResult.Fail -> false to r.message
        }

    /**
     * Entra na conta. Se a nuvem tiver carreira mais avançada, ela vence; se a deste
     * aparelho estiver na frente, é ela que sobe. Nunca se perde o maior progresso.
     */
    suspend fun signIn(email: String, password: String): Pair<Boolean, String> =
        when (val r = cloud.signIn(email, password)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                true to merged
            }

            is CloudResult.Fail -> false to r.message
        }

    private val googleAuth = GoogleAuth()

    /**
     * Entra com a conta Google: pede a credencial ao sistema e troca o token de
     * identidade por uma sessão no Supabase. Mesmo gatilho de fusão de carreira do
     * login por e-mail — se o comandante já tinha conta pelo e-mail do Google, cai
     * na mesma carreira (ver `supabase/schema.sql`, `on_auth_user_created`).
     */
    suspend fun signInWithGoogle(): Pair<Boolean, String> = when (val g = googleAuth.signIn()) {
        is GoogleAuthResult.Cancelled -> false to ""
        is GoogleAuthResult.Fail -> false to g.message
        is GoogleAuthResult.Ok -> when (val r = cloud.signInWithGoogle(g.idToken)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                true to merged
            }

            is CloudResult.Fail -> false to r.message
        }
    }

    /** Sincroniza sob demanda, pelo botão da tela de conta. */
    suspend fun syncNow(): Boolean {
        if (!profile.signedIn) return false
        return mergeWithCloud() != null
    }

    /** Sobe a carreira em silêncio depois de uma partida ou de uma compra. */
    suspend fun pushQuietly() {
        if (!profile.signedIn) return
        authed { session -> cloud.saveProfile(session, profile.snapshot()) }
    }

    /**
     * Na abertura do app, com conta conectada: renova a sessão e traz o que estiver
     * na nuvem. É o que garante token válido antes da primeira sincronização do dia.
     */
    suspend fun resumeSession() {
        if (!profile.signedIn) return
        renew()
        mergeWithCloud()
    }

    /**
     * Roda [block] com a sessão atual e, se ela tiver vencido, renova o token com o
     * refresh guardado e repete uma vez. O comandante não vê nada disso.
     */
    private suspend fun <T> authed(block: suspend (Session) -> CloudResult<T>): CloudResult<T> {
        val session = profile.currentSession()
            ?: return CloudResult.Fail(t(K.AUTH_SIGN_IN_TO_SYNC))
        val first = block(session)
        if (first !is CloudResult.Fail || !first.expired) return first

        val renewed = renew() ?: return CloudResult.Fail(t(K.AUTH_EXPIRED))
        return block(renewed)
    }

    /** Troca o refresh token guardado por uma sessão nova. */
    private suspend fun renew(): Session? {
        val current = profile.currentSession() ?: return null
        return when (val r = cloud.refresh(current.refreshToken)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                r.value
            }
            // refresh recusado: a conta continua no aparelho, só a sessão caiu
            is CloudResult.Fail -> null
        }
    }

    /**
     * Troca a senha de quem já está logado. Reautentica com a senha atual antes —
     * é o que impede alguém que ache o celular destravado de trocar a senha sem
     * saber a de verdade — e só então chama a troca com o token confirmado.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Pair<Boolean, String> {
        val email = profile.accountEmail
        if (email.isBlank()) return false to t(K.AUTH_SIGN_IN_TO_SYNC)

        return when (val reauth = cloud.signIn(email, currentPassword)) {
            is CloudResult.Fail -> false to reauth.message
            is CloudResult.Ok -> {
                profile.rememberSession(reauth.value)
                when (val r = cloud.updatePassword(reauth.value, newPassword)) {
                    is CloudResult.Ok -> true to t(K.AUTH_PASSWORD_CHANGED)
                    is CloudResult.Fail -> false to r.message
                }
            }
        }
    }

    /** "Esqueci minha senha": manda o link de recuperação para o e-mail informado. */
    suspend fun forgotPassword(email: String): Pair<Boolean, String> =
        when (val r = cloud.sendPasswordReset(email)) {
            is CloudResult.Ok -> true to t(K.AUTH_RESET_SENT)
            is CloudResult.Fail -> false to r.message
        }

    /** Devolve a mensagem da fusão, ou nulo quando nem isso foi possível. */
    private suspend fun mergeWithCloud(): String? =
        when (val remote = authed { session -> cloud.loadProfile(session) }) {
            is CloudResult.Ok -> {
                val cloudProfile = remote.value
                if (cloudProfile != null && cloudProfile.xp > profile.xp) {
                    profile.adopt(cloudProfile)
                    t(K.AUTH_RESTORED)
                } else {
                    authed { session -> cloud.saveProfile(session, profile.snapshot()) }
                    t(K.AUTH_UPLOADED)
                }
            }

            is CloudResult.Fail -> null
        }
}

@Composable
fun App() {
    val profile = remember { Profile(Prefs()) }
    val cloud = remember { CloudApi() }
    val state = remember { AppState(profile, cloud) }
    // idioma escolhido pelo comandante, aplicado antes de a primeira tela desenhar
    remember(profile.langCode) { I18n.lang = Lang.of(profile.langCode); profile.langCode }
    val scope = rememberCoroutineScope()
    state.uiScope = scope
    val music = remember { MusicPlayer() }
    // playlist do deque: uma faixa por abertura do app, em rodízio — fixada uma vez
    // por sessão (remember), pra não trocar de música sozinha a cada recomposição
    val theme = remember { THEME_PLAYLIST[profile.rollThemeTrack(THEME_PLAYLIST.size)] }

    // a trilha acompanha a tela: tema no deque, faixa de combate na batalha
    LaunchedEffect(state.screen, state.musicOn) {
        profile.setMusic(state.musicOn)
        if (!state.musicOn) {
            music.stop()
        } else {
            music.play(if (state.screen == Screen.BATTLE) Music.BATTLE else theme)
        }
    }
    DisposableEffect(Unit) { onDispose { music.release() } }

    // com conta conectada, a abertura já renova a sessão e busca o que há na nuvem
    LaunchedEffect(Unit) { state.resumeSession() }

    // avisa se já existe uma versão mais nova publicada, sem precisar de servidor de push
    LaunchedEffect(Unit) { state.updateAvailable = checkUpdateAvailable() }

    NavalTheme {
        Box(Modifier.fillMaxSize().background(Naval.bg)) {
            when (state.screen) {
                Screen.SPLASH -> SplashScreen(state)
                Screen.WELCOME -> WelcomeScreen(state)
                Screen.MENU -> MenuScreen(state)
                Screen.SHIPYARD -> ShipyardScreen(state)
                Screen.STORE -> StoreScreen(state)
                Screen.PROFILE -> ProfileScreen(state)
                Screen.LAN -> LanScreen(state)
                Screen.ONLINE -> OnlineScreen(state)
                Screen.NAMES -> state.match?.let { NamesScreen(state, it) }
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.HANDOFF -> state.match?.let { HandoffScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
            }
        }
    }
}
