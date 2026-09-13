package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side

@Composable
fun ResultScreen(state: AppState, match: Match) {
    val victory = match.winner == Side.PLAYER
    val local = match.opponent == Opponent.LOCAL
    val winnerSide = match.winner ?: Side.PLAYER

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar("RELATÓRIO DE COMBATE", match.mode.label.uppercase())
        Spacer(Modifier.weight(0.4f))

        Text(
            if (local) match.sideName(winnerSide).uppercase() else if (victory) "VITÓRIA" else "DERROTA",
            style = NavalType.display,
            color = if (local || victory) Naval.amberStrong else Naval.danger
        )
        Gap(4)
        HudLabel(
            when {
                local -> "VENCEU A BATALHA"
                victory -> "FROTA INIMIGA NEUTRALIZADA"
                else -> "NOSSA FROTA FOI DESTRUÍDA"
            },
            Naval.inkSoft
        )

        Gap(24)
        if (local) {
            listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                HudLabel(match.sideName(side).uppercase(), Naval.amberStrong)
                Gap(4)
                StatRow(
                    "TIROS / ACERTOS",
                    "${if (side == Side.PLAYER) match.playerShots else match.enemyShots} / " +
                        "${if (side == Side.PLAYER) match.playerHits else match.enemyHits}"
                )
                StatRow("PRECISÃO", "${match.accuracyOf(side)}%")
                StatRow(
                    "NAVIOS RESTANTES",
                    "${match.board(side).remainingShips().size} / ${ShipClass.fleet.size}"
                )
                Gap(12)
            }
            StatRow("TURNOS", match.turnCount.toString())
        } else {
            StatRow("TIROS DISPARADOS", match.playerShots.toString())
            StatRow("ACERTOS", match.playerHits.toString())
            StatRow("PRECISÃO", "${match.accuracy}%")
            StatRow("TURNOS", match.turnCount.toString())
            StatRow(
                "NAVIOS RESTANTES",
                "${match.playerBoard.remainingShips().size} / ${ShipClass.fleet.size}"
            )

            Gap(24)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface2)
                    .border(1.dp, Naval.line)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HudLabel("ELO", Naval.muted)
                Text(
                    if (victory) "1742 → 1766  (+24)" else "1742 → 1723  (−19)",
                    style = NavalType.mono,
                    color = if (victory) Naval.greenBright else Naval.danger
                )
            }
            Gap(6)
            HudLabel("SIMULADO — RANQUEADA ONLINE ENTRA NA PRÓXIMA FASE", Naval.muted)
        }

        Spacer(Modifier.weight(1f))
        PrimaryButton("Nova partida") { state.newMatch(match.opponent) }
        Gap(8)
        SecondaryButton("Deque de comando") { state.screen = Screen.MENU }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudLabel(label, Naval.muted)
        Text(value, style = NavalType.mono, color = Naval.ink)
    }
}
