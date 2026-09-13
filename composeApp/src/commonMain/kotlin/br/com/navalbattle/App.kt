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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.navalbattle.audio.Music
import br.com.navalbattle.audio.MusicPlayer
import br.com.navalbattle.data.CloudApi
import br.com.navalbattle.data.CloudResult
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Session
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Livery
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Profile
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.AuthScreen
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

enum class Screen { SPLASH, MENU, SHIPYARD, STORE, PROFILE, AUTH, NAMES, PLACEMENT, HANDOFF, BATTLE, RESULT }

class AppState(val profile: Profile, private val cloud: CloudApi) {
    var screen by mutableStateOf(Screen.SPLASH)
    var mode by mutableStateOf(GameMode.TACTICAL)
    var match by mutableStateOf<Match?>(null)

    /** O visual em uso — linha de casco e camuflagem — vem do perfil gravado no aparelho. */
    val skin: Skin
        get() = Skin(Livery.of(profile.equipped), FleetLine.of(profile.equippedFleet))

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
        match = null
        screen = Screen.MENU
    }

    // ---------------- conta e sincronização ----------------

    /** Cria a conta e já sobe a carreira que existir neste aparelho. */
    suspend fun createAccount(email: String, password: String, username: String): Pair<Boolean, String> =
        when (val r = cloud.signUp(email, password, username)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                profile.rename(username)
                cloud.saveProfile(r.value, profile.snapshot())
                true to "Conta criada. A carreira já está na nuvem."
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
                val merged = mergeWithCloud() ?: "Conectado. Não consegui sincronizar agora."
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
            ?: return CloudResult.Fail("Entre na conta para sincronizar.")
        val first = block(session)
        if (first !is CloudResult.Fail || !first.expired) return first

        val renewed = renew() ?: return CloudResult.Fail(
            "Sua sessão expirou. Entre de novo para sincronizar."
        )
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
                    "Carreira da nuvem restaurada neste aparelho."
                } else {
                    authed { session -> cloud.saveProfile(session, profile.snapshot()) }
                    "Carreira deste aparelho enviada para a nuvem."
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
                Screen.NAMES -> state.match?.let { NamesScreen(state, it) }
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.HANDOFF -> state.match?.let { HandoffScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
            }
        }
    }
}
