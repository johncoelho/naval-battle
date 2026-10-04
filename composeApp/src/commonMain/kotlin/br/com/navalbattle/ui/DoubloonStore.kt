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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawCompassRose
import br.com.navalbattle.design.drawDoubloon
import br.com.navalbattle.design.drawDoubloonPack
import br.com.navalbattle.game.DoubloonPack
import br.com.navalbattle.game.brl
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Aba Dobrões da loja. Hoje a compra é simulada para beta testers (limite diário
 * controlado no servidor, nenhum cartão real); o aviso de teste fica sempre visível.
 * Cada pacote tem arte própria — bolsa, baú, cofre, tesouro — para dizer de longe o
 * que se está comprando.
 */
@Composable
fun DoubloonAisle(state: AppState, onPick: (DoubloonPack) -> Unit) {
    if (!state.profile.signedIn) {
        InfoCard {
            Text(t(K.DOUBLOON_SIGN_IN), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            Gap(12)
            PrimaryButton(t(K.DOUBLOON_SIGN_IN_CTA)) { state.openLogin(Screen.STORE) }
        }
        return
    }
    val st = state.betaStore
    if (st == null) {
        HudLabel(t(K.DOUBLOON_LOADING), Naval.muted)
        return
    }
    if (!st.eligible) {
        InfoCard { Text(t(K.DOUBLOON_BETA_ONLY), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center) }
        return
    }

    // aviso de compra de teste + quanto resta hoje
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.amber)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(22.dp)) { drawDoubloon(Offset(size.width / 2f, size.height / 2f), size.minDimension) }
            Spacer(Modifier.width(8.dp))
            Text(t(K.DOUBLOON_TEST_TITLE).uppercase(), style = NavalType.mono, color = Naval.amberStrong)
        }
        Gap(8)
        Text(t(K.DOUBLOON_TEST_BODY, brl(st.limitCents)), style = NavalType.body, color = Naval.ink)
        Gap(12)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudLabel(t(K.DOUBLOON_REMAINING, brl(st.remainingCents)), Naval.amberStrong)
            HudLabel(t(K.DOUBLOON_RESETS, countdown(st.resetsInSeconds)), Naval.muted)
        }
        Gap(6)
        // barra do gasto do dia
        val used = if (st.limitCents == 0) 1f else (st.spentCents.toFloat() / st.limitCents).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(6.dp).background(Naval.lineSoft)) {
            Box(Modifier.fillMaxWidth(used).height(6.dp).background(Naval.amber))
        }
    }

    // vitrine em duas colunas
    DoubloonPack.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { pack ->
                PackCard(
                    pack = pack,
                    locked = pack.priceCents > st.remainingCents,
                    busy = state.betaStoreBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(pack) }
                )
            }
        }
    }

    state.betaStoreNotice?.let { HudLabel(it, Naval.amberStrong) }

    // milhas náuticas também se compram com dobrões
    state.miles?.let { m ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface)
                .border(1.dp, Naval.line)
                .clickable { state.milesPopup = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(32.dp)) { drawCompassRose(Offset(size.width / 2f, size.height / 2f), size.minDimension) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t(K.MILES_TITLE).uppercase(), style = NavalType.mono, color = Naval.ink)
                HudLabel(t(K.MILES_BALANCE, m.miles), Naval.muted)
            }
            CoinLabel(m.packPrice.toString(), style = NavalType.mono)
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
            .border(1.dp, Naval.line)
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

/** Cartão de um pacote: arte grande sobre brilho dourado, nome, dobrões, bônus e preço. */
@Composable
private fun PackCard(pack: DoubloonPack, locked: Boolean, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val best = pack == DoubloonPack.TREASURE
    Column(
        modifier
            .alpha(if (locked) 0.45f else 1f)
            .background(Naval.surface)
            .border(if (best) 2.dp else 1.dp, if (best) Naval.amberStrong else Naval.amber)
            .clickable(enabled = !locked && !busy, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Naval.amber.copy(alpha = 0.35f), Naval.abyss),
                        radius = 220f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(84.dp)) {
                drawDoubloonPack(pack.art, Offset(size.width / 2f, size.height / 2f), size.minDimension)
            }
            // selo de bônus no canto, e a faixa de melhor valor no maior
            if (pack.bonusPercent > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Naval.danger)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(t(K.DOUBLOON_BONUS, pack.bonusPercent), style = NavalType.monoSmall, color = Naval.ink)
                }
            }
            if (best) {
                // embaixo da arte, para não brigar com o selo de bônus do canto
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .background(Naval.amberStrong)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(t(K.DOUBLOON_BEST).uppercase(), style = NavalType.monoSmall, color = Naval.amberInk)
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(t(pack.key).uppercase(), style = NavalType.monoSmall, color = Naval.inkSoft, textAlign = TextAlign.Center)
            Gap(6)
            CoinLabel(formatThousands(pack.doubloons), style = NavalType.title, iconSize = 22.dp)
            Gap(10)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(if (locked) Naval.surface3 else Naval.amber)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (locked) t(K.DOUBLOON_LOCKED) else pack.priceLabel,
                    style = NavalType.mono,
                    color = if (locked) Naval.muted else Naval.amberInk,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Confirmação da compra simulada: pacote, dobrões que entram, preço de mentira e o
 * saldo depois — e de novo o aviso de que nada é cobrado. Tocar fora cancela.
 */
@Composable
fun PackConfirm(pack: DoubloonPack, credits: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
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
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(Modifier.size(96.dp)) {
                drawDoubloonPack(pack.art, Offset(size.width / 2f, size.height / 2f), size.minDimension)
            }
            Gap(8)
            HudLabel(t(K.STORE_CONFIRM_TITLE), Naval.muted)
            Gap(4)
            Text(t(pack.key).uppercase(), style = NavalType.title, color = Naval.ink)
            Gap(14)
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HudLabel(t(K.DOUBLOON_CONFIRM_GET), Naval.muted)
                CoinLabel(formatThousands(pack.doubloons), style = NavalType.mono)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HudLabel(t(K.DOUBLOON_CONFIRM_PRICE), Naval.muted)
                Text(pack.priceLabel, style = NavalType.mono, color = Naval.inkSoft)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                HudLabel(t(K.STORE_CONFIRM_AFTER), Naval.muted)
                CoinLabel(formatThousands(credits + pack.doubloons), Naval.ink, NavalType.mono)
            }
            Gap(10)
            HudLabel(t(K.DOUBLOON_NO_CHARGE), Naval.greenBright)
            Gap(16)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.CANCEL), modifier = Modifier.weight(1f), onClick = onCancel)
                PrimaryButton(t(K.STORE_CONFIRM_BUY), modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

/** 6500 → "6.500" (separador brasileiro, sem depender de formatação de plataforma). */
fun formatThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()
