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
import br.com.navalbattle.ReferralPopupKind
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.REFERRAL_SHOWN_DOUBLOONS
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Popups do convite (#44): "Convite aceito" (logo depois de digitar o código), o mesmo título com o valor
 * pago ao convidado, e "Seu convite rendeu" para quem convidou. Só fora de partida.
 */
@Composable
fun ReferralPopup(state: AppState) {
    val popup = state.referralPopup ?: return
    if (state.match != null) return
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
        ) {
            when (popup.kind) {
                ReferralPopupKind.ACCEPTED -> {
                    HudLabel(t(K.REFERRAL_ACCEPTED_TITLE), Naval.muted)
                    Gap(10)
                    Text(
                        t(K.REFERRAL_ACCEPTED_BODY, popup.name, REFERRAL_SHOWN_DOUBLOONS),
                        style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ReferralPopupKind.INVITEE_PAID -> {
                    HudLabel(t(K.REFERRAL_ACCEPTED_TITLE), Naval.muted)
                    Gap(10)
                    Text(
                        t(K.REFERRAL_INVITEE_GOT, popup.amount),
                        style = NavalType.title, color = Naval.amberStrong, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ReferralPopupKind.INVITER_PAID -> {
                    HudLabel(t(K.REFERRAL_INVITER_TITLE), Naval.muted)
                    Gap(10)
                    Text(
                        t(K.REFERRAL_INVITER_GOT, popup.amount),
                        style = NavalType.title, color = Naval.amberStrong, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Gap(20)
            PrimaryButton(t(K.GIFT_CLOSE)) { state.closeReferralPopup() }
        }
    }
}
