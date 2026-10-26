package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Faixa fixa do treino contra a IA enquanto a partida rápida ainda procura (#41). A IA é sempre
 * identificada como IA: nunca nome, patente ou avatar de jogador. "Parar de procurar" fecha a sala
 * e o treino segue como partida comum contra a IA.
 */
@Composable
fun PracticeBanner(state: AppState, match: Match) {
    if (!state.practiceWhileSearching || match.opponent != Opponent.AI) return
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
        HudLabel(t(K.PRACTICE_BANNER), Naval.amberStrong, Modifier.weight(1f))
        GapW(8)
        HudLabel(
            t(K.PRACTICE_STOP),
            Naval.danger,
            Modifier
                .border(1.dp, Naval.line)
                .clickable { state.stopSearchKeepPractice() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
