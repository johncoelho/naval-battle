package br.com.navalbattle.ui

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aviso de temporada nova — só aparece se o comandante ainda não aceitou a
 * temporada corrente (comparação feita no servidor, ver [AppState.seasonPopupNeeded]).
 * Aceitar libera a partida rápida ranqueada; sem isso, o toggle fica bloqueado.
 * O ícone e a cor mudam com a estação — mais fácil de sentir que é algo novo
 * do que só trocar o texto.
 */
@Composable
fun SeasonPopup(state: AppState) {
    val season = state.currentSeason ?: return
    if (!state.profile.signedIn || !state.seasonPopupNeeded) return
    if (state.updateAvailable || state.pendingInvite != null || state.match != null) return

    // "2026-outono" -> "outono": o nome que volta do servidor só existe em
    // português, então o sufixo da chave é o que dá pra traduzir de verdade
    val suffix = season.seasonKey.substringAfterLast('-')
    val accent = seasonAccent(suffix)
    val name = t(seasonNameKey(suffix))

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
                .border(1.dp, accent)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SeasonIcon(suffix, accent)
            Gap(14)
            HudLabel(t(K.SEASON_POPUP_EYEBROW), Naval.muted)
            Gap(8)
            Text(
                t(K.SEASON_POPUP_TITLE, name),
                style = NavalType.title,
                color = Naval.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Gap(10)
            HudLabel(t(K.SEASON_POPUP_SUB), Naval.inkSoft)
            Gap(20)
            PrimaryButton(t(K.SEASON_POPUP_JOIN)) { state.acceptSeason() }
        }
    }
}

private fun seasonNameKey(suffix: String) = when (suffix) {
    "verao" -> K.SEASON_NAME_VERAO
    "outono" -> K.SEASON_NAME_OUTONO
    "inverno" -> K.SEASON_NAME_INVERNO
    else -> K.SEASON_NAME_PRIMAVERA
}

private fun seasonAccent(suffix: String): Color = when (suffix) {
    "verao" -> Color(0xFFFFC95C) // sol de verão — o próprio âmbar do jogo
    "outono" -> Color(0xFFE07A3F) // folha seca
    "inverno" -> Color(0xFF7AD1E0) // gelo, o mesmo ciano do circuito da fábrica
    else -> Color(0xFF8ED17A) // brotos da primavera — o verde já usado no jogo
}

/** Um ícone por estação, tudo vetor — nenhuma imagem, igual ao resto do jogo. */
@Composable
private fun SeasonIcon(suffix: String, accent: Color) {
    Canvas(Modifier.size(64.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        when (suffix) {
            "verao" -> {
                // sol: núcleo cheio + raios curtos ao redor
                drawCircle(color = accent, radius = r * 0.42f, center = c)
                repeat(8) { i ->
                    val a = (i / 8f) * 2f * kotlin.math.PI.toFloat()
                    val inner = r * 0.62f
                    drawLine(
                        color = accent,
                        start = Offset(c.x + inner * cos(a), c.y + inner * sin(a)),
                        end = Offset(c.x + r * cos(a), c.y + r * sin(a)),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            "outono" -> {
                // folha: duas curvas formando a lâmina + nervura central + cabinho
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y - r * 0.85f)
                    quadraticTo(c.x + r * 0.95f, c.y - r * 0.2f, c.x, c.y + r * 0.75f)
                    quadraticTo(c.x - r * 0.95f, c.y - r * 0.2f, c.x, c.y - r * 0.85f)
                    close()
                }
                drawPath(path, color = accent, style = Stroke(width = 3.dp.toPx()))
                drawLine(
                    color = accent,
                    start = Offset(c.x, c.y - r * 0.7f),
                    end = Offset(c.x, c.y + r * 0.75f),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = accent,
                    start = Offset(c.x, c.y + r * 0.75f),
                    end = Offset(c.x + r * 0.18f, c.y + r * 0.98f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            "inverno" -> {
                // floco de neve: três eixos cruzados, cada um com duas farpas
                repeat(3) { i ->
                    val a = (i / 3f) * kotlin.math.PI.toFloat()
                    val dx = r * cos(a)
                    val dy = r * sin(a)
                    drawLine(accent, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy), 2.5.dp.toPx(), StrokeCap.Round)
                    listOf(0.35f, 0.7f).forEach { t ->
                        val bx = c.x + dx * t
                        val by = c.y + dy * t
                        val branch = r * 0.22f
                        val a1 = a + kotlin.math.PI.toFloat() / 4f
                        val a2 = a - kotlin.math.PI.toFloat() / 4f
                        drawLine(accent, Offset(bx, by), Offset(bx + branch * cos(a1), by + branch * sin(a1)), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(accent, Offset(bx, by), Offset(bx + branch * cos(a2), by + branch * sin(a2)), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
            }

            else -> {
                // primavera: broto — caule com duas folhas e uma flor de 5 pétalas no topo
                drawLine(accent, Offset(c.x, c.y + r), Offset(c.x, c.y - r * 0.15f), 3.dp.toPx(), StrokeCap.Round)
                listOf(-1f, 1f).forEach { side ->
                    val leaf = androidx.compose.ui.graphics.Path().apply {
                        moveTo(c.x, c.y + r * 0.25f)
                        quadraticTo(c.x + side * r * 0.6f, c.y + r * 0.05f, c.x, c.y - r * 0.05f)
                        close()
                    }
                    drawPath(leaf, color = accent, style = Stroke(width = 2.5.dp.toPx()))
                }
                repeat(5) { i ->
                    val a = (i / 5f) * 2f * kotlin.math.PI.toFloat()
                    val petal = Offset(c.x + r * 0.4f * cos(a), c.y - r * 0.55f + r * 0.4f * sin(a))
                    drawCircle(color = accent, radius = r * 0.22f, center = petal)
                }
                drawCircle(color = Naval.amberInk, radius = r * 0.16f, center = Offset(c.x, c.y - r * 0.55f))
            }
        }
    }
}
