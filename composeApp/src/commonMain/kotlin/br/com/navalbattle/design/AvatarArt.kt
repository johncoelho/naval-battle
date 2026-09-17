package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import br.com.navalbattle.game.Avatar

/**
 * Retratos desenhados a traço, num círculo de diâmetro [size] centrado em [center].
 * Mesma ideia da [drawInsignia]: nenhuma imagem no APK, a mesma arte serve em
 * qualquer resolução. Três pares homem/mulher com bonés distintos (oficial de dois
 * tipos e capitão), para dar opção de identidade sem depender de fotos.
 */
fun DrawScope.drawAvatar(avatar: Avatar, center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val w = size * 0.06f
    val stroke = Stroke(width = w, cap = StrokeCap.Round)
    val isWoman = avatar == Avatar.OFFICER_F1 || avatar == Avatar.OFFICER_F2 || avatar == Avatar.CAPTAIN_F

    // moldura circular comum
    drawCircle(color.copy(alpha = 0.35f), radius = s * 0.98f, center = center, style = Stroke(width = size * 0.03f))

    // rosto
    val faceR = s * 0.52f
    val faceCenter = Offset(center.x, center.y + s * 0.08f)
    drawCircle(color, radius = faceR, center = faceCenter, style = stroke)

    // cabelo/silhueta lateral para as mulheres — dois traços curvos descendo dos lados do boné
    if (isWoman) {
        listOf(-1f, 1f).forEach { d ->
            val path = Path().apply {
                moveTo(faceCenter.x + d * faceR * 0.92f, faceCenter.y - faceR * 0.15f)
                quadraticTo(
                    faceCenter.x + d * faceR * 1.25f, faceCenter.y + faceR * 0.55f,
                    faceCenter.x + d * faceR * 0.75f, faceCenter.y + faceR * 1.05f
                )
            }
            drawPath(path, color, style = stroke)
        }
    }

    // ombros/colarinho
    val shoulderY = center.y + s * 0.78f
    val shoulderPath = Path().apply {
        moveTo(center.x - s * 0.95f, center.y + s * 1.3f)
        quadraticTo(center.x - s * 0.85f, shoulderY, center.x - faceR * 0.7f, faceCenter.y + faceR * 0.85f)
        moveTo(center.x + s * 0.95f, center.y + s * 1.3f)
        quadraticTo(center.x + s * 0.85f, shoulderY, center.x + faceR * 0.7f, faceCenter.y + faceR * 0.85f)
    }
    drawPath(shoulderPath, color, style = stroke)

    when (avatar) {
        // boné de oficial: pala reta e faixa simples
        Avatar.OFFICER_M1, Avatar.OFFICER_F1 -> {
            val capY = faceCenter.y - faceR * 0.92f
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(faceCenter.x - faceR * 0.95f, capY - faceR * 0.55f),
                size = Size(faceR * 1.9f, faceR * 1.1f),
                style = stroke
            )
            drawLine(color, Offset(faceCenter.x - faceR * 0.98f, capY), Offset(faceCenter.x + faceR * 0.98f, capY), w)
            drawLine(
                color,
                Offset(faceCenter.x - faceR * 1.05f, capY + faceR * 0.1f),
                Offset(faceCenter.x + faceR * 1.05f, capY + faceR * 0.1f),
                w * 0.9f
            )
        }
        // boné de oficial com âncora bordada no centro da pala
        Avatar.OFFICER_M2, Avatar.OFFICER_F2 -> {
            val capY = faceCenter.y - faceR * 0.92f
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(faceCenter.x - faceR * 0.95f, capY - faceR * 0.55f),
                size = Size(faceR * 1.9f, faceR * 1.1f),
                style = stroke
            )
            drawLine(color, Offset(faceCenter.x - faceR * 0.98f, capY), Offset(faceCenter.x + faceR * 0.98f, capY), w)
            drawCircle(color, radius = faceR * 0.1f, center = Offset(faceCenter.x, capY - faceR * 0.28f), style = stroke)
            drawLine(
                color,
                Offset(faceCenter.x, capY - faceR * 0.18f),
                Offset(faceCenter.x, capY - faceR * 0.42f),
                w * 0.7f
            )
        }
        // quepe de capitão: pala mais alta, duas faixas e um brasão central
        Avatar.CAPTAIN_M, Avatar.CAPTAIN_F -> {
            val capY = faceCenter.y - faceR * 1.05f
            drawRect(
                color = color,
                topLeft = Offset(faceCenter.x - faceR * 0.85f, capY - faceR * 0.35f),
                size = Size(faceR * 1.7f, faceR * 0.55f),
                style = stroke
            )
            drawArc(
                color = color,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(faceCenter.x - faceR * 1.05f, capY - faceR * 0.15f),
                size = Size(faceR * 2.1f, faceR * 0.75f),
                style = stroke
            )
            drawLine(
                color,
                Offset(faceCenter.x - faceR * 0.55f, capY - faceR * 0.1f),
                Offset(faceCenter.x + faceR * 0.55f, capY - faceR * 0.1f),
                w * 0.8f
            )
            drawCircle(color, radius = faceR * 0.09f, center = Offset(faceCenter.x, capY - faceR * 0.1f), style = stroke)
        }
    }
    // sombra de sobrancelha simples, comum a todos — dá expressão sem precisar de mais traços
    listOf(-1f, 1f).forEach { d ->
        drawLine(
            color.copy(alpha = 0.7f),
            Offset(faceCenter.x + d * faceR * 0.22f, faceCenter.y - faceR * 0.12f),
            Offset(faceCenter.x + d * faceR * 0.45f, faceCenter.y - faceR * 0.18f),
            w * 0.7f
        )
    }
}
