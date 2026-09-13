package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawInsignia
import br.com.navalbattle.game.Insignia
import br.com.navalbattle.game.Rank

@Composable
fun ProfileScreen(state: AppState) {
    val profile = state.profile
    var name by remember { mutableStateOf(profile.name) }
    var confirmReset by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenTopBar("PERFIL", "◆ ${profile.credits}")
            Gap(14)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // brasão, nome e patente
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(78.dp)) {
                        drawInsignia(
                            insignia = profile.insignia,
                            center = Offset(size.width / 2f, size.height / 2f),
                            size = size.minDimension * 0.68f,
                            color = Naval.amberStrong
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.rank.label.uppercase(), style = NavalType.title, color = Naval.amberStrong)
                        Gap(2)
                        Text(profile.name, style = NavalType.body, color = Naval.ink)
                        Gap(6)
                        HudLabel("${profile.xp} XP", Naval.muted)
                    }
                }

                Gap(12)
                RankBar(profile.xp, profile.rankProgress)

                Gap(16)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Naval.surface2)
                        .border(1.dp, if (profile.signedIn) Naval.green else Naval.line)
                        .clickable { state.screen = Screen.AUTH }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        HudLabel(
                            if (profile.signedIn) "CONTA CONECTADA" else "SEM CONTA",
                            if (profile.signedIn) Naval.greenBright else Naval.amberStrong
                        )
                        Gap(4)
                        HudLabel(
                            if (profile.signedIn) profile.accountEmail.uppercase()
                            else "CRIE UMA PARA GUARDAR A CARREIRA NA NUVEM",
                            Naval.muted
                        )
                    }
                    HudLabel(if (profile.signedIn) "GERIR" else "CRIAR", Naval.inkSoft)
                }

                Gap(22)
                HudLabel("NOME DE GUERRA")
                Gap(6)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(Naval.surface2)
                            .border(1.dp, Naval.line)
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { if (it.length <= 18) name = it },
                            singleLine = true,
                            textStyle = NavalType.button.copy(color = Naval.ink),
                            cursorBrush = SolidColor(Naval.amberStrong),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    HudLabel(
                        "SALVAR",
                        if (name.trim() != profile.name) Naval.amberStrong else Naval.muted,
                        Modifier
                            .border(1.dp, Naval.line)
                            .clickable { profile.rename(name); name = profile.name }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    )
                }

                Gap(22)
                HudLabel("INSÍGNIA")
                Gap(8)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Insignia.entries.forEach { option ->
                        val chosen = profile.insignia == option
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .background(if (chosen) Naval.surface3 else Naval.surface)
                                .border(1.dp, if (chosen) Naval.amber else Naval.line)
                                .clickable { profile.chooseInsignia(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(38.dp)) {
                                drawInsignia(
                                    insignia = option,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    size = size.minDimension * 0.62f,
                                    color = if (chosen) Naval.amberStrong else Naval.inkSoft
                                )
                            }
                        }
                    }
                }
                Gap(6)
                HudLabel(profile.insignia.label.uppercase(), Naval.muted)

                Gap(22)
                HudLabel("FOLHA DE SERVIÇO")
                Gap(8)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat("PARTIDAS", profile.matches.toString(), Modifier.weight(1f))
                    BigStat("VITÓRIAS", profile.wins.toString(), Modifier.weight(1f), Naval.greenBright)
                    BigStat("DERROTAS", profile.losses.toString(), Modifier.weight(1f), Naval.danger)
                }
                Gap(10)
                StatLine("APROVEITAMENTO", "${profile.winRate}%")
                StatLine("PRECISÃO DE TIRO", "${profile.accuracy}%")
                StatLine("TIROS / ACERTOS", "${profile.shots} / ${profile.hits}")
                StatLine("NAVIOS AFUNDADOS", profile.sunk.toString())
                StatLine("SEQUÊNCIA ATUAL", "${profile.streak} vitórias")
                StatLine("MELHOR SEQUÊNCIA", "${profile.bestStreak} vitórias")
                StatLine("CASCOS NO ESTALEIRO", profile.ownedFleets.size.toString())
                StatLine("CAMUFLAGENS", profile.owned.size.toString())

                Gap(18)
                HudLabel("PRÓXIMAS PATENTES", Naval.muted)
                Gap(6)
                Rank.entries.filter { it.xp > profile.xp }.take(3).forEach { r ->
                    StatLine(r.label.uppercase(), "${r.xp - profile.xp} XP")
                }
                if (Rank.next(profile.xp) == null) {
                    HudLabel("PATENTE MÁXIMA ALCANÇADA", Naval.amberStrong)
                }

                Gap(20)
                HudLabel(
                    "ZERAR CARREIRA",
                    Naval.danger,
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Naval.line)
                        .clickable { confirmReset = true }
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                )
                Gap(16)
            }

            Gap(10)
            PrimaryButton("Voltar ao deque") { state.screen = Screen.MENU }
        }

        if (confirmReset) {
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
                    HudLabel("BAIXA DEFINITIVA", Naval.muted)
                    Gap(8)
                    Text("ZERAR A CARREIRA?", style = NavalType.display, color = Naval.ink)
                    Gap(6)
                    HudLabel("PATENTE, CRÉDITOS, ESTATÍSTICAS E FROTAS VOLTAM AO INÍCIO", Naval.muted)
                    Gap(22)
                    PrimaryButton("Manter carreira") { confirmReset = false }
                    Gap(8)
                    SecondaryButton("Zerar tudo") {
                        profile.reset()
                        name = profile.name
                        confirmReset = false
                    }
                }
            }
        }
    }
}

/** Barra de progresso até a próxima patente. */
@Composable
private fun RankBar(xp: Int, progress: Float) {
    val next = Rank.next(xp)
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Naval.surface3)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                    .height(4.dp)
                    .background(Naval.amber)
            )
        }
        Gap(6)
        HudLabel(
            if (next == null) "CARREIRA COMPLETA" else "FALTAM ${next.xp - xp} XP PARA ${next.label.uppercase()}",
            Naval.muted
        )
    }
}

@Composable
private fun BigStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Naval.ink
) {
    Column(
        modifier
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, style = NavalType.title, color = color)
        Gap(2)
        HudLabel(label, Naval.muted)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudLabel(label, Naval.muted)
        Text(value, style = NavalType.mono, color = Naval.ink)
    }
}
