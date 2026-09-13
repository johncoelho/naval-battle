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
import kotlinx.coroutines.launch
import br.com.navalbattle.audio.Music
import br.com.navalbattle.audio.MusicPlayer
import br.com.navalbattle.data.CloudApi
import br.com.navalbattle.data.CloudResult
import br.com.navalbattle.data.LanGame
import br.com.navalbattle.data.LanLink
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Protocol
import br.com.navalbattle.data.Session
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
import br.com.navalbattle.game.Profile
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.AuthScreen
import br.com.navalbattle.ui.LanScreen
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.HandoffScreen
import br.com.navalbattle.ui.MenuScreen
import br.com.navalbattle.ui.NamesScreen
import br.com.navalbattle.ui.PlacementScreen
import br.com.navalbattle.ui.ProfileScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.ShipyardScreen
import br.com.navalbattle.ui.StoreScreen
import br.com.navalbattle.ui.SplashScreen

enum class Screen { SPLASH, MENU, SHIPYARD, STORE, PROFILE, AUTH, LAN, NAMES, PLACEMENT, HANDOFF, BATTLE, RESULT }

class AppState(val profile: Profile, private val cloud: CloudApi) {
    var screen by mutableStateOf(Screen.SPLASH)
    var mode by mutableStateOf(GameMode.TACTICAL)
    var match by mutableStateOf<Match?>(null)

    /** O visual em uso — linha de casco e camuflagem — vem do perfil gravado no aparelho. */
    val skin: Skin
        get() = Skin(Paint.of(profile.equipped), FleetLine.of(profile.equippedFleet))

    /** Quem deve pegar o aparelho para posicionar a própria frota. */
    var handoffSide by mutableStateOf(Side.PLAYER)

    /** Trilha ligada. Os efeitos de combate continuam tocando de qualquer jeito. */
    var musicOn by mutableStateOf(profile.musicOn)

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
        if (match?.opponent == Opponent.LAN) closeLink()
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

    private val link = LanLink()

    private fun onMain(block: () -> Unit) {
        val scope = uiScope
        if (scope == null) block() else scope.launch { block() }
    }

    /** Anuncia a partida no Wi-Fi e espera alguém entrar. Quem hospeda joga primeiro. */
    fun hostGame() {
        link.close()
        linkState = LinkState.HOSTING
        link.host(
            name = profile.displayName,
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
    }

    /** Conectou: abre a partida deste lado e se apresenta ao adversário. */
    private fun onLinkState(state: LinkState, side: Side) {
        linkState = state
        if (state != LinkState.CONNECTED) return
        val m = Match(mode, Opponent.LAN, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        link.send(Protocol.hello(profile.displayName, mode.name))
        screen = Screen.PLACEMENT
    }

    /** Uma linha chegou do outro aparelho. */
    private fun onLine(line: String) {
        val m = match ?: return
        val parts = Protocol.parts(line)
        when (parts.firstOrNull()) {
            Protocol.HELLO -> parts.getOrNull(1)?.let { m.setName(m.mySide.other(), it) }

            Protocol.FLEET -> parts.getOrNull(1)
                ?.let { m.applyRemoteFleet(FleetCodec.decode(it)) }

            Protocol.ABILITY -> parts.getOrNull(1)
                ?.let { code -> Ability.entries.firstOrNull { it.code == code } }
                ?.let { m.selectAbility(it) }

            Protocol.ACT -> {
                val x = parts.getOrNull(1)?.toIntOrNull() ?: return
                val y = parts.getOrNull(2)?.toIntOrNull() ?: return
                m.act(Coord(x, y))
            }

            Protocol.QUIT -> {
                m.abandon(m.mySide)
                closeLink()
            }
        }
    }

    /** Dispara e conta ao adversário — os dois aparelhos resolvem o mesmo tiro. */
    fun fireShared(coord: Coord) {
        val m = match ?: return
        if (m.opponent == Opponent.LAN) link.send(Protocol.act(coord.x, coord.y))
        m.act(coord)
    }

    fun useAbilityShared(ability: Ability) {
        val m = match ?: return
        if (m.opponent == Opponent.LAN) link.send(Protocol.ability(ability.code))
        m.selectAbility(ability)
    }

    /** Manda a própria frota assim que ela é confirmada. */
    fun sendFleet() {
        val m = match ?: return
        if (m.opponent != Opponent.LAN) return
        link.send(Protocol.fleet(FleetCodec.encode(m.board(m.mySide).ships)))
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

    // a trilha acompanha a tela: tema no deque, faixa de combate na batalha
    LaunchedEffect(state.screen, state.musicOn) {
        profile.setMusic(state.musicOn)
        if (!state.musicOn) {
            music.stop()
        } else {
            music.play(if (state.screen == Screen.BATTLE) Music.BATTLE else Music.THEME)
        }
    }
    DisposableEffect(Unit) { onDispose { music.release() } }

    // com conta conectada, a abertura já renova a sessão e busca o que há na nuvem
    LaunchedEffect(Unit) { state.resumeSession() }

    NavalTheme {
        Box(Modifier.fillMaxSize().background(Naval.bg)) {
            when (state.screen) {
                Screen.SPLASH -> SplashScreen(state)
                Screen.MENU -> MenuScreen(state)
                Screen.SHIPYARD -> ShipyardScreen(state)
                Screen.STORE -> StoreScreen(state)
                Screen.PROFILE -> ProfileScreen(state)
                Screen.AUTH -> AuthScreen(state)
                Screen.LAN -> LanScreen(state)
                Screen.NAMES -> state.match?.let { NamesScreen(state, it) }
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.HANDOFF -> state.match?.let { HandoffScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
            }
        }
    }
}
