package br.com.navalbattle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.navalbattle.design.Livery
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.MenuScreen
import br.com.navalbattle.ui.PlacementScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.ShipyardScreen

enum class Screen { MENU, SHIPYARD, PLACEMENT, BATTLE, RESULT }

class AppState {
    var screen by mutableStateOf(Screen.MENU)
    var mode by mutableStateOf(GameMode.TACTICAL)
    var livery by mutableStateOf(Livery.BRAZIL)
    var match by mutableStateOf<Match?>(null)

    fun newMatch() {
        match = Match(mode).also { it.randomizePlayerFleet() }
        screen = Screen.PLACEMENT
    }
}

@Composable
fun App() {
    val state = remember { AppState() }

    NavalTheme {
        Box(Modifier.fillMaxSize().background(Naval.bg)) {
            when (state.screen) {
                Screen.MENU -> MenuScreen(state)
                Screen.SHIPYARD -> ShipyardScreen(state)
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
            }
        }
    }
}
