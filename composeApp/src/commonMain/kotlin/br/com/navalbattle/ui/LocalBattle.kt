package br.com.navalbattle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Impact
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.delay

/**
 * Mostra [content] deitado (90°), para jogar com o celular na horizontal — sem mexer
 * na orientação do sistema, então funciona igual no Android e no iPhone. O conteúdo é
 * medido com largura e altura trocadas e girado em volta do centro; o toque segue a
 * rotação. As barras do sistema ficam de fora antes de girar.
 */
@Composable
fun Landscape(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = 90f }
                .layout { measurable, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val placeable = measurable.measure(Constraints.fixed(h, w))
                    layout(w, h) { placeable.place((w - h) / 2, (h - w) / 2) }
                }
        ) { content() }
    }
}

/**
 * Batalha de dois no mesmo aparelho, na horizontal: cada um segura o celular do seu
 * lado e tem o seu quadro — o mar inimigo que ele ataca, com o nome dele no topo. Só
 * o quadro da vez fica aceso e tocável. A vez troca junto em tudo (topo, quadro,
 * habilidades) só quando a animação do tiro termina ([viewSide] já vem atrasado da
 * tela de batalha), e um balão "FULANO, SUA VEZ!" aparece no lado de quem joga.
 * "⇄" inverte os lados, para quando o amigo está sentado do outro lado.
 */
@Composable
fun LocalBattleLandscape(
    state: AppState,
    match: Match,
    viewSide: Side,
    myTurn: Boolean,
    secondsLeft: Int,
    playerImpact: Impact?,
    enemyImpact: Impact?,
    onQuit: () -> Unit,
    overlay: @Composable () -> Unit
) {
    var banner by remember { mutableStateOf<Side?>(null) }
    LaunchedEffect(viewSide, match.phase) {
        if (match.phase != Phase.BATTLE) return@LaunchedEffect
        banner = viewSide
        delay(1800)
        banner = null
    }

    Landscape {
        Box(Modifier.fillMaxSize().background(Naval.bg)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                val order = if (state.localSidesSwapped) listOf(Side.ENEMY, Side.PLAYER) else listOf(Side.PLAYER, Side.ENEMY)
                CommanderHalf(state, match, order[0], viewSide, myTurn, secondsLeft, playerImpact, enemyImpact, banner, Modifier.weight(1f))
                CenterRail(state, match, viewSide, order, banner != null, onQuit)
                CommanderHalf(state, match, order[1], viewSide, myTurn, secondsLeft, playerImpact, enemyImpact, banner, Modifier.weight(1f))
            }
            // fim de partida: "VITÓRIA DE FULANO" no centro antes do relatório
            match.winner?.takeIf { match.phase == Phase.RESULT }?.let { w -> VictoryCard(match, w) }
            overlay()
        }
    }
}

@Composable
private fun VictoryCard(match: Match, winner: Side) {
    // deixa o último tiro (o navio afundando) tocar antes de cobrir a tela
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1300)
        shown = true
    }
    if (!shown) return
    val color = commanderColor(winner)
    Box(Modifier.fillMaxSize().background(Naval.bg.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Naval.surface2)
                .border(2.dp, color)
                .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(t(K.LOCAL_VICTORY_EYEBROW).uppercase(), Naval.muted)
            Text(t(K.LOCAL_VICTORY, match.sideName(winner)).uppercase(), style = NavalType.display, color = color, textAlign = TextAlign.Center)
            Text(
                t(K.LOCAL_VICTORY_SUB, match.sideName(winner.other())),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Coluna do meio: turno, inverter lados e sair. */
@Composable
private fun CenterRail(
    state: AppState,
    match: Match,
    viewSide: Side,
    order: List<Side>,
    flash: Boolean,
    onQuit: () -> Unit
) {
    Column(
        Modifier.fillMaxHeight().width(96.dp).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HudLabel(t(K.TURN), Naval.muted)
            Text(match.turnCount.toString().padStart(2, '0'), style = NavalType.title, color = Naval.ink)
        }
        // de quem é a vez, no meio, sem cobrir nenhum quadro: seta para o lado dele e o
        // nome na cor dele; acende forte quando a vez acabou de trocar
        val color = commanderColor(viewSide)
        val pointsLeft = order.first() == viewSide
        Column(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = if (flash) 0.28f else 0.10f))
                .border(if (flash) 2.dp else 1.dp, color)
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (pointsLeft) "◀" else "▶", style = NavalType.title, color = color)
            Text(
                match.sideName(viewSide).uppercase(),
                style = NavalType.monoSmall,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(t(K.LOCAL_TURN_SHORT).uppercase(), style = NavalType.monoSmall, color = Naval.inkSoft, maxLines = 1, softWrap = false)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Naval.line)
                .clickable { state.localSidesSwapped = !state.localSidesSwapped }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⇄", style = NavalType.title, color = Naval.amberStrong)
                Text(t(K.LOCAL_SWAP), style = NavalType.monoSmall, color = Naval.inkSoft, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
        Box(
            Modifier.fillMaxWidth().border(1.dp, Naval.line).clickable(onClick = onQuit).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(t(K.LOCAL_EXIT).uppercase(), style = NavalType.monoSmall, color = Naval.danger, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Metade de um comandante: nome e situação no topo, o mar inimigo que ele ataca e,
 * no Tático, as habilidades dele embaixo. Fora da vez, tudo apagado e travado.
 */
@Composable
private fun CommanderHalf(
    state: AppState,
    match: Match,
    side: Side,
    viewSide: Side,
    myTurn: Boolean,
    secondsLeft: Int,
    playerImpact: Impact?,
    enemyImpact: Impact?,
    banner: Side?,
    modifier: Modifier
) {
    val color = commanderColor(side)
    // no fim da partida o quadro aceso é o de quem venceu (antes os dois apagavam)
    val isView = if (match.phase == Phase.RESULT) match.winner == side else viewSide == side && match.phase == Phase.BATTLE
    val active = isView && myTurn
    val target = match.board(side.other())
    val impact = if (side == Side.PLAYER) playerImpact else enemyImpact
    val scan = match.lastScan?.takeIf { it.targetSide == side.other() }
    val afloat = match.board(side).remainingShips().size

    Column(modifier.fillMaxHeight()) {
        // topo: nome em destaque, frota que resta, acerto e o relógio na vez dele
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (isView) color.copy(alpha = 0.14f) else Naval.surface)
                .border(1.dp, if (isView) color else Naval.lineSoft)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).background(color))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    match.sideName(side).uppercase(),
                    style = NavalType.mono,
                    color = if (isView) color else Naval.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$afloat/${ShipClass.fleet.size} ${t(K.SHIPS_AFLOAT)} · ${t(K.ACCURACY)} ${match.accuracyOf(side)}%",
                    style = NavalType.monoSmall,
                    color = Naval.muted,
                    maxLines = 1
                )
            }
            if (isView) {
                Text(
                    if (active) "00:${secondsLeft.toString().padStart(2, '0')}" else "--:--",
                    style = NavalType.mono,
                    color = if (active && secondsLeft <= 5) Naval.danger else Naval.amberStrong
                )
            } else {
                HudLabel(t(K.WAIT), Naval.muted)
            }
        }

        // o mar inimigo que este comandante ataca — só os tiros dele, nada da frota
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp), contentAlignment = Alignment.Center) {
            val size = if (maxWidth < maxHeight) maxWidth else maxHeight
            Box(Modifier.size(size).clipToBounds().alpha(if (isView) 1f else 0.4f)) {
                BoardView(
                    board = target,
                    skin = state.skin,
                    showShips = false,
                    interactive = active,
                    impact = impact,
                    scan = scan,
                    markTint = commanderColor(side.other()),
                    modifier = Modifier.fillMaxSize()
                ) { coord -> if (active) match.act(coord) }
            }
            // balão de vez, por cima do quadro de quem vai jogar
        }
        // anúncio do último tiro numa linha só, fora do quadro (o balão grande cobria a carta)
        Text(
            if (isView) match.callout?.let { c -> "${c.main} · ${c.sub}" }.orEmpty() else "",
            style = NavalType.monoSmall,
            color = Naval.amberStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )

        if (match.mode == GameMode.TACTICAL) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp).alpha(if (active) 1f else 0.4f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf(Ability.SONAR_PING, Ability.AIR_RECON, Ability.DOUBLE_BARRAGE, Ability.SMOKE).forEach { ability ->
                    val ready = active && match.abilityAvailable(ability)
                    val charges = state.profile.chargesOf(ability)
                    val usingCharge = active && !ready && charges > 0 && match.abilityAvailable(ability, ignoreCooldown = true)
                    AbilityButton(
                        ability = ability,
                        name = ability.shortName,
                        enabled = ready || usingCharge,
                        selected = active && match.pendingAbility == ability,
                        cooldown = if (active) match.abilityCooldown(ability) else 0
                    ) {
                        if (usingCharge) state.profile.consumeCharge(ability)
                        match.selectAbility(ability, usingCharge)
                    }
                }
            }
        }
    }
}

/** Balão "FULANO, SUA VEZ!" por cima do quadro de quem vai jogar. */
@Composable
private fun TurnBanner(visible: Boolean, text: String, color: androidx.compose.ui.graphics.Color) {
    AnimatedVisibility(visible = visible, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut()) {
        Box(
            Modifier
                .background(Naval.bg.copy(alpha = 0.9f))
                .border(2.dp, color)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = NavalType.title, color = color, textAlign = TextAlign.Center)
        }
    }
}
