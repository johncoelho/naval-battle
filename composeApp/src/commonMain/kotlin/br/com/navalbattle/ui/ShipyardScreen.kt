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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Livery
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.ShipClass

@Composable
fun ShipyardScreen(state: AppState) {
    val profile = state.profile
    var preview by remember { mutableStateOf(Livery.of(profile.equipped)) }
    var notice by remember { mutableStateOf<String?>(null) }

    val owned = profile.owns(preview.id)
    val equipped = profile.equipped == preview.id
    val canAfford = profile.credits >= preview.price

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar("ESTALEIRO", "◆ ${profile.credits}")
        Gap(14)

        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.abyss)
                .border(1.dp, Naval.lineSoft)
                .padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            listOf(ShipClass.CARRIER, ShipClass.CRUISER, ShipClass.DESTROYER).forEach { type ->
                Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                    val length = size.width * (type.size / 5f) * 0.94f
                    drawShip(
                        type = type,
                        center = Offset(size.width / 2f, size.height / 2f),
                        lengthPx = length,
                        thicknessPx = length / type.size,
                        vertical = false,
                        livery = preview
                    )
                }
            }
        }

        Gap(10)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(preview.name.uppercase(), style = NavalType.title, color = Naval.ink)
                HudLabel(
                    when {
                        equipped -> "EM SERVIÇO NA SUA FROTA"
                        owned -> "NO SEU ESTALEIRO"
                        else -> "CUSTA ${preview.priceLabel} · VOCÊ TEM ◆ ${profile.credits}"
                    },
                    if (equipped) Naval.greenBright else Naval.muted
                )
            }
            if (!owned) Text(preview.priceLabel, style = NavalType.mono, color = Naval.amberStrong)
        }

        Gap(14)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(Livery.all) { livery ->
                LiveryCard(
                    livery = livery,
                    selected = preview.id == livery.id,
                    owned = profile.owns(livery.id),
                    equipped = profile.equipped == livery.id
                ) {
                    preview = livery
                    notice = null
                }
            }
        }

        notice?.let {
            Gap(8)
            HudLabel(it, Naval.amberStrong)
        }

        Gap(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Voltar", modifier = Modifier.weight(1f)) {
                state.screen = Screen.MENU
            }
            PrimaryButton(
                when {
                    equipped -> "Em serviço"
                    owned -> "Equipar"
                    else -> "Comprar"
                },
                enabled = !equipped && (owned || canAfford),
                modifier = Modifier.weight(1f)
            ) {
                if (owned) {
                    profile.equip(preview.id)
                    notice = "${preview.name} entrou em serviço"
                } else if (profile.buy(preview.id, preview.price)) {
                    profile.equip(preview.id)
                    notice = "${preview.name} construída e em serviço"
                }
            }
        }
        if (!owned && !canAfford) {
            Gap(6)
            HudLabel(
                "FALTAM ◆ ${preview.price - profile.credits} — GANHE CRÉDITOS EM COMBATE",
                Naval.danger
            )
        }
    }
}

@Composable
private fun LiveryCard(
    livery: Livery,
    selected: Boolean,
    owned: Boolean,
    equipped: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .background(Naval.surface)
            .border(1.dp, if (selected) Naval.amber else Naval.line)
            .clickable(onClick = onClick)
    ) {
        Box {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(Naval.abyss)
                    .padding(horizontal = 10.dp)
            ) {
                drawShip(
                    type = ShipClass.BATTLESHIP,
                    center = Offset(size.width / 2f, size.height / 2f),
                    lengthPx = size.width,
                    thicknessPx = size.width / 4f,
                    vertical = false,
                    livery = livery,
                    alpha = if (owned) 1f else 0.45f
                )
            }
            if (equipped) {
                HudLabel(
                    "EM SERVIÇO",
                    Naval.greenBright,
                    Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                livery.name.uppercase(),
                style = NavalType.monoSmall,
                color = if (selected) Naval.amberStrong else Naval.ink
            )
            Spacer(Modifier.height(3.dp))
            Text(
                if (owned) "CONQUISTADA" else livery.priceLabel,
                style = NavalType.monoSmall,
                color = if (owned) Naval.muted else Naval.amberStrong
            )
        }
    }
}
