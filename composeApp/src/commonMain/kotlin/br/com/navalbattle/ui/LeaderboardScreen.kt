package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Placar ranqueado — geral (histórico completo) e de temporada (reinicia a
 * cada estação). Mostra a posição do próprio comandante mesmo quando ele não
 * está no topo, pra sempre dar um número pra se comparar.
 */
@Composable
fun LeaderboardScreen(state: AppState) {
    LaunchedEffect(state.leaderboardSeasonMode) { state.loadLeaderboard(state.leaderboardSeasonMode) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        val seasonSuffix = state.currentSeason?.seasonKey?.substringAfterLast('-')
        val seasonLabel = when (seasonSuffix) {
            "verao" -> t(K.SEASON_NAME_VERAO)
            "outono" -> t(K.SEASON_NAME_OUTONO)
            "inverno" -> t(K.SEASON_NAME_INVERNO)
            "primavera" -> t(K.SEASON_NAME_PRIMAVERA)
            else -> ""
        }
        ScreenTopBar(t(K.LEADERBOARD_TITLE), seasonLabel)
        Gap(14)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip(t(K.LEADERBOARD_SEASON), state.leaderboardSeasonMode, Modifier.weight(1f)) {
                state.leaderboardSeasonMode = true
            }
            ModeChip(t(K.LEADERBOARD_OVERALL), !state.leaderboardSeasonMode, Modifier.weight(1f)) {
                state.leaderboardSeasonMode = false
            }
        }

        state.myRank?.let { rank ->
            Gap(14)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface3)
                    .border(1.dp, Naval.amber)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HudLabel(t(K.LEADERBOARD_YOUR_RANK), Naval.amberStrong)
                Text(
                    "${t(K.LEADERBOARD_POSITION, rank.position)} · ${t(K.LEADERBOARD_POINTS, rank.rating)}",
                    style = NavalType.button,
                    color = Naval.ink
                )
            }
        } ?: run {
            Gap(14)
            HudLabel(t(K.LEADERBOARD_UNRANKED), Naval.muted)
        }

        Gap(18)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (state.leaderboardEntries.isEmpty()) {
                HudLabel(t(K.LEADERBOARD_EMPTY), Naval.muted)
            } else {
                state.leaderboardEntries.forEachIndexed { index, entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .background(Naval.surface2)
                            .border(1.dp, Naval.line)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HudLabel(t(K.LEADERBOARD_POSITION, index + 1), Naval.muted)
                            GapW(12)
                            Text(entry.username.uppercase(), style = NavalType.mono, color = Naval.ink)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(t(K.LEADERBOARD_POINTS, entry.rating), style = NavalType.button, color = Naval.amberStrong)
                            HudLabel("${entry.wins}/${entry.matches}", Naval.muted)
                        }
                    }
                }
            }
            Gap(16)
        }

        Gap(10)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
    }
}
