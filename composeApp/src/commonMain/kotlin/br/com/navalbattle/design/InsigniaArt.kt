package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.navalbattle.game.Insignia
import kotlin.math.cos
import kotlin.math.sin

/**
 * Insígnias desenhadas a traço, num quadrado de lado [size] centrado em [center].
 * Nada de imagem no APK: a mesma arte serve em qualquer resolução.
 */
fun DrawScope.drawInsignia(insignia: Insignia, center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val w = size * 0.055f
    val stroke = Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)

    when (insignia) {
        Insignia.ANCHOR -> {
            drawCircle(color, radius = s * 0.18f, center = Offset(center.x, center.y - s * 0.72f), style = stroke)
            drawLine(color, Offset(center.x, center.y - s * 0.54f), Offset(center.x, center.y + s * 0.78f), w)
            drawLine(color, Offset(center.x - s * 0.42f, center.y - s * 0.32f), Offset(center.x + s * 0.42f, center.y - s * 0.32f), w)
            drawArc(
                color = color,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - s * 0.7f, center.y - s * 0.1f),
                size = Size(s * 1.4f, s * 1.1f),
                style = stroke
            )
        }

        Insignia.TRIDENT -> {
            drawLine(color, Offset(center.x, center.y - s * 0.5f), Offset(center.x, center.y + s * 0.85f), w)
            listOf(-1f, 1f).forEach { d ->
                drawLine(color, Offset(center.x + d * s * 0.5f, center.y - s * 0.85f), Offset(center.x + d * s * 0.5f, center.y - s * 0.2f), w)
                drawLine(color, Offset(center.x + d * s * 0.5f, center.y - s * 0.2f), Offset(center.x, center.y - s * 0.05f), w)
            }
            drawLine(color, Offset(center.x, center.y - s * 0.95f), Offset(center.x, center.y - s * 0.2f), w)
            drawLine(color, Offset(center.x - s * 0.3f, center.y + s * 0.5f), Offset(center.x + s * 0.3f, center.y + s * 0.5f), w)
        }

        Insignia.STAR -> {
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) s * 0.92f else s * 0.4f
                val a = (-90f + i * 36f) * 3.1415926f / 180f
                val p = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color, style = stroke)
        }

        Insignia.WHEEL -> {
            drawCircle(color, radius = s * 0.55f, center = center, style = stroke)
            drawCircle(color, radius = s * 0.16f, center = center, style = stroke)
            for (i in 0 until 8) {
                val a = (i * 45f) * 3.1415926f / 180f
                val from = Offset(center.x + cos(a) * s * 0.5f, center.y + sin(a) * s * 0.5f)
                val to = Offset(center.x + cos(a) * s * 0.92f, center.y + sin(a) * s * 0.92f)
                drawLine(color, from, to, w)
            }
        }

        Insignia.WAVES -> {
            for (row in 0 until 3) {
                val y = center.y - s * 0.4f + row * s * 0.42f
                val path = Path().apply {
                    moveTo(center.x - s * 0.85f, y)
                    cubicTo(center.x - s * 0.45f, y - s * 0.3f, center.x - s * 0.05f, y + s * 0.3f, center.x + s * 0.3f, y)
                    cubicTo(center.x + s * 0.55f, y - s * 0.22f, center.x + s * 0.7f, y - s * 0.05f, center.x + s * 0.85f, y - s * 0.02f)
                }
                drawPath(path, color, style = stroke)
            }
        }

        Insignia.SKULL -> {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - s * 0.6f, center.y - s * 0.75f),
                size = Size(s * 1.2f, s * 1.1f),
                style = stroke
            )
            drawRect(
                color = color,
                topLeft = Offset(center.x - s * 0.6f, center.y - s * 0.2f),
                size = Size(s * 1.2f, s * 0.42f),
                style = stroke
            )
            listOf(-1f, 1f).forEach { d ->
                drawCircle(color, radius = s * 0.16f, center = Offset(center.x + d * s * 0.26f, center.y - s * 0.16f))
            }
            // ossos cruzados
            listOf(-1f, 1f).forEach { d ->
                drawLine(
                    color,
                    Offset(center.x - s * 0.85f, center.y + s * 0.4f * d + s * 0.35f),
                    Offset(center.x + s * 0.85f, center.y - s * 0.4f * d + s * 0.35f),
                    w * 0.8f
                )
            }
        }
    }
    // moldura hexagonal comum a todas, para virarem um brasão
    val frame = Path()
    for (i in 0 until 6) {
        val a = (-90f + i * 60f) * 3.1415926f / 180f
        val p = Offset(center.x + cos(a) * s * 1.35f, center.y + sin(a) * s * 1.35f)
        if (i == 0) frame.moveTo(p.x, p.y) else frame.lineTo(p.x, p.y)
    }
    frame.close()
    drawPath(frame, color.copy(alpha = 0.35f), style = Stroke(width = size * 0.03f))
}
