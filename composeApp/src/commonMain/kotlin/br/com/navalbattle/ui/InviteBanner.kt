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
import br.com.navalbattle.data.OnlineMatch
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Convite de amigo mirado, num popup de verdade por cima de qualquer tela —
 * antes era só uma faixa fina no topo, fácil de não notar (motivo mais provável
 * de o convite "não funcionar" na prática). Só fecha pelos botões, de propósito:
 * não dá pra ignorar sem responder, já que quem convidou fica esperando.
 */
@Composable
fun InviteBanner(state: AppState, invite: OnlineMatch) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .padding(20.dp)
        ) {
            HudLabel(t(K.ONLINE_INVITE_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.ONLINE_INVITE_BANNER, invite.hostName),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            // convite de amigo é sempre casual, mas o selo aparece igual: quem
            // aceita sabe na hora que não vale ranking, e em que modo vai jogar
            MatchTags(ranked = invite.ranked, mode = invite.mode, modifier = Modifier.align(Alignment.CenterHorizontally))
            Gap(20)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.FRIENDS_DECLINE), modifier = Modifier.weight(1f)) { state.declineInvite() }
                PrimaryButton(t(K.FRIENDS_ACCEPT), modifier = Modifier.weight(1f)) { state.acceptInvite() }
            }
        }
    }
}
