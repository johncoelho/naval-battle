package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Bug ou sugestão, mandado direto pro servidor — revisado manualmente (ver
 * `supabase/feedback.sql`). Bug confirmado rende crédito, melhoria aceita rende
 * uma carga de habilidade; o comandante fica sabendo pelo [FeedbackRewardPopup]
 * na próxima vez que abrir o app.
 */
@Composable
fun FeedbackFormScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var isBug by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val needsAccount = !state.profile.signedIn

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar(t(K.SETTINGS_FEEDBACK), "")
        Gap(20)

        Text(t(K.FEEDBACK_FORM_TITLE_1).uppercase(), style = NavalType.display, color = Naval.ink)
        Text(t(K.FEEDBACK_FORM_TITLE_2).uppercase(), style = NavalType.display, color = Naval.amberStrong)
        Gap(8)
        HudLabel(t(K.FEEDBACK_FORM_SUB))

        Gap(24)

        if (needsAccount) {
            HudLabel(t(K.FEEDBACK_FORM_NEEDS_ACCOUNT), Naval.danger)
        } else if (state.feedbackSent) {
            HudLabel(t(K.FEEDBACK_FORM_SENT), Naval.greenBright)
        } else {
            HudLabel(t(K.FEEDBACK_FORM_KIND))
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(t(K.FEEDBACK_FORM_KIND_BUG), selected = isBug, modifier = Modifier.weight(1f)) {
                    isBug = true
                }
                ModeChip(t(K.FEEDBACK_FORM_KIND_IMPROVEMENT), selected = !isBug, modifier = Modifier.weight(1f)) {
                    isBug = false
                }
            }

            Gap(20)
            HudLabel(t(K.FEEDBACK_FORM_MESSAGE))
            Gap(6)
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp)
                    .background(Naval.surface2)
                    .border(1.dp, if (message.isBlank()) Naval.line else Naval.green)
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = message,
                    onValueChange = { if (it.length <= 800) message = it },
                    textStyle = NavalType.body.copy(color = Naval.ink),
                    cursorBrush = SolidColor(Naval.amberStrong),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (!needsAccount && !state.feedbackSent) {
            PrimaryButton(
                t(K.FEEDBACK_FORM_SEND),
                enabled = message.isNotBlank() && !state.feedbackSending
            ) {
                scope.launch { state.submitFeedback(if (isBug) "bug" else "improvement", message) }
            }
            Gap(8)
        }
        SecondaryButton(t(K.BACK)) { state.screen = Screen.SETTINGS }
    }
}
