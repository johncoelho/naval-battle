package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.SeasonEnd
import br.com.navalbattle.data.SeasonPassStatus
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.SeasonTheme
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.accent
import br.com.navalbattle.design.drawSeasonBanner
import br.com.navalbattle.design.drawSeasonIcon
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/** "2026-primavera" -> "Temporada de Primavera" (nos três idiomas). */
fun seasonTitle(seasonKey: String): String = t(K.SEASON_TITLE, t(seasonNameKey(seasonKey.substringAfterLast('-'))))

internal fun seasonNameKey(suffix: String) = when (suffix) {
    "verao" -> K.SEASON_NAME_VERAO
    "outono" -> K.SEASON_NAME_OUTONO
    "inverno" -> K.SEASON_NAME_INVERNO
    else -> K.SEASON_NAME_PRIMAVERA
}

/**
 * Banner da temporada com as opções de passe. Abre sozinho quando o comandante
 * ainda não aderiu à temporada corrente (convite da temporada nova, com "Agora não")
 * e pelo chip "Temporada de…" do menu — aí mostra o passe atual e, para quem está
 * no gratuito, o upgrade para o Passe de Temporada, mais caro que na adesão.
 */
@Composable
fun SeasonPopup(state: AppState) {
    val pass = state.seasonPass ?: return
    if (!state.seasonPopupShowing) return
    if (state.updateAvailable && !state.updatePopupDismissed) return
    if (state.pendingInvite != null || state.match != null || state.seasonEnd != null) return
    val theme = SeasonTheme.ofKey(pass.seasonKey)
    val scope = rememberCoroutineScope()

    Overlay(onDismiss = { state.closeSeasonPopup() }) {
        SeasonBanner(
            theme = theme,
            eyebrow = if (pass.joined) t(K.SEASON_YOUR_PASS) else t(K.SEASON_POPUP_EYEBROW),
            title = seasonTitle(pass.seasonKey)
        )
        Column(Modifier.padding(16.dp)) {
            when {
                pass.premium -> PremiumOwned(pass, theme)
                pass.joined -> {
                    Text(t(K.SEASON_FREE_OWNED), style = NavalType.body, color = Naval.inkSoft)
                    Gap(12)
                    PremiumOffer(
                        pass = pass,
                        theme = theme,
                        price = pass.upgradePrice,
                        priceNote = t(K.SEASON_UPGRADE_NOTE, formatThousands(pass.entryPrice)),
                        cta = t(K.SEASON_UPGRADE_CTA),
                        busy = state.seasonBusy
                    ) { scope.launch { state.joinSeason(premium = true) } }
                }
                else -> {
                    Text(t(K.SEASON_POPUP_SUB), style = NavalType.body, color = Naval.inkSoft)
                    Gap(12)
                    PremiumOffer(
                        pass = pass,
                        theme = theme,
                        price = pass.entryPrice,
                        priceNote = null,
                        cta = t(K.SEASON_PREMIUM_CTA),
                        busy = state.seasonBusy
                    ) { scope.launch { state.joinSeason(premium = true) } }
                    Gap(10)
                    FreeOffer(busy = state.seasonBusy) { scope.launch { state.joinSeason(premium = false) } }
                }
            }
            state.seasonNotice?.let {
                Gap(10)
                HudLabel(it, theme.accent)
            }
            Gap(14)
            SecondaryButton(if (pass.joined) t(K.MILES_CLOSE) else t(K.SEASON_NOT_NOW)) { state.closeSeasonPopup() }
        }
    }
}

/** Fundo escurecido + cartão rolável; tocar fora fecha. */
@Composable
private fun Overlay(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 18.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.line)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
        ) { content() }
    }
}

/** A arte da estação com o título por cima, na faixa escura de baixo. */
@Composable
fun SeasonBanner(theme: SeasonTheme, eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        Canvas(Modifier.fillMaxSize()) { drawSeasonBanner(theme) }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Naval.bg.copy(alpha = 0.85f))))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            HudLabel(eyebrow, theme.accent)
            Text(title.uppercase(), style = NavalType.title, color = Naval.ink)
        }
    }
}

/** Uma linha de benefício: marcador na cor da estação e o texto. */
@Composable
private fun Perk(text: String, color: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color))
        Spacer(Modifier.width(8.dp))
        Text(text, style = NavalType.body, color = Naval.ink)
    }
}

/** Prévia do encouraçado com a camuflagem exclusiva da estação. */
@Composable
private fun SeasonCamoPreview(paint: Paint) {
    Canvas(Modifier.fillMaxWidth().height(46.dp).background(Naval.abyss).padding(horizontal = 10.dp)) {
        val cell = minOf(size.width * 0.9f / 4f, size.height * 1.15f)
        drawShip(
            type = ShipClass.BATTLESHIP,
            center = Offset(size.width / 2f, size.height / 2f),
            lengthPx = cell * 4,
            thicknessPx = cell,
            vertical = false,
            skin = Skin(paint, FleetLine.STANDARD)
        )
    }
}

@Composable
private fun PremiumOffer(
    pass: SeasonPassStatus,
    theme: SeasonTheme,
    price: Int,
    priceNote: String?,
    cta: String,
    busy: Boolean,
    onBuy: () -> Unit
) {
    val paint = Paint.ofSeason(pass.seasonKey)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(2.dp, theme.accent)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(K.SEASON_PREMIUM_NAME).uppercase(), style = NavalType.mono, color = theme.accent, modifier = Modifier.weight(1f))
            Box(Modifier.background(theme.accent).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(t(K.SEASON_RECOMMENDED).uppercase(), style = NavalType.monoSmall, color = Naval.amberInk)
            }
        }
        Gap(8)
        paint?.let {
            SeasonCamoPreview(it)
            Gap(6)
            Perk(t(K.SEASON_PERK_CAMO, it.name), theme.accent)
        }
        Perk(t(K.SEASON_PERK_DOUBLOONS, formatThousands(pass.passDoubloons)), theme.accent)
        Perk(t(K.SEASON_PERK_MILES, pass.passMiles), theme.accent)
        Perk(t(K.SEASON_PERK_RANKED), theme.accent)
        Gap(10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                CoinLabel(formatThousands(price), style = NavalType.title, iconSize = 20.dp)
                priceNote?.let { HudLabel(it, Naval.muted) }
            }
            Box(
                Modifier
                    .background(if (busy) Naval.surface3 else theme.accent)
                    .clickable(enabled = !busy, onClick = onBuy)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(cta.uppercase(), style = NavalType.mono, color = Naval.amberInk)
            }
        }
    }
}

@Composable
private fun FreeOffer(busy: Boolean, onJoin: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, Naval.line)
            .padding(12.dp)
    ) {
        Text(t(K.SEASON_FREE_NAME).uppercase(), style = NavalType.mono, color = Naval.ink)
        Gap(6)
        Text(t(K.SEASON_FREE_DESC), style = NavalType.body, color = Naval.inkSoft)
        Gap(10)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t(K.SEASON_FREE_PRICE).uppercase(), style = NavalType.title, color = Naval.inkSoft, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .border(1.dp, Naval.line)
                    .clickable(enabled = !busy, onClick = onJoin)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(t(K.SEASON_FREE_CTA).uppercase(), style = NavalType.mono, color = Naval.ink)
            }
        }
    }
}

@Composable
private fun PremiumOwned(pass: SeasonPassStatus, theme: SeasonTheme) {
    Text(t(K.SEASON_PREMIUM_OWNED), style = NavalType.body, color = Naval.ink)
    Gap(10)
    Paint.ofSeason(pass.seasonKey)?.let {
        SeasonCamoPreview(it)
        Gap(6)
        Perk(t(K.SEASON_PERK_CAMO, it.name), theme.accent)
    }
    Perk(t(K.SEASON_PERK_DOUBLOONS, formatThousands(pass.passDoubloons)), theme.accent)
    Perk(t(K.SEASON_PERK_MILES, pass.passMiles), theme.accent)
    Perk(t(K.SEASON_PERK_RANKED), theme.accent)
}

/** Tocou em Ranqueada sem ter aderido: explica a regra e leva ao banner do passe. */
@Composable
fun RankedLockedPrompt(state: AppState) {
    if (!state.rankedLockedPrompt) return
    val pass = state.seasonPass
    val theme = SeasonTheme.ofKey(pass?.seasonKey ?: "")
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.rankedLockedPrompt = false }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, theme.accent)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(48.dp)) { drawSeasonIcon(theme, Offset(size.width / 2f, size.height / 2f), size.minDimension) }
            Gap(10)
            Text(t(K.SEASON_RANKED_LOCKED_TITLE), style = NavalType.title, color = Naval.ink, textAlign = TextAlign.Center)
            Gap(8)
            Text(
                t(K.SEASON_RANKED_LOCKED_BODY, pass?.let { seasonTitle(it.seasonKey) } ?: state.currentSeason?.let { seasonTitle(it.seasonKey) } ?: t(K.SEASON_FALLBACK)),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center
            )
            Gap(16)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.SEASON_NOT_NOW), modifier = Modifier.weight(1f)) { state.rankedLockedPrompt = false }
                PrimaryButton(t(K.SEASON_SEE_PASS), modifier = Modifier.weight(1f)) { state.openSeasonFromRanked() }
            }
        }
    }
}

/**
 * Fim de temporada: posição, números e o prêmio já creditado. Os 3 primeiros ganham
 * título e cor de pódio (ouro, prata, bronze) e o prêmio especial.
 */
@Composable
fun SeasonEndPopup(state: AppState) {
    val end = state.seasonEnd ?: return
    if (state.match != null) return
    val theme = SeasonTheme.ofKey(end.seasonKey)
    val podium = podiumColor(end.position)

    Overlay(onDismiss = { state.ackSeasonEnd() }) {
        SeasonBanner(theme = theme, eyebrow = t(K.SEASON_END_EYEBROW), title = seasonTitle(end.seasonKey))
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (podium != null) {
                Canvas(Modifier.size(72.dp)) { drawPodiumMedal(podium, theme.accent) }
                Gap(6)
                Text(t(podiumTitle(end.position)).uppercase(), style = NavalType.title, color = podium)
            }
            Text(
                t(K.SEASON_END_POSITION, "#${end.position}", end.totalPlayers),
                style = NavalType.display,
                color = Naval.ink
            )
            Gap(4)
            HudLabel(t(K.SEASON_END_STATS, end.points, end.wins, end.matches), Naval.muted)
            Gap(14)
            HudLabel(t(K.SEASON_END_REWARD), Naval.muted)
            Gap(6)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoinLabel("+${formatThousands(end.doubloons)}", style = NavalType.title, iconSize = 22.dp)
                Spacer(Modifier.width(18.dp))
                MilesLabel("+${end.miles}", color = Naval.amberStrong, style = NavalType.title, iconSize = 22.dp)
            }
            if (podium != null) {
                Gap(8)
                Text(t(K.SEASON_END_PODIUM_NOTE), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            }
            Gap(16)
            PrimaryButton(t(K.SEASON_END_COLLECT)) { state.ackSeasonEnd() }
        }
    }
}

/**
 * Medalha de pódio: duas pontas de fita na cor da estação, disco de metal (ouro,
 * prata ou bronze) com brilho, aro cunhado e uma estrela no centro.
 */
private fun DrawScope.drawPodiumMedal(metal: Color, ribbon: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.3f
    val c = Offset(w / 2f, h - r - h * 0.04f)
    // fita em V, atrás do disco
    listOf(-1f, 1f).forEach { side ->
        val p = Path().apply {
            moveTo(w / 2f + side * w * 0.06f, c.y - r * 0.4f)
            lineTo(w / 2f + side * w * 0.34f, 0f)
            lineTo(w / 2f + side * w * 0.16f, 0f)
            lineTo(w / 2f - side * w * 0.06f, c.y - r * 0.6f)
            close()
        }
        drawPath(p, ribbon)
        drawPath(p, Color.Black.copy(alpha = 0.25f), style = Stroke(width = w * 0.012f))
    }
    // disco com brilho no alto à esquerda
    drawCircle(metal.copy(red = metal.red * 0.6f, green = metal.green * 0.6f, blue = metal.blue * 0.6f), radius = r, center = c)
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.85f), metal, metal.copy(red = metal.red * 0.75f, green = metal.green * 0.75f, blue = metal.blue * 0.75f)),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.5f
        ),
        radius = r * 0.88f,
        center = c
    )
    drawCircle(Color.Black.copy(alpha = 0.22f), radius = r * 0.66f, center = c, style = Stroke(width = r * 0.06f))
    // estrela de cinco pontas
    val star = Path().apply {
        for (i in 0 until 10) {
            val a = -kotlin.math.PI.toFloat() / 2f + i * kotlin.math.PI.toFloat() / 5f
            val rr = if (i % 2 == 0) r * 0.46f else r * 0.2f
            val pt = Offset(c.x + kotlin.math.cos(a) * rr, c.y + kotlin.math.sin(a) * rr)
            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
        close()
    }
    drawPath(star, Color.White.copy(alpha = 0.9f))
    drawPath(star, Color.Black.copy(alpha = 0.25f), style = Stroke(width = r * 0.04f))
}

private fun podiumColor(position: Int): Color? = when (position) {
    1 -> Color(0xFFF2C14E)
    2 -> Color(0xFFC9D2D9)
    3 -> Color(0xFFCD8A4E)
    else -> null
}

private fun podiumTitle(position: Int): K = when (position) {
    1 -> K.SEASON_END_FIRST
    2 -> K.SEASON_END_SECOND
    else -> K.SEASON_END_THIRD
}

/**
 * Chip do topo do menu: "TEMPORADA DE PRIMAVERA" com o ícone da estação e o passe
 * do comandante. Tocar abre o banner — passe atual, upgrade ou adesão.
 */
@Composable
fun SeasonChip(pass: SeasonPassStatus, onClick: () -> Unit) {
    val theme = SeasonTheme.ofKey(pass.seasonKey)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, theme.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) { drawSeasonIcon(theme, Offset(size.width / 2f, size.height / 2f), size.minDimension) }
        Spacer(Modifier.width(8.dp))
        Text(seasonTitle(pass.seasonKey).uppercase(), style = NavalType.mono, color = theme.accent, modifier = Modifier.weight(1f))
        Text(
            when {
                pass.premium -> t(K.SEASON_CHIP_PREMIUM)
                pass.joined -> t(K.SEASON_CHIP_FREE)
                else -> t(K.SEASON_CHIP_JOIN)
            }.uppercase(),
            style = NavalType.monoSmall,
            color = if (pass.joined) Naval.inkSoft else theme.accent
        )
    }
}
