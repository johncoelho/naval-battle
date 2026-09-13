package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType

/**
 * Partida entre dois aparelhos no mesmo Wi-Fi. Um anuncia, o outro encontra e entra.
 * Não passa por servidor nenhum: os dois conversam direto na rede da casa.
 */
@Composable
fun LanScreen(state: AppState) {
    // sair da tela sem ter conectado encerra o anúncio e a busca
    DisposableEffect(Unit) {
        onDispose { if (state.screen == Screen.MENU) state.closeLink() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        ScreenTopBar("REDE LOCAL", stateLabel(state.linkState))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(18)
            Text("BATALHA", style = NavalType.display, color = Naval.ink)
            Text("NO MESMO WI-FI", style = NavalType.display, color = Naval.amberStrong)
            Gap(8)
            HudLabel("OS DOIS APARELHOS PRECISAM ESTAR NA MESMA REDE", Naval.muted)

            Gap(26)
            PrimaryButton(
                "Criar partida",
                subtitle = "você abre o fogo",
                enabled = state.linkState != LinkState.HOSTING
            ) { state.hostGame() }
            Gap(8)
            SecondaryButton(
                "Procurar partida",
                subtitle = "entrar em uma aberta",
                enabled = state.linkState != LinkState.SEARCHING
            ) { state.searchGames() }

            when (state.linkState) {
                LinkState.HOSTING -> {
                    Gap(20)
                    Panel(Naval.green) {
                        HudLabel("ANUNCIANDO NA REDE", Naval.greenBright)
                        Gap(6)
                        HudLabel(
                            "PEÇA PARA O OUTRO COMANDANTE TOCAR EM PROCURAR PARTIDA",
                            Naval.muted
                        )
                    }
                }

                LinkState.SEARCHING -> {
                    Gap(20)
                    HudLabel("PARTIDAS ENCONTRADAS")
                    Gap(8)
                    if (state.foundGames.isEmpty()) {
                        Panel(Naval.line) {
                            HudLabel("PROCURANDO…", Naval.inkSoft)
                            Gap(6)
                            HudLabel("NENHUMA PARTIDA ANUNCIADA AINDA", Naval.muted)
                        }
                    } else {
                        state.foundGames.forEach { game ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .background(Naval.surface2)
                                    .border(1.dp, Naval.amber)
                                    .clickable { state.joinGame(game) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(9.dp).background(Naval.greenBright))
                                GapW(10)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        game.name.uppercase(),
                                        style = NavalType.mono,
                                        color = Naval.ink
                                    )
                                    Gap(2)
                                    HudLabel(game.host, Naval.muted)
                                }
                                HudLabel("ENTRAR", Naval.amberStrong)
                            }
                        }
                    }
                }

                LinkState.CONNECTING -> {
                    Gap(20)
                    Panel(Naval.amber) { HudLabel("CONECTANDO…", Naval.amberStrong) }
                }

                LinkState.FAILED -> {
                    Gap(20)
                    Panel(Naval.danger) {
                        HudLabel("A LIGAÇÃO CAIU", Naval.danger)
                        Gap(6)
                        HudLabel(
                            "CONFIRME QUE OS DOIS ESTÃO NA MESMA REDE E TENTE DE NOVO",
                            Naval.muted
                        )
                    }
                }

                else -> Unit
            }

            Gap(24)
            Panel(Naval.line) {
                HudLabel("COMO FUNCIONA", Naval.inkSoft)
                Gap(8)
                Step("1", "Um comandante toca em Criar partida")
                Step("2", "O outro toca em Procurar e escolhe o nome que aparecer")
                Step("3", "Cada um posiciona a própria frota e a batalha começa")
                Gap(6)
                HudLabel("QUEM CRIA A PARTIDA ATIRA PRIMEIRO", Naval.muted)
            }
            Gap(16)
        }

        Gap(8)
        SecondaryButton("Voltar ao deque") {
            state.closeLink()
            state.screen = Screen.MENU
        }
    }
}

private fun stateLabel(state: LinkState): String = when (state) {
    LinkState.IDLE -> "PRONTO"
    LinkState.HOSTING -> "ANUNCIANDO"
    LinkState.SEARCHING -> "PROCURANDO"
    LinkState.CONNECTING -> "CONECTANDO"
    LinkState.CONNECTED -> "CONECTADO"
    LinkState.FAILED -> "SEM LIGAÇÃO"
}

@Composable
private fun Panel(border: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, border)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun Step(number: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(number, style = NavalType.mono, color = Naval.amberStrong)
        GapW(10)
        HudLabel(text, Naval.inkSoft)
    }
}
