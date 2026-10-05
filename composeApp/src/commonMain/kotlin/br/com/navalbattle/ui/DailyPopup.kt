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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.DailyStatus
import br.com.navalbattle.design.DoubloonPackArt
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawDoubloon
import br.com.navalbattle.design.drawDoubloonPack
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/** Texto da missão do dia — o servidor manda só o código (ver `supabase/daily.sql`). */
fun missionText(code: String): String = when (code) {
    "PLAY_1" -> t(K.MISSION_PLAY_1)
    "WIN_1" -> t(K.MISSION_WIN_1)
    "SINK_5" -> t(K.MISSION_SINK_5)
    "ABILITY_2" -> t(K.MISSION_ABILITY_2)
    "ACCURACY_60" -> t(K.MISSION_ACCURACY_60)
    else -> code
}

/**
 * Diário de bordo: trilha de 7 dias de check-in (o 7º é o baú do bônus da semana) e
 * o desafio do dia com o progresso. Abre sozinho a cada entrada no app com prêmio
 * esperando, ou pelo chip do menu. Tocar fora fecha.
 */
@Composable
fun DailyPopup(state: AppState) {
    if (!state.dailyPopupShowing) return
    val d = state.daily ?: return
    val scope = rememberCoroutineScope()
    // dia que o check-in de hoje vai marcar (ou marcou) na trilha
    val todaySlot = if (d.checkedIn) d.streak else d.streak + 1

    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.closeDailyPopup() }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HudLabel(t(K.DAILY_TITLE).uppercase(), Naval.amberStrong)
            Gap(2)
            Text(t(K.DAILY_SUB), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            Gap(14)

            // trilha de 7 dias: feitos em dourado, hoje com borda, o 7º é o baú
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..7).forEach { day ->
                    DayCell(
                        day = day,
                        done = day <= d.streak,
                        today = day == todaySlot,
                        reward = if (day == 7) d.checkinReward + d.weekBonus else d.checkinReward,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Gap(8)
            HudLabel(t(K.DAILY_WEEK_HINT, d.weekBonus), Naval.inkSoft)
            HudLabel(t(K.DAILY_STREAK_RULE), Naval.muted)
            Gap(12)
            if (d.checkedIn) {
                SecondaryButton(t(K.DAILY_CHECKED), enabled = false, modifier = Modifier.fillMaxWidth()) {}
            } else {
                PrimaryButton(
                    t(K.DAILY_CHECKIN),
                    subtitle = "+${if (todaySlot == 7) d.checkinReward + d.weekBonus else d.checkinReward}",
                    enabled = !state.dailyBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { scope.launch { state.dailyCheckin() } }
            }

            Gap(18)
            // desafio do dia
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface)
                    .border(1.dp, if (state.dailyChallengeDone && !d.challengeClaimed) Naval.amber else Naval.lineSoft)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudLabel(t(K.DAILY_CHALLENGE).uppercase(), Naval.muted, Modifier.weight(1f))
                    CoinLabel("+${d.challengeReward}", style = NavalType.mono)
                }
                Gap(6)
                Text(missionText(d.mission), style = NavalType.mono, color = Naval.ink)
                HudLabel(t(K.DAILY_CHALLENGE_WHERE), Naval.muted)
                Gap(10)
                val progress = state.dailyProgress.coerceAtMost(d.missionTarget)
                val fraction = progress.toFloat() / d.missionTarget.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).background(Naval.surface2).border(1.dp, Naval.lineSoft)) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(8.dp)
                                .background(if (fraction >= 1f) Naval.greenBright else Naval.amber)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val unit = if (d.mission == "ACCURACY_60") "%" else ""
                    Text("$progress$unit / ${d.missionTarget}$unit", style = NavalType.monoSmall, color = Naval.inkSoft)
                }
                Gap(12)
                when {
                    d.challengeClaimed ->
                        SecondaryButton(t(K.DAILY_CLAIMED), enabled = false, modifier = Modifier.fillMaxWidth()) {}
                    state.dailyChallengeDone && !d.checkedIn ->
                        HudLabel(t(K.DAILY_CHALLENGE_LOCKED), Naval.amberStrong)
                    else -> PrimaryButton(
                        t(K.DAILY_CLAIM),
                        enabled = state.dailyChallengeDone && !state.dailyBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { scope.launch { state.claimDailyChallenge() } }
                }
            }

            state.dailyNotice?.let {
                Gap(12)
                Text(it, style = NavalType.mono, color = Naval.amberStrong, textAlign = TextAlign.Center)
            }
            Gap(12)
            HudLabel(t(K.DAILY_RESETS, countdown(d.resetsInSeconds)), Naval.muted)
            Gap(12)
            SecondaryButton(t(K.MILES_CLOSE), modifier = Modifier.fillMaxWidth()) { state.closeDailyPopup() }
        }
    }
}

@Composable
private fun DayCell(day: Int, done: Boolean, today: Boolean, reward: Int, modifier: Modifier = Modifier) {
    val chest = day == 7
    Column(
        modifier
            .background(if (done) Naval.amber.copy(alpha = 0.16f) else Naval.surface)
            .border(1.dp, if (today) Naval.amberStrong else if (done) Naval.amber else Naval.lineSoft)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t(K.DAILY_DAY, day), style = NavalType.monoSmall, color = if (done || today) Naval.ink else Naval.muted)
        Gap(4)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(horizontal = 4.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (chest) {
                drawDoubloonPack(DoubloonPackArt.CHEST, c, size.minDimension * 0.9f)
            } else {
                drawDoubloon(c, size.minDimension * 0.7f, alpha = if (done || today) 1f else 0.45f)
            }
            if (done) {
                // visto de feito por cima da moeda
                val r = size.minDimension * 0.2f
                val c2 = Offset(size.width * 0.78f, size.height * 0.22f)
                drawCircle(Naval.greenBright, radius = r, center = c2)
                val tick = Path().apply {
                    moveTo(c2.x - r * 0.5f, c2.y)
                    lineTo(c2.x - r * 0.1f, c2.y + r * 0.42f)
                    lineTo(c2.x + r * 0.55f, c2.y - r * 0.4f)
                }
                drawPath(tick, Naval.bg, style = Stroke(width = r * 0.32f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Gap(2)
        Text("+$reward", style = NavalType.monoSmall, color = if (chest) Naval.amberStrong else Naval.inkSoft)
    }
}

/** Chip do menu: dia da trilha e se tem prêmio esperando. */
@Composable
fun DailyChip(daily: DailyStatus, pending: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, if (pending) Naval.amber else Naval.lineSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(20.dp)) { drawDoubloon(Offset(size.width / 2f, size.height / 2f), size.minDimension) }
        Spacer(Modifier.width(8.dp))
        Text(
            "${t(K.DAILY_TITLE)} · ${daily.streak}/7".uppercase(),
            style = NavalType.mono,
            color = if (pending) Naval.amberStrong else Naval.inkSoft,
            modifier = Modifier.weight(1f)
        )
        Text(
            (if (pending) t(K.DAILY_CHIP_PENDING) else t(K.DAILY_CHIP_DONE)).uppercase(),
            style = NavalType.monoSmall,
            color = if (pending) Naval.amberStrong else Naval.muted
        )
    }
}
