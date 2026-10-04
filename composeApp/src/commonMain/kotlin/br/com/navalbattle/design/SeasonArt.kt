package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Arte das temporadas da ranqueada: o banner ilustrado de cada estação e o ícone
 * pequeno que acompanha o nome da temporada nos chips. Tudo vetorial, como os navios
 * e os retratos — nenhuma imagem no APK.
 *
 * O banner é uma vista lateral do convés de um navio de guerra em alto-mar: céu e mar
 * ao fundo, balaustrada, uma torre de canhão à esquerda e a superestrutura à direita,
 * com três marinheiros no meio vestidos conforme a estação. A luz vem de cima e da
 * esquerda, a mesma convenção de ShipArt.kt.
 *
 * Composição pensada para a interface por cima: o quinto de baixo é convés em sombra
 * (calmo, bom para texto) e o canto superior esquerdo fica só com céu.
 */

/** As quatro estações do ano (hemisfério sul), na ordem do calendário da ranqueada. */
enum class SeasonTheme {
    VERAO, OUTONO, INVERNO, PRIMAVERA;

    companion object {
        /** "2026-primavera" -> PRIMAVERA. Chave desconhecida cai na primavera. */
        fun ofKey(seasonKey: String): SeasonTheme {
            val suffix = seasonKey.substringAfterLast('-').trim().lowercase()
            return when (suffix) {
                "verao" -> VERAO
                "outono" -> OUTONO
                "inverno" -> INVERNO
                "primavera" -> PRIMAVERA
                else -> PRIMAVERA
            }
        }
    }
}

/** Cor de destaque de cada estação (bordas, botões, título). */
val SeasonTheme.accent: Color
    get() = when (this) {
        SeasonTheme.VERAO -> Color(0xFFFFC95C)
        SeasonTheme.OUTONO -> Color(0xFFE07A3F)
        SeasonTheme.INVERNO -> Color(0xFF7AD1E0)
        SeasonTheme.PRIMAVERA -> Color(0xFF8ED17A)
    }

// Linhas-guia da cena, em fração da altura do banner.
private const val HORIZON = 0.50f
private const val RAIL_TOP = 0.625f
private const val RAIL_MID = 0.70f
private const val DECK = 0.78f

/** Clareia em direção ao branco. [t] de 0 (cor original) a 1 (branco). */
private fun Color.toWhite(t: Float) = Color(
    red + (1f - red) * t,
    green + (1f - green) * t,
    blue + (1f - blue) * t,
    alpha
)

/** Escurece em direção ao preto. */
private fun Color.toBlack(t: Float) = Color(red * (1f - t), green * (1f - t), blue * (1f - t), alpha)

private fun Color.blendTo(other: Color, t: Float) = Color(
    red + (other.red - red) * t,
    green + (other.green - green) * t,
    blue + (other.blue - blue) * t,
    alpha + (other.alpha - alpha) * t
)

/**
 * Gerador congruencial minúsculo: neve, pétalas, folhas e marolas caem sempre nos
 * mesmos lugares para a mesma estação, então o banner não "pisca" ao recompor.
 */
private class SeasonRng(seed: Int) {
    private var s = seed

    fun next(): Float {
        s = s * 1664525 + 1013904223
        return (s ushr 8) / 16777216f
    }

    fun range(a: Float, b: Float) = a + (b - a) * next()
}

/** Tintas da cena para uma estação: céu, mar, aço do navio e névoa do horizonte. */
private class SeasonPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val seaFar: Color,
    val seaNear: Color,
    val wave: Color,
    val steel: Color,
    val steelLight: Color,
    val steelDark: Color,
    val deck: Color,
    val haze: Color,
    val seed: Int
)

private fun paletteOf(theme: SeasonTheme) = when (theme) {
    SeasonTheme.VERAO -> SeasonPalette(
        skyTop = Color(0xFF2C78D4), skyMid = Color(0xFF5FADEB), skyLow = Color(0xFFC8ECF8),
        seaFar = Color(0xFF46C2CF), seaNear = Color(0xFF0E7896), wave = Color(0xFFE4FBFC),
        steel = Color(0xFF8E9BA7), steelLight = Color(0xFFD3DCE3), steelDark = Color(0xFF4A5662),
        deck = Color(0xFF46515B), haze = Color(0xFFD8F1FA), seed = 11
    )
    // pôr do sol: o aço pega a luz alaranjada por cima
    SeasonTheme.OUTONO -> SeasonPalette(
        skyTop = Color(0xFF2E2548), skyMid = Color(0xFFB4553F), skyLow = Color(0xFFFFB25A),
        seaFar = Color(0xFF5A4A5E), seaNear = Color(0xFF16212E), wave = Color(0xFFFFC283),
        steel = Color(0xFF7C7A82), steelLight = Color(0xFFE0A77A), steelDark = Color(0xFF34323B),
        deck = Color(0xFF34313A), haze = Color(0xFFF0A070), seed = 23
    )
    SeasonTheme.INVERNO -> SeasonPalette(
        skyTop = Color(0xFF435A78), skyMid = Color(0xFF7D96B0), skyLow = Color(0xFFCAD8E3),
        seaFar = Color(0xFF628AA2), seaNear = Color(0xFF1C3650), wave = Color(0xFFE6EFF4),
        steel = Color(0xFF7D8994), steelLight = Color(0xFFC2CDD6), steelDark = Color(0xFF434F5B),
        deck = Color(0xFF3A444E), haze = Color(0xFFD5DEE5), seed = 37
    )
    SeasonTheme.PRIMAVERA -> SeasonPalette(
        skyTop = Color(0xFF5FA7E0), skyMid = Color(0xFFA4D2F0), skyLow = Color(0xFFFFE3D2),
        seaFar = Color(0xFF5DB5AE), seaNear = Color(0xFF17687A), wave = Color(0xFFEAFFF7),
        steel = Color(0xFF8D9AA6), steelLight = Color(0xFFD6DFE6), steelDark = Color(0xFF4A5662),
        deck = Color(0xFF44505A), haze = Color(0xFFF6E6DE), seed = 53
    )
}

private fun strokeOf(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Retângulo de cantos arredondados montado com curvas quadráticas. */
private fun roundRectPath(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    quadraticTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r)
    quadraticTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h)
    quadraticTo(x, y + h, x, y + h - r)
    lineTo(x, y + r)
    quadraticTo(x, y, x + r, y)
    close()
}

/**
 * Banner ilustrado da temporada, preenchendo todo o DrawScope (pensado para ~16:9,
 * mas tem que funcionar de 2:1 a 4:3 sem cortar o essencial).
 *
 * As medidas de profundidade (alturas, espessuras, figuras) seguem a altura do banner
 * e as posições na horizontal seguem a largura: num banner mais estreito a cena só
 * aproxima os elementos, sem achatar os marinheiros.
 */
fun DrawScope.drawSeasonBanner(theme: SeasonTheme) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val p = paletteOf(theme)
    val rng = SeasonRng(p.seed)
    clipRect(0f, 0f, w, h) {
        seasonSky(theme, p, w, h)
        seasonSea(theme, p, w, h, rng)
        superstructure(theme, p, w, h)
        guardRail(theme, p, w, h)
        deckFloor(theme, p, w, h)
        gunTurret(theme, p, w, h)
        if (theme == SeasonTheme.PRIMAVERA) bunting(w, h)
        crew(theme, w, h)
        seasonWeather(theme, w, h, rng)
        // base calma para o texto da interface e cantos fechados
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.45f to Color(0x66000000), 1f to Color(0xD9050806),
                startY = h * 0.66f, endY = h
            ),
            Offset(0f, h * 0.66f), Size(w, h * 0.34f)
        )
        drawRect(
            Brush.radialGradient(
                0f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color(0x59000000),
                center = Offset(w * 0.5f, h * 0.45f), radius = max(w, h) * 0.75f
            ),
            Offset.Zero, Size(w, h)
        )
    }
}

// ---------- céu ----------

private fun DrawScope.seasonSky(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float) {
    val hy = h * HORIZON
    drawRect(
        Brush.verticalGradient(0f to p.skyTop, 0.62f to p.skyMid, 1f to p.skyLow, startY = 0f, endY = hy),
        Offset.Zero, Size(w, hy + 1f)
    )
    when (theme) {
        SeasonTheme.VERAO -> {
            // sol forte, alto, com leque de raios
            val sx = w * 0.64f
            val sy = h * 0.17f
            val r = h * 0.075f
            val c = Offset(sx, sy)
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0x99FFF6D0), 0.35f to Color(0x40FFF0B0), 1f to Color.Transparent,
                    center = c, radius = h * 0.55f
                ),
                radius = h * 0.55f, center = c
            )
            clipRect(0f, 0f, w, hy) {
                repeat(12) { i ->
                    val a = i / 12f * 2f * PI.toFloat() + 0.2f
                    val len = h * 1.1f
                    val ray = Path().apply {
                        moveTo(sx, sy)
                        lineTo(sx + len * cos(a - 0.07f), sy + len * sin(a - 0.07f))
                        lineTo(sx + len * cos(a + 0.07f), sy + len * sin(a + 0.07f))
                        close()
                    }
                    drawPath(ray, Brush.radialGradient(listOf(Color(0x47FFF8DC), Color.Transparent), c, len))
                }
            }
            drawCircle(Color(0xFFFFF4C2), radius = r * 1.25f, center = c, alpha = 0.35f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, Color(0xFFFFF0A8), Color(0xFFFFD45C)),
                    Offset(sx - r * 0.2f, sy - r * 0.2f), r * 1.2f
                ),
                radius = r, center = c
            )
            puffCloud(w * 0.30f, h * 0.43f, h * 0.20f, Color.White, Color(0xFFB9DDF0), 0.9f)
            puffCloud(w * 0.80f, h * 0.45f, h * 0.15f, Color.White, Color(0xFFB9DDF0), 0.85f)
            island(w * 0.08f, w * 0.30f, hy, h * 0.035f, p.seaFar.blendTo(p.haze, 0.45f))
            // gaivotas
            gull(w * 0.44f, h * 0.16f, h * 0.030f)
            gull(w * 0.50f, h * 0.11f, h * 0.024f)
            gull(w * 0.54f, h * 0.24f, h * 0.020f)
            gull(w * 0.80f, h * 0.30f, h * 0.022f)
        }

        SeasonTheme.OUTONO -> {
            // sol se pondo no horizonte, metade já atrás do mar
            val sx = w * 0.60f
            val sy = hy - h * 0.015f
            val r = h * 0.10f
            val c = Offset(sx, sy)
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xCCFFD27A), 0.3f to Color(0x66FF9A4A), 1f to Color.Transparent,
                    center = c, radius = h * 0.7f
                ),
                radius = h * 0.7f, center = c
            )
            clipRect(0f, 0f, w, hy) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFFFFF2C0), Color(0xFFFFC060), Color(0xFFF07A30)),
                        Offset(sx, sy - r * 0.3f), r * 1.1f
                    ),
                    radius = r, center = c
                )
            }
            stratus(w * 0.18f, h * 0.20f, w * 0.42f, h * 0.035f, Color(0xFF7A3E58), 0.85f)
            stratus(w * 0.70f, h * 0.12f, w * 0.50f, h * 0.03f, Color(0xFF6A3552), 0.8f)
            stratus(w * 0.52f, h * 0.31f, w * 0.36f, h * 0.026f, Color(0xFFE07A50), 0.75f)
            stratus(w * 0.88f, h * 0.38f, w * 0.30f, h * 0.022f, Color(0xFFF0965A), 0.7f)
            stratus(w * 0.30f, h * 0.42f, w * 0.30f, h * 0.018f, Color(0xFFF5A866), 0.6f)
        }

        SeasonTheme.INVERNO -> {
            // sol pálido atrás do céu fechado
            val c = Offset(w * 0.62f, h * 0.19f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x80FFFFFF), Color.Transparent), c, h * 0.3f),
                radius = h * 0.3f, center = c
            )
            drawCircle(Color(0xFFF4F7F9), radius = h * 0.06f, center = c, alpha = 0.75f)
            stratus(w * 0.25f, h * 0.06f, w * 0.80f, h * 0.09f, Color(0xFF5A6B7E), 0.9f)
            stratus(w * 0.85f, h * 0.10f, w * 0.60f, h * 0.07f, Color(0xFF677A8C), 0.85f)
            stratus(w * 0.45f, h * 0.34f, w * 0.55f, h * 0.04f, Color(0xFFA9B7C3), 0.6f)
            // costa gelada ao longe, com neve no cume
            island(0f, w * 0.36f, hy, h * 0.05f, Color(0xFFAEBCC8))
            drawPath(
                Path().apply {
                    moveTo(0f, hy - h * 0.05f)
                    lineTo(w * 0.05f, hy - h * 0.065f)
                    lineTo(w * 0.12f, hy - h * 0.045f)
                    lineTo(w * 0.20f, hy - h * 0.03f)
                    lineTo(0f, hy - h * 0.025f)
                    close()
                },
                Color(0xFFF1F5F8), alpha = 0.9f
            )
        }

        SeasonTheme.PRIMAVERA -> {
            // manhã limpa: sol suave, nuvens rosadas e costa verde
            val c = Offset(w * 0.70f, h * 0.13f)
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xB3FFFBEA), 0.25f to Color(0x55FFF3D6), 1f to Color.Transparent,
                    center = c, radius = h * 0.45f
                ),
                radius = h * 0.45f, center = c
            )
            drawCircle(Color(0xFFFFFCF0), radius = h * 0.045f, center = c, alpha = 0.95f)
            puffCloud(w * 0.50f, h * 0.30f, h * 0.17f, Color.White, Color(0xFFF2D6D8), 0.85f)
            puffCloud(w * 0.20f, h * 0.40f, h * 0.14f, Color.White, Color(0xFFE9D4DC), 0.8f)
            island(0f, w * 0.42f, hy, h * 0.06f, Color(0xFF5E9A86).blendTo(p.haze, 0.5f))
            island(w * 0.10f, w * 0.34f, hy, h * 0.035f, Color(0xFF4E8A70).blendTo(p.haze, 0.3f))
        }
    }
    // névoa do horizonte
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, p.haze.copy(alpha = 0.55f)), hy - h * 0.06f, hy),
        Offset(0f, hy - h * 0.06f), Size(w, h * 0.06f)
    )
    escort(w * 0.36f, hy, h * 0.20f, p.seaNear.blendTo(p.haze, 0.45f))
}

/** Nuvem de algodão: bolhas de sombra embaixo, as claras por cima, base achatada. */
private fun DrawScope.puffCloud(cx: Float, cy: Float, s: Float, color: Color, shade: Color, a: Float) {
    val bumps = arrayOf(
        floatArrayOf(-0.38f, 0.02f, 0.22f), floatArrayOf(-0.16f, -0.14f, 0.30f),
        floatArrayOf(0.12f, -0.08f, 0.26f), floatArrayOf(0.36f, 0.04f, 0.18f),
        floatArrayOf(0.0f, 0.06f, 0.24f)
    )
    for (b in bumps) {
        drawCircle(shade, radius = s * b[2], center = Offset(cx + s * b[0] * 1.5f, cy + s * b[1] + s * 0.05f), alpha = a)
    }
    for (b in bumps) {
        drawCircle(
            color, radius = s * b[2] * 0.94f,
            center = Offset(cx + s * b[0] * 1.5f - s * 0.02f, cy + s * b[1] - s * 0.01f), alpha = a
        )
    }
    drawOval(shade, Offset(cx - s * 0.75f, cy + s * 0.04f), Size(s * 1.5f, s * 0.22f), alpha = a * 0.7f)
}

/** Nuvem em faixa, alongada — o céu de entardecer e o de inverno fechado. */
private fun DrawScope.stratus(cx: Float, cy: Float, len: Float, th: Float, color: Color, a: Float) {
    drawPath(
        Path().apply {
            moveTo(cx - len / 2f, cy)
            cubicTo(cx - len * 0.3f, cy - th * 1.4f, cx + len * 0.2f, cy - th * 1.2f, cx + len / 2f, cy - th * 0.1f)
            cubicTo(cx + len * 0.25f, cy + th * 0.6f, cx - len * 0.25f, cy + th * 0.5f, cx - len / 2f, cy)
            close()
        },
        color, alpha = a
    )
}

/** Morro baixo de costa no horizonte. */
private fun DrawScope.island(x0: Float, x1: Float, hy: Float, ht: Float, color: Color) {
    val m = x1 - x0
    drawPath(
        Path().apply {
            moveTo(x0 - m * 0.1f, hy)
            cubicTo(x0 + m * 0.15f, hy - ht * 0.8f, x0 + m * 0.3f, hy - ht * 1.1f, x0 + m * 0.45f, hy - ht)
            cubicTo(x0 + m * 0.6f, hy - ht * 0.9f, x0 + m * 0.75f, hy - ht * 0.55f, x1, hy)
            close()
        },
        color
    )
}

private fun DrawScope.gull(x: Float, y: Float, s: Float) {
    drawPath(
        Path().apply {
            moveTo(x - s, y - s * 0.1f)
            quadraticTo(x - s * 0.5f, y - s * 0.7f, x, y)
            quadraticTo(x + s * 0.5f, y - s * 0.7f, x + s, y - s * 0.1f)
        },
        Color(0xFF2E3A46), style = strokeOf(s * 0.2f)
    )
}

/** Escolta distante no horizonte: silhueta chapada tingida pela névoa. */
private fun DrawScope.escort(cx: Float, hy: Float, len: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(cx - len / 2f, hy - len * 0.035f)
            lineTo(cx + len / 2f, hy - len * 0.05f)
            lineTo(cx + len * 0.44f, hy)
            lineTo(cx - len * 0.46f, hy)
            close()
        },
        color
    )
    drawRect(color, Offset(cx - len * 0.10f, hy - len * 0.10f), Size(len * 0.24f, len * 0.06f))
    drawRect(color, Offset(cx - len * 0.03f, hy - len * 0.15f), Size(len * 0.10f, len * 0.05f))
    drawRect(color, Offset(cx + len * 0.01f, hy - len * 0.24f), Size(len * 0.012f, len * 0.09f))
    drawRect(color, Offset(cx - len * 0.30f, hy - len * 0.075f), Size(len * 0.10f, len * 0.03f))
}

// ---------- mar ----------

private fun DrawScope.seasonSea(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float, rng: SeasonRng) {
    val hy = h * HORIZON
    val dy = h * DECK
    drawRect(Brush.verticalGradient(listOf(p.seaFar, p.seaNear), hy, dy), Offset(0f, hy), Size(w, dy - hy + 1f))
    // brilho do sol na água, mais largo e grosso perto do navio
    if (theme == SeasonTheme.VERAO || theme == SeasonTheme.OUTONO) {
        val sx = if (theme == SeasonTheme.VERAO) w * 0.64f else w * 0.60f
        val col = if (theme == SeasonTheme.VERAO) Color(0xFFFFFBE0) else Color(0xFFFFC870)
        repeat(26) {
            val t = rng.next()
            val y = hy + (dy - hy) * t * t * 0.9f + h * 0.004f
            val spread = h * (0.03f + 0.22f * t)
            val x = sx + rng.range(-1f, 1f) * spread
            val l = h * (0.015f + 0.05f * t)
            drawLine(
                col, Offset(x - l / 2f, y), Offset(x + l / 2f, y),
                strokeWidth = h * (0.003f + 0.005f * t), cap = StrokeCap.Round, alpha = 0.35f + 0.45f * (1f - t)
            )
        }
    }
    // marolas: cava escura e crista clara, crescendo com a proximidade
    val n = if (theme == SeasonTheme.OUTONO) 70 else 50
    repeat(n) {
        val t = rng.next()
        val y = hy + (dy - hy) * (0.03f + 0.97f * t * t)
        val x = rng.range(-0.05f, 1.05f) * w
        val l = h * (0.02f + 0.09f * t)
        drawLine(
            p.seaNear.toBlack(0.2f), Offset(x - l * 0.4f, y + h * 0.004f), Offset(x + l * 0.4f, y + h * 0.004f),
            strokeWidth = h * (0.002f + 0.004f * t), cap = StrokeCap.Round, alpha = 0.3f
        )
        drawLine(
            p.wave, Offset(x - l * 0.35f, y), Offset(x + l * 0.35f, y),
            strokeWidth = h * (0.002f + 0.004f * t), cap = StrokeCap.Round,
            alpha = if (theme == SeasonTheme.OUTONO) 0.55f else 0.35f
        )
        if (theme == SeasonTheme.OUTONO && t > 0.45f && rng.next() < 0.4f) {
            // carneirinhos do mar picado
            drawOval(Color(0xFFF7E6D6), Offset(x - l * 0.2f, y - h * 0.006f), Size(l * 0.4f, h * 0.009f), alpha = 0.6f)
        }
    }
    if (theme == SeasonTheme.INVERNO) {
        val floes = arrayOf(
            floatArrayOf(0.10f, 0.18f, 0.10f), floatArrayOf(0.32f, 0.42f, 0.13f), floatArrayOf(0.58f, 0.10f, 0.07f),
            floatArrayOf(0.70f, 0.55f, 0.16f), floatArrayOf(0.20f, 0.75f, 0.18f), floatArrayOf(0.92f, 0.30f, 0.09f),
            floatArrayOf(0.46f, 0.85f, 0.2f)
        )
        for (f in floes) iceFloe(w * f[0], hy + (dy - hy) * f[1], h * f[2] * (0.4f + 0.8f * f[1]))
    }
}

/** Placa de gelo à deriva: tampo claro, borda azulada e sombra na água. */
private fun DrawScope.iceFloe(cx: Float, cy: Float, s: Float) {
    drawOval(Color(0xFF1C3040), Offset(cx - s * 0.5f, cy + s * 0.02f), Size(s * 1.0f, s * 0.14f), alpha = 0.45f)
    drawPath(
        Path().apply {
            moveTo(cx - s * 0.5f, cy)
            lineTo(cx - s * 0.25f, cy + s * 0.07f)
            lineTo(cx + s * 0.32f, cy + s * 0.06f)
            lineTo(cx + s * 0.5f, cy - s * 0.04f)
            lineTo(cx + s * 0.5f, cy + s * 0.02f)
            lineTo(cx + s * 0.32f, cy + s * 0.12f)
            lineTo(cx - s * 0.25f, cy + s * 0.13f)
            lineTo(cx - s * 0.5f, cy + s * 0.05f)
            close()
        },
        Color(0xFF8FB4C8)
    )
    drawPath(
        Path().apply {
            moveTo(cx - s * 0.5f, cy)
            lineTo(cx - s * 0.3f, cy - s * 0.10f)
            lineTo(cx + s * 0.15f, cy - s * 0.12f)
            lineTo(cx + s * 0.5f, cy - s * 0.04f)
            lineTo(cx + s * 0.32f, cy + s * 0.06f)
            lineTo(cx - s * 0.25f, cy + s * 0.07f)
            close()
        },
        Brush.linearGradient(
            listOf(Color.White, Color(0xFFD3E4EE)),
            Offset(cx - s * 0.3f, cy - s * 0.12f), Offset(cx + s * 0.3f, cy + s * 0.07f)
        )
    )
}

// ---------- navio ----------

/** Neve assentada por cima de uma aresta: contorno azulado e miolo branco. */
private fun DrawScope.snowCap(path: Path, th: Float) {
    drawPath(path, Color(0xFFB8C8D4), style = strokeOf(th * 1.1f))
    drawPath(path, Color.White, style = strokeOf(th))
}

/** Superestrutura à direita: passadiço com janelas, porta estanque, vigias e mastro. */
private fun DrawScope.superstructure(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float) {
    val x0 = w * 0.865f
    val top = h * 0.30f
    val dy = h * DECK
    val bevel = h * 0.06f
    val mx = x0 + h * 0.15f
    // mastro com verga e antena
    drawPath(
        Path().apply {
            moveTo(mx - h * 0.012f, top)
            lineTo(mx - h * 0.005f, h * 0.03f)
            lineTo(mx + h * 0.005f, h * 0.03f)
            lineTo(mx + h * 0.012f, top)
            close()
        },
        Brush.linearGradient(listOf(p.steelLight, p.steelDark), Offset(mx - h * 0.012f, 0f), Offset(mx + h * 0.012f, 0f))
    )
    drawLine(p.steelDark, Offset(mx - h * 0.10f, h * 0.12f), Offset(mx + h * 0.10f, h * 0.12f), h * 0.008f, cap = StrokeCap.Round)
    drawLine(
        p.steelLight, Offset(mx - h * 0.10f, h * 0.115f), Offset(mx + h * 0.10f, h * 0.115f), h * 0.003f,
        cap = StrokeCap.Round, alpha = 0.8f
    )
    drawRect(p.steelDark, Offset(mx - h * 0.045f, h * 0.065f), Size(h * 0.09f, h * 0.014f))
    drawLine(p.steelDark, Offset(mx - h * 0.10f, h * 0.12f), Offset(mx - h * 0.02f, top), h * 0.003f, alpha = 0.8f)
    drawCircle(Color(0xFFFF5040), radius = h * 0.007f, center = Offset(mx, h * 0.03f))
    // corpo, com a quina da frente chanfrada pegando luz
    val body = Path().apply {
        moveTo(x0, dy)
        lineTo(x0, top + bevel)
        lineTo(x0 + bevel * 0.8f, top)
        lineTo(w + 2f, top)
        lineTo(w + 2f, dy)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(p.steelLight, p.steel, p.steelDark), top, dy))
    drawPath(
        Path().apply {
            moveTo(x0, dy)
            lineTo(x0, top + bevel)
            lineTo(x0 + bevel * 0.8f, top)
            lineTo(x0 + bevel * 0.8f + h * 0.02f, top)
            lineTo(x0 + h * 0.02f, top + bevel)
            lineTo(x0 + h * 0.02f, dy)
            close()
        },
        p.steelLight.toWhite(0.3f), alpha = 0.6f
    )
    // janelas do passadiço
    val wy = top + h * 0.035f
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF1A2733), Color(0xFF31495C)), wy, wy + h * 0.04f),
        Offset(x0 + bevel * 0.55f, wy), Size(w - x0, h * 0.04f)
    )
    for (i in 0 until 6) {
        drawRect(p.steel, Offset(x0 + bevel * 0.55f + h * 0.05f * (i + 1), wy), Size(h * 0.006f, h * 0.04f))
    }
    drawLine(
        Color.White, Offset(x0 + bevel * 0.6f, wy + h * 0.006f), Offset(x0 + bevel * 0.6f + h * 0.03f, wy + h * 0.006f),
        h * 0.004f, alpha = 0.4f
    )
    // porta estanque e vigias
    val dx = x0 + h * 0.06f
    drawPath(roundRectPath(dx, h * 0.53f, h * 0.07f, h * 0.22f, h * 0.02f), p.steel.toBlack(0.25f))
    drawPath(roundRectPath(dx, h * 0.53f, h * 0.07f, h * 0.22f, h * 0.02f), p.steelDark, style = strokeOf(h * 0.006f))
    drawLine(p.steelLight, Offset(dx + h * 0.05f, h * 0.62f), Offset(dx + h * 0.05f, h * 0.66f), h * 0.008f, cap = StrokeCap.Round)
    for (i in 0 until 2) {
        val px = dx + h * 0.12f + h * 0.07f * i
        drawCircle(p.steelDark, radius = h * 0.018f, center = Offset(px, h * 0.46f))
        drawCircle(Color(0xFF2A3E50), radius = h * 0.013f, center = Offset(px, h * 0.46f))
    }
    drawRect(Color.Black, Offset(x0, dy - h * 0.03f), Size(w - x0, h * 0.03f), alpha = 0.25f)
    if (theme == SeasonTheme.INVERNO) {
        snowCap(Path().apply { moveTo(x0 + bevel * 0.8f, top); lineTo(w + 2f, top) }, h * 0.022f)
        snowCap(Path().apply { moveTo(mx - h * 0.10f, h * 0.115f); lineTo(mx + h * 0.10f, h * 0.115f) }, h * 0.01f)
    }
}

/** Balaustrada de bordo: balaústres, dois cabos e a boia salva-vidas pendurada. */
private fun DrawScope.guardRail(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float) {
    val top = h * RAIL_TOP
    val mid = h * RAIL_MID
    val dy = h * DECK
    val end = w * 0.865f
    val step = w * 0.075f
    var x = w * 0.02f
    while (x < end) {
        drawRect(
            Brush.linearGradient(listOf(p.steelLight, p.steelDark), Offset(x, 0f), Offset(x + h * 0.014f, 0f)),
            Offset(x, top), Size(h * 0.014f, dy - top)
        )
        x += step
    }
    drawLine(p.steelDark, Offset(0f, mid), Offset(end, mid), h * 0.009f)
    drawLine(p.steelDark, Offset(0f, top + h * 0.004f), Offset(end, top + h * 0.004f), h * 0.014f)
    drawLine(p.steelLight, Offset(0f, top), Offset(end, top), h * 0.008f)
    // boia salva-vidas
    val b = Offset(w * 0.83f, h * 0.665f)
    val br = h * 0.042f
    drawCircle(
        Color.Black, radius = br * 0.72f, center = Offset(b.x + br * 0.08f, b.y + br * 0.1f),
        alpha = 0.25f, style = Stroke(br * 0.55f)
    )
    drawCircle(Color(0xFFF2F2EE), radius = br * 0.72f, center = b, style = Stroke(br * 0.5f))
    val ring = br * 0.72f
    for (i in 0 until 4) {
        drawArc(
            Color(0xFFE0562E), startAngle = i * 90f + 20f, sweepAngle = 40f, useCenter = false,
            topLeft = Offset(b.x - ring, b.y - ring), size = Size(ring * 2f, ring * 2f), style = Stroke(br * 0.5f)
        )
    }
    if (theme == SeasonTheme.INVERNO) {
        snowCap(Path().apply { moveTo(0f, top - h * 0.006f); lineTo(end, top - h * 0.006f) }, h * 0.016f)
    }
}

/** Convés em chapa de aço, escurecendo para perto de quem olha. */
private fun DrawScope.deckFloor(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float) {
    val dy = h * DECK
    drawRect(
        Brush.verticalGradient(listOf(p.deck.toWhite(0.15f), p.deck, p.deck.toBlack(0.45f)), dy, h),
        Offset(0f, dy), Size(w, h - dy)
    )
    drawRect(p.steelDark, Offset(0f, dy), Size(w, h * 0.012f))
    drawLine(p.deck.toWhite(0.3f), Offset(0f, dy + h * 0.012f), Offset(w, dy + h * 0.012f), h * 0.003f, alpha = 0.6f)
    for (i in 1 until 4) {
        val y = dy + (h - dy) * (i * i) / 12f
        drawLine(p.deck.toBlack(0.3f), Offset(0f, y), Offset(w, y), h * 0.003f, alpha = 0.5f)
    }
    if (theme == SeasonTheme.INVERNO) {
        drawPath(
            Path().apply {
                moveTo(0f, dy + h * 0.012f)
                quadraticTo(w * 0.3f, dy + h * 0.03f, w * 0.5f, dy + h * 0.018f)
                quadraticTo(w * 0.8f, dy + h * 0.008f, w, dy + h * 0.03f)
                lineTo(w, dy + h * 0.01f)
                lineTo(0f, dy + h * 0.01f)
                close()
            },
            Color(0xFFEFF4F8), alpha = 0.9f
        )
    }
}

/** Torre de canhão de faces inclinadas, apontando o cano para a proa (esquerda). */
private fun DrawScope.gunTurret(theme: SeasonTheme, p: SeasonPalette, w: Float, h: Float) {
    val x0 = w * 0.05f
    val tw = h * 0.40f
    val th = h * 0.17f
    val base = h * DECK + h * 0.035f
    val bot = base - h * 0.03f
    val top = bot - th
    // anel da barbeta
    drawPath(
        roundRectPath(x0 + tw * 0.08f, bot - h * 0.005f, tw * 0.86f, h * 0.035f + h * 0.005f, h * 0.01f),
        Brush.verticalGradient(listOf(p.steel, p.steelDark), bot, base)
    )
    // canhão, levemente elevado
    val px = x0 + tw * 0.14f
    val py = top + th * 0.42f
    val len = h * 0.62f
    rotate(-14f, Offset(px, py)) {
        drawPath(
            roundRectPath(px - len, py - h * 0.018f, len, h * 0.036f, h * 0.01f),
            Brush.verticalGradient(listOf(p.steelLight, p.steel, p.steelDark), py - h * 0.018f, py + h * 0.018f)
        )
        drawRect(
            Brush.verticalGradient(listOf(p.steelLight, p.steelDark), py - h * 0.024f, py + h * 0.024f),
            Offset(px - len * 0.45f, py - h * 0.024f), Size(len * 0.10f, h * 0.048f)
        )
        drawRect(
            Brush.verticalGradient(listOf(p.steel, p.steelDark.toBlack(0.3f)), py - h * 0.022f, py + h * 0.022f),
            Offset(px - len, py - h * 0.022f), Size(len * 0.05f, h * 0.044f)
        )
    }
    val body = Path().apply {
        moveTo(x0, bot)
        lineTo(x0 + tw * 0.20f, top)
        lineTo(x0 + tw * 0.86f, top)
        quadraticTo(x0 + tw, top, x0 + tw, top + th * 0.25f)
        lineTo(x0 + tw, bot)
        close()
    }
    drawPath(body, Brush.verticalGradient(listOf(p.steelLight, p.steel, p.steelDark), top, bot))
    // face inclinada da frente pega a luz
    drawPath(
        Path().apply {
            moveTo(x0, bot)
            lineTo(x0 + tw * 0.20f, top)
            lineTo(x0 + tw * 0.30f, top)
            lineTo(x0 + tw * 0.12f, bot)
            close()
        },
        p.steelLight.toWhite(0.35f), alpha = 0.55f
    )
    drawLine(p.steelDark.toBlack(0.2f), Offset(x0 + tw * 0.5f, top + th * 0.15f), Offset(x0 + tw * 0.5f, bot), h * 0.004f, alpha = 0.5f)
    drawLine(
        p.steelLight.toWhite(0.5f), Offset(x0 + tw * 0.20f, top + h * 0.003f), Offset(x0 + tw * 0.86f, top + h * 0.003f),
        h * 0.004f, alpha = 0.7f
    )
    drawPath(roundRectPath(x0 + tw * 0.62f, top + th * 0.30f, tw * 0.2f, th * 0.18f, h * 0.006f), p.steelDark, alpha = 0.7f)
    // mantelete
    drawPath(
        roundRectPath(px - h * 0.035f, py - h * 0.04f, h * 0.06f, h * 0.08f, h * 0.015f),
        Brush.verticalGradient(listOf(p.steel, p.steelDark), py - h * 0.04f, py + h * 0.04f)
    )
    drawRect(Color.Black, Offset(x0 + tw * 0.08f, base - h * 0.006f), Size(tw * 0.86f, h * 0.006f), alpha = 0.3f)
    if (theme == SeasonTheme.INVERNO) {
        snowCap(
            Path().apply {
                moveTo(x0 + tw * 0.21f, top - h * 0.004f)
                lineTo(x0 + tw * 0.86f, top - h * 0.004f)
                quadraticTo(x0 + tw * 0.97f, top, x0 + tw * 0.985f, top + th * 0.12f)
            },
            h * 0.024f
        )
    }
}

/** Bandeirolas de sinalização do mastro até a torre — festa de primavera. */
private fun DrawScope.bunting(w: Float, h: Float) {
    val ax = w * 0.865f + h * 0.05f
    val ay = h * 0.12f
    val bx = w * 0.05f + h * 0.40f * 0.86f
    val by = h * DECK + h * 0.005f - h * 0.17f
    val cx = (ax + bx) / 2f
    val cy = max(ay, by) + h * 0.10f
    drawPath(
        Path().apply { moveTo(ax, ay); quadraticTo(cx, cy, bx, by) },
        Color(0xFF2B2B2B), alpha = 0.9f, style = strokeOf(h * 0.004f)
    )
    val cols = listOf(
        Color(0xFFE0453A), Color(0xFFFFD34D), Color(0xFF2F6FD0),
        Color(0xFFF4F4F0), Color(0xFF3FAE5A), Color(0xFFF08A2E)
    )
    val n = 15
    for (i in 1 until n) {
        val t = i / n.toFloat()
        val x = (1 - t) * (1 - t) * ax + 2 * (1 - t) * t * cx + t * t * bx
        val y = (1 - t) * (1 - t) * ay + 2 * (1 - t) * t * cy + t * t * by
        val s = h * 0.035f
        drawPath(
            Path().apply { moveTo(x - s * 0.45f, y); lineTo(x + s * 0.45f, y); lineTo(x, y + s * 1.1f); close() },
            cols[i % cols.size]
        )
        drawPath(
            Path().apply { moveTo(x, y); lineTo(x + s * 0.45f, y); lineTo(x, y + s * 1.1f); close() },
            Color.Black, alpha = 0.15f
        )
    }
}

// ---------- tripulação ----------

private enum class Pose { SIDES, BINOCULARS, SALUTE, POINT, MUG }

private enum class Headgear { DIXIE, PEAKED, BEANIE }

/**
 * Farda de uma estação: cor da peça de cima e da calça, cobertura e os detalhes que
 * mudam a silhueta (gola de marinheiro, cachecol, japona comprida, luvas).
 */
private class SailorLook(
    val top: Color,
    val trousers: Color,
    val headgear: Headgear,
    val capColor: Color,
    val collar: Color? = null,
    val scarf: Color? = null,
    val gloves: Color? = null,
    val coat: Boolean = false,
    val shortSleeves: Boolean = false,
    val dress: Boolean = false
)

private val Skins = listOf(Color(0xFFEAB98E), Color(0xFF8E5B3C), Color(0xFFC88B5E))

private val Gold = Color(0xFFE6AC3F)

private fun lookOf(theme: SeasonTheme, idx: Int) = when (theme) {
    // farda branca de verão, manga curta e gorro de marinheiro
    SeasonTheme.VERAO -> SailorLook(
        top = Color(0xFFF5F7F9), trousers = Color(0xFFE6EBF0), headgear = Headgear.DIXIE,
        capColor = Color.White, collar = Color(0xFF1E3566), shortSleeves = true
    )
    // jaqueta azul-marinho de convés com cachecol
    SeasonTheme.OUTONO -> SailorLook(
        top = Color(0xFF263655), trousers = Color(0xFF1A2236), headgear = Headgear.PEAKED,
        capColor = Color(0xFF1C2742),
        scarf = listOf(Color(0xFFE07A3F), Color(0xFFC8423A), Color(0xFFE6AC3F))[idx]
    )
    // japona pesada, gorro de lã, cachecol e luvas
    SeasonTheme.INVERNO -> SailorLook(
        top = Color(0xFF2B323C), trousers = Color(0xFF1A1E25), headgear = Headgear.BEANIE,
        capColor = listOf(Color(0xFF2E3F5C), Color(0xFF3A3F46), Color(0xFF4A2E2E))[idx],
        scarf = Color(0xFF8C97A2), gloves = Color(0xFF16181C), coat = true
    )
    // túnica de gala com quepe branco
    SeasonTheme.PRIMAVERA -> SailorLook(
        top = Color(0xFF1B2948), trousers = Color(0xFF16213B), headgear = Headgear.PEAKED,
        capColor = Color(0xFFF7F8FA), dress = true
    )
}

private fun DrawScope.crew(theme: SeasonTheme, w: Float, h: Float) {
    val poses = when (theme) {
        SeasonTheme.VERAO -> listOf(Pose.POINT, Pose.BINOCULARS, Pose.SIDES)
        SeasonTheme.OUTONO -> listOf(Pose.SIDES, Pose.BINOCULARS, Pose.POINT)
        SeasonTheme.INVERNO -> listOf(Pose.SIDES, Pose.BINOCULARS, Pose.MUG)
        SeasonTheme.PRIMAVERA -> listOf(Pose.SALUTE, Pose.SIDES, Pose.SALUTE)
    }
    // x (fração da largura), pés (fração da altura), altura da figura (fração da altura)
    val spots = arrayOf(
        floatArrayOf(0.43f, 0.925f, 0.55f),
        floatArrayOf(0.585f, 0.895f, 0.51f),
        floatArrayOf(0.735f, 0.93f, 0.555f)
    )
    // o do meio está um passo atrás, então entra primeiro
    for (i in intArrayOf(1, 0, 2)) {
        val s = spots[i]
        sailor(theme, w * s[0], h * s[1], h * s[2], poses[i], Skins[i], i)
    }
}

/**
 * Um marinheiro de frente, em pé no convés. [cx] é o eixo do corpo, [fy] a linha dos
 * pés e [hgt] a altura total da figura; todas as medidas saem dessa altura.
 */
private fun DrawScope.sailor(theme: SeasonTheme, cx: Float, fy: Float, hgt: Float, pose: Pose, skin: Color, idx: Int) {
    val look = lookOf(theme, idx)
    val skinSh = skin.toBlack(0.25f)
    val hh = hgt
    val hr = hh * 0.072f
    val hcy = fy - hh * 0.875f
    val sy = fy - hh * 0.785f
    val hy = fy - hh * 0.45f
    val sw = hh * 0.135f
    val ww = hh * 0.108f
    val hem = if (look.coat) fy - hh * 0.27f else hy + hh * 0.02f

    // sombra no convés
    drawOval(Color.Black, Offset(cx - hh * 0.20f, fy - hh * 0.03f), Size(hh * 0.40f, hh * 0.06f), alpha = 0.35f)

    // pernas
    val tr = Brush.linearGradient(
        listOf(look.trousers.toWhite(0.12f), look.trousers.toBlack(0.35f)),
        Offset(cx - hh * 0.1f, 0f), Offset(cx + hh * 0.1f, 0f)
    )
    drawPath(
        Path().apply {
            moveTo(cx - hh * 0.102f, hy); lineTo(cx - hh * 0.004f, hy)
            lineTo(cx - hh * 0.022f, fy - hh * 0.035f); lineTo(cx - hh * 0.088f, fy - hh * 0.035f); close()
        },
        tr
    )
    drawPath(
        Path().apply {
            moveTo(cx + hh * 0.004f, hy); lineTo(cx + hh * 0.102f, hy)
            lineTo(cx + hh * 0.088f, fy - hh * 0.035f); lineTo(cx + hh * 0.022f, fy - hh * 0.035f); close()
        },
        tr
    )
    drawLine(look.trousers.toBlack(0.4f), Offset(cx, hy + hh * 0.04f), Offset(cx, fy - hh * 0.05f), hh * 0.006f, alpha = 0.6f)
    // sapatos
    drawOval(Color(0xFF121212), Offset(cx - hh * 0.115f, fy - hh * 0.05f), Size(hh * 0.10f, hh * 0.05f))
    drawOval(Color(0xFF121212), Offset(cx + hh * 0.015f, fy - hh * 0.05f), Size(hh * 0.10f, hh * 0.05f))
    drawOval(Color.White, Offset(cx - hh * 0.10f, fy - hh * 0.047f), Size(hh * 0.04f, hh * 0.012f), alpha = 0.25f)

    // tronco: a japona desce até o joelho e abre um pouco na barra
    val flare = if (look.coat) hh * 0.128f else ww
    val waist = if (look.coat) sw * 0.9f else ww
    val torso = Path().apply {
        moveTo(cx - sw, sy + hh * 0.045f)
        quadraticTo(cx - sw, sy, cx - sw + hh * 0.045f, sy)
        lineTo(cx + sw - hh * 0.045f, sy)
        quadraticTo(cx + sw, sy, cx + sw, sy + hh * 0.045f)
        lineTo(cx + waist, hy)
        lineTo(cx + flare, hem)
        lineTo(cx - flare, hem)
        lineTo(cx - waist, hy)
        close()
    }
    drawPath(
        torso,
        Brush.linearGradient(
            listOf(look.top.toWhite(0.18f), look.top, look.top.toBlack(0.38f)),
            Offset(cx - sw, sy), Offset(cx + sw, sy + hh * 0.2f)
        )
    )
    clipPath(torso) {
        drawRect(Color.Black, Offset(cx + sw * 0.45f, sy), Size(sw, hem - sy), alpha = 0.12f)
    }
    // pescoço
    drawRect(skinSh, Offset(cx - hh * 0.028f, hcy + hr * 0.6f), Size(hh * 0.056f, sy - hcy - hr * 0.3f))

    if (look.shortSleeves && look.collar != null) {
        // farda branca: gola de marinheiro azul e lenço
        drawPath(
            Path().apply { moveTo(cx - sw * 0.78f, sy); lineTo(cx + sw * 0.78f, sy); lineTo(cx, sy + hh * 0.14f); close() },
            look.collar
        )
        drawPath(
            Path().apply {
                moveTo(cx - sw * 0.62f, sy + hh * 0.008f); lineTo(cx, sy + hh * 0.118f); lineTo(cx + sw * 0.62f, sy + hh * 0.008f)
            },
            Color.White, alpha = 0.9f, style = strokeOf(hh * 0.006f)
        )
        drawPath(
            Path().apply { moveTo(cx - sw * 0.4f, sy); lineTo(cx + sw * 0.4f, sy); lineTo(cx, sy + hh * 0.075f); close() },
            skinSh
        )
        drawPath(
            Path().apply {
                moveTo(cx - hh * 0.03f, sy + hh * 0.11f); lineTo(cx + hh * 0.03f, sy + hh * 0.11f)
                lineTo(cx + hh * 0.012f, sy + hh * 0.2f); lineTo(cx - hh * 0.012f, sy + hh * 0.2f); close()
            },
            Color(0xFF15151A)
        )
        // cinto com fivela
        drawRect(Color(0xFF1E2430), Offset(cx - ww, hy - hh * 0.012f), Size(ww * 2f, hh * 0.024f))
        drawRect(Gold, Offset(cx - hh * 0.016f, hy - hh * 0.01f), Size(hh * 0.032f, hh * 0.02f))
    }
    if (look.dress) {
        // túnica de gala: camisa branca, gravata, botões dourados, dragonas e barretas
        drawPath(
            Path().apply { moveTo(cx - sw * 0.5f, sy); lineTo(cx + sw * 0.5f, sy); lineTo(cx, sy + hh * 0.15f); close() },
            Color(0xFFF4F4F2)
        )
        drawPath(
            Path().apply {
                moveTo(cx - hh * 0.012f, sy + hh * 0.01f); lineTo(cx + hh * 0.012f, sy + hh * 0.01f)
                lineTo(cx + hh * 0.009f, sy + hh * 0.13f); lineTo(cx, sy + hh * 0.15f)
                lineTo(cx - hh * 0.009f, sy + hh * 0.13f); close()
            },
            Color(0xFF101014)
        )
        drawPath(
            Path().apply { moveTo(cx - sw * 0.5f, sy); lineTo(cx, sy + hh * 0.15f); lineTo(cx + sw * 0.5f, sy) },
            look.top.toBlack(0.5f), alpha = 0.9f, style = strokeOf(hh * 0.008f)
        )
        for (r in 0 until 3) {
            for (sd in intArrayOf(-1, 1)) {
                drawCircle(Gold, radius = hh * 0.011f, center = Offset(cx + sd * hh * 0.042f, sy + hh * (0.18f + 0.075f * r)))
            }
        }
        drawRect(Gold, Offset(cx - sw * 0.95f, sy + hh * 0.02f), Size(hh * 0.05f, hh * 0.012f), alpha = 0.9f)
        drawRect(Gold, Offset(cx + sw * 0.95f - hh * 0.05f, sy + hh * 0.02f), Size(hh * 0.05f, hh * 0.012f), alpha = 0.9f)
        drawRect(Color(0xFFE0453A), Offset(cx - sw * 0.75f, sy + hh * 0.09f), Size(hh * 0.05f, hh * 0.016f))
        drawRect(Color(0xFF2F6FD0), Offset(cx - sw * 0.75f, sy + hh * 0.106f), Size(hh * 0.05f, hh * 0.012f))
    }
    if (look.coat) {
        // japona: lapela alta e duas fileiras de botões
        drawPath(
            Path().apply {
                moveTo(cx - sw * 0.85f, sy + hh * 0.01f); lineTo(cx - hh * 0.01f, sy + hh * 0.02f)
                lineTo(cx - hh * 0.03f, sy + hh * 0.17f); close()
            },
            look.top.toWhite(0.12f)
        )
        drawPath(
            Path().apply {
                moveTo(cx + sw * 0.85f, sy + hh * 0.01f); lineTo(cx + hh * 0.01f, sy + hh * 0.02f)
                lineTo(cx + hh * 0.03f, sy + hh * 0.17f); close()
            },
            look.top.toBlack(0.2f)
        )
        for (r in 0 until 3) {
            for (sd in intArrayOf(-1, 1)) {
                drawCircle(Color(0xFFC9A24A), radius = hh * 0.01f, center = Offset(cx + sd * hh * 0.045f, sy + hh * (0.20f + 0.085f * r)))
            }
        }
        drawLine(look.top.toBlack(0.5f), Offset(cx + hh * 0.01f, sy + hh * 0.17f), Offset(cx + hh * 0.01f, hem), hh * 0.006f, alpha = 0.8f)
        drawRect(look.top.toBlack(0.4f), Offset(cx - sw * 0.9f, hy - hh * 0.01f), Size(sw * 1.8f, hh * 0.02f), alpha = 0.7f)
    }
    look.scarf?.let { sc ->
        // cachecol enrolado no pescoço com a ponta solta
        drawPath(
            roundRectPath(cx - hh * 0.07f, sy - hh * 0.035f, hh * 0.14f, hh * 0.06f, hh * 0.025f),
            Brush.linearGradient(listOf(sc.toWhite(0.15f), sc.toBlack(0.3f)), Offset(cx - hh * 0.07f, 0f), Offset(cx + hh * 0.07f, 0f))
        )
        drawPath(
            Path().apply {
                moveTo(cx + hh * 0.01f, sy + hh * 0.01f); lineTo(cx + hh * 0.06f, sy + hh * 0.012f)
                lineTo(cx + hh * 0.075f, sy + hh * 0.17f); lineTo(cx + hh * 0.035f, sy + hh * 0.165f); close()
            },
            sc.toBlack(0.12f)
        )
        drawLine(sc.toWhite(0.4f), Offset(cx + hh * 0.04f, sy + hh * 0.16f), Offset(cx + hh * 0.075f, sy + hh * 0.163f), hh * 0.006f, alpha = 0.8f)
    }

    // cabeça: orelhas, rosto com o lado direito na sombra, cabelo, olhos e boca
    drawCircle(skinSh, radius = hr * 0.24f, center = Offset(cx - hr * 0.96f, hcy + hr * 0.12f))
    drawCircle(skinSh, radius = hr * 0.24f, center = Offset(cx + hr * 0.96f, hcy + hr * 0.12f))
    val head = Path().apply { addOval(Rect(cx - hr, hcy - hr * 1.05f, cx + hr, hcy + hr * 1.05f)) }
    drawPath(head, skinSh)
    clipPath(head) {
        drawCircle(skin, radius = hr, center = Offset(cx - hr * 0.2f, hcy - hr * 0.14f))
        drawRect(Color(0xFF241A14), Offset(cx - hr, hcy - hr * 1.2f), Size(hr * 2f, hr * 0.75f))
    }
    drawCircle(Color(0xFF1A1410), radius = hr * 0.09f, center = Offset(cx - hr * 0.36f, hcy + hr * 0.08f))
    drawCircle(Color(0xFF1A1410), radius = hr * 0.09f, center = Offset(cx + hr * 0.36f, hcy + hr * 0.08f))
    drawLine(
        skin.toBlack(0.45f), Offset(cx - hr * 0.2f, hcy + hr * 0.55f), Offset(cx + hr * 0.2f, hcy + hr * 0.55f),
        hr * 0.1f, cap = StrokeCap.Round, alpha = 0.7f
    )
    drawLine(
        skin.toBlack(0.2f), Offset(cx + hr * 0.05f, hcy + hr * 0.12f), Offset(cx + hr * 0.1f, hcy + hr * 0.35f),
        hr * 0.1f, cap = StrokeCap.Round, alpha = 0.6f
    )
    headgear(look, cx, hcy, hr)

    // braços: ombro, cotovelo e mão; a pose só troca os dois últimos pontos
    val sh = sy + hh * 0.035f
    val aw = hh * 0.068f
    fun side(sd: Float) = arrayOf(
        Offset(cx + sd * (sw - hh * 0.02f), sh),
        Offset(cx + sd * (sw + hh * 0.018f), sy + hh * 0.18f),
        Offset(cx + sd * (sw + hh * 0.008f), hy + hh * 0.01f)
    )
    var left = side(-1f)
    var right = side(1f)
    when (pose) {
        Pose.BINOCULARS -> {
            left = arrayOf(left[0], Offset(cx - sw - hh * 0.02f, sy + hh * 0.13f), Offset(cx - hh * 0.06f, hcy + hr * 0.3f))
            right = arrayOf(right[0], Offset(cx + sw + hh * 0.02f, sy + hh * 0.13f), Offset(cx + hh * 0.06f, hcy + hr * 0.3f))
        }
        Pose.SALUTE -> left = arrayOf(left[0], Offset(cx - sw - hh * 0.10f, sy + hh * 0.03f), Offset(cx - hr * 0.85f, hcy - hr * 0.5f))
        Pose.POINT -> left = arrayOf(left[0], Offset(cx - sw - hh * 0.09f, sy - hh * 0.005f), Offset(cx - sw - hh * 0.20f, sy - hh * 0.07f))
        Pose.MUG -> right = arrayOf(right[0], Offset(cx + sw + hh * 0.02f, sy + hh * 0.17f), Offset(cx + hh * 0.05f, sy + hh * 0.15f))
        Pose.SIDES -> Unit
    }
    sailorArm(look, left, aw, skin)
    sailorArm(look, right, aw, skin)

    when (pose) {
        Pose.BINOCULARS -> {
            for (sd in intArrayOf(-1, 1)) {
                drawPath(
                    roundRectPath(cx + sd * hr * 0.48f - hh * 0.026f, hcy - hr * 0.25f, hh * 0.052f, hh * 0.06f, hh * 0.012f),
                    Color(0xFF15181C)
                )
                drawCircle(Color(0xFF6FA8C8), radius = hh * 0.012f, center = Offset(cx + sd * hr * 0.48f, hcy - hr * 0.1f), alpha = 0.8f)
            }
            drawRect(Color(0xFF15181C), Offset(cx - hr * 0.5f, hcy - hr * 0.05f), Size(hr, hh * 0.02f))
        }
        Pose.POINT -> {
            val tip = left[2]
            drawLine(
                look.gloves ?: skin, tip, Offset(tip.x - hh * 0.04f, tip.y - hh * 0.016f),
                hh * 0.018f, cap = StrokeCap.Round
            )
        }
        Pose.MUG -> {
            // caneca fumegando na mão enluvada
            val m = right[2]
            val mw = hh * 0.062f
            drawPath(
                roundRectPath(m.x - mw * 0.6f, m.y - mw * 0.9f, mw, mw * 1.1f, mw * 0.15f),
                Brush.linearGradient(listOf(Color(0xFFF2F2EE), Color(0xFFB0B6BC)), Offset(m.x - mw * 0.6f, 0f), Offset(m.x + mw * 0.4f, 0f))
            )
            drawRect(Color(0xFF1F3A7A), Offset(m.x - mw * 0.6f, m.y - mw * 0.55f), Size(mw, mw * 0.25f))
            drawCircle(look.gloves ?: skin, radius = aw * 0.45f, center = Offset(m.x + mw * 0.42f, m.y - mw * 0.3f))
            for (i in 0 until 2) {
                val x = m.x - mw * 0.3f + mw * 0.4f * i
                drawPath(
                    Path().apply {
                        moveTo(x, m.y - mw * 1.0f)
                        quadraticTo(x - mw * 0.4f, m.y - mw * 1.5f, x, m.y - mw * 1.9f)
                        quadraticTo(x + mw * 0.4f, m.y - mw * 2.3f, x, m.y - mw * 2.7f)
                    },
                    Color.White, alpha = 0.5f, style = strokeOf(hh * 0.008f)
                )
            }
        }
        else -> Unit
    }
    if (theme == SeasonTheme.INVERNO && pose != Pose.MUG) {
        // respiração condensando no ar frio
        for (i in 0 until 3) {
            drawCircle(
                Color.White, radius = hr * (0.22f + 0.12f * i),
                center = Offset(cx - hr * (0.5f + 0.55f * i), hcy + hr * (0.75f - 0.15f * i)), alpha = 0.38f - 0.1f * i
            )
        }
    }
}

/** Braço em dois segmentos com manga (curta no verão), brilho de luz e a mão. */
private fun DrawScope.sailorArm(look: SailorLook, pts: Array<Offset>, aw: Float, skin: Color) {
    val arm = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        lineTo(pts[1].x, pts[1].y)
        lineTo(pts[2].x, pts[2].y)
    }
    if (look.shortSleeves) {
        drawPath(arm, skin.toBlack(0.1f), style = strokeOf(aw * 0.85f))
        val ex = pts[0].x + (pts[1].x - pts[0].x) * 0.6f
        val ey = pts[0].y + (pts[1].y - pts[0].y) * 0.6f
        drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); lineTo(ex, ey) }, look.top.toBlack(0.12f), style = strokeOf(aw * 1.15f))
        drawPath(
            Path().apply { moveTo(pts[0].x - aw * 0.15f, pts[0].y - aw * 0.1f); lineTo(ex - aw * 0.15f, ey - aw * 0.1f) },
            look.top.toWhite(0.5f), alpha = 0.35f, style = strokeOf(aw * 0.3f)
        )
    } else {
        drawPath(arm, look.top.toBlack(0.12f), style = strokeOf(aw))
        drawPath(
            Path().apply {
                moveTo(pts[0].x - aw * 0.18f, pts[0].y - aw * 0.12f)
                lineTo(pts[1].x - aw * 0.18f, pts[1].y - aw * 0.12f)
            },
            look.top.toWhite(0.25f), alpha = 0.5f, style = strokeOf(aw * 0.32f)
        )
        if (look.dress) {
            // galão dourado no punho
            val d = hypot(pts[2].x - pts[1].x, pts[2].y - pts[1].y)
            if (d > 0f) {
                val ux = (pts[2].x - pts[1].x) / d
                val uy = (pts[2].y - pts[1].y) / d
                val cxp = pts[2].x - ux * aw * 0.9f
                val cyp = pts[2].y - uy * aw * 0.9f
                drawLine(
                    Gold, Offset(cxp - uy * aw * 0.45f, cyp + ux * aw * 0.45f),
                    Offset(cxp + uy * aw * 0.45f, cyp - ux * aw * 0.45f), aw * 0.18f
                )
            }
        }
    }
    drawCircle(look.gloves ?: skin, radius = aw * 0.52f, center = pts[2])
}

private fun DrawScope.headgear(look: SailorLook, cx: Float, hcy: Float, hr: Float) {
    val c = look.capColor
    when (look.headgear) {
        Headgear.DIXIE -> {
            // gorro branco de marinheiro: cúpula e aba virada
            val b = hcy - hr * 0.5f
            drawPath(
                Path().apply {
                    moveTo(cx - hr * 0.92f, b)
                    cubicTo(cx - hr * 0.88f, b - hr * 1.05f, cx + hr * 0.88f, b - hr * 1.05f, cx + hr * 0.92f, b)
                    close()
                },
                Brush.linearGradient(listOf(Color.White, Color(0xFFC9D3DC)), Offset(cx - hr, b - hr), Offset(cx + hr, b))
            )
            drawPath(
                roundRectPath(cx - hr * 1.12f, b - hr * 0.22f, hr * 2.24f, hr * 0.42f, hr * 0.2f),
                Brush.linearGradient(listOf(Color.White, Color(0xFFB4C0CB)), Offset(cx - hr, b - hr * 0.2f), Offset(cx + hr, b + hr * 0.2f))
            )
            drawLine(Color(0xFF9AA8B5), Offset(cx - hr * 1.05f, b + hr * 0.15f), Offset(cx + hr * 1.05f, b + hr * 0.15f), hr * 0.06f, alpha = 0.8f)
        }
        Headgear.PEAKED -> {
            // quepe: copa larga, fita escura, pala preta e distintivo
            val white = c.red > 0.8f
            val band = if (white) Color(0xFF14161C) else Color(0xFF0D121E)
            drawPath(
                Path().apply {
                    moveTo(cx - hr * 0.95f, hcy - hr * 0.58f)
                    lineTo(cx - hr * 1.3f, hcy - hr * 1.08f)
                    quadraticTo(cx, hcy - hr * 1.42f, cx + hr * 1.3f, hcy - hr * 1.08f)
                    lineTo(cx + hr * 0.95f, hcy - hr * 0.58f)
                    close()
                },
                Brush.linearGradient(
                    listOf(c.toWhite(0.15f), c.toBlack(if (white) 0.2f else 0.3f)),
                    Offset(cx - hr, hcy - hr * 1.3f), Offset(cx + hr, hcy - hr * 0.6f)
                )
            )
            drawRect(band, Offset(cx - hr * 0.97f, hcy - hr * 0.88f), Size(hr * 1.94f, hr * 0.32f))
            drawPath(
                Path().apply {
                    moveTo(cx - hr * 0.98f, hcy - hr * 0.58f)
                    quadraticTo(cx, hcy - hr * 0.18f, cx + hr * 0.98f, hcy - hr * 0.58f)
                    quadraticTo(cx, hcy - hr * 0.42f, cx - hr * 0.98f, hcy - hr * 0.58f)
                    close()
                },
                Color(0xFF0A0A0C)
            )
            drawCircle(Gold, radius = hr * 0.2f, center = Offset(cx, hcy - hr * 0.8f))
            drawLine(
                Color.White, Offset(cx - hr * 0.6f, hcy - hr * 0.5f), Offset(cx - hr * 0.2f, hcy - hr * 0.4f),
                hr * 0.06f, cap = StrokeCap.Round, alpha = 0.35f
            )
        }
        Headgear.BEANIE -> {
            // gorro de lã justo, com barra dobrada
            drawPath(
                Path().apply {
                    moveTo(cx - hr * 1.05f, hcy - hr * 0.2f)
                    cubicTo(cx - hr * 1.1f, hcy - hr * 1.6f, cx + hr * 1.1f, hcy - hr * 1.6f, cx + hr * 1.05f, hcy - hr * 0.2f)
                    close()
                },
                Brush.linearGradient(listOf(c.toWhite(0.2f), c.toBlack(0.3f)), Offset(cx - hr, hcy - hr), Offset(cx + hr, hcy))
            )
            for (i in -2..2) {
                drawLine(
                    c.toBlack(0.35f), Offset(cx + i * hr * 0.35f, hcy - hr * 1.0f + abs(i) * hr * 0.12f),
                    Offset(cx + i * hr * 0.38f, hcy - hr * 0.5f), hr * 0.06f, alpha = 0.6f
                )
            }
            drawPath(
                roundRectPath(cx - hr * 1.1f, hcy - hr * 0.62f, hr * 2.2f, hr * 0.44f, hr * 0.16f),
                Brush.linearGradient(listOf(c.toWhite(0.1f), c.toBlack(0.4f)), Offset(cx - hr, 0f), Offset(cx + hr, 0f))
            )
            for (i in -4..4) {
                drawLine(c.toBlack(0.45f), Offset(cx + i * hr * 0.24f, hcy - hr * 0.58f), Offset(cx + i * hr * 0.24f, hcy - hr * 0.22f), hr * 0.05f, alpha = 0.6f)
            }
        }
    }
}

// ---------- clima ----------

private fun DrawScope.seasonWeather(theme: SeasonTheme, w: Float, h: Float, rng: SeasonRng) {
    when (theme) {
        SeasonTheme.INVERNO -> repeat(140) {
            // neve: flocos maiores e mais opacos parecem mais perto
            val x = rng.next() * w
            val y = rng.next() * h
            val d = rng.next()
            drawCircle(Color.White, radius = h * (0.003f + 0.007f * d * d), center = Offset(x, y), alpha = 0.45f + 0.5f * d)
        }

        SeasonTheme.OUTONO -> {
            val cols = listOf(Color(0xFFE07A3F), Color(0xFFC8423A), Color(0xFFE6AC3F), Color(0xFF8A4A22))
            for (i in 0 until 16) {
                var x = rng.next() * w
                val y = rng.range(0.05f, 0.8f) * h
                // canto superior esquerdo fica livre para o título
                if (x < w * 0.32f && y < h * 0.35f) x += w * 0.4f
                leaf(x, y, h * rng.range(0.018f, 0.034f), rng.range(0f, 360f), cols[i % 4])
            }
        }

        SeasonTheme.PRIMAVERA -> {
            blossomBranch(w, h)
            for (i in 0 until 30) {
                var x = rng.next() * w
                val y = rng.range(0.02f, 0.86f) * h
                if (x < w * 0.30f && y < h * 0.35f) x += w * 0.45f
                val s = h * rng.range(0.008f, 0.016f)
                rotate(rng.range(0f, 180f), Offset(x, y)) {
                    drawOval(
                        if (i % 5 == 0) Color(0xFFFFE07A) else Color(0xFFF7A8C8),
                        Offset(x - s, y - s * 0.55f), Size(s * 2f, s * 1.1f), alpha = 0.9f
                    )
                }
            }
        }

        SeasonTheme.VERAO -> Unit
    }
}

/** Folha seca soprada pelo vento, com nervura e cabinho. */
private fun DrawScope.leaf(x: Float, y: Float, s: Float, rot: Float, color: Color) {
    rotate(rot, Offset(x, y)) {
        drawPath(
            Path().apply {
                moveTo(x - s, y)
                quadraticTo(x - s * 0.2f, y - s * 0.75f, x + s, y)
                quadraticTo(x - s * 0.2f, y + s * 0.75f, x - s, y)
                close()
            },
            Brush.linearGradient(listOf(color.toWhite(0.2f), color.toBlack(0.25f)), Offset(x, y - s * 0.5f), Offset(x, y + s * 0.5f))
        )
        drawLine(color.toBlack(0.4f), Offset(x - s * 0.9f, y), Offset(x + s * 0.8f, y), s * 0.08f, alpha = 0.8f)
        drawLine(color.toBlack(0.4f), Offset(x - s * 1.25f, y), Offset(x - s * 0.9f, y), s * 0.1f, cap = StrokeCap.Round, alpha = 0.9f)
    }
}

/** Galho de ipê-rosa entrando pelo canto superior direito. */
private fun DrawScope.blossomBranch(w: Float, h: Float) {
    val bark = Color(0xFF4A3426)
    drawPath(
        Path().apply {
            moveTo(w + h * 0.02f, h * 0.02f)
            cubicTo(w - h * 0.18f, h * 0.03f, w - h * 0.30f, h * 0.10f, w - h * 0.46f, h * 0.12f)
        },
        bark, style = strokeOf(h * 0.016f)
    )
    drawPath(
        Path().apply { moveTo(w - h * 0.20f, h * 0.045f); quadraticTo(w - h * 0.26f, h * 0.14f, w - h * 0.33f, h * 0.20f) },
        bark, style = strokeOf(h * 0.009f)
    )
    drawPath(
        Path().apply { moveTo(w - h * 0.08f, h * 0.025f); quadraticTo(w - h * 0.10f, h * 0.12f, w - h * 0.06f, h * 0.17f) },
        bark, style = strokeOf(h * 0.008f)
    )
    val blooms = arrayOf(
        floatArrayOf(0.46f, 0.12f, 1.0f), floatArrayOf(0.39f, 0.10f, 0.8f), floatArrayOf(0.33f, 0.20f, 0.9f),
        floatArrayOf(0.27f, 0.13f, 0.85f), floatArrayOf(0.20f, 0.06f, 0.75f), floatArrayOf(0.06f, 0.17f, 0.85f),
        floatArrayOf(0.12f, 0.08f, 0.7f), floatArrayOf(0.30f, 0.04f, 0.6f)
    )
    for (b in blooms) blossom(w - h * b[0], h * b[1], h * 0.034f * b[2])
}

/** Flor de cinco pétalas com miolo amarelo. */
private fun DrawScope.blossom(cx: Float, cy: Float, s: Float) {
    for (i in 0 until 5) {
        val a = i / 5f * 2f * PI.toFloat() - PI.toFloat() / 2f
        val px = cx + cos(a) * s * 0.55f
        val py = cy + sin(a) * s * 0.55f
        drawCircle(Color(0xFFD9558C), radius = s * 0.48f, center = Offset(px + s * 0.06f, py + s * 0.08f))
        drawCircle(Color(0xFFF7A8C8), radius = s * 0.44f, center = Offset(px, py))
    }
    drawCircle(Color(0xFFFFE07A), radius = s * 0.22f, center = Offset(cx, cy))
}

// ---------- ícones ----------

/** Ícone pequeno e simples da estação (sol, folha, floco, flor) num quadrado de lado [size], para chips de 16-24dp. */
fun DrawScope.drawSeasonIcon(theme: SeasonTheme, center: Offset, size: Float) {
    val r = size / 2f
    val a = theme.accent
    val cx = center.x
    val cy = center.y
    val tau = 2f * PI.toFloat()
    when (theme) {
        SeasonTheme.VERAO -> {
            for (i in 0 until 8) {
                val ang = i / 8f * tau
                drawLine(
                    a, Offset(cx + cos(ang) * r * 0.62f, cy + sin(ang) * r * 0.62f),
                    Offset(cx + cos(ang) * r * 0.95f, cy + sin(ang) * r * 0.95f), r * 0.16f, cap = StrokeCap.Round
                )
            }
            drawCircle(
                Brush.radialGradient(listOf(a.toWhite(0.5f), a, a.toBlack(0.15f)), Offset(cx - r * 0.15f, cy - r * 0.15f), r * 0.6f),
                radius = r * 0.46f, center = center
            )
        }

        SeasonTheme.OUTONO -> rotate(-40f, center) {
            val vein = a.toBlack(0.5f)
            drawPath(
                Path().apply {
                    moveTo(cx - r * 0.95f, cy)
                    quadraticTo(cx - r * 0.1f, cy - r * 0.85f, cx + r * 0.95f, cy)
                    quadraticTo(cx - r * 0.1f, cy + r * 0.85f, cx - r * 0.95f, cy)
                    close()
                },
                Brush.linearGradient(listOf(a.toWhite(0.25f), a.toBlack(0.2f)), Offset(cx, cy - r * 0.5f), Offset(cx, cy + r * 0.5f))
            )
            drawLine(vein, Offset(cx - r * 0.95f, cy), Offset(cx + r * 0.7f, cy), r * 0.1f, cap = StrokeCap.Round)
            for (t in floatArrayOf(-0.35f, 0.15f)) {
                drawLine(vein, Offset(cx + r * t, cy), Offset(cx + r * (t + 0.3f), cy - r * 0.28f), r * 0.07f, cap = StrokeCap.Round)
                drawLine(vein, Offset(cx + r * t, cy), Offset(cx + r * (t + 0.3f), cy + r * 0.28f), r * 0.07f, cap = StrokeCap.Round)
            }
        }

        SeasonTheme.INVERNO -> {
            for (i in 0 until 6) {
                val ang = i / 6f * tau - tau / 4f
                drawLine(a, center, Offset(cx + cos(ang) * r * 0.92f, cy + sin(ang) * r * 0.92f), r * 0.14f, cap = StrokeCap.Round)
                val bx = cx + cos(ang) * r * 0.55f
                val by = cy + sin(ang) * r * 0.55f
                for (sd in intArrayOf(-1, 1)) {
                    val b = ang + sd * 0.75f
                    drawLine(a, Offset(bx, by), Offset(bx + cos(b) * r * 0.3f, by + sin(b) * r * 0.3f), r * 0.12f, cap = StrokeCap.Round)
                }
            }
            drawCircle(a.toWhite(0.5f), radius = r * 0.14f, center = center)
        }

        SeasonTheme.PRIMAVERA -> {
            for (i in 0 until 5) {
                val ang = i / 5f * tau - tau / 4f
                drawCircle(a, radius = r * 0.36f, center = Offset(cx + cos(ang) * r * 0.52f, cy + sin(ang) * r * 0.52f))
            }
            for (i in 0 until 5) {
                val ang = i / 5f * tau - tau / 4f
                drawCircle(
                    a.toWhite(0.3f), radius = r * 0.2f,
                    center = Offset(cx + cos(ang) * r * 0.5f - r * 0.05f, cy + sin(ang) * r * 0.5f - r * 0.05f)
                )
            }
            drawCircle(Color(0xFFFFE07A), radius = r * 0.26f, center = center)
        }
    }
}
