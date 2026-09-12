package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Orientation
import br.com.navalbattle.game.Ship
import br.com.navalbattle.game.ShipClass

@Composable
fun PlacementScreen(state: AppState, match: Match) {
    var orientation by remember { mutableStateOf(Orientation.HORIZONTAL) }
    var selected by remember { mutableStateOf(ShipClass.CARRIER) }
    var error by remember { mutableStateOf<String?>(null) }

    val board = match.playerBoard
    val placedTypes = board.ships.map { it.type }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar("POSICIONAMENTO", match.mode.label.uppercase())
        Gap(10)

        BoardView(
            board = board,
            livery = state.livery,
            showShips = true,
            interactive = true,
            sweep = false,
            modifier = Modifier.fillMaxWidth()
        ) { coord ->
            val ship = Ship(selected, coord, orientation)
            if (board.canPlace(ship)) {
                board.place(ship)
                error = null
                val next = ShipClass.fleet.firstOrNull { it !in board.ships.map { s -> s.type } }
                if (next != null) selected = next
            } else {
                error = "Posição inválida para ${selected.label}"
            }
        }

        Gap(10)
        HudLabel(error ?: "Toque no grid para ancorar a proa · ${orientation.labelPt()}",
            if (error != null) Naval.danger else Naval.muted)

        Gap(10)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ShipClass.fleet.size) { i ->
                val type = ShipClass.fleet[i]
                val placed = type in placedTypes
                Box(
                    Modifier
                        .background(if (selected == type) Naval.surface3 else Naval.surface2)
                        .border(
                            1.dp,
                            when {
                                selected == type -> Naval.amber
                                placed -> Naval.green
                                else -> Naval.line
                            }
                        )
                        .clickable { selected = type }
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        "${type.label.uppercase()} ${type.size}",
                        style = NavalType.monoSmall,
                        color = when {
                            selected == type -> Naval.amberStrong
                            placed -> Naval.greenBright
                            else -> Naval.muted
                        }
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Girar", modifier = Modifier.weight(1f)) {
                orientation = orientation.flipped()
            }
            SecondaryButton("Aleatório", modifier = Modifier.weight(1f)) {
                match.randomizePlayerFleet()
                error = null
            }
        }
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Voltar", modifier = Modifier.weight(1f)) {
                state.screen = Screen.MENU
            }
            PrimaryButton(
                "Confirmar",
                enabled = board.ships.size == ShipClass.fleet.size,
                modifier = Modifier.weight(1f)
            ) {
                match.startBattle()
                state.screen = Screen.BATTLE
            }
        }
    }
}

private fun Orientation.labelPt(): String =
    if (this == Orientation.HORIZONTAL) "HORIZONTAL" else "VERTICAL"

@Composable
private fun BoxAlign(content: @Composable () -> Unit) =
    Box(contentAlignment = Alignment.Center) { content() }
