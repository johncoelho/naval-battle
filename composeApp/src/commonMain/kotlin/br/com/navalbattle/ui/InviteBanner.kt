package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.OnlineMatch
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Convite de amigo mirado, mostrado por cima de qualquer tela enquanto o
 * comandante não está em partida nenhuma — sem isso, o convite só aparecia
 * pra quem já estivesse com a tela Online aberta.
 */
@Composable
fun InviteBanner(state: AppState, invite: OnlineMatch) {
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .padding(14.dp)
        ) {
            HudLabel(t(K.ONLINE_INVITE_BANNER, invite.hostName), Naval.amberStrong)
            Gap(12)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(t(K.FRIENDS_ACCEPT), modifier = Modifier.weight(1f)) { state.acceptInvite() }
                SecondaryButton(t(K.FRIENDS_DECLINE), modifier = Modifier.weight(1f)) { state.declineInvite() }
            }
        }
    }
}
