package br.com.navalbattle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import kotlin.math.sin

/** Duração total da abertura. Um toque na tela pula o resto. */
private const val SPLASH_MS = 3600

/** Jargão de praxe enquanto a frota se prepara. */
private val LOADING_LINES = listOf(K.LOAD_1, K.LOAD_2, K.LOAD_3, K.LOAD_4, K.LOAD_5)

/** Contatos que a varredura acende antes do nome entrar. */
private val BLIPS = listOf(
    Triple(0.33f, 0.30f, 0.30f),
    Triple(0.66f, 0.45f, 0.42f),
    Triple(0.42f, 0.58f, 0.54f)
)

/**
 * Abertura "Varredura": o radar do jogo vira a marca — a grade acende, a varredura
 * dá duas voltas acendendo contatos e o nome carimba num clarão verde.
 */
@Composable
fun SplashScreen(state: AppState) {
    val anim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(SPLASH_MS, easing = LinearEasing))
        state.afterSplash()
    }

    val p = anim.value
    val gridAlpha = ((p - 0.03f) / 0.14f).coerceIn(0f, 1f)
    val stampAt = 0.50f
    val stamp = ((p - stampAt) / 0.12f).coerceIn(0f, 1f)
    val flash = if (p < stampAt || p > stampAt + 0.09f) 0f
    else sin((p - stampAt) / 0.09f * 3.1416f) * 0.45f
    val line = t(LOADING_LINES[(p * LOADING_LINES.size).toInt().coerceIn(0, LOADING_LINES.lastIndex)])

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg)
            .pointerInput(Unit) { detectTapGestures { state.afterSplash() } }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.42f)
            val radius = size.width * 0.62f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Naval.abyss2, Naval.bg),
                    center = center,
                    radius = radius * 1.5f
                )
            )

            // grade da carta náutica
            val cell = size.width / 10f
            var g = 0f
            while (g <= size.width) {
                drawLine(Naval.gridLine.copy(alpha = 0.5f * gridAlpha), Offset(g, 0f), Offset(g, size.height), 1f)
                g += cell
            }
            g = 0f
            while (g <= size.height) {
                drawLine(Naval.gridLine.copy(alpha = 0.5f * gridAlpha), Offset(0f, g), Offset(size.width, g), 1f)
                g += cell
            }

            // anéis de alcance
            listOf(0.30f, 0.50f, 0.70f, 0.90f).forEach { f ->
                drawCircle(
                    color = Naval.greenBright.copy(alpha = 0.14f * gridAlpha),
                    radius = radius * f,
                    center = center,
                    style = Stroke(1f)
                )
            }
            drawLine(
                Naval.greenBright.copy(alpha = 0.12f * gridAlpha),
                Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y),
                1f
            )
            drawLine(
                Naval.greenBright.copy(alpha = 0.12f * gridAlpha),
                Offset(center.x, center.y - radius),
                Offset(center.x, center.y + radius),
                1f
            )

            // duas voltas da varredura, terminando no carimbo
            val sweepProgress = (p / stampAt).coerceIn(0f, 1f)
            if (sweepProgress < 1f) {
                rotate(sweepProgress * 720f, center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.00f to Naval.greenBright.copy(alpha = 0.34f),
                            0.10f to Naval.greenBright.copy(alpha = 0.06f),
                            0.30f to Color.Transparent,
                            1.00f to Color.Transparent,
                            center = center
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }

            // contatos acendendo conforme a varredura passa
            BLIPS.forEach { (fx, fy, at) ->
                val local = ((p - at) / 0.22f).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val pos = Offset(size.width * fx, size.height * fy)
                val fade = if (p > stampAt) (1f - stamp) else 1f
                drawCircle(
                    Naval.greenBright.copy(alpha = 0.22f * (1f - local) * fade),
                    radius = cell * (0.3f + local * 0.9f),
                    center = pos
                )
                drawCircle(
                    Naval.greenBright.copy(alpha = (0.35f + 0.65f * local) * fade),
                    radius = cell * 0.09f,
                    center = pos
                )
            }

            if (flash > 0f) drawRect(Naval.greenBright.copy(alpha = flash))
        }

        // marca carimbando no centro do radar
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(bottom = 56.dp)
                .alpha(stamp)
                .scale(1.3f - 0.3f * stamp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NAVAL", style = NavalType.display, color = Naval.ink, textAlign = TextAlign.Center)
            Text("BATTLE", style = NavalType.display, color = Naval.amberStrong, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(72.dp).height(2.dp).background(Naval.amber))
            Spacer(Modifier.height(8.dp))
            HudLabel(t(K.SPLASH_TAG), Naval.inkSoft)
        }

        // barra de carregamento com o jargão de bordo
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 32.dp, vertical = 26.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(line.uppercase(), Naval.inkSoft)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Naval.surface3)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.coerceIn(0.02f, 1f))
                        .height(3.dp)
                        .background(Naval.amber)
                )
            }
            Spacer(Modifier.height(8.dp))
            HudLabel("${(p * 100).toInt()}%", Naval.muted)
        }
    }
}
