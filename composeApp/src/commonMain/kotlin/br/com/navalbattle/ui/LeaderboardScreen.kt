package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.LeaderboardEntry
import br.com.navalbattle.data.SeasonTrophy
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawAvatar
import br.com.navalbattle.design.drawTrophy
import br.com.navalbattle.game.Avatar
import br.com.navalbattle.game.liveTierFor
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

private enum class BoardTab { SEASON, OVERALL, TROPHIES }

private fun tierColor(tier: String?): Color = when (tier) {
    "ouro" -> Naval.amberStrong
    "prata" -> Color(0xFFC7D0DA)
    "bronze" -> Color(0xFFCB8A54)
    else -> Naval.line
}

private fun tierLabel(tier: String?): K? = when (tier) {
    "ouro" -> K.LEADERBOARD_TIER_GOLD
    "prata" -> K.LEADERBOARD_TIER_SILVER
    "bronze" -> K.LEADERBOARD_TIER_BRONZE
    else -> null
}

/**
 * Placar ranqueado — temporada corrente, geral (histórico completo) e o
 * ranking de troféus da última temporada encerrada. Mostra a posição do
 * próprio comandante mesmo quando ele não está no topo, pra sempre dar um
 * número pra se comparar, e agrupa visualmente o pódio em faixas (ouro,
 * prata, bronze), igual à maioria dos jogos do gênero.
 */
@Composable
fun LeaderboardScreen(state: AppState) {
    var tab by remember { mutableStateOf(BoardTab.SEASON) }
    LaunchedEffect(state.leaderboardSeasonMode, tab) {
        if (tab != BoardTab.TROPHIES) state.loadLeaderboard(state.leaderboardSeasonMode)
    }
    LaunchedEffect(tab) { if (tab == BoardTab.TROPHIES) state.loadSeasonTrophies() }

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
            ModeChip(t(K.LEADERBOARD_SEASON), tab == BoardTab.SEASON, Modifier.weight(1f)) {
                tab = BoardTab.SEASON
                state.leaderboardSeasonMode = true
            }
            ModeChip(t(K.LEADERBOARD_OVERALL), tab == BoardTab.OVERALL, Modifier.weight(1f)) {
                tab = BoardTab.OVERALL
                state.leaderboardSeasonMode = false
            }
            ModeChip(t(K.LEADERBOARD_TROPHIES_TAB), tab == BoardTab.TROPHIES, Modifier.weight(1f)) {
                tab = BoardTab.TROPHIES
            }
        }

        if (tab != BoardTab.TROPHIES) {
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
        }

        Gap(18)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (tab == BoardTab.TROPHIES) {
                TrophyBoard(state.seasonTrophies)
            } else {
                LiveBoard(state.leaderboardEntries)
            }
            Gap(16)
        }

        Gap(10)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
    }
}

@Composable
private fun LiveBoard(entries: List<LeaderboardEntry>) {
    if (entries.isEmpty()) {
        HudLabel(t(K.LEADERBOARD_EMPTY), Naval.muted)
        return
    }
    var lastTier: String? = "__start__"
    entries.forEachIndexed { index, entry ->
        val position = index + 1
        val tier = liveTierFor(position, entries.size)
        if (tier != lastTier && tier != null) {
            if (lastTier != "__start__") Gap(10)
            TierBand(tier)
            Gap(8)
        }
        lastTier = tier
        BoardRow(
            position = position,
            username = entry.username,
            avatar = entry.avatar,
            points = entry.rating,
            record = "${entry.wins}/${entry.matches}",
            tier = tier
        )
    }
}

@Composable
private fun TrophyBoard(trophies: List<SeasonTrophy>) {
    HudLabel(t(K.LEADERBOARD_TROPHIES_HEAD), Naval.muted)
    Gap(14)
    val medalists = trophies.filter { it.tier != null }
    if (medalists.isEmpty()) {
        HudLabel(t(K.LEADERBOARD_TROPHIES_EMPTY), Naval.muted)
        return
    }
    var lastTier: String? = "__start__"
    medalists.forEach { entry ->
        if (entry.tier != lastTier) {
            if (lastTier != "__start__") Gap(10)
            entry.tier?.let { TierBand(it) }
            Gap(8)
        }
        lastTier = entry.tier
        BoardRow(
            position = entry.position.toInt(),
            username = entry.username,
            avatar = entry.avatar,
            points = entry.points,
            record = null,
            tier = entry.tier
        )
    }
}

@Composable
private fun TierBand(tier: String) {
    val label = tierLabel(tier)?.let { t(it) } ?: return
    val color = tierColor(tier)
    Row(
        Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(16.dp)) {
            drawTrophy(color, Offset(size.width / 2f, size.height / 2f), size.minDimension)
        }
        GapW(8)
        Text(label.uppercase(), style = NavalType.button, color = color)
    }
}

@Composable
private fun BoardRow(
    position: Int,
    username: String,
    avatar: String,
    points: Int,
    record: String?,
    tier: String?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Naval.surface2)
            .border(1.dp, if (tier != null) tierColor(tier).copy(alpha = 0.6f) else Naval.line)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tier != null && position <= 3) {
                Canvas(Modifier.size(22.dp)) {
                    drawTrophy(tierColor(tier), Offset(size.width / 2f, size.height / 2f), size.minDimension)
                }
            } else {
                HudLabel(t(K.LEADERBOARD_POSITION, position), Naval.muted)
            }
            GapW(10)
            Canvas(Modifier.size(30.dp)) {
                drawAvatar(
                    avatar = Avatar.of(avatar),
                    center = Offset(size.width / 2f, size.height / 2f),
                    size = size.minDimension * 0.92f,
                    color = Naval.amberStrong
                )
            }
            GapW(10)
            Text(username.uppercase(), style = NavalType.mono, color = Naval.ink)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(t(K.LEADERBOARD_POINTS, points), style = NavalType.button, color = Naval.amberStrong)
            record?.let { HudLabel(it, Naval.muted) }
        }
    }
}
