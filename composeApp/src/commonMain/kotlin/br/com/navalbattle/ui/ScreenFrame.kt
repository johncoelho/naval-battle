package br.com.navalbattle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.game.Opponent

/**
 * Moldura do roteador de telas: em tela grande (tablet, dobrável, Chromebook) o conteúdo fica
 * centralizado com no máximo [Naval.contentMaxWidth], em vez de esticar. No celular (< 600 dp)
 * não muda nada. A batalha passa dessa largura quando precisa: lado a lado em paisagem
 * ([boardLayout]) e no modo local (mesmo aparelho), que já ocupa a tela toda. Os popups ficam
 * fora desta moldura (o véu cobre a tela) e limitam a si mesmos com [navalPanelWidth].
 */
@Composable
fun ScreenFrame(state: AppState, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val local = state.match?.opponent == Opponent.LOCAL
        val sideBySide = boardLayout(maxWidth.value, maxHeight.value).sideBySide
        val limit = when {
            state.screen == Screen.BATTLE && local -> maxWidth // mesmo aparelho: a tela toda
            state.screen == Screen.BATTLE && sideBySide -> minOf(maxWidth, SIDE_BY_SIDE_MAX_WIDTH_DP.dp)
            else -> Naval.contentMaxWidth
        }
        Box(Modifier.fillMaxHeight().widthIn(max = limit).fillMaxWidth()) {
            content()
        }
    }
}
