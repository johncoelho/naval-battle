package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.audio.Sfx
import br.com.navalbattle.audio.SoundPlayer
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import br.com.navalbattle.game.Tone
import kotlinx.coroutines.delay

private const val TURN_SECONDS = 20

@Composable
fun BattleScreen(state: AppState, match: Match) {
    var secondsLeft by remember { mutableStateOf(TURN_SECONDS) }
    val haptics = LocalHapticFeedback.current
    val sound = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    val local = match.opponent == Opponent.LOCAL
    // contra a IA a tela é sempre a do humano; no local é de quem pegou o aparelho
    val viewSide = if (local) state.battleViewSide else Side.PLAYER
    val targetBoard = match.board(viewSide.other())
    val ownBoard = match.board(viewSide)
    val myImpact = if (viewSide == Side.PLAYER) match.playerImpact else match.enemyImpact
    val incomingImpact = if (viewSide == Side.PLAYER) match.enemyImpact else match.playerImpact
    val myTurn = match.phase == Phase.BATTLE && match.turnOwner == viewSide

    LaunchedEffect(match.turnOwner, match.turnCount, match.phase) {
        if (match.phase == Phase.BATTLE && myTurn) {
            secondsLeft = TURN_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            match.fireRandom()
        }
    }

    // só contra a IA existe turno automático do adversário
    LaunchedEffect(match.turnOwner, match.phase) {
        if (!local && match.phase == Phase.BATTLE && match.turnOwner == Side.ENEMY) {
            delay(1500)
            match.enemyTurn()
        }
    }

    // no modo local: quando a vez passa, deixa a jogada terminar de tocar e então
    // cobre a tela para o aparelho trocar de mãos
    LaunchedEffect(match.turnOwner, match.phase) {
        if (local && match.phase == Phase.BATTLE && match.turnOwner != viewSide) {
            delay(2800)
            if (match.phase == Phase.BATTLE) {
                state.handoffTo(match.turnOwner, Screen.BATTLE)
            }
        }
    }

    LaunchedEffect(match.playerImpact?.id) {
        match.playerImpact?.let { imp -> playShot(sound, haptics, imp.tone, alarm = false) }
    }

    LaunchedEffect(match.enemyImpact?.id) {
        match.enemyImpact?.let { imp ->
            // contra a IA, um tiro do adversário sempre cai na SUA frota: entra o alarme de bordo
            playShot(sound, haptics, imp.tone, alarm = !local)
        }
    }

    LaunchedEffect(match.phase) {
        if (match.phase == Phase.RESULT) {
            delay(3800)
            state.screen = Screen.RESULT
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar(
            if (local) "${match.sideName(viewSide).uppercase()} ATACA" else "ALVO INIMIGO",
            "TURNO ${match.turnCount.toString().padStart(2, '0')}"
        )
        Gap(6)

        Text(
            if (myTurn) formatTime(secondsLeft) else "--:--",
            style = NavalType.timer,
            color = if (secondsLeft <= 5 && myTurn) Naval.danger else Naval.amberStrong,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        HudLabel(
            if (myTurn) "SEU TURNO · AGUARDANDO COORDENADA" else "AGUARDE",
            Naval.muted,
            Modifier.fillMaxWidth().padding(top = 2.dp)
        )

        Gap(10)
        BoardView(
            board = targetBoard,
            livery = state.livery,
            showShips = false,
            interactive = myTurn,
            impact = myImpact,
            modifier = Modifier.fillMaxWidth()
        ) { coord -> match.act(coord) }

        // mensagens ficam abaixo do tabuleiro para não cobrir a ação
        Box(
            Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            CalloutBanner(match.callout)
        }

        if (match.mode == GameMode.TACTICAL) {
            AbilityBar(match)
        }

        Spacer(Modifier.weight(1f))

        if (local) {
            // nenhum dos dois vê a própria frota (o outro está do lado), mas ambos
            // acompanham o placar ao vivo
            Scoreboard(match, viewSide, ownBoard.smokeActive)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(150.dp)
                        .background(Naval.abyss)
                        .border(1.dp, Naval.line)
                ) {
                    BoardView(
                        board = ownBoard,
                        livery = state.livery,
                        showShips = true,
                        sweep = false,
                        impact = incomingImpact,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                GapW(14)
                Column {
                    HudLabel("SUA FROTA", Naval.inkSoft)
                    Gap(4)
                    val afloat = ownBoard.remainingShips().size
                    Text(
                        "$afloat / ${ShipClass.fleet.size} À TONA",
                        style = NavalType.mono,
                        color = if (afloat <= 2) Naval.danger else Naval.greenBright
                    )
                    Gap(6)
                    HudLabel("PRECISÃO ${match.accuracyOf(viewSide)}%")
                    if (ownBoard.smokeActive) {
                        Gap(4)
                        HudLabel("CORTINA ATIVA", Naval.greenBright)
                    }
                }
            }
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

/** Placar ao vivo do modo local: os dois acompanham sem revelar posição de navio. */
@Composable
private fun Scoreboard(match: Match, viewSide: Side, smokeActive: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        HudLabel("PLACAR DA BATALHA")
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                val isViewer = side == viewSide
                val afloat = match.board(side).remainingShips().size
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (isViewer) Naval.surface3 else Naval.surface2)
                        .border(1.dp, if (isViewer) Naval.amber else Naval.line)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        match.sideName(side).uppercase(),
                        style = NavalType.monoSmall,
                        color = if (isViewer) Naval.amberStrong else Naval.inkSoft
                    )
                    Gap(6)
                    Text(
                        "$afloat / ${ShipClass.fleet.size}",
                        style = NavalType.title,
                        color = when {
                            afloat <= 1 -> Naval.danger
                            afloat <= 2 -> Naval.amberStrong
                            else -> Naval.greenBright
                        }
                    )
                    HudLabel("NAVIOS À TONA", Naval.muted)
                    Gap(6)
                    HudLabel("PRECISÃO ${match.accuracyOf(side)}%", Naval.muted)
                }
            }
        }
        if (smokeActive) {
            Gap(6)
            HudLabel("SUA CORTINA DE FUMAÇA ESTÁ ATIVA", Naval.greenBright)
        }
    }
}

@Composable
private fun AbilityBar(match: Match) {
    val abilities = listOf(
        Ability.SONAR_PING,
        Ability.AIR_RECON,
        Ability.DOUBLE_BARRAGE,
        Ability.SMOKE
    )
    Column {
        HudLabel("HABILIDADES TÁTICAS")
        Gap(6)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            abilities.forEach { ability ->
                AbilityButton(
                    code = ability.code,
                    name = ability.shortName(),
                    enabled = match.abilityAvailable(ability),
                    selected = match.pendingAbility == ability,
                    cooldown = match.abilityCooldown(ability)
                ) { match.selectAbility(ability) }
            }
        }
        Gap(6)
        val hint = match.pendingAbility?.let { "${it.label}: toque no alvo" }
            ?: "Toque num ícone para usar, ou dispare direto no alvo"
        HudLabel(hint, if (match.pendingAbility != null) Naval.amberStrong else Naval.muted)
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

private fun Ability.shortName(): String = when (this) {
    Ability.SONAR_PING -> "Sonar"
    Ability.AIR_RECON -> "Radar"
    Ability.DOUBLE_BARRAGE -> "2x Tiro"
    Ability.SMOKE -> "Fumaça"
    Ability.DIVE -> "Imersão"
}

private fun Tone.toSfx(): Sfx = when (this) {
    Tone.HIT -> Sfx.HIT
    Tone.SUNK -> Sfx.SUNK
    else -> Sfx.MISS
}
