package br.com.navalbattle.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.audio.Sfx
import br.com.navalbattle.audio.SoundPlayer
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Impact
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import br.com.navalbattle.game.Tone
import kotlinx.coroutines.delay

private const val TURN_SECONDS = 20

/** Cor de identificação de cada comandante no modo local (mesmo aparelho). */
fun commanderColor(side: Side): Color =
    if (side == Side.PLAYER) Naval.commanderOne else Naval.commanderTwo

@Composable
fun BattleScreen(state: AppState, match: Match) {
    var secondsLeft by remember { mutableStateOf(TURN_SECONDS) }
    val haptics = LocalHapticFeedback.current
    val sound = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    val local = match.opponent == Opponent.LOCAL
    val lan = match.opponent == Opponent.LAN
    // aparelhos separados (IA ou rede) mostram a experiência cheia de single player:
    // frota própria sempre visível, alarme de bordo, tabuleiro alvo grande. Só o modo
    // local (mesmo aparelho, tela compartilhada) esconde a frota e reveza a visão.
    val solo = !local
    var confirmQuit by remember { mutableStateOf(false) }

    // no modo local cada comandante enxerga só a própria memória de tiros: a carta
    // exibida é sempre a da frota que ele ataca. Em rede e contra a IA, cada aparelho
    // é sempre o mesmo lado do início ao fim da partida.
    var localView by remember { mutableStateOf(Side.PLAYER) }
    val viewSide = when {
        local -> localView
        lan -> match.mySide
        else -> Side.PLAYER
    }
    val myTurn = match.phase == Phase.BATTLE && match.turnOwner == viewSide

    // só o disparo mais recente anima — as marcas antigas ficam paradas na carta
    val playerImpact = match.playerImpact?.takeIf { it.id == match.lastImpactId }
    val enemyImpact = match.enemyImpact?.takeIf { it.id == match.lastImpactId }
    // impacto que EU causei (mira o tabuleiro alvo) e o que EU sofri (mira minha frota)
    val myImpact = if (viewSide == Side.PLAYER) playerImpact else enemyImpact
    val incomingImpact = if (viewSide == Side.PLAYER) enemyImpact else playerImpact

    // a carta só troca de dono depois que a jogada termina de tocar
    LaunchedEffect(match.turnOwner, match.phase) {
        if (local && match.phase == Phase.BATTLE && match.turnOwner != localView) {
            val tone = (if (localView == Side.PLAYER) match.playerImpact else match.enemyImpact)?.tone
            delay(if (tone == Tone.SUNK) 3400L else 1700L)
            if (match.phase == Phase.BATTLE) localView = match.turnOwner
        }
    }

    LaunchedEffect(match.turnOwner, match.turnCount, match.phase, viewSide) {
        if (match.phase == Phase.BATTLE && myTurn) {
            secondsLeft = TURN_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            // em rede, quem escolhe a coordenada é o dono do turno, e ela viaja
            if (lan) match.pickTarget()?.let { state.fireShared(it) } else match.fireRandom()
        }
    }

    // só contra a IA existe turno automático do adversário
    LaunchedEffect(match.turnOwner, match.phase) {
        if (!local && !lan && match.phase == Phase.BATTLE && match.turnOwner == Side.ENEMY) {
            delay(1500)
            match.enemyTurn()
        }
    }

    // alarme de bordo: soa quando A MINHA frota é atingida, nunca no meu próprio tiro.
    // no modo local não há identidade fixa por aparelho, então o alarme fica desligado.
    val mySide = if (solo) viewSide else null
    LaunchedEffect(match.playerImpact?.id) {
        match.playerImpact?.let { imp ->
            playShot(sound, haptics, imp.tone, alarm = mySide != null && mySide != Side.PLAYER)
        }
    }
    LaunchedEffect(match.enemyImpact?.id) {
        match.enemyImpact?.let { imp ->
            playShot(sound, haptics, imp.tone, alarm = mySide != null && mySide != Side.ENEMY)
        }
    }

    LaunchedEffect(match.phase) {
        if (match.phase == Phase.RESULT) {
            delay(3800)
            state.screen = Screen.RESULT
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenTopBar(
                    if (local) t(K.BATTLE_TURN_OF, match.sideName(viewSide)).uppercase()
                    else t(K.BATTLE_ENEMY_TARGET),
                    "${t(K.TURN)} ${match.turnCount.toString().padStart(2, '0')}",
                    Modifier.weight(1f)
                )
                GapW(10)
                HudLabel(
                    t(K.QUIT),
                    Naval.danger,
                    Modifier
                        .border(1.dp, Naval.line)
                        .clickable { confirmQuit = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Gap(6)

            Text(
                if (myTurn) formatTime(secondsLeft) else "--:--",
                style = NavalType.timer,
                color = if (secondsLeft <= 5 && myTurn) Naval.danger else Naval.amberStrong,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HudLabel(
                when {
                    local && myTurn -> t(K.BATTLE_MEMORY, match.sideName(viewSide.other())).uppercase()
                    local -> t(K.BATTLE_PASSING, match.sideName(match.turnOwner)).uppercase()
                    myTurn -> t(K.BATTLE_YOUR_TURN)
                    else -> t(K.WAIT)
                },
                // no modo local a cor é a da frota alvo, a mesma das marcas na carta
                if (local) commanderColor(viewSide.other()) else Naval.muted,
                Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Gap(10)
            if (local) {
                // cada comandante vê só os próprios tiros: a carta da frota que ele ataca
                BoardView(
                    board = match.board(viewSide.other()),
                    skin = state.skin,
                    showShips = false,
                    interactive = myTurn,
                    impact = myImpact,
                    markTint = commanderColor(viewSide.other()),
                    modifier = Modifier.fillMaxWidth()
                ) { coord -> match.act(coord) }
            } else {
                // IA e rede: sempre o mesmo alvo, do início ao fim da partida
                BoardView(
                    board = match.board(viewSide.other()),
                    skin = state.skin,
                    showShips = false,
                    interactive = myTurn,
                    impact = myImpact,
                    modifier = Modifier.fillMaxWidth()
                ) { coord -> if (lan) state.fireShared(coord) else match.act(coord) }
            }

            // mensagens ficam abaixo do tabuleiro para não cobrir a ação
            Box(
                Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CalloutBanner(match.callout)
            }

            if (match.mode == GameMode.TACTICAL) {
                AbilityBar(match) { ability ->
                    if (lan) state.useAbilityShared(ability) else match.selectAbility(ability)
                }
            }

            if (local) {
                Spacer(Modifier.weight(1f))
                Scoreboard(match)
            } else {
                // IA e rede mostram a própria frota inteira, do jeito que o single
                // player sempre mostrou — inclusive em rede, onde ninguém mais vê esta tela
                OwnFleetPanel(state, match, viewSide, incomingImpact)
            }
        }

        if (confirmQuit) {
            QuitOverlay(
                onKeep = { confirmQuit = false },
                onQuit = { state.quitToMenu() }
            )
        }
    }
}

/**
 * Frota própria em tela cheia, como no modo solo: ocupa toda a faixa livre da
 * metade de baixo, sempre quadrada. Usada contra a IA e na rede — nos dois casos
 * ninguém mais está olhando para este aparelho.
 */
@Composable
private fun OwnFleetPanel(state: AppState, match: Match, viewSide: Side, incomingImpact: Impact?) {
    val board = match.board(viewSide)
    val afloat = board.remainingShips().size
    Gap(6)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudLabel(t(K.BATTLE_YOUR_FLEET), Naval.inkSoft)
        Text(
            "$afloat / ${ShipClass.fleet.size} ${t(K.SHIPS_AFLOAT)}" +
                " · ${t(K.ACCURACY)} ${match.accuracyOf(viewSide)}%",
            style = NavalType.mono,
            color = if (afloat <= 2) Naval.danger else Naval.greenBright
        )
    }
    if (board.smokeActive) {
        HudLabel(t(K.BATTLE_SMOKE_ACTIVE), Naval.greenBright)
    }
    Gap(6)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        // quadrada, do tamanho do que sobrou — larga no clássico, um pouco menor no
        // tático, onde a barra de habilidades ocupa parte da faixa
        val side = if (maxWidth < maxHeight) maxWidth else maxHeight
        BoardView(
            board = board,
            skin = state.skin,
            showShips = true,
            sweep = false,
            impact = incomingImpact,
            modifier = Modifier.size(side)
        )
    }
}

/**
 * Placar do modo local. A cor de cada comandante é a mesma que pinta os danos da
 * frota dele na carta, então dá para ler o mapa sem legenda extra.
 */
@Composable
private fun Scoreboard(match: Match) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
            val color = commanderColor(side)
            val playing = match.phase == Phase.BATTLE && match.turnOwner == side
            val afloat = match.board(side).remainingShips().size
            Column(
                Modifier
                    .weight(1f)
                    .background(if (playing) Naval.surface3 else Naval.surface2)
                    .border(1.dp, if (playing) color else Naval.line)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(color))
                    GapW(6)
                    Text(
                        match.sideName(side).uppercase(),
                        style = NavalType.monoSmall,
                        color = color,
                        maxLines = 1
                    )
                }
                Gap(6)
                Text(
                    "$afloat / ${ShipClass.fleet.size}",
                    style = NavalType.title,
                    color = when {
                        afloat <= 1 -> Naval.danger
                        afloat <= 2 -> Naval.amberStrong
                        else -> Naval.ink
                    }
                )
                HudLabel(t(K.SHIPS_AFLOAT), Naval.muted)
                Gap(6)
                HudLabel("${t(K.ACCURACY)} ${match.accuracyOf(side)}%", Naval.muted)
                if (match.board(side).smokeActive) {
                    HudLabel(t(K.BATTLE_SMOKE_ACTIVE), Naval.greenBright)
                }
            }
        }
    }
}

/** Confirmação para abandonar a partida no meio — evita sair por toque acidental. */
@Composable
private fun QuitOverlay(onKeep: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.94f))
            .clickable(enabled = false) {}
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth()) {
            HudLabel(t(K.BATTLE_QUIT_EYEBROW), Naval.muted)
            Gap(8)
            Text(t(K.BATTLE_QUIT_TITLE).uppercase(), style = NavalType.display, color = Naval.ink)
            Gap(6)
            HudLabel(t(K.BATTLE_QUIT_WARN), Naval.muted)
            Gap(22)
            PrimaryButton(t(K.BATTLE_KEEP_PLAYING)) { onKeep() }
            Gap(8)
            SecondaryButton(t(K.QUIT_MATCH)) { onQuit() }
        }
    }
}

private suspend fun playShot(
    sound: SoundPlayer,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    tone: Tone,
    alarm: Boolean
) {
    sound.play(Sfx.LAUNCH)
    delay(SHOT_TRAVEL_MS.toLong())
    sound.play(tone.toSfx())
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    if (!alarm) return
    when (tone) {
        Tone.SUNK -> {
            delay(220)
            sound.play(Sfx.ALARM_CRITICAL)
        }

        Tone.HIT -> {
            delay(160)
            sound.play(Sfx.ALARM)
        }

        else -> Unit
    }
}

@Composable
private fun AbilityBar(match: Match, onUse: (Ability) -> Unit) {
    val abilities = listOf(
        Ability.SONAR_PING,
        Ability.AIR_RECON,
        Ability.DOUBLE_BARRAGE,
        Ability.SMOKE
    )
    Column {
        HudLabel(t(K.BATTLE_ABILITIES))
        Gap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            abilities.forEach { ability ->
                AbilityButton(
                    code = ability.code,
                    name = ability.shortName,
                    enabled = match.abilityAvailable(ability),
                    selected = match.pendingAbility == ability,
                    cooldown = match.abilityCooldown(ability)
                ) { onUse(ability) }
            }
        }
        Gap(6)
        val hint = match.pendingAbility?.let { t(K.BATTLE_ABILITY_AIM, it.label) }
            ?: t(K.BATTLE_ABILITY_HINT)
        HudLabel(hint, if (match.pendingAbility != null) Naval.amberStrong else Naval.muted)
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

private fun Tone.toSfx(): Sfx = when (this) {
    Tone.HIT -> Sfx.HIT
    Tone.SUNK -> Sfx.SUNK
    else -> Sfx.MISS
}
