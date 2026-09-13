package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
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
        Gap(16)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (local) match.sideName(winnerSide).uppercase()
                else if (victory) "VITÓRIA" else "DERROTA",
                style = NavalType.display,
                color = if (local) commanderColor(winnerSide)
                else if (victory) Naval.amberStrong else Naval.danger
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

            Gap(20)
            if (local) {
                // encerrada a partida as duas frotas se revelam: onde estavam os navios
                // e todos os tiros que cada uma levou, na cor do seu dono
                HudLabel("CARTA DA BATALHA")
                Gap(6)
                BoardView(
                    board = match.playerBoard,
                    livery = state.livery,
                    showShips = true,
                    sweep = false,
                    markTint = commanderColor(Side.PLAYER),
                    overlay = match.enemyBoard,
                    overlayTint = commanderColor(Side.ENEMY),
                    modifier = Modifier.fillMaxWidth()
                )
                Gap(8)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(commanderColor(side)))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "FROTA DE ${match.sideName(side).uppercase()}",
                                style = NavalType.monoSmall,
                                color = commanderColor(side)
                            )
                        }
                    }
                }

                Gap(16)
                listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                    HudLabel(match.sideName(side).uppercase(), commanderColor(side))
                    StatRow(
                        "TIROS / ACERTOS · PRECISÃO",
                        "${if (side == Side.PLAYER) match.playerShots else match.enemyShots}" +
                            " / ${if (side == Side.PLAYER) match.playerHits else match.enemyHits}" +
                            " · ${match.accuracyOf(side)}%"
                    )
                    StatRow(
                        "NAVIOS RESTANTES",
                        "${match.board(side).remainingShips().size} / ${ShipClass.fleet.size}"
                    )
                    Gap(8)
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
            Gap(16)
        }

        Gap(12)
        PrimaryButton("Nova partida") { state.newMatch(match.opponent) }
        Gap(8)
        SecondaryButton("Deque de comando") { state.quitToMenu() }
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
