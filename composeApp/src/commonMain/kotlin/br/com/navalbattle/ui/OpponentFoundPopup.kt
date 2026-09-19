package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawAvatar
import br.com.navalbattle.game.Avatar
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Rank
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Popup de "adversário encontrado" — some assim que a sala online conecta,
 * antes do posicionamento começar. Antes disso o comandante só via a própria
 * tela de posicionamento sem saber contra quem tinha caído.
 */
@Composable
fun OpponentFoundPopup(state: AppState) {
    if (state.match?.opponent != Opponent.ONLINE) return
    val opponent = state.opponentProfile ?: return
    var dismissed by remember(state.match, opponent) { mutableStateOf(false) }
    if (dismissed) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(t(K.OPPONENT_FOUND_EYEBROW), Naval.muted)
            Gap(14)
            Canvas(Modifier.size(78.dp)) {
                drawAvatar(
                    avatar = Avatar.of(opponent.avatar),
                    center = Offset(size.width / 2f, size.height / 2f),
                    size = size.minDimension * 0.9f,
                    color = Naval.amberStrong
                )
            }
            Gap(12)
            Text(opponent.username.uppercase(), style = NavalType.title, color = Naval.ink)
            Gap(4)
            HudLabel(Rank.of(opponent.xp).label.uppercase(), Naval.amberStrong)
            if (state.onlineMatchRanked) {
                Gap(6)
                HudLabel(t(K.LEADERBOARD_POINTS, opponent.rankedRating), Naval.inkSoft)
            }
            Gap(20)
            PrimaryButton(t(K.OPPONENT_FOUND_CONTINUE)) { dismissed = true }
        }
    }
}
