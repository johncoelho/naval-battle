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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Duração total da abertura. Um toque na tela pula o resto. */
private const val SPLASH_MS = 4200

/** Jargão de praxe enquanto a frota se prepara. */
private val LOADING_LINES = listOf(K.LOAD_1, K.LOAD_2, K.LOAD_3, K.LOAD_4, K.LOAD_5)

/** Navio da cena: classe e posição no plano do mar (frações da largura do plano). */
private data class SceneShip(val type: ShipClass, val x: Float, val y: Float)

private val FLEET = listOf(
    SceneShip(ShipClass.BATTLESHIP, 0.56f, 0.34f),
    SceneShip(ShipClass.CARRIER, 0.47f, 0.46f),
    SceneShip(ShipClass.CRUISER, 0.58f, 0.58f)
)

/** Tiros que caem na água: posição no plano e instante (ms) — em ciclo. */
private val SPLASHES = listOf(
    Triple(0.22f, 0.30f, 300L), Triple(0.84f, 0.50f, 900L), Triple(0.30f, 0.78f, 1500L),
    Triple(0.88f, 0.22f, 2100L), Triple(0.18f, 0.58f, 2700L), Triple(0.80f, 0.84f, 3300L)
)

/**
 * Abertura "Frota navegando": a frota do jogo em perspectiva sobre a carta de tiro,
 * com o radar girando, tiros caindo na água e um acerto em chamas no encouraçado —
 * enquanto a barra carrega com o jargão de convés. Os navios são os mesmos do jogo
 * ([drawShip], com torres girando e esteira), inclinados por uma câmera em 3D.
 */
@Composable
fun SplashScreen(state: AppState) {
    val anim = remember { Animatable(0f) }
    var now by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        anim.animateTo(1f, tween(SPLASH_MS, easing = LinearEasing))
        state.afterSplash()
    }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { now = it - start }
    }

    val p = anim.value
    val reveal = (p / 0.18f).coerceIn(0f, 1f)
    val stamp = ((p - 0.30f) / 0.14f).coerceIn(0f, 1f)
    val line = t(LOADING_LINES[(p * LOADING_LINES.size).toInt().coerceIn(0, LOADING_LINES.lastIndex)])

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17606C), Color(0xFF0A3540), Color(0xFF031018)),
                    center = Offset.Unspecified,
                    radius = 1400f
                )
            )
            .pointerInput(Unit) { detectTapGestures { state.afterSplash() } }
    ) {
        // o mar em perspectiva: plano inclinado pela câmera, maior que a tela para
        // não mostrar borda depois de girar
        Canvas(
            Modifier
                .fillMaxSize()
                .alpha(reveal)
                .graphicsLayer {
                    rotationX = 40f
                    rotationZ = -26f
                    scaleX = 1.2f
                    scaleY = 1.2f
                    translationY = -size.height * 0.10f
                    cameraDistance = 9f * density
                }
        ) {
            drawSea(now, state.skin)
        }

        // marca entrando por cima da cena
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(top = 220.dp)
                .alpha(stamp)
                .scale(1.15f - 0.15f * stamp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NAVAL", style = NavalType.display, color = Naval.ink, textAlign = TextAlign.Center)
            Text("BATTLE", style = NavalType.display, color = Naval.amberStrong, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(72.dp).height(2.dp).background(Naval.amber))
            Spacer(Modifier.height(8.dp))
            HudLabel(t(K.SPLASH_TAG), Naval.greenBright)
        }

        // barra de carregamento com o jargão de bordo
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2031018))))
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

private fun DrawScope.drawSea(now: Long, skin: br.com.navalbattle.design.Skin) {
    val w = size.width
    val h = size.height
    val t = now / 1000f

    // carta de tiro
    val cell = w / 9f
    var gx = (w / 2f) % cell
    while (gx < w) { drawLine(Naval.gridLine.copy(alpha = 0.55f), Offset(gx, 0f), Offset(gx, h), 2f); gx += cell }
    var gy = (h / 2f) % cell
    while (gy < h) { drawLine(Naval.gridLine.copy(alpha = 0.55f), Offset(0f, gy), Offset(w, gy), 2f); gy += cell }

    // radar: anéis, varredura girando e contatos piscando quando o feixe passa
    val c = Offset(w * 0.55f, h * 0.52f)
    val r = w * 0.62f
    listOf(0.33f, 0.66f, 1f).forEach { f ->
        drawCircle(Naval.greenBright.copy(alpha = 0.30f), radius = r * f, center = c, style = Stroke(2.5f))
    }
    val sweepDeg = (t * 90f) % 360f
    rotate(sweepDeg, c) {
        drawCircle(
            // borda acesa na frente do giro (horário), rastro apagando para trás
            brush = Brush.sweepGradient(
                0.00f to Color.Transparent,
                0.75f to Color.Transparent,
                0.91f to Naval.greenBright.copy(alpha = 0.10f),
                1.00f to Naval.greenBright.copy(alpha = 0.42f),
                center = c
            ),
            radius = r,
            center = c
        )
    }
    listOf(Offset(w * 0.86f, h * 0.30f) to Naval.amberStrong, Offset(w * 0.24f, h * 0.70f) to Naval.danger).forEach { (pos, col) ->
        val ang = (kotlin.math.atan2(pos.y - c.y, pos.x - c.x) * 180f / PI.toFloat() + 360f) % 360f
        val since = ((sweepDeg - ang + 360f) % 360f) / 90f
        val glow = (1f - since / 2.5f).coerceIn(0.2f, 1f)
        drawCircle(col.copy(alpha = 0.25f * glow), radius = cell * 0.35f * glow, center = pos)
        drawCircle(col.copy(alpha = glow), radius = cell * 0.09f, center = pos)
    }

    // tiros na água: anel de impacto abrindo e gotas subindo, em ciclo de 3,6s
    SPLASHES.forEach { (fx, fy, at) ->
        val age = ((now - at) % 3600L + 3600L) % 3600L
        if (age > 900L) return@forEach
        val u = age / 900f
        val pos = Offset(w * fx, h * fy)
        drawCircle(Color.White.copy(alpha = 0.65f * (1f - u)), radius = cell * (0.12f + 0.45f * u), center = pos, style = Stroke(4f))
        drawCircle(Color.White.copy(alpha = 0.35f * (1f - u)), radius = cell * (0.06f + 0.25f * u), center = pos, style = Stroke(3f))
        repeat(7) { k ->
            val a = k / 7f * 2f * PI.toFloat()
            val d = cell * 0.35f * u
            drawCircle(
                Color(0xFFE6F3F7).copy(alpha = 0.8f * (1f - u)),
                radius = 4f * (1f - u * 0.5f),
                center = Offset(pos.x + cos(a) * d, pos.y + sin(a) * d - cell * 0.2f * sin(u * PI.toFloat()))
            )
        }
        // marca de tiro na água que fica depois do splash (anel branco do jogo)
    }

    // frota: sombra, casco com espessura (camadas escuras) e o navio do jogo por cima
    val unit = w * 0.13f
    FLEET.forEach { s ->
        val len = unit * s.type.size
        val thick = unit * 0.82f
        val center = Offset(w * s.x, h * s.y)
        tinted(Color.Black.copy(alpha = 0.45f)) {
            drawShip(s.type, center + Offset(10f, 16f), len, thick, false, skin, 1f)
        }
        for (k in 5 downTo 1) {
            tinted(Color(0xFF0E1A10).copy(alpha = 0.9f - k * 0.08f)) {
                drawShip(s.type, center + Offset(0f, k * 2.2f), len, thick, false, skin, 1f)
            }
        }
        drawShip(s.type, center, len, thick, false, skin, 1f, animSeconds = t)
    }

    // acerto no encouraçado: clarão, fogo e fumaça, repetindo a cada 2,8s
    val bs = FLEET.first()
    val hitPos = Offset(w * bs.x + unit * 0.4f, h * bs.y)
    val hitAge = ((now - 1200L) % 2800L + 2800L) % 2800L
    if (now > 1200L && hitAge < 1600L) {
        val u = hitAge / 1600f
        // fumaça subindo e se espalhando
        repeat(4) { k ->
            val drift = Offset(-cell * 0.25f * u * (k + 1) * 0.6f, -cell * 0.5f * u * (k + 1) * 0.5f)
            drawCircle(
                Color(0xFF1A1E1B).copy(alpha = 0.55f * (1f - u)),
                radius = cell * (0.25f + 0.3f * u + k * 0.06f),
                center = hitPos + drift
            )
        }
        // bola de fogo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFF6D6), Color(0xFFFFC95C), Color(0xFFF0793A), Color.Transparent),
                center = hitPos,
                radius = cell * (0.2f + 0.45f * kotlin.math.sqrt(u))
            ),
            radius = cell * (0.2f + 0.45f * kotlin.math.sqrt(u)),
            center = hitPos,
            alpha = 1f - u * 0.8f
        )
        // fagulhas
        repeat(8) { k ->
            val a = k / 8f * 2f * PI.toFloat() + 0.3f
            val d = cell * 0.7f * u
            drawCircle(Color(0xFFFFD9A0).copy(alpha = 1f - u), radius = 3.5f, center = hitPos + Offset(cos(a) * d, sin(a) * d))
        }
    }
}

/** Desenha [block] inteiro numa cor só (silhueta) — sombra e espessura do casco. */
private fun DrawScope.tinted(color: Color, block: DrawScope.() -> Unit) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { colorFilter = ColorFilter.tint(color, BlendMode.SrcIn) })
        block()
        canvas.restore()
    }
}
