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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawAbilityIcon
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Etapa "Escolha o modo", que abre depois de escolher como jogar (vs. IA, 2 jogadores,
 * rede local, convite de amigo) — antes o Clássico/Tático ficava no deque, antes de
 * saber como se ia jogar, e valia até onde não mandava (quem entra na sala joga no modo
 * de quem a abriu). O último modo usado vem marcado; tocar num cartão já começa.
 */
@Composable
fun ModePickerSheet(state: AppState) {
    val target = state.modePick ?: return
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.86f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.modePick = null }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            HudLabel(t(target.titleKey).uppercase(), Naval.muted)
            Gap(4)
            Text(t(K.MODE_PICK_TITLE), style = NavalType.title, color = Naval.ink)
            if (target == ModePick.LAN) {
                Gap(4)
                HudLabel(t(K.MODE_PICK_LAN_HINT), Naval.muted)
            }
            // convite de amigo: Casual ou Ranqueada (só enquanto o servidor liberar, no beta)
            if (target is ModePick.Friend && state.friendRankedAllowed) {
                Gap(14)
                HudLabel(t(K.MODE_PICK_FRIEND_TYPE).uppercase(), Naval.muted)
                Gap(6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(t(K.ONLINE_MODE_CASUAL), selected = !state.friendRanked, modifier = Modifier.weight(1f)) {
                        state.friendRanked = false
                    }
                    ModeChip(t(K.ONLINE_MODE_RANKED), selected = state.friendRanked, modifier = Modifier.weight(1f)) {
                        state.friendRanked = true
                    }
                }
                if (state.friendRanked) {
                    Gap(6)
                    HudLabel(t(K.MODE_PICK_RANKED_BETA), Naval.amberStrong)
                }
            }
            Gap(16)
            ModeCard(
                mode = GameMode.CLASSIC,
                last = state.mode == GameMode.CLASSIC,
                title = t(K.MODE_CLASSIC),
                body = t(K.MODE_PICK_CLASSIC_BODY)
            ) { state.confirmMode(GameMode.CLASSIC) }
            Gap(10)
            ModeCard(
                mode = GameMode.TACTICAL,
                last = state.mode == GameMode.TACTICAL,
                title = t(K.MODE_TACTICAL),
                body = t(K.MODE_PICK_TACTICAL_BODY)
            ) { state.confirmMode(GameMode.TACTICAL) }
            Gap(14)
            SecondaryButton(t(K.MODE_PICK_BACK)) { state.modePick = null }
        }
    }
}

@Composable
private fun ModeCard(mode: GameMode, last: Boolean, title: String, body: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (last) Naval.amber.copy(alpha = 0.08f) else Naval.surface)
            .border(1.dp, if (last) Naval.amber else Naval.line)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = NavalType.button, color = if (last) Naval.amberStrong else Naval.ink, modifier = Modifier.weight(1f))
            if (last) HudLabel(t(K.MODE_PICK_LAST).uppercase(), Naval.amberStrong)
        }
        Gap(4)
        Text(body, style = NavalType.body, color = Naval.inkSoft)
        Gap(10)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (mode == GameMode.TACTICAL) {
                listOf(Ability.SONAR_PING, Ability.AIR_RECON, Ability.DOUBLE_BARRAGE, Ability.SMOKE).forEach { ability ->
                    Box(Modifier.size(30.dp).border(1.dp, Naval.lineSoft), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(22.dp)) {
                            drawAbilityIcon(ability, Offset(size.width / 2f, size.height / 2f), size.minDimension, Naval.amberStrong)
                        }
                    }
                }
            } else {
                // três tiros na carta: água, acerto, acerto — a regra do "acertou, atira de novo"
                listOf(false, true, true).forEach { hit ->
                    Box(Modifier.size(30.dp).border(1.dp, Naval.lineSoft), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(22.dp)) {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            if (hit) {
                                drawCircle(Naval.danger.copy(alpha = 0.35f), size.minDimension * 0.45f, c)
                                drawCircle(Naval.amberStrong, size.minDimension * 0.18f, c)
                            } else {
                                drawCircle(Naval.inkSoft.copy(alpha = 0.6f), size.minDimension * 0.18f, c, style = Stroke(2f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** De onde a escolha de modo foi aberta — decide o que acontece depois de escolher. */
sealed class ModePick(val titleKey: K) {
    data object Ai : ModePick(K.MENU_TILE_AI)
    data object Local : ModePick(K.MENU_TILE_LOCAL)
    data object LAN : ModePick(K.MENU_TILE_LAN)
    data class Friend(val friendId: String) : ModePick(K.FRIENDS_INVITE)
}
