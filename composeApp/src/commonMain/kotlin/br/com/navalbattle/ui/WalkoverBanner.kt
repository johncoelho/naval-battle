package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.WalkoverRules
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Faixa do posicionamento online quando o adversário aceitou a sala e não entrou (#85): mesmo padrão
 * da faixa de conexão da batalha (superfície 2, borda âmbar, título à esquerda e contagem à direita).
 * Só aparece depois de [WalkoverRules.BANNER_AFTER_SECONDS] sem sinal; ao zerar, o servidor dá a
 * vitória por ausência.
 */
@Composable
fun WalkoverBanner(state: AppState, match: Match) {
    if (match.opponent != Opponent.ONLINE) return
    val left = state.walkoverSecondsLeft
    if (!WalkoverRules.bannerVisible(left) || left == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Naval.surface2)
            .border(1.dp, Naval.amber)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            HudLabel(t(K.WO_WAITING), Naval.amberStrong)
            HudLabel(t(K.WO_WAITING_SUB, left), Naval.muted)
        }
        Text(left.toString().padStart(2, '0'), style = NavalType.title, color = Naval.amberStrong)
    }
}
