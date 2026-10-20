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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.StoreProduct
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawCompassRose
import br.com.navalbattle.design.drawDoubloon
import br.com.navalbattle.design.drawDoubloonPack
import br.com.navalbattle.game.DoubloonPack
import br.com.navalbattle.game.PremioState
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.i18n.tMilesBalance

/**
 * Aba Dobrões da loja (#15, #28). Duas partes independentes: o Prêmio Beta (testador de
 * licença pega 6.500 por dia pelo servidor, sem a Play) e a vitrine paga (pacotes pela
 * Play: para todos com a venda real ligada, e para testador com a vitrine beta ligada).
 * No iPhone a aba mostra só o Prêmio (#30): sem Play, nenhum pacote.
 * Cada pacote tem arte própria: bolsa, baú, cofre, tesouro.
 */
@Composable
fun DoubloonAisle(state: AppState, onPick: (DoubloonPack) -> Unit, onClaim: () -> Unit) {
    if (!state.profile.signedIn) {
        InfoCard {
            Text(t(K.DOUBLOON_SIGN_IN), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            Gap(12)
            PrimaryButton(t(K.DOUBLOON_SIGN_IN_CTA)) { state.openLogin(Screen.STORE) }
        }
        return
    }
    val status = state.dobroesStatus
    if (status == null) {
        HudLabel(t(K.DOUBLOON_LOADING), Naval.muted)
        return
    }
    val premio = state.premioView ?: PremioState.ESCONDIDO
    val vitrine = state.vitrineAberta
    if (premio == PremioState.ESCONDIDO && !vitrine) {
        InfoCard {
            Text(t(K.DOUBLOON_CLOSED), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
        }
    }
    when (premio) {
        PremioState.PEGAR -> PremioCard(onClaim)
        PremioState.JA_PEGOU -> InfoCard {
            Text(
                t(K.PREMIO_TAKEN, countdown(status.premioResetsInSeconds)),
                style = NavalType.body,
                color = Naval.inkSoft,
                textAlign = TextAlign.Center
            )
        }
        PremioState.ESCONDIDO -> Unit
    }
    if (vitrine) {
        if (premio != PremioState.ESCONDIDO) {
            Gap(12)
            HudLabel(t(K.DOUBLOON_PACKS_SECTION), Naval.muted)
            Gap(6)
        }
        if (status.beta) {
            // testador: a vitrine cobra de verdade (exceto na compra de teste da Play)
            Text(t(K.DOUBLOON_BETA_CHARGE), style = NavalType.mono, color = Naval.amberStrong, textAlign = TextAlign.Center)
            Gap(8)
        }
        Vitrine(state, onPick)
    }

    state.dobroesNotice?.let { HudLabel(it, Naval.amberStrong) }

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
                HudLabel(tMilesBalance(m.miles), Naval.muted)
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

/** Prêmio Beta (#28): 6.500 dobrões de graça para o testador, uma vez por dia (Brasília), sem a Play. */
@Composable
private fun PremioCard(onClaim: () -> Unit) {
    val pack = DoubloonPack.TREASURE
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(2.dp, Naval.amberStrong)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.radialGradient(listOf(Naval.amber.copy(alpha = 0.35f), Naval.abyss), radius = 260f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(92.dp)) {
                drawDoubloonPack(pack.art, Offset(size.width / 2f, size.height / 2f), size.minDimension)
            }
        }
        Gap(10)
        Text(t(K.PREMIO_TITLE).uppercase(), style = NavalType.mono, color = Naval.amberStrong, textAlign = TextAlign.Center)
        Gap(6)
        Text(t(K.PREMIO_BODY), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
        Gap(10)
        CoinLabel(formatThousands(pack.doubloons), style = NavalType.title, iconSize = 22.dp)
        Gap(12)
        PrimaryButton(t(K.PREMIO_TAKE), modifier = Modifier.fillMaxWidth()) { onClaim() }
    }
}

/** Vitrine paga: os quatro pacotes com o preço que a Play devolve. */
@Composable
private fun Vitrine(state: AppState, onPick: (DoubloonPack) -> Unit) {
    val products: List<StoreProduct> = state.dobroesProducts
    if (products.isEmpty()) {
        InfoCard {
            Text(t(K.DOUBLOON_UNAVAILABLE), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
        }
        return
    }
    val prices = products.associate { it.productId to it.formattedPrice }
    DoubloonPack.entries.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { pack ->
                PackCard(
                    pack = pack,
                    price = prices[pack.code],
                    busy = state.dobroesBusy,
                    modifier = Modifier.weight(1f),
                    onClick = { onPick(pack) }
                )
            }
        }
    }
}

/** Cartão de um pacote: arte grande sobre brilho dourado, nome, dobrões, bônus e preço da Play. */
@Composable
private fun PackCard(pack: DoubloonPack, price: String?, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val best = pack == DoubloonPack.TREASURE
    Column(
        modifier
            .alpha(if (price == null) 0.45f else 1f)
            .background(Naval.surface)
            .border(if (best) 2.dp else 1.dp, if (best) Naval.amberStrong else Naval.amber)
            .clickable(enabled = price != null && !busy, onClick = onClick),
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
                    .background(if (price == null) Naval.surface3 else Naval.amber)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    price ?: t(K.DOUBLOON_LOCKED),
                    style = NavalType.mono,
                    color = if (price == null) Naval.muted else Naval.amberInk,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 6500 → "6.500" (separador brasileiro, sem depender de formatação de plataforma). */
fun formatThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(".").reversed()
