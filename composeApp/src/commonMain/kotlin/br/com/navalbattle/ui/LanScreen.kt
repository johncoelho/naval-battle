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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType

/**
 * Partida entre dois aparelhos no mesmo Wi-Fi. Um anuncia, o outro encontra e entra.
 * Não passa por servidor nenhum: os dois conversam direto na rede da casa.
 */
@Composable
fun LanScreen(state: AppState) {
    var gameName by remember { mutableStateOf("") }

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
        ScreenTopBar(t(K.LAN_TITLE), stateLabel(state.linkState))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(18)
            Text(t(K.LAN_HEAD_1).uppercase(), style = NavalType.display, color = Naval.ink)
            Text(t(K.LAN_HEAD_2).uppercase(), style = NavalType.display, color = Naval.amberStrong)
            Gap(8)
            HudLabel(t(K.LAN_SUB), Naval.muted)

            Gap(26)
            HudLabel(t(K.LAN_GAME_NAME), Naval.muted)
            Gap(6)
            GameNameField(gameName, enabled = state.linkState != LinkState.HOSTING) { gameName = it }
            Gap(6)
            HudLabel(t(K.LAN_GAME_NAME_HINT), Naval.muted)

            Gap(16)
            PrimaryButton(
                t(K.LAN_HOST),
                subtitle = t(K.LAN_HOST_SUB),
                enabled = state.linkState != LinkState.HOSTING
            ) { state.hostGame(gameName) }
            Gap(8)
            SecondaryButton(
                t(K.LAN_SEARCH),
                subtitle = t(K.LAN_SEARCH_SUB),
                enabled = state.linkState != LinkState.SEARCHING
            ) { state.searchGames() }

            when (state.linkState) {
                LinkState.HOSTING -> {
                    Gap(20)
                    Panel(Naval.green) {
                        HudLabel(t(K.LAN_ANNOUNCING), Naval.greenBright)
                        Gap(6)
                        HudLabel(
                            t(K.LAN_ANNOUNCING_SUB),
                            Naval.muted
                        )
                    }
                }

                LinkState.SEARCHING -> {
                    Gap(20)
                    HudLabel(t(K.LAN_FOUND))
                    Gap(8)
                    if (state.foundGames.isEmpty()) {
                        Panel(Naval.line) {
                            HudLabel(t(K.LAN_LOOKING), Naval.inkSoft)
                            Gap(6)
                            HudLabel(t(K.LAN_NONE_YET), Naval.muted)
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
                                HudLabel(t(K.LAN_JOIN), Naval.amberStrong)
                            }
                        }
                    }
                }

                LinkState.CONNECTING -> {
                    Gap(20)
                    Panel(Naval.amber) { HudLabel(t(K.LAN_CONNECTING), Naval.amberStrong) }
                }

                LinkState.FAILED -> {
                    Gap(20)
                    Panel(Naval.danger) {
                        HudLabel(t(K.LAN_DROPPED), Naval.danger)
                        Gap(6)
                        HudLabel(
                            t(K.LAN_DROPPED_SUB),
                            Naval.muted
                        )
                    }
                }

                else -> Unit
            }

            Gap(24)
            Panel(Naval.line) {
                HudLabel(t(K.LAN_HOW), Naval.inkSoft)
                Gap(8)
                Step("1", t(K.LAN_STEP_1))
                Step("2", t(K.LAN_STEP_2))
                Step("3", t(K.LAN_STEP_3))
                Gap(6)
                HudLabel(t(K.LAN_HOST_FIRST), Naval.muted)
            }
            Gap(16)
        }

        Gap(8)
        SecondaryButton(t(K.BACK_TO_DECK)) {
            state.closeLink()
            state.screen = Screen.MENU
        }
    }
}

private fun stateLabel(state: LinkState): String = t(
    when (state) {
        LinkState.IDLE -> K.LAN_READY
        LinkState.HOSTING -> K.LAN_STATE_ANNOUNCING
        LinkState.SEARCHING -> K.LAN_STATE_SEARCHING
        LinkState.CONNECTING -> K.LAN_STATE_CONNECTING
        LinkState.CONNECTED -> K.LAN_STATE_CONNECTED
        LinkState.FAILED -> K.LAN_STATE_FAILED
    }
)

@Composable
private fun GameNameField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, if (value.isBlank()) Naval.line else Naval.green)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 24) onChange(it) },
            enabled = enabled,
            singleLine = true,
            textStyle = NavalType.button.copy(color = Naval.ink),
            cursorBrush = SolidColor(Naval.amberStrong),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        ) { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(t(K.LAN_GAME_NAME), style = NavalType.button, color = Naval.muted)
                }
                inner()
            }
        }
    }
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
