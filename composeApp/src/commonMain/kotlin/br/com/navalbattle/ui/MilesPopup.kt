package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawCompassRose
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Milhas náuticas: saldo, regras, quanto falta para a recarga e a compra de um pacote
 * com dobrões. Abre pelo selo do topo do menu ou sozinho quando falta milha para
 * entrar numa partida online. Tocar fora fecha.
 */
@Composable
fun MilesPopup(state: AppState) {
    if (!state.milesPopup) return
    val m = state.miles ?: return
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.milesPopup = false }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(64.dp)) {
                drawCompassRose(Offset(size.width / 2f, size.height / 2f), size.minDimension)
            }
            Gap(10)
            HudLabel(t(K.MILES_TITLE), Naval.muted)
            Gap(4)
            Text(
                t(K.MILES_BALANCE, m.miles),
                style = NavalType.title,
                color = if (m.miles > 0) Naval.ink else Naval.danger,
                textAlign = TextAlign.Center
            )
            if (m.miles < 1) {
                Gap(6)
                Text(t(K.MILES_EMPTY), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            }
            Gap(12)
            Text(
                t(K.MILES_EXPLAIN, m.daily, m.rankedWin, m.casualWin),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center
            )
            Gap(6)
            HudLabel(t(K.MILES_OFFLINE_FREE), Naval.muted)
            Gap(10)
            HudLabel(t(K.MILES_RESETS, countdown(m.resetsInSeconds)), Naval.amberStrong)

            Gap(18)
            // pacote de milhas pago com dobrões — mesmo selo de preço da loja
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface)
                    .border(1.dp, if (state.profile.credits >= m.packPrice) Naval.amber else Naval.lineSoft)
                    .clickable { scope.launch { state.buyMilesPack() } }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(28.dp)) {
                    drawCompassRose(Offset(size.width / 2f, size.height / 2f), size.minDimension)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(K.MILES_BUY, m.packSize).uppercase(), style = NavalType.mono, color = Naval.ink)
                    HudLabel(t(K.MILES_CARD_SUB), Naval.muted)
                }
                CoinLabel(m.packPrice.toString(), style = NavalType.mono)
            }
            state.milesNotice?.let {
                Gap(10)
                HudLabel(it, Naval.amberStrong)
            }
            Gap(16)
            SecondaryButton(t(K.MILES_CLOSE)) { state.milesPopup = false }
        }
    }
}

/** "5h 12min" / "12min" — contador até a meia-noite de Brasília. */
fun countdown(seconds: Int): String {
    val h = seconds / 3600
    val min = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${min.toString().padStart(2, '0')}min" else "${min}min"
}
