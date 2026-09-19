package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/** Taça de campeão, vetor simples — pódio do placar ao vivo e troféus de temporada. */
fun DrawScope.drawTrophy(color: Color, center: Offset, size: Float) {
    val s = size / 2f
    val cupTop = center.y - s * 0.75f
    val cupBottom = center.y - s * 0.05f

    val cup = Path().apply {
        moveTo(center.x - s * 0.5f, cupTop)
        lineTo(center.x + s * 0.5f, cupTop)
        lineTo(center.x + s * 0.28f, cupBottom)
        lineTo(center.x - s * 0.28f, cupBottom)
        close()
    }
    drawPath(cup, color)

    val handleStroke = Stroke(width = s * 0.14f)
    drawArc(
        color = color,
        startAngle = -150f,
        sweepAngle = 210f,
        useCenter = false,
        style = handleStroke,
        topLeft = Offset(center.x - s * 0.95f, cupTop - s * 0.05f),
        size = Size(s * 0.5f, s * 0.55f)
    )
    drawArc(
        color = color,
        startAngle = 120f,
        sweepAngle = 210f,
        useCenter = false,
        style = handleStroke,
        topLeft = Offset(center.x + s * 0.45f, cupTop - s * 0.05f),
        size = Size(s * 0.5f, s * 0.55f)
    )

    drawRect(color, topLeft = Offset(center.x - s * 0.08f, cupBottom), size = Size(s * 0.16f, s * 0.28f))
    drawRect(
        color,
        topLeft = Offset(center.x - s * 0.36f, cupBottom + s * 0.28f),
        size = Size(s * 0.72f, s * 0.12f)
    )
}
