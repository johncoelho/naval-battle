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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
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
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.ShipClass

private enum class Aisle(val key: K) { FLEETS(K.STORE_HULLS), CAMOS(K.STORE_CAMOS), ABILITIES(K.STORE_ABILITIES) }

/** Preço do cartucho avulso de cada habilidade — mais caro quanto mais decisivo o efeito. */
private fun abilityPrice(ability: Ability): Int = when (ability) {
    Ability.DOUBLE_BARRAGE -> 100
    Ability.AIR_RECON -> 80
    Ability.SONAR_PING -> 60
    Ability.SMOKE -> 50
    Ability.DIVE -> 0
}

/**
 * Loja do jogo. Tudo é pago com os créditos ganhos em combate — dinheiro de verdade
 * só entra quando o jogo for publicado, e aí como outra forma de obter créditos.
 */
@Composable
fun StoreScreen(state: AppState) {
    val profile = state.profile
    var aisle by remember { mutableStateOf(Aisle.FLEETS) }
    var notice by remember { mutableStateOf<String?>(null) }

    // toda compra sobe para a conta, quando existe uma conectada

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ScreenTopBar(t(K.STORE_TITLE), "◆ ${profile.credits}")
        Gap(12)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Aisle.entries.forEach { option ->
                ModeChip(
                    label = t(option.key),
                    selected = aisle == option,
                    modifier = Modifier.weight(1f)
                ) { aisle = option; notice = null }
            }
        }
        Gap(6)
        HudLabel(
            when (aisle) {
                Aisle.FLEETS -> t(K.STORE_HULLS_SUB)
                Aisle.CAMOS -> t(K.STORE_CAMOS_SUB)
                Aisle.ABILITIES -> t(K.STORE_ABILITIES_SUB)
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
            if (aisle == Aisle.ABILITIES) {
                Ability.entries.filter { it.active }.forEach { ability ->
                    AbilityStoreRow(
                        ability = ability,
                        owned = profile.chargesOf(ability),
                        price = abilityPrice(ability),
                        credits = profile.credits,
                        onBuy = {
                            if (profile.buyAbilityCharge(ability, abilityPrice(ability))) {
                                notice = t(K.STORE_ABILITY_BOUGHT, ability.label)
                            } else {
                                notice = t(K.STORE_MISSING, abilityPrice(ability) - profile.credits, ability.label)
                            }
                        }
                    )
                }
            } else if (aisle == Aisle.FLEETS) {
                FleetLine.all.forEach { line ->
                    StoreRow(
                        title = line.name,
                        subtitle = line.description,
                        price = line.price,
                        owned = profile.ownsFleet(line.id),
                        equipped = profile.equippedFleet == line.id,
                        credits = profile.credits,
                        preview = Skin(state.skin.paint, line),
                        onBuy = {
                            if (profile.buyFleet(line.id, line.price)) {
                                profile.equipFleet(line.id)
                                notice = t(K.STORE_COMMISSIONED, line.name)
                            } else {
                                notice = t(K.STORE_MISSING, line.price - profile.credits, line.name)
                            }
                        },
                        onEquip = {
                            profile.equipFleet(line.id)
                            notice = t(K.STORE_COMMISSIONED, line.name)
                        }
                    )
                }
            } else {
                Paint.all.forEach { paint ->
                    StoreRow(
                        title = paint.name,
                        subtitle = camoLabel(paint),
                        price = paint.price,
                        owned = profile.owns(paint.id),
                        equipped = profile.equipped == paint.id,
                        credits = profile.credits,
                        preview = Skin(paint, state.skin.fleet),
                        onBuy = {
                            if (profile.buy(paint.id, paint.price)) {
                                profile.equip(paint.id)
                                notice = t(K.STORE_PAINTED, paint.name)
                            } else {
                                notice = t(K.STORE_MISSING, paint.price - profile.credits, paint.name)
                            }
                        },
                        onEquip = {
                            profile.equip(paint.id)
                            notice = t(K.STORE_PAINTED, paint.name)
                        }
                    )
                }
            }
            Gap(4)
            HudLabel(t(K.STORE_CREDITS_HINT), Naval.muted)
            Gap(8)
        }

        notice?.let {
            Gap(8)
            HudLabel(it, Naval.amberStrong)
        }

        Gap(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(t(K.BACK), modifier = Modifier.weight(1f)) { state.screen = Screen.MENU }
            SecondaryButton(t(K.MENU_SHIPYARD), modifier = Modifier.weight(1f)) { state.screen = Screen.SHIPYARD }
        }
    }
}

/**
 * Cartucho avulso de habilidade: não tem "equipar", só "comprar mais um" — o estoque
 * atual aparece na descrição em vez de virar um selo de "conquistada".
 */
@Composable
private fun AbilityStoreRow(ability: Ability, owned: Int, price: Int, credits: Int, onBuy: () -> Unit) {
    val affordable = credits >= price
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, Naval.line)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .background(Naval.surface2)
                .border(1.dp, Naval.line)
                .padding(12.dp)
        ) {
            Text(ability.icon, style = NavalType.title)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(ability.label.uppercase(), style = NavalType.mono, color = Naval.ink)
            Spacer(Modifier.height(3.dp))
            HudLabel(ability.description, Naval.muted)
            if (owned > 0) {
                Spacer(Modifier.height(3.dp))
                HudLabel(t(K.STORE_ABILITY_OWNED, owned.toString()), Naval.amberStrong)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .border(1.dp, if (affordable) Naval.amber else Naval.lineSoft)
                .clickable(enabled = affordable, onClick = onBuy)
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(
                "◆ $price",
                style = NavalType.monoSmall,
                color = if (affordable) Naval.amberStrong else Naval.muted
            )
        }
    }
}

private fun camoLabel(paint: Paint): String = t(
    when (paint.camo) {
        br.com.navalbattle.design.Camo.LISA -> K.CAMO_PLAIN
        br.com.navalbattle.design.Camo.DAZZLE -> K.CAMO_DAZZLE
        br.com.navalbattle.design.Camo.ESTILHACO -> K.CAMO_SPLINTER
        br.com.navalbattle.design.Camo.LISTRAS -> K.CAMO_STRIPES
        br.com.navalbattle.design.Camo.DIGITAL -> K.CAMO_DIGITAL
    }
)

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
                        equipped -> t(K.STORE_IN_USE).uppercase()
                        owned -> t(K.STORE_USE).uppercase()
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
