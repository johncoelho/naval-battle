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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Livery
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.ShipClass
import kotlinx.coroutines.launch

private enum class Aisle(val label: String) { FLEETS("Cascos"), CAMOS("Camuflagens") }

/**
 * Loja do jogo. Tudo é pago com os créditos ganhos em combate — dinheiro de verdade
 * só entra quando o jogo for publicado, e aí como outra forma de obter créditos.
 */
@Composable
fun StoreScreen(state: AppState) {
    val profile = state.profile
    var aisle by remember { mutableStateOf(Aisle.FLEETS) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // toda compra sobe para a conta, quando existe uma conectada
    fun sync() = scope.launch { state.pushQuietly() }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar("LOJA DO ARSENAL", "◆ ${profile.credits}")
        Gap(12)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Aisle.entries.forEach { option ->
                ModeChip(
                    label = option.label,
                    selected = aisle == option,
                    modifier = Modifier.weight(1f)
                ) { aisle = option; notice = null }
            }
        }
        Gap(6)
        HudLabel(
            if (aisle == Aisle.FLEETS) {
                "MUDAM A SILHUETA DAS CINCO EMBARCAÇÕES"
            } else {
                "MUDAM A PINTURA E O PADRÃO DE CAMUFLAGEM"
            },
            Naval.muted
        )

        Gap(12)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (aisle == Aisle.FLEETS) {
                FleetLine.all.forEach { line ->
                    StoreRow(
                        title = line.name,
                        subtitle = line.description,
                        price = line.price,
                        owned = profile.ownsFleet(line.id),
                        equipped = profile.equippedFleet == line.id,
                        credits = profile.credits,
                        preview = Skin(state.skin.livery, line),
                        onBuy = {
                            if (profile.buyFleet(line.id, line.price)) {
                                profile.equipFleet(line.id)
                                sync()
                                notice = "${line.name} entrou em serviço"
                            } else {
                                notice = "Faltam ◆ ${line.price - profile.credits} para a ${line.name}"
                            }
                        },
                        onEquip = {
                            profile.equipFleet(line.id)
                            sync()
                            notice = "${line.name} entrou em serviço"
                        }
                    )
                }
            } else {
                Livery.all.forEach { livery ->
                    StoreRow(
                        title = livery.name,
                        subtitle = camoLabel(livery),
                        price = livery.price,
                        owned = profile.owns(livery.id),
                        equipped = profile.equipped == livery.id,
                        credits = profile.credits,
                        preview = Skin(livery, state.skin.fleet),
                        onBuy = {
                            if (profile.buy(livery.id, livery.price)) {
                                profile.equip(livery.id)
                                sync()
                                notice = "${livery.name} aplicada na frota"
                            } else {
                                notice = "Faltam ◆ ${livery.price - profile.credits} para a ${livery.name}"
                            }
                        },
                        onEquip = {
                            profile.equip(livery.id)
                            sync()
                            notice = "${livery.name} aplicada na frota"
                        }
                    )
                }
            }
            Gap(4)
            HudLabel("CRÉDITOS SE GANHAM EM COMBATE · PAGAMENTO REAL ENTRA NA PUBLICAÇÃO", Naval.muted)
            Gap(8)
        }

        notice?.let {
            Gap(8)
            HudLabel(it, Naval.amberStrong)
        }

        Gap(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Voltar", modifier = Modifier.weight(1f)) { state.screen = Screen.MENU }
            SecondaryButton("Estaleiro", modifier = Modifier.weight(1f)) { state.screen = Screen.SHIPYARD }
        }
    }
}

private fun camoLabel(livery: Livery): String = when (livery.camo) {
    br.com.navalbattle.design.Camo.LISA -> "Pintura lisa"
    br.com.navalbattle.design.Camo.DAZZLE -> "Faixas dazzle de alto contraste"
    br.com.navalbattle.design.Camo.ESTILHACO -> "Manchas angulares de estilhaço"
    br.com.navalbattle.design.Camo.LISTRAS -> "Faixas de linha d'água"
    br.com.navalbattle.design.Camo.DIGITAL -> "Retículo digital moderno"
}

@Composable
private fun StoreRow(
    title: String,
    subtitle: String,
    price: Int,
    owned: Boolean,
    equipped: Boolean,
    credits: Int,
    preview: Skin,
    onBuy: () -> Unit,
    onEquip: () -> Unit
) {
    val affordable = credits >= price
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, if (equipped) Naval.amber else Naval.line)
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(Naval.abyss)
                .padding(horizontal = 12.dp)
        ) {
            drawShip(
                type = ShipClass.BATTLESHIP,
                center = Offset(size.width / 2f, size.height / 2f),
                lengthPx = size.width,
                thicknessPx = size.width / 4.2f,
                vertical = false,
                skin = preview,
                alpha = if (owned) 1f else 0.55f
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = NavalType.mono, color = Naval.ink)
                Spacer(Modifier.height(3.dp))
                HudLabel(subtitle, Naval.muted)
            }
            Box(
                Modifier
                    .border(
                        1.dp,
                        when {
                            equipped -> Naval.green
                            owned -> Naval.line
                            affordable -> Naval.amber
                            else -> Naval.lineSoft
                        }
                    )
                    .clickable(enabled = !equipped && (owned || affordable)) {
                        if (owned) onEquip() else onBuy()
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text(
                    when {
                        equipped -> "EM USO"
                        owned -> "USAR"
                        else -> "◆ $price"
                    },
                    style = NavalType.monoSmall,
                    color = when {
                        equipped -> Naval.greenBright
                        owned -> Naval.inkSoft
                        affordable -> Naval.amberStrong
                        else -> Naval.muted
                    }
                )
            }
        }
    }
}
