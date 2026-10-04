package br.com.navalbattle.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Arte da moeda do jogo (o dobrão) e dos pacotes da loja de dobrões. Tudo vetorial,
 * desenhado em código como os navios — nada de imagem no APK. Diferente dos glifos
 * de habilidade (só traço), aqui as peças são cheias e coloridas: é vitrine de compra,
 * cada pacote tem que dizer de longe o que é e o quanto vale.
 */

private val GoldLight = Color(0xFFFFE08A)
private val Gold = Color(0xFFF2B33D)
private val GoldDark = Color(0xFFB57514)
private val GoldEdge = Color(0xFF7A4A0A)
private val WoodLight = Color(0xFFA9703F)
private val Wood = Color(0xFF7A4A26)
private val WoodDark = Color(0xFF4E2D15)
private val Iron = Color(0xFF59636B)
private val IronDark = Color(0xFF2E353B)
private val Steel = Color(0xFF8C99A3)
private val SteelLight = Color(0xFFC3CDD4)
private val Leather = Color(0xFF8E5A33)
private val LeatherDark = Color(0xFF5C361B)
private val Ruby = Color(0xFFD8364A)
private val Emerald = Color(0xFF2FB56B)
private val Navy = Color(0xFF1F3A7A)

/** Os quatro pacotes da loja de dobrões, do menor para o maior. */
enum class DoubloonPackArt { POUCH, CHEST, STRONGBOX, TREASURE }

/**
 * Dobrão visto de frente: borda serrilhada escura, disco dourado com brilho no alto
 * e uma âncora cunhada no centro. É o ícone que substitui o antigo ◆ em todo o app.
 */
fun DrawScope.drawDoubloon(center: Offset, size: Float, alpha: Float = 1f) {
    val r = size / 2f
    // borda (canto da moeda) e disco
    drawCircle(GoldEdge, radius = r, center = center, alpha = alpha)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(GoldLight, Gold, GoldDark),
            center = Offset(center.x - r * 0.3f, center.y - r * 0.35f),
            radius = r * 1.4f
        ),
        radius = r * 0.86f,
        center = center,
        alpha = alpha
    )
    // anel interno cunhado
    drawCircle(GoldDark, radius = r * 0.66f, center = center, alpha = alpha * 0.8f, style = Stroke(width = r * 0.07f))
    drawAnchor(center, r * 0.9f, GoldEdge, alpha)
}

/** Âncora simples (argola, haste, cepo e braços), usada na moeda e nas placas dos pacotes. */
private fun DrawScope.drawAnchor(center: Offset, size: Float, color: Color, alpha: Float) {
    val s = size / 2f
    val w = s * 0.16f
    val stroke = Stroke(width = w, cap = StrokeCap.Round)
    // argola
    drawCircle(color, radius = s * 0.16f, center = Offset(center.x, center.y - s * 0.62f), alpha = alpha, style = Stroke(width = w * 0.8f))
    // haste
    drawLine(color, Offset(center.x, center.y - s * 0.46f), Offset(center.x, center.y + s * 0.62f), w, StrokeCap.Round, alpha = alpha)
    // cepo
    drawLine(color, Offset(center.x - s * 0.34f, center.y - s * 0.3f), Offset(center.x + s * 0.34f, center.y - s * 0.3f), w * 0.9f, StrokeCap.Round, alpha = alpha)
    // braços em arco
    val arms = Path().apply {
        moveTo(center.x - s * 0.62f, center.y + s * 0.12f)
        quadraticBezierTo(center.x - s * 0.5f, center.y + s * 0.78f, center.x, center.y + s * 0.66f)
        quadraticBezierTo(center.x + s * 0.5f, center.y + s * 0.78f, center.x + s * 0.62f, center.y + s * 0.12f)
    }
    drawPath(arms, color, alpha = alpha, style = stroke)
}

/**
 * Ícone de cada pacote, num quadrado de lado [size] centrado em [center]. Cresce em
 * riqueza: bolsa de couro → baú de marinheiro → cofre de aço → baú do tesouro
 * transbordando, com joias e brilho.
 */
fun DrawScope.drawDoubloonPack(pack: DoubloonPackArt, center: Offset, size: Float) {
    when (pack) {
        DoubloonPackArt.POUCH -> drawPouch(center, size)
        DoubloonPackArt.CHEST -> drawSeaChest(center, size)
        DoubloonPackArt.STRONGBOX -> drawStrongbox(center, size)
        DoubloonPackArt.TREASURE -> drawTreasure(center, size)
    }
}

/** Pilha de moedas vistas de lado (cilindros achatados), para os pacotes maiores. */
private fun DrawScope.coinStack(baseX: Float, baseY: Float, width: Float, count: Int) {
    val h = width * 0.22f
    for (i in 0 until count) {
        val y = baseY - i * h * 0.78f
        drawRoundRect(
            GoldEdge,
            topLeft = Offset(baseX - width / 2f, y - h),
            size = Size(width, h),
            cornerRadius = CornerRadius(h / 2f)
        )
        drawRoundRect(
            Brush.horizontalGradient(listOf(GoldDark, GoldLight, Gold), startX = baseX - width / 2f, endX = baseX + width / 2f),
            topLeft = Offset(baseX - width / 2f + width * 0.04f, y - h * 0.92f),
            size = Size(width * 0.92f, h * 0.7f),
            cornerRadius = CornerRadius(h / 2f)
        )
    }
}

/** Estrelinha de brilho em quatro pontas. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color = Color.White) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color, alpha = 0.9f)
}

// ------------------------------------------------------------------ bolsa

private fun DrawScope.drawPouch(c: Offset, size: Float) {
    val s = size / 2f
    // corpo de couro em gota
    val body = Path().apply {
        moveTo(c.x - s * 0.28f, c.y - s * 0.38f)
        cubicTo(c.x - s * 0.95f, c.y - s * 0.1f, c.x - s * 0.85f, c.y + s * 0.82f, c.x, c.y + s * 0.82f)
        cubicTo(c.x + s * 0.85f, c.y + s * 0.82f, c.x + s * 0.95f, c.y - s * 0.1f, c.x + s * 0.28f, c.y - s * 0.38f)
        close()
    }
    drawPath(
        body,
        Brush.radialGradient(listOf(Leather.copy(red = 0.66f), Leather, LeatherDark), center = Offset(c.x - s * 0.25f, c.y), radius = s * 1.1f)
    )
    drawPath(body, LeatherDark, style = Stroke(width = s * 0.05f))
    // boca franzida e cordão
    val neck = Path().apply {
        moveTo(c.x - s * 0.3f, c.y - s * 0.38f)
        lineTo(c.x - s * 0.42f, c.y - s * 0.62f)
        lineTo(c.x + s * 0.42f, c.y - s * 0.62f)
        lineTo(c.x + s * 0.3f, c.y - s * 0.38f)
        close()
    }
    drawPath(neck, Leather)
    drawLine(Gold, Offset(c.x - s * 0.34f, c.y - s * 0.4f), Offset(c.x + s * 0.34f, c.y - s * 0.4f), s * 0.08f, StrokeCap.Round)
    drawLine(GoldDark, Offset(c.x + s * 0.2f, c.y - s * 0.4f), Offset(c.x + s * 0.4f, c.y - s * 0.14f), s * 0.05f, StrokeCap.Round)
    // moedas aparecendo na boca e uma caída ao lado
    drawDoubloon(Offset(c.x - s * 0.12f, c.y - s * 0.7f), s * 0.42f)
    drawDoubloon(Offset(c.x + s * 0.16f, c.y - s * 0.74f), s * 0.38f)
    drawDoubloon(Offset(c.x + s * 0.72f, c.y + s * 0.66f), s * 0.42f)
    // âncora gravada no couro
    drawAnchor(Offset(c.x, c.y + s * 0.26f), s * 0.62f, LeatherDark, 0.85f)
}

// ------------------------------------------------------------------ baú de marinheiro

private fun DrawScope.drawSeaChest(c: Offset, size: Float) {
    val s = size / 2f
    val left = c.x - s * 0.82f
    val w = s * 1.64f
    val bodyTop = c.y - s * 0.05f
    val bodyH = s * 0.78f
    // brilho das moedas saindo da tampa entreaberta
    drawOval(
        Brush.radialGradient(listOf(GoldLight.copy(alpha = 0.85f), Color.Transparent), center = Offset(c.x, bodyTop), radius = s * 0.9f),
        topLeft = Offset(c.x - s * 0.9f, bodyTop - s * 0.75f),
        size = Size(s * 1.8f, s * 1.1f)
    )
    // moedas na boca
    drawDoubloon(Offset(c.x - s * 0.3f, bodyTop - s * 0.06f), s * 0.42f)
    drawDoubloon(Offset(c.x + s * 0.08f, bodyTop - s * 0.12f), s * 0.42f)
    drawDoubloon(Offset(c.x + s * 0.42f, bodyTop - s * 0.04f), s * 0.38f)
    // tampa abaulada levantada
    val lid = Path().apply {
        moveTo(left, bodyTop - s * 0.12f)
        cubicTo(left, bodyTop - s * 0.78f, left + w, bodyTop - s * 0.78f, left + w, bodyTop - s * 0.12f)
        lineTo(left + w, bodyTop - s * 0.32f)
        cubicTo(left + w * 0.8f, bodyTop - s * 0.6f, left + w * 0.2f, bodyTop - s * 0.6f, left, bodyTop - s * 0.32f)
        close()
    }
    drawPath(lid, Brush.verticalGradient(listOf(WoodLight, Wood), startY = bodyTop - s * 0.8f, endY = bodyTop))
    drawPath(lid, WoodDark, style = Stroke(width = s * 0.04f))
    // corpo de madeira com tábuas
    drawRect(Brush.verticalGradient(listOf(Wood, WoodDark), startY = bodyTop, endY = bodyTop + bodyH), topLeft = Offset(left, bodyTop), size = Size(w, bodyH))
    for (i in 1..2) {
        val y = bodyTop + bodyH * i / 3f
        drawLine(WoodDark, Offset(left, y), Offset(left + w, y), s * 0.03f)
    }
    // cintas de ferro e fecho
    listOf(left + w * 0.18f, left + w * 0.82f).forEach { x ->
        drawRect(Iron, topLeft = Offset(x - s * 0.07f, bodyTop), size = Size(s * 0.14f, bodyH))
    }
    drawRect(IronDark, topLeft = Offset(left, bodyTop), size = Size(w, bodyH), style = Stroke(width = s * 0.05f))
    drawRoundRect(Gold, topLeft = Offset(c.x - s * 0.14f, bodyTop + s * 0.06f), size = Size(s * 0.28f, s * 0.3f), cornerRadius = CornerRadius(s * 0.05f))
    drawCircle(GoldEdge, radius = s * 0.05f, center = Offset(c.x, bodyTop + s * 0.2f))
}

// ------------------------------------------------------------------ cofre de aço

private fun DrawScope.drawStrongbox(c: Offset, size: Float) {
    val s = size / 2f
    val left = c.x - s * 0.62f
    val top = c.y - s * 0.72f
    val w = s * 1.24f
    val h = s * 1.36f
    // pilhas de moedas atrás, dos dois lados
    coinStack(c.x - s * 0.66f, c.y + s * 0.82f, s * 0.46f, 5)
    coinStack(c.x + s * 0.7f, c.y + s * 0.82f, s * 0.46f, 4)
    // caixa de aço com cantos arredondados
    drawRoundRect(
        Brush.linearGradient(listOf(SteelLight, Steel, Iron), start = Offset(left, top), end = Offset(left + w, top + h)),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(s * 0.12f)
    )
    drawRoundRect(IronDark, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(s * 0.12f), style = Stroke(width = s * 0.05f))
    // porta embutida
    drawRoundRect(Iron, topLeft = Offset(left + s * 0.12f, top + s * 0.12f), size = Size(w - s * 0.24f, h - s * 0.24f), cornerRadius = CornerRadius(s * 0.08f), style = Stroke(width = s * 0.04f))
    // rebites
    listOf(0.2f, 0.8f).forEach { fx ->
        listOf(0.12f, 0.88f).forEach { fy ->
            drawCircle(IronDark, radius = s * 0.035f, center = Offset(left + w * fx, top + h * fy))
        }
    }
    // segredo (disco com marcas) e alavanca
    val dial = Offset(c.x, c.y - s * 0.12f)
    drawCircle(IronDark, radius = s * 0.32f, center = dial)
    drawCircle(Brush.radialGradient(listOf(SteelLight, Steel), center = dial, radius = s * 0.3f), radius = s * 0.26f, center = dial)
    repeat(8) { i ->
        val a = i * kotlin.math.PI.toFloat() / 4f
        drawLine(
            IronDark,
            Offset(dial.x + kotlin.math.cos(a) * s * 0.18f, dial.y + kotlin.math.sin(a) * s * 0.18f),
            Offset(dial.x + kotlin.math.cos(a) * s * 0.25f, dial.y + kotlin.math.sin(a) * s * 0.25f),
            s * 0.025f
        )
    }
    drawCircle(Gold, radius = s * 0.07f, center = dial)
    drawLine(IronDark, Offset(c.x, c.y + s * 0.3f), Offset(c.x + s * 0.3f, c.y + s * 0.3f), s * 0.08f, StrokeCap.Round)
    // placa dourada com âncora
    drawRoundRect(Gold, topLeft = Offset(c.x - s * 0.2f, top + h - s * 0.42f), size = Size(s * 0.4f, s * 0.22f), cornerRadius = CornerRadius(s * 0.04f))
    drawAnchor(Offset(c.x, top + h - s * 0.31f), s * 0.2f, GoldEdge, 1f)
}

// ------------------------------------------------------------------ tesouro do almirante

private fun DrawScope.drawTreasure(c: Offset, size: Float) {
    val s = size / 2f
    val left = c.x - s * 0.86f
    val w = s * 1.72f
    val bodyTop = c.y + s * 0.02f
    val bodyH = s * 0.74f
    // raios de luz atrás
    repeat(7) { i ->
        val a = (-160f + i * 23f) * kotlin.math.PI.toFloat() / 180f
        val p = Path().apply {
            moveTo(c.x, bodyTop)
            lineTo(c.x + kotlin.math.cos(a - 0.07f) * s * 1.05f, bodyTop + kotlin.math.sin(a - 0.07f) * s * 1.05f)
            lineTo(c.x + kotlin.math.cos(a + 0.07f) * s * 1.05f, bodyTop + kotlin.math.sin(a + 0.07f) * s * 1.05f)
            close()
        }
        drawPath(p, GoldLight, alpha = 0.35f)
    }
    // tampa aberta para trás, com forro azul-marinho
    val lid = Path().apply {
        moveTo(left + s * 0.06f, bodyTop - s * 0.1f)
        lineTo(left + s * 0.2f, bodyTop - s * 0.86f)
        cubicTo(left + w * 0.35f, bodyTop - s * 1.02f, left + w * 0.65f, bodyTop - s * 1.02f, left + w - s * 0.2f, bodyTop - s * 0.86f)
        lineTo(left + w - s * 0.06f, bodyTop - s * 0.1f)
        close()
    }
    drawPath(lid, Brush.verticalGradient(listOf(Navy, Navy.copy(red = 0.08f, green = 0.16f, blue = 0.36f)), startY = bodyTop - s, endY = bodyTop))
    drawPath(lid, Gold, style = Stroke(width = s * 0.06f))
    // monte de moedas transbordando
    val pile = Path().apply {
        moveTo(left + s * 0.02f, bodyTop + s * 0.08f)
        cubicTo(left + w * 0.2f, bodyTop - s * 0.55f, left + w * 0.8f, bodyTop - s * 0.55f, left + w - s * 0.02f, bodyTop + s * 0.08f)
        close()
    }
    drawPath(pile, Brush.verticalGradient(listOf(GoldLight, Gold, GoldDark), startY = bodyTop - s * 0.5f, endY = bodyTop + s * 0.1f))
    listOf(
        Offset(-0.45f, -0.08f), Offset(-0.1f, -0.3f), Offset(0.25f, -0.22f), Offset(0.52f, -0.04f), Offset(0.05f, -0.02f)
    ).forEach { o -> drawDoubloon(Offset(c.x + o.x * s, bodyTop + o.y * s), s * 0.3f) }
    // joias no monte
    drawCircle(Ruby, radius = s * 0.09f, center = Offset(c.x - s * 0.26f, bodyTop - s * 0.2f))
    drawCircle(Emerald, radius = s * 0.08f, center = Offset(c.x + s * 0.4f, bodyTop - s * 0.18f))
    // corpo do baú com cantoneiras douradas
    drawRect(Brush.verticalGradient(listOf(Wood, WoodDark), startY = bodyTop, endY = bodyTop + bodyH), topLeft = Offset(left, bodyTop), size = Size(w, bodyH))
    drawRect(Gold, topLeft = Offset(left, bodyTop), size = Size(w, s * 0.1f))
    listOf(left, left + w - s * 0.18f).forEach { x ->
        drawRect(Gold, topLeft = Offset(x, bodyTop), size = Size(s * 0.18f, bodyH))
    }
    drawRect(GoldEdge, topLeft = Offset(left, bodyTop), size = Size(w, bodyH), style = Stroke(width = s * 0.04f))
    // fecho com âncora
    drawRoundRect(Gold, topLeft = Offset(c.x - s * 0.17f, bodyTop + s * 0.14f), size = Size(s * 0.34f, s * 0.36f), cornerRadius = CornerRadius(s * 0.06f))
    drawAnchor(Offset(c.x, bodyTop + s * 0.32f), s * 0.3f, GoldEdge, 1f)
    // moedas caídas na frente e brilhos
    drawDoubloon(Offset(c.x - s * 0.7f, c.y + s * 0.9f), s * 0.3f)
    drawDoubloon(Offset(c.x + s * 0.74f, c.y + s * 0.86f), s * 0.26f)
    sparkle(Offset(c.x - s * 0.55f, bodyTop - s * 0.5f), s * 0.12f)
    sparkle(Offset(c.x + s * 0.6f, bodyTop - s * 0.62f), s * 0.1f)
    sparkle(Offset(c.x + s * 0.1f, bodyTop - s * 0.58f), s * 0.08f, GoldLight)
}

/**
 * Milha náutica: rosa dos ventos com a ponta norte em vermelho — o ícone do saldo de
 * milhas no topo do menu e nos avisos de partida online.
 */
fun DrawScope.drawCompassRose(center: Offset, size: Float, alpha: Float = 1f) {
    val r = size / 2f
    drawCircle(Navy, radius = r, center = center, alpha = alpha)
    drawCircle(SteelLight, radius = r * 0.9f, center = center, alpha = alpha, style = Stroke(width = r * 0.08f))
    // pontas cardeais (longas) e colaterais (curtas)
    for (i in 0 until 8) {
        val a = i * kotlin.math.PI.toFloat() / 4f - kotlin.math.PI.toFloat() / 2f
        val len = if (i % 2 == 0) r * 0.82f else r * 0.48f
        val half = if (i % 2 == 0) r * 0.16f else r * 0.11f
        val tip = Offset(center.x + kotlin.math.cos(a) * len, center.y + kotlin.math.sin(a) * len)
        val side = a + kotlin.math.PI.toFloat() / 2f
        val p = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(center.x + kotlin.math.cos(side) * half, center.y + kotlin.math.sin(side) * half)
            lineTo(center.x - kotlin.math.cos(side) * half, center.y - kotlin.math.sin(side) * half)
            close()
        }
        val color = when {
            i == 0 -> Ruby
            i % 2 == 0 -> GoldLight
            else -> Steel
        }
        drawPath(p, color, alpha = alpha)
    }
    drawCircle(Gold, radius = r * 0.12f, center = center, alpha = alpha)
}
