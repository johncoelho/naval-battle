package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.ShipClass

/**
 * Cobre a tela entre o posicionamento de um comandante e o do outro, para
 * ninguém ver onde o adversário pôs a frota. Na batalha não há troca de mãos.
 */
@Composable
fun HandoffScreen(state: AppState, match: Match) {
    val side = state.handoffSide
    val name = match.sideName(side)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        HudLabel(t(K.HANDOFF_PASS), Naval.muted)
        Gap(10)
        Text(
            name.uppercase(),
            style = NavalType.display,
            color = Naval.amberStrong,
            textAlign = TextAlign.Center
        )
        Gap(6)
        Text(
            t(K.HANDOFF_PLACE),
            style = NavalType.body,
            color = Naval.inkSoft,
            textAlign = TextAlign.Center
        )

        Gap(26)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(Naval.abyss)
                .border(1.dp, Naval.lineSoft)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            drawShip(
                type = ShipClass.CARRIER,
                center = Offset(size.width / 2f, size.height / 2f),
                lengthPx = size.width,
                thicknessPx = size.width / 5f,
                vertical = false,
                skin = state.skin
            )
        }

        Gap(22)
        HudLabel(t(K.HANDOFF_HIDE), Naval.muted)

        Spacer(Modifier.weight(1f))

        PrimaryButton(t(K.HANDOFF_HAVE_IT)) {
            state.screen = Screen.PLACEMENT
        }
        Gap(8)
        SecondaryButton(t(K.BACK)) {
            // devolve o posicionamento a quem acabou de confirmar
            match.backPlacement()
            state.screen = Screen.PLACEMENT
        }
        Gap(8)
        SecondaryButton(t(K.QUIT_MATCH)) { state.quitToMenu() }
        Gap(10)
        HudLabel(t(K.HANDOFF_PREP), Naval.muted)
    }
}
