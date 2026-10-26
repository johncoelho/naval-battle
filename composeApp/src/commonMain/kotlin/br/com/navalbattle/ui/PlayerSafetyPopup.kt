package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.AppState.SafetyStep
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.ReportReason
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Popup compartilhado de denunciar e bloquear jogador (#58), aberto por `state.openPlayerActions(...)` no ranking,
 * na busca, nos amigos, no "adversário encontrado" e no resultado da partida online. Fica uma vez só na raiz do
 * app, por cima de qualquer tela. Molde padrão do design system: véu escuro, cartão `surface2` com borda âmbar,
 * `HudLabel` de contexto, título centralizado e a saída segura à esquerda.
 */
@Composable
fun PlayerSafetyHost(state: AppState) {
    val target = state.safetyTarget ?: return
    SystemBackHandler { state.closePlayerActions() }

    SafetyFrame(onDismiss = { state.closePlayerActions() }) {
        when (state.safetyStep) {
            SafetyStep.ACTIONS -> PlayerActionsPopup(
                name = target.name,
                blocked = state.isBlocked(target.userId),
                onReport = { state.safetyGoTo(SafetyStep.REASON) },
                onBlock = { state.safetyGoTo(SafetyStep.BLOCK_CONFIRM) },
                onUnblock = { state.unblockSelected() },
                onClose = { state.closePlayerActions() }
            )

            SafetyStep.REASON -> ReportReasonPopup(
                name = target.name,
                busy = state.safetyBusy,
                onPick = { state.reportSelected(it) },
                onBack = { state.safetyGoTo(SafetyStep.ACTIONS) }
            )

            SafetyStep.BLOCK_CONFIRM -> BlockConfirmPopup(
                name = target.name,
                busy = state.safetyBusy,
                onKeep = { state.safetyGoTo(SafetyStep.ACTIONS) },
                onBlock = { state.blockSelected() }
            )

            SafetyStep.RESULT -> {
                HudLabel(t(K.SAFETY_EYEBROW), Naval.muted)
                Gap(8)
                Text(
                    state.safetyResult.orEmpty(),
                    style = NavalType.body,
                    color = Naval.inkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Gap(20)
                PrimaryButton(t(K.SAFETY_CLOSE), modifier = Modifier.fillMaxWidth()) { state.closePlayerActions() }
            }
        }
    }
}

@Composable
private fun SafetyFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() }
            .navalSafeArea()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .navalPanelWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}

/** Nome, Denunciar, Bloquear (ou Desbloquear) e Fechar. */
@Composable
fun PlayerActionsPopup(
    name: String,
    blocked: Boolean,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onClose: () -> Unit
) {
    HudLabel(t(K.SAFETY_EYEBROW), Naval.muted)
    Gap(8)
    Text(
        name,
        style = NavalType.title,
        color = Naval.amberStrong,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    Gap(20)
    SecondaryButton(t(K.SAFETY_REPORT), modifier = Modifier.fillMaxWidth(), onClick = onReport)
    Gap(8)
    if (blocked) {
        SecondaryButton(t(K.SAFETY_UNBLOCK), modifier = Modifier.fillMaxWidth(), onClick = onUnblock)
    } else {
        SecondaryButton(t(K.SAFETY_BLOCK), modifier = Modifier.fillMaxWidth(), onClick = onBlock)
    }
    Gap(8)
    SecondaryButton(t(K.SAFETY_CLOSE), modifier = Modifier.fillMaxWidth(), onClick = onClose)
}

/** Os quatro motivos, cada um com título em cima à esquerda e legenda embaixo à direita. */
@Composable
fun ReportReasonPopup(name: String, busy: Boolean, onPick: (ReportReason) -> Unit, onBack: () -> Unit) {
    HudLabel(name, Naval.muted)
    Gap(8)
    Text(
        t(K.SAFETY_REASON_TITLE),
        style = NavalType.title,
        color = Naval.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Gap(18)
    SecondaryButton(
        t(K.SAFETY_REASON_NAME), t(K.SAFETY_REASON_NAME_SUB), enabled = !busy, modifier = Modifier.fillMaxWidth()
    ) { onPick(ReportReason.NAME) }
    Gap(8)
    SecondaryButton(
        t(K.SAFETY_REASON_ABUSE), t(K.SAFETY_REASON_ABUSE_SUB), enabled = !busy, modifier = Modifier.fillMaxWidth()
    ) { onPick(ReportReason.ABUSE) }
    Gap(8)
    SecondaryButton(
        t(K.SAFETY_REASON_CHEAT), t(K.SAFETY_REASON_CHEAT_SUB), enabled = !busy, modifier = Modifier.fillMaxWidth()
    ) { onPick(ReportReason.CHEAT) }
    Gap(8)
    SecondaryButton(
        t(K.SAFETY_REASON_OTHER), t(K.SAFETY_REASON_OTHER_SUB), enabled = !busy, modifier = Modifier.fillMaxWidth()
    ) { onPick(ReportReason.OTHER) }
    Gap(14)
    SecondaryButton(t(K.SAFETY_BLOCK_KEEP), modifier = Modifier.fillMaxWidth(), onClick = onBack)
}

/** Confirmação do bloqueio: ação destrutiva em `danger`, saída segura à esquerda. */
@Composable
fun BlockConfirmPopup(name: String, busy: Boolean, onKeep: () -> Unit, onBlock: () -> Unit) {
    HudLabel(t(K.SAFETY_EYEBROW), Naval.muted)
    Gap(8)
    Text(
        t(K.SAFETY_BLOCK_CONFIRM_TITLE, name),
        style = NavalType.title,
        color = Naval.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Gap(10)
    Text(
        t(K.SAFETY_BLOCK_CONFIRM_BODY),
        style = NavalType.body,
        color = Naval.inkSoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Gap(20)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(t(K.SAFETY_BLOCK_KEEP), modifier = Modifier.weight(1f).fillMaxHeight(), onClick = onKeep)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Naval.danger)
                .clickable(enabled = !busy, onClick = onBlock),
            contentAlignment = Alignment.Center
        ) {
            Text(t(K.SAFETY_BLOCK).uppercase(), style = NavalType.button, color = Naval.bg)
        }
    }
}

/** Aviso único: o servidor trocou o nome de guerra por violar as regras. */
@Composable
fun NameResetPopup(state: AppState) {
    if (!state.nameResetNotice) return
    SystemBackHandler { state.ackNameReset() }
    SafetyFrame(onDismiss = {}) {
        HudLabel(t(K.SAFETY_EYEBROW), Naval.muted)
        Gap(8)
        Text(
            t(K.NAME_RESET_TITLE),
            style = NavalType.title,
            color = Naval.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Gap(10)
        Text(
            t(K.NAME_RESET_BODY),
            style = NavalType.body,
            color = Naval.inkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Gap(20)
        PrimaryButton(t(K.NAME_RESET_OK), modifier = Modifier.fillMaxWidth()) { state.ackNameReset() }
    }
}

private const val SITE_URL = "https://johncoelho.github.io/naval-battle/"

/** Política de privacidade na língua do jogo (âncora #pt, #en ou #es). A seção Contato fica nela. */
internal fun privacyUrl(): String = SITE_URL + "privacy.html#" + br.com.navalbattle.i18n.I18n.lang.code

/** Regras da comunidade e termos de uso na língua do jogo. */
internal fun communityRulesUrl(): String = SITE_URL + "terms.html#" + br.com.navalbattle.i18n.I18n.lang.code

/** Linha do cadastro: "Ao criar a conta você aceita as Regras da comunidade." com o link das regras. */
@Composable
internal fun RulesAcceptLine() {
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    HudLabel(t(K.ACCOUNT_RULES_ACCEPT), Naval.muted)
    Gap(4)
    HudLabel(
        t(K.ACCOUNT_RULES_LINK),
        Naval.amberStrong,
        Modifier.clickable { uri.openUri(communityRulesUrl()) }.padding(vertical = 4.dp)
    )
}
