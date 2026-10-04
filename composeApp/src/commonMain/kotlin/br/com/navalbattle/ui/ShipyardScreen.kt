package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.ShipClass

/**
 * Bancada de montagem: combina o que já foi conquistado — linha de casco e
 * camuflagem. Comprar é assunto da loja; aqui só se monta a frota.
 */
@Composable
fun ShipyardScreen(state: AppState) {
    val profile = state.profile
    var fleet by remember { mutableStateOf(FleetLine.of(profile.equippedFleet)) }
    var paint by remember { mutableStateOf(Paint.of(profile.equipped)) }
    val preview = Skin(paint, fleet)
    val inService = profile.equippedFleet == fleet.id && profile.equipped == paint.id

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar(t(K.MENU_SHIPYARD), "◆ ${profile.credits}")
        Gap(12)

        // as três silhuetas mais características, na combinação escolhida
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.abyss)
                .border(1.dp, Naval.lineSoft)
                .padding(vertical = 14.dp, horizontal = 12.dp)
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
                        skin = preview
                    )
                }
            }
        }

        Gap(10)
        Text(
            "${fleet.name.uppercase()} · ${paint.name.uppercase()}",
            style = NavalType.title,
            color = Naval.ink
        )
        HudLabel(fleet.description, Naval.muted)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(16)
            HudLabel(t(K.SHIPYARD_HULL))
            Gap(8)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // só o que é seu: comprar é assunto da loja, e o cartão do fim leva até ela
                FleetLine.all.filter { profile.ownsFleet(it.id) }.forEach { line ->
                    OptionCard(
                        label = line.name,
                        note = t(K.SHIPYARD_YOURS),
                        selected = fleet.id == line.id,
                        skin = Skin(paint, line),
                        onClick = { fleet = line }
                    )
                }
                val missingHulls = FleetLine.all.count { !profile.ownsFleet(it.id) }
                if (missingHulls > 0) {
                    MoreInStoreCard(t(K.SHIPYARD_MORE_HULLS, missingHulls)) { state.openStore(0) }
                }
            }

            Gap(18)
            HudLabel(t(K.SHIPYARD_CAMO))
            Gap(8)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Paint.all.filter { profile.owns(it.id) }.forEach { option ->
                    OptionCard(
                        label = option.name,
                        note = t(K.SHIPYARD_YOURS),
                        selected = paint.id == option.id,
                        skin = Skin(option, fleet),
                        onClick = { paint = option }
                    )
                }
                val missingCamos = Paint.all.count { !profile.owns(it.id) }
                if (missingCamos > 0) {
                    MoreInStoreCard(t(K.SHIPYARD_MORE_CAMOS, missingCamos)) { state.openStore(1) }
                }
            }
            Gap(12)
            HudLabel(t(K.SHIPYARD_STORE_HINT), Naval.muted)
            Gap(10)
        }

        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(t(K.BACK), modifier = Modifier.weight(1f)) { state.screen = Screen.MENU }
            SecondaryButton(t(K.MENU_TAB_STORE), modifier = Modifier.weight(1f)) { state.openStore(0) }
        }
        Gap(8)
        PrimaryButton(
            if (inService) t(K.SHIPYARD_IN_SERVICE) else t(K.SHIPYARD_COMMISSION),
            enabled = !inService
        ) {
            profile.equipFleet(fleet.id)
            profile.equip(paint.id)
        }
    }
}

@Composable
private fun OptionCard(
    label: String,
    note: String,
    selected: Boolean,
    skin: Skin,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(132.dp)
            .background(Naval.surface)
            .border(1.dp, if (selected) Naval.amber else Naval.line)
            .clickable(onClick = onClick)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Naval.abyss)
                .padding(horizontal = 8.dp)
        ) {
            drawShip(
                type = ShipClass.BATTLESHIP,
                center = Offset(size.width / 2f, size.height / 2f),
                lengthPx = size.width,
                thicknessPx = size.width / 4f,
                vertical = false,
                skin = skin
            )
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                label.uppercase(),
                style = NavalType.monoSmall,
                color = if (selected) Naval.amberStrong else Naval.ink,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    note,
                    style = NavalType.monoSmall,
                    color = Naval.muted
                )
            }
        }
    }
}

/** Último cartão da fileira: quantos itens faltam e um atalho direto para a aba certa da loja. */
@Composable
private fun MoreInStoreCard(note: String, onClick: () -> Unit) {
    Column(
        Modifier
            .width(132.dp)
            .height(IntrinsicSize.Min)
            .background(Naval.surface)
            .border(1.dp, Naval.amber)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(t(K.SHIPYARD_MORE_TITLE).uppercase(), style = NavalType.monoSmall, color = Naval.amberStrong)
        Spacer(Modifier.height(6.dp))
        Text(note, style = NavalType.monoSmall, color = Naval.inkSoft)
    }
}
