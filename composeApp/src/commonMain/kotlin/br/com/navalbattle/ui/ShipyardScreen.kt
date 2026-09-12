package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
    var preview by remember { mutableStateOf(state.livery) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar("ESTALEIRO", "◆ 1.240")
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
        Text(preview.name.uppercase(), style = NavalType.title, color = Naval.ink)
        HudLabel("REPINTA AS ${ShipClass.fleet.size} EMBARCAÇÕES")

        Gap(14)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(Livery.all) { livery ->
                LiveryCard(livery, selected = preview.id == livery.id) { preview = livery }
            }
        }

        Gap(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Voltar", modifier = Modifier.weight(1f)) {
                state.screen = Screen.MENU
            }
            PrimaryButton(
                if (preview.owned) "Equipar" else "Comprar",
                enabled = preview.owned,
                modifier = Modifier.weight(1f)
            ) {
                state.livery = preview
                state.screen = Screen.MENU
            }
        }
        if (!preview.owned) {
            Gap(6)
            HudLabel("LOJA ENTRA COM A INTEGRAÇÃO DE PAGAMENTO", Naval.muted)
        }
    }
}

@Composable
private fun LiveryCard(livery: Livery, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .background(Naval.surface)
            .border(1.dp, if (selected) Naval.amber else Naval.line)
            .clickable(onClick = onClick)
    ) {
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
                livery = livery
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                livery.name.uppercase(),
                style = NavalType.monoSmall,
                color = if (selected) Naval.amberStrong else Naval.ink
            )
            Spacer(Modifier.height(3.dp))
            Text(
                livery.priceLabel,
                style = NavalType.monoSmall,
                color = if (livery.owned) Naval.muted else Naval.amberStrong
            )
        }
    }
}
