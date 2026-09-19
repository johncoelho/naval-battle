package br.com.navalbattle.design

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import br.com.navalbattle.game.Avatar

/**
 * Retratos da tripulação no traço de cartaz de guerra: duas tintas sobre fundo
 * escuro, sombra chapada de um lado só e meio-tom em pontos onde a luz vira sombra —
 * o mesmo vocabulário gráfico de um pôster de recrutamento serigrafado.
 *
 * Tudo continua sendo vetor: a mesma arte serve do retrato de 28 dp da lista de
 * amigos ao de 78 dp do perfil, sem nenhuma imagem no APK.
 *
 * Os oito retratos são quatro papéis em par — comandante, oficial, contramestre e
 * aviador — com rosto de homem e de mulher. A patente é a mesma nos dois: o que se
 * escolhe aqui é a cara, não o posto.
 */
fun DrawScope.drawAvatar(avatar: Avatar, center: Offset, size: Float, color: Color) {
    val s = size / 2f

    // paleta de duas tintas derivada da cor pedida pela tela
    val ink = Color(0xFF12170D)
    val skin = color.mix(Color(0xFFF0DCAE), 0.55f)
    val skinShade = color.mix(Color(0xFF9A6A20), 0.35f)
    val cloth = Color(0xFFEDE3C8)
    val uniform = Color(0xFF232C1C)

    val face = Path().apply {
        moveTo(center.x, center.y - s * 0.66f)
        cubicTo(
            center.x + s * 0.25f, center.y - s * 0.66f,
            center.x + s * 0.43f, center.y - s * 0.52f,
            center.x + s * 0.45f, center.y - s * 0.26f
        )
        cubicTo(
            center.x + s * 0.46f, center.y - s * 0.08f,
            center.x + s * 0.43f, center.y + s * 0.10f,
            center.x + s * 0.36f, center.y + s * 0.24f
        )
        cubicTo(
            center.x + s * 0.29f, center.y + s * 0.38f,
            center.x + s * 0.16f, center.y + s * 0.47f,
            center.x, center.y + s * 0.47f
        )
        cubicTo(
            center.x - s * 0.16f, center.y + s * 0.47f,
            center.x - s * 0.29f, center.y + s * 0.38f,
            center.x - s * 0.36f, center.y + s * 0.24f
        )
        cubicTo(
            center.x - s * 0.43f, center.y + s * 0.10f,
            center.x - s * 0.46f, center.y - s * 0.08f,
            center.x - s * 0.45f, center.y - s * 0.26f
        )
        cubicTo(
            center.x - s * 0.43f, center.y - s * 0.52f,
            center.x - s * 0.25f, center.y - s * 0.66f,
            center.x, center.y - s * 0.66f
        )
        close()
    }

    clipPath(Path().apply { addOval(Rect(center - Offset(s, s), Size(size, size))) }) {
        // fundo do cartaz: raios saindo do retrato
        drawCircle(Color(0xFF1B2313), radius = s, center = center)
        repeat(4) { i ->
            val a = (i / 4f) * 2f * PI + 0.35f
            val p = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + s * 1.5f * cos(a - 0.16f), center.y + s * 1.5f * sin(a - 0.16f))
                lineTo(center.x + s * 1.5f * cos(a + 0.16f), center.y + s * 1.5f * sin(a + 0.16f))
                close()
            }
            drawPath(p, color.copy(alpha = 0.18f))
        }

        // ombros e farda
        val shoulders = Path().apply {
            moveTo(center.x - s, center.y + s * 1.1f)
            cubicTo(
                center.x - s * 0.92f, center.y + s * 0.6f,
                center.x - s * 0.54f, center.y + s * 0.42f,
                center.x - s * 0.2f, center.y + s * 0.38f
            )
            lineTo(center.x + s * 0.2f, center.y + s * 0.38f)
            cubicTo(
                center.x + s * 0.54f, center.y + s * 0.42f,
                center.x + s * 0.92f, center.y + s * 0.6f,
                center.x + s, center.y + s * 1.1f
            )
            close()
        }
        drawPath(shoulders, uniform)
        drawPath(
            Path().apply {
                moveTo(center.x - s, center.y + s * 1.1f)
                cubicTo(
                    center.x - s * 0.94f, center.y + s * 0.72f,
                    center.x - s * 0.7f, center.y + s * 0.52f,
                    center.x - s * 0.44f, center.y + s * 0.44f
                )
                cubicTo(
                    center.x - s * 0.58f, center.y + s * 0.62f,
                    center.x - s * 0.66f, center.y + s * 0.84f,
                    center.x - s * 0.7f, center.y + s * 1.1f
                )
                close()
            },
            uniform.mix(Color.White, 0.10f)
        )

        // gola branca de serviço
        drawPath(
            Path().apply {
                moveTo(center.x - s * 0.24f, center.y + s * 0.40f)
                lineTo(center.x, center.y + s * 0.74f)
                lineTo(center.x + s * 0.24f, center.y + s * 0.40f)
                lineTo(center.x + s * 0.13f, center.y + s * 0.36f)
                lineTo(center.x, center.y + s * 0.54f)
                lineTo(center.x - s * 0.13f, center.y + s * 0.36f)
                close()
            },
            cloth
        )

        val v = avatar.woman
        val role = avatar.role

        // cabelo por trás do rosto, para os retratos femininos
        if (v) {
            listOf(-1f, 1f).forEach { d ->
                drawPath(
                    Path().apply {
                        moveTo(center.x + d * s * 0.46f, center.y - s * 0.22f)
                        cubicTo(
                            center.x + d * s * 0.58f, center.y + s * 0.14f,
                            center.x + d * s * 0.54f, center.y + s * 0.44f,
                            center.x + d * s * 0.38f, center.y + s * 0.56f
                        )
                        lineTo(center.x + d * s * 0.34f, center.y + s * 0.1f)
                        close()
                    },
                    Color(0xFF26200F)
                )
            }
        }

        // pescoço
        drawRect(
            skinShade,
            topLeft = Offset(center.x - s * 0.18f, center.y + s * 0.12f),
            size = Size(s * 0.36f, s * 0.34f)
        )

        // rosto: massa clara e sombra chapada do lado direito
        drawPath(face, skin)
        clipPath(face) {
            drawRect(
                skinShade.copy(alpha = 0.55f),
                topLeft = Offset(center.x, center.y - s),
                size = Size(s, size)
            )
            // meio-tom onde a luz vira sombra
            halftone(
                Rect(center.x + s * 0.12f, center.y - s * 0.6f, center.x + s * 0.5f, center.y + s * 0.5f),
                skinShade,
                s * 0.052f
            )
            if (role == Role.BOATSWAIN || role == Role.AVIATOR) {
                // barba por fazer / barba cerrada: mancha de meio-tom na mandíbula
                halftone(
                    Rect(center.x - s * 0.4f, center.y + s * 0.02f, center.x + s * 0.4f, center.y + s * 0.46f),
                    ink,
                    s * 0.05f
                )
            }
        }

        // orelhas
        listOf(-1f, 1f).forEach { d ->
            drawPath(
                Path().apply {
                    moveTo(center.x + d * s * 0.45f, center.y - s * 0.14f)
                    cubicTo(
                        center.x + d * s * 0.53f, center.y - s * 0.14f,
                        center.x + d * s * 0.55f, center.y - s * 0.02f,
                        center.x + d * s * 0.48f, center.y + s * 0.06f
                    )
                    close()
                },
                if (d < 0) skin.mix(Color(0xFF9A6A20), 0.2f) else skinShade
            )
        }

        // feições comuns: sobrancelha baixa, olho amendoado com pálpebra, nariz
        val eyeY = center.y - s * 0.17f
        val browY = center.y - s * 0.30f
        val browColor = if (role == Role.COMMANDER && !v) cloth else ink
        listOf(-1f, 1f).forEach { d ->
            drawLine(
                browColor,
                Offset(center.x + d * s * 0.34f, browY),
                Offset(center.x + d * s * 0.07f, browY - s * 0.04f),
                strokeWidth = s * 0.075f,
                cap = StrokeCap.Round
            )
            val eye = Path().apply {
                moveTo(center.x + d * s * 0.32f, eyeY)
                cubicTo(
                    center.x + d * s * 0.26f, eyeY - s * 0.07f,
                    center.x + d * s * 0.13f, eyeY - s * 0.07f,
                    center.x + d * s * 0.07f, eyeY
                )
                cubicTo(
                    center.x + d * s * 0.13f, eyeY + s * 0.05f,
                    center.x + d * s * 0.26f, eyeY + s * 0.05f,
                    center.x + d * s * 0.32f, eyeY
                )
                close()
            }
            drawPath(eye, Color(0xFFF7F2E2))
            drawCircle(ink, radius = s * 0.045f, center = Offset(center.x + d * s * 0.195f, eyeY))
            // pálpebra pesada: o traço que tira a cara de criança
            drawPath(
                Path().apply {
                    moveTo(center.x + d * s * 0.33f, eyeY - s * 0.01f)
                    cubicTo(
                        center.x + d * s * 0.26f, eyeY - s * 0.08f,
                        center.x + d * s * 0.12f, eyeY - s * 0.08f,
                        center.x + d * s * 0.06f, eyeY - s * 0.01f
                    )
                },
                ink,
                style = Stroke(width = s * 0.035f, cap = StrokeCap.Round)
            )
            // pés de galinha
            drawLine(
                skinShade,
                Offset(center.x + d * s * 0.37f, eyeY - s * 0.03f),
                Offset(center.x + d * s * 0.44f, eyeY - s * 0.07f),
                strokeWidth = s * 0.02f,
                cap = StrokeCap.Round
            )
        }

        // nariz com dorso e asa
        drawPath(
            Path().apply {
                moveTo(center.x - s * 0.04f, center.y - s * 0.16f)
                lineTo(center.x - s * 0.08f, center.y + s * 0.06f)
                lineTo(center.x + s * 0.02f, center.y + s * 0.09f)
            },
            skinShade,
            style = Stroke(width = s * 0.033f, cap = StrokeCap.Round)
        )
        // sulco nasogeniano
        listOf(-1f, 1f).forEach { d ->
            drawPath(
                Path().apply {
                    moveTo(center.x + d * s * 0.12f, center.y + s * 0.06f)
                    cubicTo(
                        center.x + d * s * 0.17f, center.y + s * 0.15f,
                        center.x + d * s * 0.16f, center.y + s * 0.21f,
                        center.x + d * s * 0.11f, center.y + s * 0.25f
                    )
                },
                skinShade,
                style = Stroke(width = s * 0.024f, cap = StrokeCap.Round)
            )
        }

        // boca: firme nos homens, com batom escuro de cartaz nas mulheres
        if (v) {
            drawPath(
                Path().apply {
                    moveTo(center.x - s * 0.14f, center.y + s * 0.23f)
                    cubicTo(
                        center.x - s * 0.07f, center.y + s * 0.18f,
                        center.x + s * 0.07f, center.y + s * 0.18f,
                        center.x + s * 0.14f, center.y + s * 0.23f
                    )
                    cubicTo(
                        center.x + s * 0.07f, center.y + s * 0.32f,
                        center.x - s * 0.07f, center.y + s * 0.32f,
                        center.x - s * 0.14f, center.y + s * 0.23f
                    )
                    close()
                },
                Color(0xFF8C3B22)
            )
        } else if (role != Role.COMMANDER && role != Role.BOATSWAIN) {
            drawLine(
                ink,
                Offset(center.x - s * 0.14f, center.y + s * 0.25f),
                Offset(center.x + s * 0.14f, center.y + s * 0.25f),
                strokeWidth = s * 0.038f,
                cap = StrokeCap.Round
            )
        }

        // marca de tempo: testa vincada nos papéis mais velhos
        if (role == Role.COMMANDER || role == Role.BOATSWAIN) {
            listOf(0.42f, 0.50f).forEach { f ->
                drawPath(
                    Path().apply {
                        moveTo(center.x - s * 0.26f, center.y - s * f)
                        cubicTo(
                            center.x - s * 0.1f, center.y - s * (f + 0.05f),
                            center.x + s * 0.1f, center.y - s * (f + 0.05f),
                            center.x + s * 0.26f, center.y - s * f
                        )
                    },
                    skinShade,
                    style = Stroke(width = s * 0.024f, cap = StrokeCap.Round)
                )
            }
        }

        when (role) {
            Role.COMMANDER -> {
                if (!v) beard(center, s, cloth, ink, full = true)
                if (!v) pipe(center, s, ink, color)
                if (v) sweptHair(center, s, Color(0xFF26200F))
                peakedCap(center, s, cloth, ink, color, braid = true)
            }

            Role.OFFICER -> {
                if (!v) moustache(center, s, Color(0xFF2A2410))
                if (v) sweptHair(center, s, Color(0xFF2A2410))
                peakedCap(center, s, cloth, ink, color, braid = false)
            }

            Role.BOATSWAIN -> {
                beard(center, s, Color(0xFF2E2814), ink, full = !v)
                if (v) {
                    headScarf(center, s, color)
                    drawCircle(
                        color,
                        radius = s * 0.07f,
                        center = Offset(center.x - s * 0.47f, center.y + s * 0.1f),
                        style = Stroke(width = s * 0.026f)
                    )
                } else {
                    sailorCap(center, s, cloth)
                }
            }

            Role.AVIATOR -> {
                flightHelmet(center, s, ink, color)
                scarf(center, s, cloth)
            }
        }
    }

    // moldura: anel duplo de cartaz
    drawCircle(color.copy(alpha = 0.55f), radius = s * 0.97f, center = center, style = Stroke(width = s * 0.03f))
    drawCircle(color.copy(alpha = 0.3f), radius = s * 0.9f, center = center, style = Stroke(width = s * 0.012f))
}

// ------------------------------------------------------------------ peças

/** Barba: massa cheia contornando a mandíbula, com o bigode por cima. */
private fun DrawScope.beard(center: Offset, s: Float, tone: Color, ink: Color, full: Boolean) {
    if (full) {
        drawPath(
            Path().apply {
                moveTo(center.x - s * 0.41f, center.y - s * 0.06f)
                cubicTo(
                    center.x - s * 0.41f, center.y + s * 0.36f,
                    center.x - s * 0.22f, center.y + s * 0.62f,
                    center.x, center.y + s * 0.62f
                )
                cubicTo(
                    center.x + s * 0.22f, center.y + s * 0.62f,
                    center.x + s * 0.41f, center.y + s * 0.36f,
                    center.x + s * 0.41f, center.y - s * 0.06f
                )
                cubicTo(
                    center.x + s * 0.39f, center.y + s * 0.18f,
                    center.x + s * 0.24f, center.y + s * 0.32f,
                    center.x, center.y + s * 0.32f
                )
                cubicTo(
                    center.x - s * 0.24f, center.y + s * 0.32f,
                    center.x - s * 0.39f, center.y + s * 0.18f,
                    center.x - s * 0.41f, center.y - s * 0.06f
                )
                close()
            },
            tone
        )
    }
    moustache(center, s, tone)
}

private fun DrawScope.moustache(center: Offset, s: Float, tone: Color) {
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.19f, center.y + s * 0.14f)
            cubicTo(
                center.x - s * 0.1f, center.y + s * 0.07f,
                center.x + s * 0.1f, center.y + s * 0.07f,
                center.x + s * 0.19f, center.y + s * 0.14f
            )
            cubicTo(
                center.x + s * 0.1f, center.y + s * 0.27f,
                center.x - s * 0.1f, center.y + s * 0.27f,
                center.x - s * 0.19f, center.y + s * 0.14f
            )
            close()
        },
        tone
    )
}

/** Cachimbo preso no canto da boca — a peça que mais dá idade ao retrato. */
private fun DrawScope.pipe(center: Offset, s: Float, ink: Color, glow: Color) {
    drawLine(
        ink,
        Offset(center.x + s * 0.06f, center.y + s * 0.29f),
        Offset(center.x + s * 0.34f, center.y + s * 0.36f),
        strokeWidth = s * 0.05f,
        cap = StrokeCap.Round
    )
    drawPath(
        Path().apply {
            moveTo(center.x + s * 0.34f, center.y + s * 0.36f)
            cubicTo(
                center.x + s * 0.43f, center.y + s * 0.36f,
                center.x + s * 0.47f, center.y + s * 0.28f,
                center.x + s * 0.47f, center.y + s * 0.19f
            )
            lineTo(center.x + s * 0.38f, center.y + s * 0.19f)
            cubicTo(
                center.x + s * 0.38f, center.y + s * 0.26f,
                center.x + s * 0.36f, center.y + s * 0.29f,
                center.x + s * 0.32f, center.y + s * 0.29f
            )
            close()
        },
        ink
    )
    drawCircle(glow.copy(alpha = 0.65f), radius = s * 0.035f, center = Offset(center.x + s * 0.43f, center.y + s * 0.17f))
}

/** Cabelo penteado para trás, com a onda dos cartazes dos anos 40. */
private fun DrawScope.sweptHair(center: Offset, s: Float, tone: Color) {
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.44f, center.y - s * 0.28f)
            cubicTo(
                center.x - s * 0.42f, center.y - s * 0.56f,
                center.x - s * 0.24f, center.y - s * 0.68f,
                center.x, center.y - s * 0.66f
            )
            cubicTo(
                center.x + s * 0.24f, center.y - s * 0.64f,
                center.x + s * 0.42f, center.y - s * 0.54f,
                center.x + s * 0.44f, center.y - s * 0.26f
            )
            cubicTo(
                center.x + s * 0.32f, center.y - s * 0.48f,
                center.x - s * 0.2f, center.y - s * 0.5f,
                center.x - s * 0.44f, center.y - s * 0.28f
            )
            close()
        },
        tone
    )
}

/** Quepe de serviço: copa clara, pala escura e, no comandante, o bordado dourado. */
private fun DrawScope.peakedCap(center: Offset, s: Float, cloth: Color, ink: Color, gold: Color, braid: Boolean) {
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.5f, center.y - s * 0.42f)
            cubicTo(
                center.x - s * 0.47f, center.y - s * 0.76f,
                center.x - s * 0.26f, center.y - s * 0.9f,
                center.x, center.y - s * 0.9f
            )
            cubicTo(
                center.x + s * 0.26f, center.y - s * 0.9f,
                center.x + s * 0.47f, center.y - s * 0.76f,
                center.x + s * 0.5f, center.y - s * 0.42f
            )
            close()
        },
        cloth
    )
    // brilho da copa do lado da luz
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.5f, center.y - s * 0.42f)
            cubicTo(
                center.x - s * 0.47f, center.y - s * 0.74f,
                center.x - s * 0.28f, center.y - s * 0.88f,
                center.x - s * 0.04f, center.y - s * 0.89f
            )
            cubicTo(
                center.x - s * 0.28f, center.y - s * 0.8f,
                center.x - s * 0.44f, center.y - s * 0.64f,
                center.x - s * 0.47f, center.y - s * 0.44f
            )
            close()
        },
        Color(0xFFF7F2E2)
    )
    drawRect(
        ink,
        topLeft = Offset(center.x - s * 0.56f, center.y - s * 0.44f),
        size = Size(s * 1.12f, s * 0.12f)
    )
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.6f, center.y - s * 0.32f)
            cubicTo(
                center.x - s * 0.38f, center.y - s * 0.14f,
                center.x + s * 0.38f, center.y - s * 0.14f,
                center.x + s * 0.6f, center.y - s * 0.32f
            )
            lineTo(center.x + s * 0.6f, center.y - s * 0.4f)
            lineTo(center.x - s * 0.6f, center.y - s * 0.4f)
            close()
        },
        Color(0xFF0F1409)
    )
    if (braid) {
        drawPath(
            Path().apply {
                moveTo(center.x - s * 0.5f, center.y - s * 0.36f)
                cubicTo(
                    center.x - s * 0.26f, center.y - s * 0.24f,
                    center.x + s * 0.26f, center.y - s * 0.24f,
                    center.x + s * 0.5f, center.y - s * 0.36f
                )
            },
            gold,
            style = Stroke(width = s * 0.04f)
        )
    }
    star(center.x, center.y - s * 0.66f, s * 0.15f, gold)
}

/** Gorro branco de marinheiro, amassado como o de bordo. */
private fun DrawScope.sailorCap(center: Offset, s: Float, cloth: Color) {
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.44f, center.y - s * 0.46f)
            cubicTo(
                center.x - s * 0.4f, center.y - s * 0.72f,
                center.x - s * 0.22f, center.y - s * 0.82f,
                center.x, center.y - s * 0.82f
            )
            cubicTo(
                center.x + s * 0.22f, center.y - s * 0.82f,
                center.x + s * 0.4f, center.y - s * 0.72f,
                center.x + s * 0.44f, center.y - s * 0.46f
            )
            close()
        },
        cloth
    )
    withTransform({ scale(1f, 0.32f, pivot = Offset(center.x, center.y - s * 0.46f)) }) {
        drawCircle(Color(0xFFF2EAD4), radius = s * 0.5f, center = Offset(center.x, center.y - s * 0.46f))
    }
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.5f, center.y - s * 0.46f)
            cubicTo(
                center.x - s * 0.5f, center.y - s * 0.36f,
                center.x - s * 0.28f, center.y - s * 0.3f,
                center.x, center.y - s * 0.3f
            )
            cubicTo(
                center.x + s * 0.28f, center.y - s * 0.3f,
                center.x + s * 0.5f, center.y - s * 0.36f,
                center.x + s * 0.5f, center.y - s * 0.46f
            )
        },
        Color(0xFFB4A87E),
        style = Stroke(width = s * 0.026f)
    )
}

/** Lenço amarrado na cabeça, com nó na frente e bolinhas de cartaz. */
private fun DrawScope.headScarf(center: Offset, s: Float, accent: Color) {
    val scarf = Color(0xFFC8552F)
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.47f, center.y - s * 0.26f)
            cubicTo(
                center.x - s * 0.46f, center.y - s * 0.56f,
                center.x - s * 0.24f, center.y - s * 0.72f,
                center.x, center.y - s * 0.72f
            )
            cubicTo(
                center.x + s * 0.24f, center.y - s * 0.72f,
                center.x + s * 0.46f, center.y - s * 0.56f,
                center.x + s * 0.47f, center.y - s * 0.26f
            )
            cubicTo(
                center.x + s * 0.3f, center.y - s * 0.44f,
                center.x - s * 0.3f, center.y - s * 0.44f,
                center.x - s * 0.47f, center.y - s * 0.26f
            )
            close()
        },
        scarf
    )
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.47f, center.y - s * 0.26f)
            cubicTo(
                center.x - s * 0.45f, center.y - s * 0.52f,
                center.x - s * 0.28f, center.y - s * 0.66f,
                center.x - s * 0.06f, center.y - s * 0.7f
            )
            cubicTo(
                center.x - s * 0.26f, center.y - s * 0.62f,
                center.x - s * 0.4f, center.y - s * 0.48f,
                center.x - s * 0.45f, center.y - s * 0.24f
            )
            close()
        },
        Color(0xFFE0713F)
    )
    listOf(-0.26f to -0.46f, -0.08f to -0.56f, 0.12f to -0.54f, 0.28f to -0.42f).forEach { (dx, dy) ->
        drawCircle(Color(0xFFE9DFC4), radius = s * 0.03f, center = Offset(center.x + s * dx, center.y + s * dy))
    }
    // nó
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.06f, center.y - s * 0.7f)
            cubicTo(
                center.x - s * 0.02f, center.y - s * 0.8f,
                center.x + s * 0.08f, center.y - s * 0.82f,
                center.x + s * 0.12f, center.y - s * 0.76f
            )
            cubicTo(
                center.x + s * 0.16f, center.y - s * 0.82f,
                center.x + s * 0.26f, center.y - s * 0.8f,
                center.x + s * 0.26f, center.y - s * 0.7f
            )
            cubicTo(
                center.x + s * 0.2f, center.y - s * 0.72f,
                center.x + s * 0.1f, center.y - s * 0.68f,
                center.x - s * 0.06f, center.y - s * 0.7f
            )
            close()
        },
        scarf
    )
}

/** Touca de couro com óculos de voo levantados na testa. */
private fun DrawScope.flightHelmet(center: Offset, s: Float, ink: Color, accent: Color) {
    val leather = Color(0xFF5A4420)
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.48f, center.y - s * 0.04f)
            cubicTo(
                center.x - s * 0.5f, center.y - s * 0.56f,
                center.x - s * 0.27f, center.y - s * 0.8f,
                center.x, center.y - s * 0.8f
            )
            cubicTo(
                center.x + s * 0.27f, center.y - s * 0.8f,
                center.x + s * 0.5f, center.y - s * 0.56f,
                center.x + s * 0.48f, center.y - s * 0.04f
            )
            lineTo(center.x + s * 0.38f, center.y - s * 0.04f)
            cubicTo(
                center.x + s * 0.4f, center.y - s * 0.56f,
                center.x + s * 0.22f, center.y - s * 0.66f,
                center.x, center.y - s * 0.66f
            )
            cubicTo(
                center.x - s * 0.22f, center.y - s * 0.66f,
                center.x - s * 0.4f, center.y - s * 0.56f,
                center.x - s * 0.38f, center.y - s * 0.04f
            )
            close()
        },
        leather
    )
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.48f, center.y - s * 0.1f)
            cubicTo(
                center.x - s * 0.48f, center.y - s * 0.58f,
                center.x - s * 0.26f, center.y - s * 0.8f,
                center.x, center.y - s * 0.8f
            )
            cubicTo(
                center.x + s * 0.12f, center.y - s * 0.8f,
                center.x + s * 0.22f, center.y - s * 0.76f,
                center.x + s * 0.3f, center.y - s * 0.7f
            )
            cubicTo(
                center.x + s * 0.1f, center.y - s * 0.76f,
                center.x - s * 0.3f, center.y - s * 0.64f,
                center.x - s * 0.44f, center.y - s * 0.26f
            )
            close()
        },
        Color(0xFF7A5D2C)
    )
    // abas de orelha
    listOf(-1f, 1f).forEach { d ->
        drawPath(
            Path().apply {
                moveTo(center.x + d * s * 0.48f, center.y - s * 0.14f)
                cubicTo(
                    center.x + d * s * 0.52f, center.y + s * 0.06f,
                    center.x + d * s * 0.5f, center.y + s * 0.24f,
                    center.x + d * s * 0.42f, center.y + s * 0.34f
                )
                cubicTo(
                    center.x + d * s * 0.34f, center.y + s * 0.28f,
                    center.x + d * s * 0.32f, center.y + s * 0.1f,
                    center.x + d * s * 0.34f, center.y - s * 0.08f
                )
                close()
            },
            if (d < 0) leather else Color(0xFF48361A)
        )
    }
    // óculos levantados
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.46f, center.y - s * 0.4f)
            cubicTo(
                center.x - s * 0.3f, center.y - s * 0.52f,
                center.x + s * 0.3f, center.y - s * 0.52f,
                center.x + s * 0.46f, center.y - s * 0.4f
            )
            lineTo(center.x + s * 0.46f, center.y - s * 0.28f)
            cubicTo(
                center.x + s * 0.3f, center.y - s * 0.4f,
                center.x - s * 0.3f, center.y - s * 0.4f,
                center.x - s * 0.46f, center.y - s * 0.28f
            )
            close()
        },
        Color(0xFF2A2410)
    )
    listOf(-1f, 1f).forEach { d ->
        drawCircle(
            Color(0xFF3C3F22),
            radius = s * 0.13f,
            center = Offset(center.x + d * s * 0.22f, center.y - s * 0.36f)
        )
        drawCircle(
            accent.copy(alpha = 0.85f),
            radius = s * 0.13f,
            center = Offset(center.x + d * s * 0.22f, center.y - s * 0.36f),
            style = Stroke(width = s * 0.03f)
        )
    }
    drawLine(
        accent.copy(alpha = 0.85f),
        Offset(center.x - s * 0.09f, center.y - s * 0.38f),
        Offset(center.x + s * 0.09f, center.y - s * 0.38f),
        strokeWidth = s * 0.034f
    )
}

/** Cachecol de aviador, com a ponta solta voando. */
private fun DrawScope.scarf(center: Offset, s: Float, cloth: Color) {
    drawPath(
        Path().apply {
            moveTo(center.x - s * 0.38f, center.y + s * 0.42f)
            cubicTo(
                center.x - s * 0.2f, center.y + s * 0.54f,
                center.x + s * 0.2f, center.y + s * 0.54f,
                center.x + s * 0.38f, center.y + s * 0.42f
            )
            lineTo(center.x + s * 0.46f, center.y + s * 0.52f)
            cubicTo(
                center.x + s * 0.38f, center.y + s * 0.74f,
                center.x - s * 0.06f, center.y + s * 0.78f,
                center.x - s * 0.22f, center.y + s * 0.64f
            )
            close()
        },
        cloth
    )
    drawPath(
        Path().apply {
            moveTo(center.x + s * 0.22f, center.y + s * 0.62f)
            cubicTo(
                center.x + s * 0.34f, center.y + s * 0.68f,
                center.x + s * 0.4f, center.y + s * 0.8f,
                center.x + s * 0.38f, center.y + s * 0.96f
            )
            lineTo(center.x + s * 0.2f, center.y + s * 0.92f)
            cubicTo(
                center.x + s * 0.24f, center.y + s * 0.8f,
                center.x + s * 0.22f, center.y + s * 0.7f,
                center.x + s * 0.16f, center.y + s * 0.64f
            )
            close()
        },
        Color(0xFFF2EAD4)
    )
}

/** Estrela de cinco pontas do quepe. */
private fun DrawScope.star(cx: Float, cy: Float, r: Float, color: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.44f
        val a = -PI / 2f + i * PI / 5f
        val x = cx + rad * cos(a)
        val y = cy + rad * sin(a)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** Retícula de meio-tom: pontos em diagonal, como a trama de um cartaz impresso. */
private fun DrawScope.halftone(area: Rect, color: Color, step: Float) {
    var y = area.top
    var row = 0
    while (y < area.bottom) {
        var x = area.left + if (row % 2 == 0) 0f else step / 2f
        while (x < area.right) {
            drawCircle(color.copy(alpha = 0.5f), radius = step * 0.22f, center = Offset(x, y))
            x += step
        }
        y += step
        row++
    }
}

private fun Color.mix(other: Color, t: Float) = Color(
    red + (other.red - red) * t,
    green + (other.green - green) * t,
    blue + (other.blue - blue) * t,
    alpha
)

private const val PI = 3.14159265f

private fun cos(a: Float) = kotlin.math.cos(a)
private fun sin(a: Float) = kotlin.math.sin(a)

/** Papel a bordo — define que peças o retrato usa. */
private enum class Role { COMMANDER, OFFICER, BOATSWAIN, AVIATOR }

private val Avatar.role: Role
    get() = when (this) {
        Avatar.COMMANDER_M, Avatar.COMMANDER_F -> Role.COMMANDER
        Avatar.OFFICER_M, Avatar.OFFICER_F -> Role.OFFICER
        Avatar.BOATSWAIN_M, Avatar.BOATSWAIN_F -> Role.BOATSWAIN
        Avatar.AVIATOR_M, Avatar.AVIATOR_F -> Role.AVIATOR
    }
