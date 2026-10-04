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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.OnlineMatch
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.delay

/**
 * Balão "partida rápida disponível" — aparece em qualquer tela para quem marcou
 * nos Ajustes que está disponível, quando alguém abre uma sala de partida rápida
 * esperando adversário (ver [AppState.pollQuickOffer]). Mesmo desenho do convite
 * de amigo ([InviteBanner]), mas aqui dá para dispensar: ninguém convidou este
 * comandante em particular, então "Agora não" só esconde a sala nesta sessão.
 */
@Composable
fun QuickOfferBanner(state: AppState, offer: OnlineMatch) {
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
                .border(1.dp, if (offer.ranked) Naval.amber else Naval.line)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(t(K.QUICK_OFFER_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.QUICK_OFFER_TITLE, offer.hostName),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            MatchTags(ranked = offer.ranked, mode = offer.mode)
            Gap(20)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.QUICK_OFFER_LATER), modifier = Modifier.weight(1f)) { state.dismissQuickOffer() }
                PrimaryButton(t(K.FRIENDS_ACCEPT), modifier = Modifier.weight(1f)) { state.acceptQuickOffer() }
            }
        }
    }
}

/**
 * Aviso curto de rodapé ("Essa partida já começou") — some sozinho depois de
 * alguns segundos, sem botão: é só para o comandante entender por que o
 * "Aceitar" não levou a lugar nenhum.
 */
@Composable
fun QuickOfferNotice(state: AppState, notice: String) {
    LaunchedEffect(notice) {
        delay(2600)
        state.clearQuickOfferNotice()
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface3)
                .border(1.dp, Naval.line)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(notice, style = NavalType.mono, color = Naval.ink, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Selos de tipo e modo da partida — "RANQUEADA · TÁTICO" — usados em todo balão
 * de partida (convite de amigo, partida rápida, adversário encontrado). Ranqueada
 * em âmbar, casual em tom apagado: dá para saber o que se está aceitando antes de
 * aceitar. [mode] é o nome do [GameMode] como viaja na sala ("CLASSIC"/"TACTICAL").
 */
@Composable
fun MatchTags(ranked: Boolean, mode: String, modifier: Modifier = Modifier) {
    val gameMode = GameMode.entries.firstOrNull { it.name == mode } ?: GameMode.CLASSIC
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .background(if (ranked) Naval.surface3 else Naval.surface)
                .border(1.dp, if (ranked) Naval.amber else Naval.line)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            HudLabel(
                t(if (ranked) K.ONLINE_MODE_RANKED else K.ONLINE_MODE_CASUAL),
                if (ranked) Naval.amberStrong else Naval.inkSoft
            )
        }
        HudLabel("·", Naval.muted)
        HudLabel(gameMode.label, Naval.inkSoft)
    }
}
