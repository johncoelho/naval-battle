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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import kotlinx.coroutines.delay

private const val TURN_SECONDS = 20

@Composable
fun BattleScreen(state: AppState, match: Match) {
    var secondsLeft by remember { mutableStateOf(TURN_SECONDS) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(match.turnOwner, match.turnCount, match.phase) {
        if (match.phase == Phase.BATTLE && match.turnOwner == Side.PLAYER) {
            secondsLeft = TURN_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            match.playerFireRandom()
        }
    }

    LaunchedEffect(match.turnOwner, match.phase) {
        if (match.phase == Phase.BATTLE && match.turnOwner == Side.ENEMY) {
            delay(1200)
            match.enemyTurn()
        }
    }

    LaunchedEffect(match.playerImpact?.id, match.enemyImpact?.id) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(match.phase) {
        if (match.phase == Phase.RESULT) {
            delay(1600)
            state.screen = Screen.RESULT
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar("ALVO INIMIGO", "TURNO ${match.turnCount.toString().padStart(2, '0')}")
        Gap(6)

        Text(
            if (match.turnOwner == Side.PLAYER) formatTime(secondsLeft) else "--:--",
            style = NavalType.timer,
            color = if (secondsLeft <= 5 && match.turnOwner == Side.PLAYER) Naval.danger else Naval.amberStrong,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        HudLabel(
            if (match.turnOwner == Side.PLAYER) "SEU TURNO · AGUARDANDO COORDENADA" else "INIMIGO ATACANDO",
            Naval.muted,
            Modifier.fillMaxWidth().padding(top = 2.dp)
        )

        Gap(10)
        Box(Modifier.fillMaxWidth()) {
            BoardView(
                board = match.enemyBoard,
                livery = state.livery,
                showShips = false,
                interactive = match.phase == Phase.BATTLE && match.turnOwner == Side.PLAYER,
                impact = match.playerImpact,
                modifier = Modifier.fillMaxWidth()
            ) { coord -> match.playerAct(coord) }

            CalloutBanner(
                match.callout,
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
            )
        }

        if (match.mode == GameMode.TACTICAL) {
            Gap(10)
            AbilityBar(match)
        }

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(112.dp)
                    .background(Naval.abyss)
                    .border(1.dp, Naval.line)
            ) {
                BoardView(
                    board = match.playerBoard,
                    livery = state.livery,
                    showShips = true,
                    sweep = false,
                    impact = match.enemyImpact,
                    modifier = Modifier.fillMaxSize()
                )
            }
            GapW(14)
            Column {
                HudLabel("SUA FROTA", Naval.inkSoft)
                Gap(4)
                val afloat = match.playerBoard.remainingShips().size
                Text(
                    "$afloat / ${ShipClass.fleet.size} À TONA",
                    style = NavalType.mono,
                    color = if (afloat <= 2) Naval.danger else Naval.greenBright
                )
                Gap(6)
                HudLabel("PRECISÃO ${match.accuracy}%")
                if (match.playerBoard.smokeActive) {
                    Gap(4)
                    HudLabel("CORTINA ATIVA", Naval.greenBright)
                }
            }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            abilities.forEach { ability ->
                AbilityButton(
                    code = ability.code,
                    enabled = match.abilityAvailable(ability),
                    selected = match.pendingAbility == ability,
                    cooldown = match.abilityCooldown(ability)
                ) { match.selectAbility(ability) }
            }
        }
        match.pendingAbility?.let {
            Gap(6)
            HudLabel("${it.label}: toque no alvo", Naval.amberStrong)
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
