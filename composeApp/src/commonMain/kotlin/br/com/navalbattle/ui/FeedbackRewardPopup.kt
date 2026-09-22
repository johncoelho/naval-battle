package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import br.com.navalbattle.data.FeedbackUpdate
import br.com.navalbattle.game.Ability
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Aviso automático de que um feedback foi avaliado — nasce de [AppState.checkFeedbackRewards],
 * chamado na abertura do app (mesmo padrão sem push de verdade do [SeasonPopup]). Some
 * ao fechar e não volta a aparecer pra esse feedback (ver [AppState.ackFeedbackReward]).
 */
@Composable
fun FeedbackRewardPopup(state: AppState) {
    val reward = state.feedbackReward ?: return
    if (state.updateAvailable || state.seasonPopupNeeded || state.pendingInvite != null) return

    val approved = reward.status == "approved"
    val accent = if (approved) Naval.amber else Naval.line

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
                .border(1.dp, accent)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(t(K.FEEDBACK_REWARD_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.FEEDBACK_REWARD_TITLE),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            HudLabel(rewardDescription(reward), Naval.inkSoft)
            Gap(20)
            PrimaryButton(t(K.FEEDBACK_REWARD_CLOSE)) { state.ackFeedbackReward() }
        }
    }
}

private fun rewardDescription(reward: FeedbackUpdate): String {
    if (reward.status != "approved") return t(K.FEEDBACK_REWARD_REJECTED)
    if (reward.rewardCredits > 0) return t(K.FEEDBACK_REWARD_CREDITS, reward.rewardCredits)
    val ability = reward.rewardAbilityCode?.let { code -> Ability.entries.firstOrNull { it.code == code } }
    if (ability != null) return t(K.FEEDBACK_REWARD_ABILITY, t(ability.key))
    return t(K.FEEDBACK_REWARD_REJECTED)
}
