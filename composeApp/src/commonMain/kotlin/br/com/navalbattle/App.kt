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
import br.com.navalbattle.design.Livery
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.HandoffScreen
import br.com.navalbattle.ui.MenuScreen
import br.com.navalbattle.ui.NamesScreen
import br.com.navalbattle.ui.PlacementScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.ShipyardScreen
import br.com.navalbattle.ui.SplashScreen

enum class Screen { SPLASH, MENU, SHIPYARD, NAMES, PLACEMENT, HANDOFF, BATTLE, RESULT }

class AppState {
    var screen by mutableStateOf(Screen.SPLASH)
    var mode by mutableStateOf(GameMode.TACTICAL)
    var livery by mutableStateOf(Livery.BRAZIL)
    var match by mutableStateOf<Match?>(null)

    /** Quem deve pegar o aparelho para posicionar a própria frota. */
    var handoffSide by mutableStateOf(Side.PLAYER)

    /** Trilha ligada. Os efeitos de combate continuam tocando de qualquer jeito. */
    var musicOn by mutableStateOf(true)

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
}

@Composable
fun App() {
    val state = remember { AppState() }
    val music = remember { MusicPlayer() }

    // a trilha acompanha a tela: tema no deque, faixa de combate na batalha
    LaunchedEffect(state.screen, state.musicOn) {
        if (!state.musicOn) {
            music.stop()
        } else {
            music.play(if (state.screen == Screen.BATTLE) Music.BATTLE else Music.THEME)
        }
    }
    DisposableEffect(Unit) { onDispose { music.release() } }

    NavalTheme {
        Box(Modifier.fillMaxSize().background(Naval.bg)) {
            when (state.screen) {
                Screen.SPLASH -> SplashScreen(state)
                Screen.MENU -> MenuScreen(state)
                Screen.SHIPYARD -> ShipyardScreen(state)
                Screen.NAMES -> state.match?.let { NamesScreen(state, it) }
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.HANDOFF -> state.match?.let { HandoffScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
            }
        }
    }
}
