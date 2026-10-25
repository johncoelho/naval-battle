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
import br.com.navalbattle.game.LaunchVariant
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Aviso de lançamento (#36): "chegou à Google Play", só no Android, uma vez por conta. Testador do
 * beta lê o agradecimento; jogador novo, a boas-vindas. Ação única: "Entendi" (o voltar do sistema
 * faz o mesmo). Mesmo molde do [FeedbackRewardPopup].
 */
@Composable
fun LaunchNoticePopup(state: AppState) {
    val variant = state.launchNotice ?: return
    if (!state.launchNoticeShowing) return

    SystemBackHandler { state.ackLaunchNotice() }

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
            HudLabel(t(K.LAUNCH_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.LAUNCH_TITLE),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            Text(
                t(if (variant == LaunchVariant.TESTER) K.LAUNCH_BODY_TESTER else K.LAUNCH_BODY_NEW),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(20)
            PrimaryButton(t(K.LAUNCH_OK)) { state.ackLaunchNotice() }
        }
    }
}
