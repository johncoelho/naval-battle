package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import br.com.navalbattle.data.appVersionLabel
import br.com.navalbattle.data.openStoreListing
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawAvatar
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
        if (state.updateAvailable) {
            HudLabel(
                t(K.MENU_UPDATE_AVAILABLE),
                Naval.amberInk,
                Modifier
                    .fillMaxWidth()
                    .background(Naval.amber)
                    .clickable { openStoreListing() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { state.screen = Screen.PROFILE }
            ) {
                Canvas(Modifier.size(30.dp)) {
                    drawAvatar(
                        avatar = state.profile.avatar,
                        center = Offset(size.width / 2f, size.height / 2f),
                        size = size.minDimension,
                        color = Naval.amberStrong
                    )
                }
                Spacer(Modifier.width(8.dp))
                HudLabel(state.profile.rank.label.uppercase(), Naval.inkSoft)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                HudLabel(
                    if (state.musicOn) t(K.MENU_MUSIC_ON) else t(K.MENU_MUSIC_OFF),
                    if (state.musicOn) Naval.greenBright else Naval.muted,
                    Modifier
                        .border(1.dp, Naval.line)
                        .clickable { state.musicOn = !state.musicOn }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(Modifier.width(12.dp))
                HudLabel("◆ ${state.profile.credits}", Naval.amberStrong)
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(t(K.MENU_TITLE_1).uppercase(), style = NavalType.display, color = Naval.ink)
        Text(t(K.MENU_TITLE_2).uppercase(), style = NavalType.display, color = Naval.amberStrong)
        Spacer(Modifier.height(4.dp))
        HudLabel("${state.profile.displayName.uppercase()} · ${state.profile.xp} XP")

        Spacer(Modifier.height(20.dp))
        FleetPreview(state)

        Spacer(Modifier.height(20.dp))
        HudLabel(t(K.MENU_MODE))
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
            PrimaryButton(t(K.MENU_QUICK), t(K.MENU_QUICK_SUB)) { state.newMatch(Opponent.AI) }
            SecondaryButton(t(K.MENU_LOCAL), t(K.MENU_LOCAL_SUB)) { state.newMatch(Opponent.LOCAL) }
            SecondaryButton(t(K.MENU_LAN), t(K.MENU_LAN_SUB)) { state.screen = Screen.LAN }
            SecondaryButton(
                t(K.MENU_ONLINE),
                if (state.profile.signedIn) t(K.MENU_ONLINE_SUB) else t(K.MENU_ONLINE_LOCKED),
                enabled = state.profile.signedIn
            ) { state.screen = Screen.ONLINE }
            SecondaryButton(t(K.MENU_SHIPYARD), state.skin.paint.name) { state.screen = Screen.SHIPYARD }
            SecondaryButton(t(K.MENU_STORE), "◆ ${state.profile.credits}") { state.screen = Screen.STORE }
            SecondaryButton(
                if (state.profile.signedIn) t(K.MENU_PROFILE) else t(K.MENU_CREATE_ACCOUNT),
                if (state.profile.signedIn) state.profile.rank.label else t(K.MENU_ACCOUNT_SUB)
            ) { state.screen = Screen.PROFILE }
        }

        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HudLabel(t(K.MENU_TAB_DECK), Naval.amberStrong)
            HudLabel(
                t(K.MENU_SHIPYARD),
                Naval.muted,
                Modifier.clickable { state.screen = Screen.SHIPYARD }
            )
            HudLabel(
                t(K.MENU_PROFILE),
                Naval.muted,
                Modifier.clickable { state.screen = Screen.PROFILE }
            )
            HudLabel(
                t(K.MENU_TAB_STORE),
                Naval.muted,
                Modifier.clickable { state.screen = Screen.STORE }
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HudLabel("v$appVersionLabel", Naval.line)
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
                skin = state.skin
            )
        }
        Spacer(Modifier.height(8.dp))
        HudLabel("${state.skin.fleet.name.uppercase()} · ${state.skin.paint.name.uppercase()}")
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
