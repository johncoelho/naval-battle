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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.data.openStoreListing
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Lembrete de avaliação na loja — dispara a cada tantas partidas (ver
 * [br.com.navalbattle.game.Profile.feedbackNextPromptAt]), não só uma vez, mas
 * some de vez se o comandante disser que não quer ver de novo. Só aparece fora
 * de partida e sem nenhum outro popup mais urgente na frente.
 */
@Composable
fun FeedbackPopup(state: AppState) {
    if (!state.feedbackPopupNeeded || state.match != null) return
    if (state.updateAvailable || state.seasonPopupNeeded || state.pendingInvite != null) return
    if (state.onlineLinkState == LinkState.SEARCHING || state.onlineLinkState == LinkState.HOSTING) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.green)
                .padding(20.dp)
        ) {
            HudLabel(t(K.FEEDBACK_POPUP_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.FEEDBACK_POPUP_TITLE),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            HudLabel(t(K.FEEDBACK_POPUP_SUB), Naval.inkSoft)
            Gap(20)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.FEEDBACK_POPUP_LATER), modifier = Modifier.weight(1f)) {
                    state.profile.deferFeedbackPrompt()
                }
                PrimaryButton(t(K.FEEDBACK_POPUP_RATE), modifier = Modifier.weight(1f)) {
                    openStoreListing()
                    state.profile.optOutFeedback()
                }
            }
            Gap(12)
            HudLabel(
                t(K.FEEDBACK_POPUP_NEVER),
                Naval.muted,
                Modifier.clickable { state.profile.optOutFeedback() }
            )
        }
    }
}
