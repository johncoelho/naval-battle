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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Award
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side

@Composable
fun ResultScreen(state: AppState, match: Match) {
    val victory = match.winner == Side.PLAYER
    // partida entre pessoas (mesmo aparelho ou rede) mostra os dois comandantes
    val local = match.opponent != Opponent.AI
    val winnerSide = match.winner ?: Side.PLAYER

    // a carreira só conta partidas contra a IA: no local os dois usam o mesmo perfil.
    // fica num efeito para creditar uma única vez, e não a cada recomposição
    var award by remember(match) { mutableStateOf<Award?>(null) }
    LaunchedEffect(match) {
        if (match.opponent == Opponent.AI) {
            award = state.profile.registerMatch(
                victory = victory,
                shotsFired = match.playerShots,
                hitsLanded = match.playerHits,
                shipsSunk = ShipClass.fleet.size - match.enemyBoard.remainingShips().size,
                turns = match.turnCount
            )
            // com conta conectada a carreira sobe sozinha depois de cada partida
            state.pushQuietly()
        }
    }

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
                    skin = state.skin,
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

                award?.let { a ->
                    Gap(24)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Naval.surface2)
                            .border(1.dp, if (a.rankUp != null) Naval.amber else Naval.line)
                            .padding(14.dp)
                    ) {
                        HudLabel("CARREIRA", Naval.muted)
                        Gap(8)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("+${a.xp} XP", style = NavalType.title, color = Naval.greenBright)
                            Text("◆ +${a.credits}", style = NavalType.title, color = Naval.amberStrong)
                        }
                        Gap(8)
                        HudLabel(
                            "${state.profile.rank.label.uppercase()} · ${state.profile.xp} XP · SALDO ◆ ${state.profile.credits}",
                            Naval.inkSoft
                        )
                        a.rankUp?.let { r ->
                            Gap(8)
                            Text(
                                "PROMOVIDO A ${r.label.uppercase()}",
                                style = NavalType.mono,
                                color = Naval.amberStrong
                            )
                        }
                    }
                    Gap(6)
                    HudLabel("CRÉDITOS VALEM NOVAS FROTAS NO ESTALEIRO", Naval.muted)
                }
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
