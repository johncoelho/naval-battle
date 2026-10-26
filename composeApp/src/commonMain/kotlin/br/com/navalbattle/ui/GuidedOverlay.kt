package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.GuidedStep
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Dica da primeira partida guiada (#43): painel compacto "Passo N de 4" com título, uma linha
 * de instrução e o "Pular" sempre visível. Fica no fluxo da tela (posicionamento) ou fixo
 * embaixo (batalha); nunca realça casa do alvo nem mexe no estado de célula. Quando o passo é
 * o último, mostra "Concluir". Sem token novo: superfície, borda âmbar e rótulos HUD.
 */
@Composable
fun GuidedTip(
    step: GuidedStep,
    onSkip: () -> Unit,
    onFinish: () -> Unit = onSkip,
    modifier: Modifier = Modifier
) {
    if (step == GuidedStep.DONE) return
    val (title, body) = when (step) {
        GuidedStep.PLACE -> K.GUIDED_P1_TITLE to K.GUIDED_P1_BODY
        GuidedStep.FIRE -> K.GUIDED_P2_TITLE to K.GUIDED_P2_BODY
        GuidedStep.HIT -> K.GUIDED_P3_TITLE to K.GUIDED_P3_BODY
        else -> K.GUIDED_P4_TITLE to K.GUIDED_P4_BODY
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.amber)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudLabel(t(K.GUIDED_STEP, step.ordinal + 1), Naval.muted)
            if (step == GuidedStep.SUNK) {
                HudLabel(
                    t(K.GUIDED_FINISH),
                    Naval.amberStrong,
                    Modifier.clickable(onClick = onFinish).padding(horizontal = 6.dp, vertical = 4.dp)
                )
            } else {
                HudLabel(
                    t(K.GUIDED_SKIP),
                    Naval.danger,
                    Modifier.clickable(onClick = onSkip).padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
        Text(t(title), style = NavalType.title, color = Naval.amberStrong)
        Text(t(body), style = NavalType.body, color = Naval.inkSoft)
    }
}
