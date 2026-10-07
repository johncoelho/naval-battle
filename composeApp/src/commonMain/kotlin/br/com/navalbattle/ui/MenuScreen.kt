package br.com.navalbattle.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.appVersionLabel
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.SeasonTheme
import br.com.navalbattle.design.accent
import br.com.navalbattle.design.drawAvatar
import br.com.navalbattle.design.drawCompassRose
import br.com.navalbattle.design.drawDoubloon
import br.com.navalbattle.design.drawSeasonIcon
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Rank
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Deque de comando (tela inicial). Ordem pensada pelo que mais engaja: quem eu sou e
 * quanto falta para a próxima patente; o que tem para fazer hoje (temporada e Diário
 * de bordo); a frota (tocar troca no Estaleiro); **Jogar online** como ação principal
 * (antes era o 4º cartão de um carrossel que quase ninguém passava); os outros modos
 * lado a lado; e atalhos para Amigos (com selo de pedidos), Placar, Loja e Perfil.
 */
@Composable
fun MenuScreen(state: AppState) {
    // posição na temporada e pedidos de amizade, para o cartão do dia e o selo de Amigos
    LaunchedEffect(state.profile.signedIn, state.seasonPass?.joined) { state.loadMenuExtras() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        if (state.updateAvailable) {
            HudLabel(
                t(K.MENU_UPDATE_AVAILABLE),
                Naval.amberInk,
                Modifier
                    .fillMaxWidth()
                    .background(Naval.amber)
                    .clickable { state.updatePopupDismissed = false }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Gap(10)
        }

        CommanderHeader(state)
        if (!state.profile.currencyTipSeen && state.miles != null) {
            Gap(8)
            CurrencyTip { state.profile.markCurrencyTipSeen() }
        }

        // tela alta: a frota vira o herói e ocupa a sobra, jogar e atalhos ficam no pé
        // (zona do polegar); tela baixa: tudo rola, com a frota no tamanho compacto.
        // Antes o SpaceBetween espalhava a sobra em vãos vazios entre os blocos
        BoxWithConstraints(Modifier.weight(1f)) {
            if (maxHeight >= 600.dp) {
                Column(Modifier.fillMaxSize()) {
                    if (state.seasonPass != null || state.daily != null) {
                        Gap(14)
                        DayCard(state)
                    }
                    Gap(14)
                    FleetPreview(state, Modifier.weight(1f), hero = true)
                    MenuActions(state)
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (state.seasonPass != null || state.daily != null) {
                        Gap(14)
                        DayCard(state)
                    }
                    Gap(14)
                    FleetPreview(state)
                    MenuActions(state)
                }
            }
        }
    }
}

/** Jogar online, as outras formas de jogar e os atalhos — o bloco do pé do deque. */
@Composable
private fun MenuActions(state: AppState) {
        Column(Modifier.fillMaxWidth()) {
            Gap(16)
            PlayOnlineButton(state)
            Gap(8)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // o modo (Clássico/Tático) é a etapa seguinte, não um seletor aqui no deque
                ModeTile(t(K.MENU_TILE_AI), t(K.MENU_TILE_AI_SUB), Modifier.weight(1f).fillMaxHeight()) { state.pickMode(ModePick.Ai) }
                ModeTile(t(K.MENU_TILE_LOCAL), t(K.MENU_TILE_LOCAL_SUB), Modifier.weight(1f).fillMaxHeight()) { state.pickMode(ModePick.Local) }
                ModeTile(t(K.MENU_TILE_LAN), t(K.MENU_TILE_LAN_SUB), Modifier.weight(1f).fillMaxHeight()) { state.pickMode(ModePick.LAN) }
            }

            Gap(12)
            val signedIn = state.profile.signedIn
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Shortcut(t(K.MENU_SHORT_FRIENDS), badge = state.incomingFriendRequests, modifier = Modifier.weight(1f), icon = { drawFriendsIcon(it) }) {
                    if (signedIn) state.screen = Screen.FRIENDS else state.openLogin(Screen.MENU)
                }
                Shortcut(t(K.MENU_SHORT_LEADERBOARD), modifier = Modifier.weight(1f), icon = { drawPodiumIcon(it) }) {
                    if (signedIn) state.screen = Screen.LEADERBOARD else state.openLogin(Screen.MENU)
                }
                // com oferta do dia, o selo mostra o desconto e o toque cai direto nas camuflagens
                val offer = state.dailyOffer
                Shortcut(
                    t(K.MENU_SHORT_STORE),
                    tag = if (offer != null) "-${Paint.DAILY_OFFER_OFF}%" else null,
                    modifier = Modifier.weight(1f),
                    icon = { drawDoubloon(Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.85f) }
                ) { state.openStore(if (offer != null) 1 else 0) }
                // Perfil já abre pelo avatar do topo — o 4º atalho vira o feedback, que rende dobrões
                Shortcut(t(K.MENU_SHORT_FEEDBACK), modifier = Modifier.weight(1f), icon = { drawFeedbackIcon(it) }) {
                    state.openFeedback(Screen.MENU)
                }
            }
            Gap(12)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                HudLabel("v$appVersionLabel", Naval.line)
            }
        }
}

// ------------------------------------------------------------------ cabeçalho

/** Avatar, nome e a patente com a barra até a próxima — tocar abre o Perfil. */
@Composable
private fun CommanderHeader(state: AppState) {
    val profile = state.profile
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable { state.screen = Screen.PROFILE },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Naval.surface2).border(1.5.dp, Naval.amber, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(36.dp)) {
                    drawAvatar(profile.avatar, Offset(size.width / 2f, size.height / 2f), size.minDimension, Naval.amberStrong)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = NavalType.mono, color = Naval.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Gap(2)
                HudLabel(profile.rank.label.uppercase(), Naval.amberStrong)
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            state.miles?.let { m ->
                // milha acabando fica em vermelho — é o que trava a partida online
                MilesLabel(
                    m.miles.toString(),
                    color = if (m.miles <= 2) Naval.danger else Naval.inkSoft,
                    modifier = Modifier.clickable { state.milesPopup = true }.padding(4.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            CoinLabel(profile.credits.toString(), modifier = Modifier.clickable { state.openStore(3) }.padding(4.dp))
            Spacer(Modifier.width(6.dp))
            GearButton { state.screen = Screen.SETTINGS }
        }
    }
    // barra até a próxima patente na largura toda — na coluna do nome o texto cortava
    // ("Capitão de Corveta em …"): é o "só mais uma partida"
    Gap(8)
    val next = Rank.next(profile.xp)
    Row(Modifier.fillMaxWidth().clickable { state.screen = Screen.PROFILE }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(5.dp).background(Naval.surface2)) {
            Box(Modifier.fillMaxWidth(profile.rankProgress).height(5.dp).background(Naval.amber))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (next == null) t(K.MENU_RANK_MAX) else t(K.MENU_RANK_NEXT, next.label, next.xp - profile.xp),
            style = NavalType.monoSmall,
            color = Naval.muted,
            maxLines = 1
        )
    }
}

/** Explica milhas e dobrões uma única vez — some no "Entendi" e não volta. */
@Composable
private fun CurrencyTip(onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.lineSoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(t(K.MENU_TIP_CURRENCY), style = NavalType.monoSmall, color = Naval.inkSoft, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        HudLabel(t(K.MENU_TIP_OK).uppercase(), Naval.amberStrong, Modifier.clickable(onClick = onDismiss).padding(4.dp))
    }
}

@Composable
private fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .border(1.dp, Naval.line)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("⚙", style = NavalType.body, color = Naval.inkSoft)
    }
}

// ------------------------------------------------------------------ cartão do dia

/**
 * Temporada e Diário de bordo num cartão só, duas metades tocáveis. O Diário mostra
 * o check-in pendente, o desafio em andamento com a barra ou, com tudo feito, o visto.
 */
@Composable
private fun DayCard(state: AppState) {
    val pass = state.seasonPass
    val daily = state.daily
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pass?.let {
            val theme = SeasonTheme.ofKey(it.seasonKey)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Naval.surface)
                    .border(1.dp, theme.accent.copy(alpha = if (it.joined) 0.55f else 1f))
                    .clickable { state.seasonPassOpen = true }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(16.dp)) { drawSeasonIcon(theme, Offset(size.width / 2f, size.height / 2f), size.minDimension) }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t(seasonNameKey(it.seasonKey.substringAfterLast('-'))).uppercase(),
                        style = NavalType.monoSmall,
                        color = theme.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Gap(6)
                val position = state.menuSeasonPosition
                Text(
                    when {
                        !it.joined -> t(K.SEASON_CHIP_JOIN)
                        position != null -> t(K.MENU_DAY_SEASON_POS, position)
                        else -> t(K.MENU_DAY_SEASON_PLAY)
                    },
                    style = NavalType.mono,
                    color = if (it.joined) Naval.ink else theme.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Gap(2)
                HudLabel(
                    when {
                        it.premium -> t(K.SEASON_CHIP_PREMIUM)
                        it.joined -> t(K.SEASON_CHIP_FREE)
                        else -> t(K.MENU_DAY_SEASON_LOCKED)
                    },
                    Naval.muted
                )
            }
        }
        daily?.let { d ->
            val pending = state.dailyPending
            val allDone = d.checkedIn && d.challengeClaimed
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (pending) Naval.amber.copy(alpha = 0.08f) else Naval.surface)
                    .border(1.dp, if (pending) Naval.amber else Naval.lineSoft)
                    .clickable { state.dailyPopupOpen = true }
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(16.dp)) { drawDoubloon(Offset(size.width / 2f, size.height / 2f), size.minDimension) }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t(K.MENU_DAY_DAILY, d.streak).uppercase(),
                        style = NavalType.monoSmall,
                        color = if (pending) Naval.amberStrong else Naval.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Gap(6)
                when {
                    !d.checkedIn -> {
                        Text(t(K.MENU_DAY_CHECKIN), style = NavalType.mono, color = Naval.amberStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Gap(2)
                        HudLabel("+${d.checkinReward}", Naval.muted)
                    }
                    allDone -> {
                        Text("✓ " + t(K.MENU_DAY_DONE), style = NavalType.mono, color = Naval.greenBright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Gap(2)
                        HudLabel(t(K.DAILY_RESETS, countdown(d.resetsInSeconds)), Naval.muted)
                    }
                    else -> {
                        // desafio em andamento: missão curta e progresso
                        val progress = state.dailyProgress.coerceAtMost(d.missionTarget)
                        Text(missionText(d.mission), style = NavalType.monoSmall, color = Naval.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Gap(4)
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Naval.surface2)) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress.toFloat() / d.missionTarget.coerceAtLeast(1))
                                    .height(4.dp)
                                    .background(if (state.dailyChallengeDone) Naval.greenBright else Naval.amber)
                            )
                        }
                        Gap(3)
                        HudLabel(
                            if (state.dailyChallengeDone) t(K.DAILY_CLAIM).uppercase() else "$progress / ${d.missionTarget}",
                            if (state.dailyChallengeDone) Naval.amberStrong else Naval.muted
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ frota

/** Frota ativa balançando de leve no mar; tocar abre o Estaleiro (o botão próprio saiu). */
@Composable
private fun FleetPreview(state: AppState, modifier: Modifier = Modifier, hero: Boolean = false) {
    val swell = rememberInfiniteTransition()
    val bob by swell.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    )
    // fase das ondas: uma volta completa a cada 6s, deslocando as cristas para a esquerda
    val wavePhase by swell.animateFloat(
        initialValue = 0f,
        targetValue = (2 * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart)
    )
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.radialGradient(colors = listOf(Naval.abyss2, Naval.abyss), center = Offset.Unspecified, radius = 900f))
            .clipToBounds()
            .border(1.dp, Naval.lineSoft)
            .clickable { state.screen = Screen.SHIPYARD }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hero) {
            // tela alta: a frota inteira em formação sobre a carta náutica, do maior ao
            // menor — preenche o espaço com o que é do jogador, em vez de um vazio
            Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                val ships = ShipClass.fleet
                val cell = minOf(size.width / 5.6f, size.height / (ships.size * 1.45f))
                val rowH = size.height / ships.size
                val step = cell
                var gx = (size.width / 2f) % step
                while (gx < size.width) { drawLine(Naval.gridLine.copy(alpha = 0.09f), Offset(gx, 0f), Offset(gx, size.height), 1f); gx += step }
                var gy = (size.height / 2f) % step
                while (gy < size.height) { drawLine(Naval.gridLine.copy(alpha = 0.09f), Offset(0f, gy), Offset(size.width, gy), 1f); gy += step }
                // ondas passando em cada faixa: a crista que corre pela linha d'água é a mesma
                // que sobe e desce o navio, então o balanço acompanha o mar
                val waveLen = size.width / 2.2f
                val amp = cell * 0.10f
                fun waveY(x: Float, baseY: Float, phase: Float) =
                    baseY + kotlin.math.sin(x / waveLen * 2f * kotlin.math.PI.toFloat() + phase) * amp
                ships.forEachIndexed { i, ship ->
                    val baseY = rowH * (i + 0.5f)
                    val phase = wavePhase + i * 1.3f
                    // duas linhas de onda: uma na linha d'água, outra mais fraca abaixo
                    listOf(cell * 0.52f to 0.22f, cell * 0.78f to 0.12f).forEach { (dy, alpha) ->
                        val path = androidx.compose.ui.graphics.Path()
                        var x = 0f
                        path.moveTo(0f, waveY(0f, baseY + dy, phase))
                        while (x <= size.width) {
                            path.lineTo(x, waveY(x, baseY + dy, phase))
                            x += 6f
                        }
                        drawPath(path, Naval.commanderOne.copy(alpha = alpha), style = Stroke(width = 1.4f * density, cap = StrokeCap.Round))
                    }
                    // o navio sobe e desce com a crista que passa sob o meio dele
                    val bobY = kotlin.math.sin(size.width / 2f / waveLen * 2f * kotlin.math.PI.toFloat() + phase) * amp
                    drawShip(
                        type = ship,
                        center = Offset(size.width / 2f, baseY + bobY),
                        lengthPx = cell * ship.size,
                        thicknessPx = cell * 0.82f,
                        vertical = false,
                        skin = state.skin
                    )
                }
            }
        } else {
            Canvas(Modifier.fillMaxWidth().height(42.dp).offset(y = bob.dp)) {
                drawShip(
                    type = ShipClass.CARRIER,
                    center = Offset(size.width / 2f, size.height / 2f),
                    lengthPx = size.width * 0.94f,
                    thicknessPx = size.width * 0.94f / 5f,
                    vertical = false,
                    skin = state.skin
                )
            }
        }
        Gap(8)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.skin.fleet.name.uppercase()} · ${state.skin.paint.name.uppercase()}",
                style = NavalType.monoSmall,
                color = Naval.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            HudLabel(t(K.MENU_FLEET_TAP).uppercase() + " ›", Naval.amberStrong)
        }
    }
}

// ------------------------------------------------------------------ jogar

@Composable
private fun PlayOnlineButton(state: AppState) {
    val signedIn = state.profile.signedIn
    val pass = state.seasonPass
    val subtitle = when {
        !signedIn -> t(K.MENU_ONLINE_LOCKED)
        pass != null -> t(K.MENU_ONLINE_SEASON_SUB, seasonTitle(pass.seasonKey))
        else -> t(K.MENU_ONLINE_SUB)
    }
    PrimaryButton(t(K.MENU_PLAY_ONLINE), subtitle, big = true) {
        if (signedIn) state.screen = Screen.ONLINE else state.openLogin(Screen.MENU)
    }
}

/** Forma de jogar secundária: título em cima, legenda embaixo, empilhados. */
@Composable
private fun ModeTile(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        // mono em vez de button: "2 JOGADORES" cabe inteiro num terço da largura
        Text(title.uppercase(), style = NavalType.mono, color = Naval.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Gap(3)
        Text(subtitle, style = NavalType.monoSmall, color = Naval.muted, maxLines = 2)
    }
}

/** Atalho com ícone desenhado; [badge] > 0 mostra o selo com o número. */
@Composable
private fun Shortcut(
    label: String,
    badge: Int = 0,
    tag: String? = null,
    modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
    onClick: () -> Unit
) {
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface)
                .border(1.dp, Naval.lineSoft)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(26.dp)) { icon(Naval.inkSoft) }
            Gap(6)
            Text(label.uppercase(), style = NavalType.monoSmall, color = Naval.inkSoft, maxLines = 1)
        }
        if (tag != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Naval.amber)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(tag, style = NavalType.monoSmall, color = Naval.amberInk)
            }
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Naval.danger),
                contentAlignment = Alignment.Center
            ) {
                Text(if (badge > 9) "9+" else badge.toString(), style = NavalType.monoSmall, color = Naval.ink)
            }
        }
    }
}

/** Dois comandantes lado a lado (cabeça e ombros). */
private fun DrawScope.drawFriendsIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
    listOf(0.34f to 0.6f, 0.64f to 1f).forEach { (cx, alpha) ->
        val c = color.copy(alpha = alpha)
        drawCircle(c, radius = w * 0.13f, center = Offset(w * cx, h * 0.34f), style = stroke)
        drawArc(
            c, startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(w * (cx - 0.24f), h * 0.58f), size = Size(w * 0.48f, h * 0.5f), style = stroke
        )
    }
}

/** Balão de conversa com um ponto de exclamação — bug ou ideia. */
private fun DrawScope.drawFeedbackIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
    drawRoundRect(
        color, topLeft = Offset(w * 0.1f, h * 0.12f), size = Size(w * 0.8f, h * 0.58f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f), style = stroke
    )
    drawLine(color, Offset(w * 0.3f, h * 0.7f), Offset(w * 0.22f, h * 0.9f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    drawLine(color, Offset(w * 0.22f, h * 0.9f), Offset(w * 0.46f, h * 0.7f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    drawLine(Naval.amberStrong, Offset(w * 0.5f, h * 0.26f), Offset(w * 0.5f, h * 0.46f), strokeWidth = w * 0.08f, cap = StrokeCap.Round)
    drawCircle(Naval.amberStrong, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.57f))
}

/** Pódio de três degraus, o do meio em dourado. */
private fun DrawScope.drawPodiumIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.07f)
    drawRect(color, topLeft = Offset(w * 0.08f, h * 0.55f), size = Size(w * 0.28f, h * 0.35f), style = stroke)
    drawRect(Naval.amberStrong, topLeft = Offset(w * 0.36f, h * 0.3f), size = Size(w * 0.28f, h * 0.6f), style = stroke)
    drawRect(color, topLeft = Offset(w * 0.64f, h * 0.65f), size = Size(w * 0.28f, h * 0.25f), style = stroke)
    drawCircle(Naval.amberStrong, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.14f))
}

// ------------------------------------------------------------------ peças compartilhadas

/** Barra de topo com o saldo de dobrões (ícone da moeda + número) à direita. */
@Composable
fun ScreenTopBar(left: String, coins: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudLabel(left, Naval.inkSoft)
        CoinLabel(coins.toString())
    }
}

/**
 * Valor em dobrões: a moeda desenhada seguida do número — substitui o antigo "◆ 450"
 * em todo lugar que mostra preço ou saldo.
 */
@Composable
fun CoinLabel(
    amount: String,
    color: Color = Naval.amberStrong,
    style: TextStyle = NavalType.monoSmall,
    iconSize: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(iconSize)) {
            drawDoubloon(Offset(size.width / 2f, size.height / 2f), size.minDimension)
        }
        Spacer(Modifier.width(5.dp))
        Text(amount, style = style, color = color)
    }
}

/** Saldo de milhas náuticas: rosa dos ventos + número. */
@Composable
fun MilesLabel(
    amount: String,
    color: Color = Naval.inkSoft,
    style: TextStyle = NavalType.monoSmall,
    iconSize: Dp = 14.dp,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(iconSize)) {
            drawCompassRose(Offset(size.width / 2f, size.height / 2f), size.minDimension)
        }
        Spacer(Modifier.width(5.dp))
        Text(amount, style = style, color = color)
    }
}

@Composable
fun ScreenTopBar(left: String, right: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudLabel(left, Naval.inkSoft)
        HudLabel(right, Naval.amberStrong)
    }
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun GapW(width: Int) = Spacer(Modifier.width(width.dp))
