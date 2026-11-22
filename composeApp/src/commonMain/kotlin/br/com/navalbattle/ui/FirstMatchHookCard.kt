package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.platformName
import br.com.navalbattle.data.shareStoreListing
import br.com.navalbattle.data.shareText
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.FirstMatchHook
import br.com.navalbattle.game.HookAction
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.referralShareText
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Cartão do fim da 1ª partida contra o adversário virtual (#135): desafio de amanhã (o lembrete diário), revanche e
 * chamar um amigo, na ordem da regra ([FirstMatchHook.actions]). Fica no lugar do botão de revanche da tela de
 * resultado, sem tela extra e sem recompensa nova. Um popup por vez: enquanto está na tela conta como popup aberto.
 */
@Composable
fun FirstMatchHookCard(state: AppState, match: Match, victory: Boolean) {
    val scope = rememberCoroutineScope()
    val signedIn = state.profile.signedIn
    val actions = FirstMatchHook.actions(victory, signedIn, ios = platformName == "ios")

    LaunchedEffect(signedIn) { if (signedIn) state.loadReferralCode() }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(14.dp)
    ) {
        HudLabel(t(if (victory) K.HOOK_WIN_TITLE else K.HOOK_LOSS_TITLE), Naval.amberStrong)
        Gap(6)
        Text(t(if (victory) K.HOOK_WIN_BODY else K.HOOK_LOSS_BODY), style = NavalType.body, color = Naval.inkSoft)
        actions.forEachIndexed { index, action ->
            Gap(if (index == 0) 12 else 8)
            val primary = index == 0
            when (action) {
                HookAction.REMATCH -> RematchSection(
                    state, match, label = K.HOOK_REMATCH, primary = primary,
                    onTap = { state.onHookAction(HookAction.REMATCH, victory) }
                )

                HookAction.CHALLENGE -> {
                    val done = state.hookChallengeDone
                    val title = t(if (done) K.HOOK_CHALLENGE_DONE else K.HOOK_CHALLENGE)
                    val sub = if (done) null else t(if (victory) K.HOOK_CHALLENGE_SUB_WIN else K.HOOK_CHALLENGE_SUB_LOSS)
                    if (primary) {
                        PrimaryButton(title, sub, enabled = !done) { state.onHookAction(HookAction.CHALLENGE, victory) }
                    } else {
                        SecondaryButton(title, sub, enabled = !done) { state.onHookAction(HookAction.CHALLENGE, victory) }
                    }
                }

                HookAction.INVITE -> {
                    val title = t(K.HOOK_INVITE)
                    val sub = t(if (victory) K.HOOK_INVITE_SUB_WIN else K.HOOK_INVITE_SUB_LOSS)
                    val tap: () -> Unit = {
                        state.onHookAction(HookAction.INVITE, victory)
                        scope.launch {
                            // com conta: o texto e o codigo de convite de sempre (sem valor de premio); sem conta, o link da loja
                            if (signedIn) state.loadReferralCode()
                            val code = state.referralCode
                            if (signedIn && code != null) shareText(referralShareText(code, I18n.lang)) else shareStoreListing()
                        }
                    }
                    if (primary) PrimaryButton(title, sub, onClick = tap) else SecondaryButton(title, sub, onClick = tap)
                }
            }
        }
    }
}
