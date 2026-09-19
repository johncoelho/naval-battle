package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.navalbattle.game.Medal
import kotlin.math.cos
import kotlin.math.sin

/**
 * Condecoração desenhada a vetor: fita de suspensão em cima e a peça embaixo. A
 * conquistada vem cheia, na cor do jogo; a que falta vem só de contorno em cinza —
 * mesma silhueta, para dar de olhar e reconhecer o que ainda se pode ganhar.
 */
fun DrawScope.drawMedal(medal: Medal, center: Offset, size: Float, earned: Boolean) {
    val gold = if (earned) Naval.amber else Naval.muted
    val bright = if (earned) Naval.amberStrong else Naval.muted
    val ribbon = if (earned) Color(0xFF8C3B22) else Naval.line
    val stripe = if (earned) Naval.amberStrong else Naval.muted.copy(alpha = 0.5f)
    val alpha = if (earned) 1f else 0.5f
    val s = size / 2f

    // fita
    val ribbonW = s * 0.9f
    val ribbonH = s * 0.66f
    drawRect(
        ribbon,
        topLeft = Offset(center.x - ribbonW / 2f, center.y - s),
        size = Size(ribbonW, ribbonH),
        alpha = alpha
    )
    listOf(-0.18f, 0.18f).forEach { f ->
        drawRect(
            stripe,
            topLeft = Offset(center.x + ribbonW * f - ribbonW * 0.08f, center.y - s),
            size = Size(ribbonW * 0.16f, ribbonH),
            alpha = alpha
        )
    }

    val pc = Offset(center.x, center.y + s * 0.26f)
    val r = s * 0.64f
    val stroke = Stroke(width = size * 0.055f, cap = StrokeCap.Round)

    when (medal) {
        // caçador de frota: alvo cheio, a única de disco maciço
        Medal.FLEET_HUNTER -> {
            if (earned) drawCircle(gold, radius = r, center = pc)
            drawCircle(bright, radius = r, center = pc, style = Stroke(size * 0.045f), alpha = alpha)
            val ink = if (earned) Naval.amberInk else Naval.muted
            drawCircle(ink, radius = r * 0.5f, center = pc, style = Stroke(size * 0.05f), alpha = alpha)
            drawLine(ink, Offset(pc.x, pc.y - r * 0.92f), Offset(pc.x, pc.y + r * 0.92f), size * 0.05f, alpha = alpha)
            drawLine(ink, Offset(pc.x - r * 0.92f, pc.y), Offset(pc.x + r * 0.92f, pc.y), size * 0.05f, alpha = alpha)
        }

        // estreia no mar: estrela de cinco pontas
        Medal.FIRST_SORTIE -> {
            val p = Path()
            for (i in 0 until 10) {
                val rad = if (i % 2 == 0) r else r * 0.44f
                val a = -PI / 2f + i * PI / 5f
                val x = pc.x + rad * cos(a)
                val y = pc.y + rad * sin(a)
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            if (earned) drawPath(p, gold) else drawPath(p, gold, alpha = alpha, style = stroke)
        }

        // atirador de elite: retículo de mira
        Medal.SHARPSHOOTER -> {
            drawCircle(gold, radius = r * 0.78f, center = pc, style = stroke, alpha = alpha)
            drawCircle(gold, radius = r * 0.22f, center = pc, alpha = alpha)
            listOf(-1f, 1f).forEach { d ->
                drawLine(gold, Offset(pc.x + d * r * 0.4f, pc.y), Offset(pc.x + d * r, pc.y), size * 0.05f, alpha = alpha, cap = StrokeCap.Round)
                drawLine(gold, Offset(pc.x, pc.y + d * r * 0.4f), Offset(pc.x, pc.y + d * r), size * 0.05f, alpha = alpha, cap = StrokeCap.Round)
            }
        }

        // ataque relâmpago: raio
        Medal.BLITZ -> {
            val p = Path().apply {
                moveTo(pc.x + r * 0.34f, pc.y - r)
                lineTo(pc.x - r * 0.5f, pc.y + r * 0.14f)
                lineTo(pc.x + r * 0.02f, pc.y + r * 0.14f)
                lineTo(pc.x - r * 0.24f, pc.y + r)
                lineTo(pc.x + r * 0.56f, pc.y - r * 0.1f)
                lineTo(pc.x + r * 0.04f, pc.y - r * 0.1f)
                close()
            }
            if (earned) drawPath(p, gold) else drawPath(p, gold, alpha = alpha, style = stroke)
        }

        // casco intacto: âncora
        Medal.UNTOUCHED -> {
            drawCircle(gold, radius = r * 0.24f, center = Offset(pc.x, pc.y - r * 0.74f), style = stroke, alpha = alpha)
            drawLine(gold, Offset(pc.x, pc.y - r * 0.5f), Offset(pc.x, pc.y + r * 0.82f), size * 0.055f, alpha = alpha, cap = StrokeCap.Round)
            drawLine(gold, Offset(pc.x - r * 0.52f, pc.y - r * 0.24f), Offset(pc.x + r * 0.52f, pc.y - r * 0.24f), size * 0.05f, alpha = alpha, cap = StrokeCap.Round)
            val arm = Path().apply {
                moveTo(pc.x - r * 0.72f, pc.y + r * 0.26f)
                cubicTo(pc.x - r * 0.7f, pc.y + r * 0.86f, pc.x - r * 0.24f, pc.y + r * 0.98f, pc.x, pc.y + r * 0.82f)
                cubicTo(pc.x + r * 0.24f, pc.y + r * 0.98f, pc.x + r * 0.7f, pc.y + r * 0.86f, pc.x + r * 0.72f, pc.y + r * 0.26f)
            }
            drawPath(arm, gold, alpha = alpha, style = stroke)
        }

        // três seguidas: galões empilhados
        Medal.THREE_IN_A_ROW -> {
            listOf(-0.62f, 0.02f, 0.66f).forEach { f ->
                val p = Path().apply {
                    moveTo(pc.x - r * 0.78f, pc.y + r * f)
                    lineTo(pc.x, pc.y + r * (f - 0.46f))
                    lineTo(pc.x + r * 0.78f, pc.y + r * f)
                }
                drawPath(p, gold, alpha = alpha, style = stroke)
            }
        }
    }
}

private const val PI = 3.14159265f
