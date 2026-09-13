package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.ShipClass

@Composable
fun MenuScreen(state: AppState) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudLabel("CMD. COMANDANTE", Naval.inkSoft)
            HudLabel("◆ 1.240", Naval.amberStrong)
        }

        Spacer(Modifier.height(22.dp))
        Text("DEQUE DE", style = NavalType.display, color = Naval.ink)
        Text("COMANDO", style = NavalType.display, color = Naval.amberStrong)
        Spacer(Modifier.height(4.dp))
        HudLabel("COMANDANTE · ELO 1742")

        Spacer(Modifier.height(20.dp))
        FleetPreview(state)

        Spacer(Modifier.height(20.dp))
        HudLabel("MODO DE COMBATE")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GameMode.entries.forEach { mode ->
                ModeChip(
                    label = mode.label,
                    selected = state.mode == mode,
                    modifier = Modifier.weight(1f)
                ) { state.mode = mode }
            }
        }
        Spacer(Modifier.height(6.dp))
        HudLabel(state.mode.description, Naval.muted)

        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            PrimaryButton("Partida rápida", "vs. IA") { state.newMatch(Opponent.AI) }
            SecondaryButton("Dois jogadores", "no mesmo aparelho") { state.newMatch(Opponent.LOCAL) }
            SecondaryButton("Estaleiro", state.livery.name) { state.screen = Screen.SHIPYARD }
            SecondaryButton("Ranqueada", "em breve", enabled = false) {}
            SecondaryButton("Convidar amigo", "em breve", enabled = false) {}
            SecondaryButton("Perto de mim", "em breve", enabled = false) {}
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudLabel("DEQUE", Naval.amberStrong)
            HudLabel("ESTALEIRO")
            HudLabel("LOJA")
            HudLabel("PERFIL")
        }
    }
}

@Composable
private fun FleetPreview(state: AppState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.radialGradient(
                    colors = listOf(Naval.abyss2, Naval.abyss),
                    center = Offset.Unspecified,
                    radius = 600f
                )
            )
            .border(1.dp, Naval.lineSoft)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            drawShip(
                type = ShipClass.CARRIER,
                center = Offset(size.width / 2f, size.height / 2f),
                lengthPx = size.width * 0.94f,
                thicknessPx = size.width * 0.94f / 5f,
                vertical = false,
                livery = state.livery
            )
        }
        Spacer(Modifier.height(8.dp))
        HudLabel("FROTA ATIVA · ${state.livery.name}")
    }
}

@Composable
fun ScreenTopBar(left: String, right: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudLabel(left, Naval.inkSoft)
        HudLabel(right, Naval.amberStrong)
    }
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun GapW(width: Int) = Spacer(Modifier.width(width.dp))
