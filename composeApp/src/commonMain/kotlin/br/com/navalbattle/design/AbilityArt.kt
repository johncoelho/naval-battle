package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.navalbattle.game.Ability

/**
 * Glifo de cada habilidade tática, desenhado a traço — substitui o emoji do sistema
 * (que muda de aparelho pra aparelho e não conversa com o resto do HUD) pela mesma
 * linguagem visual dos navios e insígnias do jogo. Nada de imagem no APK.
 */
fun DrawScope.drawAbilityIcon(ability: Ability, center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val w = size * 0.075f
    val stroke = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (ability) {
        Ability.AIR_RECON -> {
            // avião de reconhecimento visto de cima: fuselagem + asas em delta
            val path = Path().apply {
                moveTo(center.x, center.y - s * 0.95f)
                lineTo(center.x + s * 0.16f, center.y - s * 0.15f)
                lineTo(center.x + s * 0.95f, center.y + s * 0.18f)
                lineTo(center.x + s * 0.16f, center.y + s * 0.05f)
                lineTo(center.x + s * 0.28f, center.y + s * 0.85f)
                lineTo(center.x, center.y + s * 0.5f)
                lineTo(center.x - s * 0.28f, center.y + s * 0.85f)
                lineTo(center.x - s * 0.16f, center.y + s * 0.05f)
                lineTo(center.x - s * 0.95f, center.y + s * 0.18f)
                lineTo(center.x - s * 0.16f, center.y - s * 0.15f)
                close()
            }
            drawPath(path, color, style = Stroke(width = w * 0.7f, join = StrokeJoin.Round))
        }

        Ability.DOUBLE_BARRAGE -> {
            // dois projéteis lado a lado — a mesma leitura de "dois disparos" à distância
            listOf(-1f, 1f).forEach { d ->
                val x = center.x + d * s * 0.42f
                drawLine(color, Offset(x, center.y - s * 0.85f), Offset(x, center.y + s * 0.8f), w)
                val tip = Path().apply {
                    moveTo(x, center.y - s * 0.85f)
                    lineTo(x - s * 0.32f, center.y - s * 0.25f)
                    lineTo(x + s * 0.32f, center.y - s * 0.25f)
                    close()
                }
                drawPath(tip, color)
            }
        }

        Ability.SONAR_PING -> {
            // anéis concêntricos de sonar saindo de um ponto central
            drawCircle(color, radius = s * 0.14f, center = center)
            drawCircle(color, radius = s * 0.46f, center = center, style = Stroke(w * 0.75f))
            drawCircle(color.copy(alpha = 0.6f), radius = s * 0.78f, center = center, style = Stroke(w * 0.6f))
        }

        Ability.DIVE -> {
            // casco de submarino visto de lado, com a torre e o periscópio
            val hull = Path().apply {
                moveTo(center.x - s * 0.92f, center.y + s * 0.35f)
                cubicTo(
                    center.x - s * 0.92f, center.y - s * 0.55f,
                    center.x + s * 0.92f, center.y - s * 0.55f,
                    center.x + s * 0.92f, center.y + s * 0.35f
                )
            }
            drawPath(hull, color, style = stroke)
            val deck = androidx.compose.ui.geometry.Rect(
                left = center.x - s * 0.4f,
                top = center.y + s * 0.35f,
                right = center.x + s * 0.4f,
                bottom = center.y + s * 0.62f
            )
            drawRoundRect(color, topLeft = deck.topLeft, size = deck.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.13f), style = stroke)
            drawLine(color, Offset(center.x, center.y - s * 0.55f), Offset(center.x, center.y - s * 0.9f), w)
            drawLine(color, Offset(center.x - s * 0.14f, center.y - s * 0.9f), Offset(center.x + s * 0.14f, center.y - s * 0.9f), w)
        }

        Ability.SMOKE -> {
            // duas volutas de fumaça se dissipando
            val big = Path().apply {
                moveTo(center.x - s * 0.9f, center.y + s * 0.55f)
                cubicTo(
                    center.x - s * 0.5f, center.y + s * 0.55f,
                    center.x - s * 0.5f, center.y - s * 0.05f,
                    center.x - s * 0.05f, center.y - s * 0.05f
                )
                cubicTo(
                    center.x + s * 0.4f, center.y - s * 0.05f,
                    center.x + s * 0.2f, center.y + s * 0.75f,
                    center.x + s * 0.85f, center.y + s * 0.75f
                )
            }
            drawPath(big, color, style = stroke)
            val small = Path().apply {
                moveTo(center.x - s * 0.55f, center.y - s * 0.3f)
                cubicTo(
                    center.x - s * 0.25f, center.y - s * 0.3f,
                    center.x - s * 0.25f, center.y - s * 0.75f,
                    center.x + s * 0.15f, center.y - s * 0.75f
                )
            }
            drawPath(small, color.copy(alpha = 0.6f), style = Stroke(w * 0.7f, cap = StrokeCap.Round))
        }
    }
}
