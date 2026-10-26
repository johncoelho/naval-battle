package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.shareText
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.ReferralOutcome
import br.com.navalbattle.game.REFERRAL_SHOWN_DOUBLOONS
import br.com.navalbattle.game.REFERRAL_SHOWN_TOTAL_LIMIT
import br.com.navalbattle.game.isValidReferralCodeShape
import br.com.navalbattle.game.referralFailureText
import br.com.navalbattle.game.referralPaidCount
import br.com.navalbattle.game.referralShareText
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Cartão "Convidar amigo" da tela Amigos (#44): o código da conta, Compartilhar e o campo para digitar o
 * código de quem convidou. O servidor decide e paga; aqui só se mostra o valor fixo antes do resgate.
 */
@Composable
fun ReferralCard(state: AppState) {
    val scope = rememberCoroutineScope()
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<K?>(null) }
    val signedIn = state.profile.signedIn

    LaunchedEffect(signedIn) {
        if (signedIn) {
            state.loadReferralCode()
            state.loadReferralStatus()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(14.dp)
    ) {
        HudLabel(t(K.REFERRAL_TITLE), Naval.amberStrong)
        Gap(6)
        if (!signedIn) {
            Text(t(K.REFERRAL_NEED_ACCOUNT), style = NavalType.body, color = Naval.inkSoft)
        } else {
            Text(
                t(K.REFERRAL_BODY, REFERRAL_SHOWN_DOUBLOONS, REFERRAL_SHOWN_TOTAL_LIMIT),
                style = NavalType.body, color = Naval.inkSoft
            )
            Gap(10)
            val code = state.referralCode
            HudLabel(t(K.REFERRAL_YOUR_CODE), Naval.muted)
            Gap(4)
            Text(
                code ?: t(K.REFERRAL_WORKING),
                style = NavalType.title, color = if (code != null) Naval.ink else Naval.muted
            )
            if (state.referralInvites.isNotEmpty()) {
                Gap(4)
                HudLabel(
                    t(K.REFERRAL_PAID_COUNT, referralPaidCount(state.referralInvites), REFERRAL_SHOWN_TOTAL_LIMIT),
                    Naval.muted
                )
            }
            if (code != null) {
                Gap(10)
                PrimaryButton(t(K.REFERRAL_SHARE)) { shareText(referralShareText(code, I18n.lang)) }
            }
            Gap(8)
            if (!typing) {
                SecondaryButton(t(K.REFERRAL_HAVE_CODE)) { typing = true; failure = null }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier
                            .weight(1f)
                            .background(Naval.bg)
                            .border(1.dp, if (text.isBlank()) Naval.line else Naval.amber)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it.take(12); failure = null },
                            singleLine = true,
                            textStyle = NavalType.mono.copy(color = Naval.ink),
                            cursorBrush = SolidColor(Naval.amberStrong),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {}),
                            modifier = Modifier.weight(1f)
                        ) { inner ->
                            Box(Modifier.fillMaxWidth()) {
                                if (text.isEmpty()) Text(t(K.REFERRAL_HINT), style = NavalType.mono, color = Naval.muted)
                                inner()
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val ready = isValidReferralCodeShape(text) && !busy
                    Box(
                        Modifier
                            .background(if (ready) Naval.amber else Naval.surface2)
                            .border(1.dp, if (ready) Naval.amber else Naval.line)
                            .let { m ->
                                if (ready) m.clickable {
                                    busy = true
                                    scope.launch {
                                        val outcome = state.redeemReferral(text)
                                        busy = false
                                        if (outcome == ReferralOutcome.OK) {
                                            typing = false
                                            text = ""
                                        } else {
                                            failure = referralFailureText(outcome)
                                        }
                                    }
                                } else m
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            t(if (busy) K.REFERRAL_WORKING else K.REFERRAL_REDEEM).uppercase(),
                            style = NavalType.button, color = if (ready) Naval.amberInk else Naval.muted, maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                failure?.let {
                    Gap(6)
                    HudLabel(t(it), Naval.danger)
                }
            }
        }
    }
}
