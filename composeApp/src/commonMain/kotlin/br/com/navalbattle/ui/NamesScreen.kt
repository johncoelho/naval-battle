package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Side

/** Cada comandante escolhe seu nome antes da partida local. */
@Composable
fun NamesScreen(state: AppState, match: Match) {
    // o dono do aparelho já entra com o nome do perfil
    var one by remember { mutableStateOf(match.nameOne.ifBlank { state.profile.name }) }
    var two by remember { mutableStateOf(match.nameTwo) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar("DOIS JOGADORES", match.mode.label.uppercase())
        Gap(24)

        Text("QUEM ESTÁ NO", style = NavalType.display, color = Naval.ink)
        Text("COMANDO?", style = NavalType.display, color = Naval.amberStrong)
        Gap(6)
        HudLabel("O PLACAR DA BATALHA USA ESSES NOMES")

        Gap(28)
        NameField("Comandante 1", one) { one = it }
        Gap(14)
        NameField("Comandante 2", two) { two = it }

        Spacer(Modifier.weight(1f))

        PrimaryButton("Começar") {
            match.setName(Side.PLAYER, one)
            match.setName(Side.ENEMY, two)
            state.screen = Screen.PLACEMENT
        }
        Gap(8)
        SecondaryButton("Voltar") { state.quitToMenu() }
    }
}

@Composable
private fun NameField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        HudLabel(label.uppercase(), Naval.muted)
        Gap(6)
        Box(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, if (value.isBlank()) Naval.line else Naval.green)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 16) onChange(it) },
                singleLine = true,
                textStyle = NavalType.button.copy(color = Naval.ink),
                cursorBrush = SolidColor(Naval.amberStrong),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            ) { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(label, style = NavalType.button, color = Naval.muted)
                        }
                        inner()
                    }
                }
            }
        }
    }
}
