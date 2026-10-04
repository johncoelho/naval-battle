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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Camo
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.drawAbilityIcon
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

/** Compra esperando o "Comprar" do cartão de confirmação. */
private class PendingBuy(val name: String, val price: Int, val confirm: () -> Unit)

/**
 * Loja do jogo. Tudo é pago com os créditos ganhos em combate — dinheiro de verdade
 * só entra quando o jogo for publicado, e aí como outra forma de obter créditos.
 * Toda compra passa por um cartão de confirmação; "Usar" o que já é seu não pergunta.
 */
@Composable
fun StoreScreen(state: AppState) {
    val profile = state.profile
    var aisle by remember { mutableStateOf(Aisle.entries.getOrElse(state.storeAisle) { Aisle.FLEETS }) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingBuy?>(null) }

    // item fora do alcance: explica como chegar lá em vez de não fazer nada
    val locked: (Int) -> Unit = { price -> notice = t(K.STORE_EARN_HINT, price - profile.credits) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            ScreenTopBar(t(K.STORE_TITLE), "◆ ${profile.credits}")
            Gap(8)

            Row(Modifier.fillMaxWidth()) {
                Aisle.entries.forEach { option ->
                    UnderlineTab(
                        label = t(option.key),
                        selected = aisle == option,
                        modifier = Modifier.weight(1f)
                    ) { aisle = option; notice = null }
                }
            }
            Gap(10)
            Text(
                when (aisle) {
                    Aisle.FLEETS -> t(K.STORE_HULLS_SUB)
                    Aisle.CAMOS -> t(K.STORE_CAMOS_SUB)
                    Aisle.ABILITIES -> t(K.STORE_ABILITIES_SUB)
                },
                style = NavalType.body,
                color = Naval.inkSoft
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
                        val price = abilityPrice(ability)
                        AbilityStoreRow(
                            ability = ability,
                            owned = profile.chargesOf(ability),
                            price = price,
                            credits = profile.credits,
                            onLocked = { locked(price) },
                            onBuy = {
                                pending = PendingBuy(ability.label, price) {
                                    if (profile.buyAbilityCharge(ability, price)) {
                                        notice = t(K.STORE_ABILITY_BOUGHT, ability.label)
                                    }
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
                            onLocked = { locked(line.price) },
                            onBuy = {
                                pending = PendingBuy(line.name, line.price) {
                                    if (profile.buyFleet(line.id, line.price)) {
                                        profile.equipFleet(line.id)
                                        notice = t(K.STORE_COMMISSIONED, line.name)
                                    }
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
                            subtitle = paintDescription(paint),
                            price = paint.price,
                            owned = profile.owns(paint.id),
                            equipped = profile.equipped == paint.id,
                            credits = profile.credits,
                            preview = Skin(paint, state.skin.fleet),
                            onLocked = { locked(paint.price) },
                            onBuy = {
                                pending = PendingBuy(paint.name, paint.price) {
                                    if (profile.buy(paint.id, paint.price)) {
                                        profile.equip(paint.id)
                                        notice = t(K.STORE_PAINTED, paint.name)
                                    }
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

        pending?.let { buy ->
            PurchaseConfirm(
                buy = buy,
                credits = profile.credits,
                onConfirm = { pending = null; buy.confirm() },
                onCancel = { pending = null }
            )
        }
    }
}

/**
 * Cartão de confirmação: item, preço, saldo e saldo depois. Tocar fora cancela,
 * igual ao botão — nenhuma compra acontece com um toque só.
 */
@Composable
private fun PurchaseConfirm(buy: PendingBuy, credits: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                // toques dentro do cartão não contam como "fora"
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            HudLabel(t(K.STORE_CONFIRM_TITLE), Naval.muted)
            Gap(8)
            Text(buy.name.uppercase(), style = NavalType.title, color = Naval.ink)
            Gap(14)
            ConfirmLine(t(K.STORE_CONFIRM_PRICE), "◆ ${buy.price}", Naval.amberStrong)
            ConfirmLine(t(K.STORE_CONFIRM_BALANCE), "◆ $credits", Naval.inkSoft)
            ConfirmLine(t(K.STORE_CONFIRM_AFTER), "◆ ${credits - buy.price}", Naval.ink)
            Gap(18)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.CANCEL), modifier = Modifier.weight(1f), onClick = onCancel)
                PrimaryButton(t(K.STORE_CONFIRM_BUY), modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun ConfirmLine(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudLabel(label, Naval.muted)
        Text(value, style = NavalType.mono, color = valueColor)
    }
}

/**
 * Selo do botão de preço: sem saldo, o item fica a meia opacidade com cadeado e
 * "faltam ◆ X"; tocar explica como ganhar créditos.
 */
@Composable
private fun PriceTag(price: Int, credits: Int, onBuy: () -> Unit, onLocked: () -> Unit) {
    val affordable = credits >= price
    Row(
        Modifier
            .border(1.dp, if (affordable) Naval.amber else Naval.lineSoft)
            .clickable { if (affordable) onBuy() else onLocked() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!affordable) {
            Canvas(Modifier.size(11.dp)) { drawLock(Naval.muted) }
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "◆ $price",
                style = NavalType.monoSmall,
                color = if (affordable) Naval.amberStrong else Naval.muted
            )
            if (!affordable) {
                HudLabel(t(K.STORE_LOCKED, price - credits), Naval.muted)
            }
        }
    }
}

/** Cadeado simples: arco em cima, corpo retangular embaixo. */
private fun DrawScope.drawLock(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.14f
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.22f, h * 0.05f),
        size = Size(w * 0.56f, h * 0.6f),
        style = Stroke(stroke)
    )
    drawLine(color, Offset(w * 0.22f, h * 0.35f), Offset(w * 0.22f, h * 0.5f), stroke)
    drawLine(color, Offset(w * 0.78f, h * 0.35f), Offset(w * 0.78f, h * 0.5f), stroke)
    drawRect(color, topLeft = Offset(w * 0.08f, h * 0.45f), size = Size(w * 0.84f, h * 0.55f))
}

/**
 * Cartucho avulso de habilidade: não tem "equipar", só "comprar mais um" — o estoque
 * atual aparece sempre, mesmo zerado, para o comandante saber o que tem.
 */
@Composable
private fun AbilityStoreRow(
    ability: Ability,
    owned: Int,
    price: Int,
    credits: Int,
    onBuy: () -> Unit,
    onLocked: () -> Unit
) {
    val affordable = credits >= price
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, Naval.line)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f).alpha(if (affordable || owned > 0) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Naval.surface2)
                    .border(1.dp, Naval.line)
                    .padding(12.dp)
            ) {
                Canvas(Modifier.size(22.dp)) {
                    drawAbilityIcon(ability, center = Offset(size.width / 2f, size.height / 2f), size = size.minDimension, color = Naval.greenBright)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(ability.label.uppercase(), style = NavalType.mono, color = Naval.ink)
                Spacer(Modifier.height(3.dp))
                HudLabel(ability.description, Naval.muted)
                Spacer(Modifier.height(3.dp))
                HudLabel(t(K.STORE_ABILITY_OWNED, owned.toString()), if (owned > 0) Naval.amberStrong else Naval.muted)
            }
        }
        Spacer(Modifier.width(10.dp))
        PriceTag(price, credits, onBuy, onLocked)
    }
}

/** Descrição curta de cada pintura; as de padrão mantêm o nome do padrão. */
private fun paintDescription(paint: Paint): String = t(
    when (paint.camo) {
        Camo.DAZZLE -> K.CAMO_DAZZLE
        Camo.ESTILHACO -> K.CAMO_SPLINTER
        Camo.LISTRAS -> K.CAMO_STRIPES
        Camo.DIGITAL -> K.CAMO_DIGITAL
        Camo.LISA -> when (paint.id) {
            Paint.STANDARD.id -> K.PAINT_STD_DESC
            Paint.BRAZIL.id -> K.PAINT_BR_DESC
            Paint.JAPAN.id -> K.PAINT_JP_DESC
            Paint.USA.id -> K.PAINT_US_DESC
            Paint.UK.id -> K.PAINT_UK_DESC
            Paint.PORTUGAL.id -> K.PAINT_PT_DESC
            Paint.ARCTIC.id -> K.PAINT_ARC_DESC
            else -> K.CAMO_PLAIN
        }
    }
)

/** Ordem do carrossel de prévia; começa no encouraçado, o navio que a loja sempre mostrou. */
private val previewClasses = ShipClass.entries

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
    onEquip: () -> Unit,
    onLocked: () -> Unit
) {
    val affordable = credits >= price
    val dim = !owned && !affordable
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, if (equipped) Naval.amber else Naval.line)
    ) {
        ShipCarousel(preview, alpha = if (owned) 1f else if (dim) 0.4f else 0.7f)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).alpha(if (dim) 0.5f else 1f)) {
                Text(title.uppercase(), style = NavalType.mono, color = Naval.ink)
                Spacer(Modifier.height(3.dp))
                HudLabel(subtitle, Naval.muted)
            }
            if (owned) {
                Box(
                    Modifier
                        .border(1.dp, if (equipped) Naval.green else Naval.line)
                        .clickable(enabled = !equipped, onClick = onEquip)
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Text(
                        t(if (equipped) K.STORE_IN_USE else K.STORE_USE).uppercase(),
                        style = NavalType.monoSmall,
                        color = if (equipped) Naval.greenBright else Naval.inkSoft
                    )
                }
            } else {
                PriceTag(price, credits, onBuy, onLocked)
            }
        }
    }
}

/**
 * Prévia deslizante das cinco classes com a pintura/casco do item, com pontos
 * de posição embaixo. Abre no encouraçado.
 */
@Composable
private fun ShipCarousel(skin: Skin, alpha: Float) {
    val pager = rememberPagerState(initialPage = previewClasses.indexOf(ShipClass.BATTLESHIP)) { previewClasses.size }
    Column(Modifier.fillMaxWidth().background(Naval.abyss)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(60.dp)) { page ->
            val type = previewClasses[page]
            Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                val cell = minOf(size.width * 0.94f / type.size, size.height * 1.15f)
                drawShip(
                    type = type,
                    center = Offset(size.width / 2f, size.height / 2f),
                    lengthPx = cell * type.size,
                    thicknessPx = cell,
                    vertical = false,
                    skin = skin,
                    alpha = alpha
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            previewClasses.indices.forEach { i ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pager.currentPage) 6.dp else 4.dp)
                        .background(if (i == pager.currentPage) Naval.amberStrong else Naval.line)
                )
            }
        }
    }
}
