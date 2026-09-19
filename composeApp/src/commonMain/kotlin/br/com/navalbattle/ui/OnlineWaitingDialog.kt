package br.com.navalbattle.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Popup de espera do modo online — partida rápida procurando ou sala de amigo
 * esperando aceitar — com um radar girando pra deixar claro que o jogo está
 * fazendo alguma coisa, e um botão de cancelar pra não ficar preso esperando
 * pra sempre. Antes disso só existia um texto discreto na tela Online, que
 * sumia se o comandante saísse dela.
 */
@Composable
fun OnlineWaitingDialog(state: AppState) {
    val waiting = state.onlineLinkState == LinkState.SEARCHING || state.onlineLinkState == LinkState.HOSTING
    if (!waiting) return

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.line)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RadarSweep()
            Gap(16)
            HudLabel(
                if (state.onlineLinkState == LinkState.SEARCHING) t(K.ONLINE_WAIT_SEARCH_TITLE) else t(K.ONLINE_WAIT_HOST_TITLE),
                Naval.amberStrong
            )
            // convite direto de amigo: o código é só controle interno, o convidado
            // já recebe o popup de aceitar sem precisar digitar nada
            if (!state.onlineInvitedFriend) {
                state.onlineCode?.let { code ->
                    Gap(12)
                    HudLabel(t(K.ONLINE_YOUR_CODE), Naval.muted)
                    Gap(4)
                    androidx.compose.material3.Text(code, style = NavalType.display, color = Naval.amberStrong)
                }
            }
            Gap(20)
            SecondaryButton(t(K.AUTH_CANCEL)) { state.cancelOnlineWait() }
        }
    }
}

@Composable
private fun RadarSweep() {
    val angle by rememberInfiniteTransition(label = "radarSweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "radarSweepAngle"
    )
    Canvas(Modifier.size(96.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        listOf(1f, 0.66f, 0.33f).forEach { fraction ->
            drawCircle(
                color = Naval.green.copy(alpha = 0.35f),
                radius = radius * fraction,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
        rotate(angle, pivot = center) {
            drawLine(
                color = Naval.greenBright.copy(alpha = 0.8f),
                start = center,
                end = Offset(center.x + radius, center.y),
                strokeWidth = 2.dp.toPx()
            )
        }
        drawCircle(color = Naval.amberStrong, radius = 3.dp.toPx(), center = center)
    }
}
