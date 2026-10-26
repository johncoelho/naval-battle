package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.WELCOME_SHOWN_DOUBLOONS
import br.com.navalbattle.game.WELCOME_SHOWN_MILES
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/** Véu e painel dos dois popups da #43, no mesmo molde dos outros (safe area e largura de painel). */
@Composable
private fun WelcomePanel(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .navalSafeArea()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .navalPanelWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { content() }
    }
}

/** "Primeira missão": começar a guiada ou pular (pular também entrega o presente). */
@Composable
fun GuidedOfferPopup(state: AppState) {
    if (!state.guidedOffer || state.screen != br.com.navalbattle.Screen.MENU || state.match != null) return
    WelcomePanel {
        HudLabel(t(K.GUIDED_EYEBROW), Naval.muted)
        Gap(8)
        Text(t(K.GUIDED_OFFER_TITLE), style = NavalType.title, color = Naval.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Gap(10)
        Text(
            t(K.GUIDED_OFFER_BODY, WELCOME_SHOWN_DOUBLOONS, WELCOME_SHOWN_MILES),
            style = NavalType.body,
            color = Naval.inkSoft,
            textAlign = TextAlign.Center
        )
        Gap(20)
        PrimaryButton(t(K.GUIDED_START)) { state.startGuidedMatch() }
        Gap(8)
        SecondaryButton(t(K.GUIDED_SKIP)) { state.skipGuided() }
    }
}

/** Presente pago (valor devolvido pelo servidor) ou, para convidado, o convite a entrar na conta. */
@Composable
fun WelcomeGiftPopup(state: AppState) {
    val paid = state.welcomeGiftPaid
    if (paid == null && !state.welcomeGuestNotice) return
    if (state.match != null) return
    WelcomePanel {
        HudLabel(t(K.GIFT_TITLE), Naval.muted)
        Gap(10)
        if (paid != null) {
            Text(t(K.GIFT_GOT, paid.doubloons, paid.miles), style = NavalType.title, color = Naval.amberStrong, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                t(K.GIFT_GUEST, WELCOME_SHOWN_DOUBLOONS, WELCOME_SHOWN_MILES),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center
            )
        }
        Gap(20)
        PrimaryButton(t(K.GIFT_CLOSE)) { state.closeWelcomePopups() }
    }
}
