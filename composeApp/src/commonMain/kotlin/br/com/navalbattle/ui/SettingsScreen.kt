package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.appVersionLabel
import br.com.navalbattle.data.shareStoreListing
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.QuickOfferKind
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import androidx.compose.material3.Text

/**
 * Preferências do jogo, num lugar só — antes o idioma vivia duplicado no Perfil,
 * o que confundia sobre onde mexer. Áudio e idioma aqui; opções de tabuleiro (cor
 * do oceano, espessura da grade) e notificações ficam para uma rodada futura, pois
 * exigem preferências novas correndo até o desenho do tabuleiro.
 */
@Composable
fun SettingsScreen(state: AppState) {
    val profile = state.profile

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar(t(K.SETTINGS_TITLE), "")
        Gap(22)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            HudLabel(t(K.SETTINGS_AUDIO))
            Gap(10)
            SettingsToggleRow(
                label = t(K.SETTINGS_MUSIC),
                on = state.musicOn
            ) { state.musicOn = !state.musicOn }
            Gap(8)
            SettingsToggleRow(
                label = t(K.SETTINGS_SFX),
                on = profile.sfxOn
            ) { profile.setSfx(!profile.sfxOn) }
            if (profile.signedIn) {
                Gap(8)
                SettingsToggleRow(
                    label = t(K.SETTINGS_REMINDER),
                    on = profile.reminderOn
                ) { state.setReminder(!profile.reminderOn) }
                HudLabel(t(K.SETTINGS_REMINDER_SUB), Naval.muted)
            }

            Gap(26)
            HudLabel(t(K.SETTINGS_ONLINE))
            Gap(10)
            if (!profile.signedIn) {
                // sem conta não há partida online — a opção nem aparece, só o porquê
                HudLabel(t(K.SETTINGS_QUICK_OFFER_SIGN_IN), Naval.muted)
            } else {
                SettingsToggleRow(
                    label = t(K.SETTINGS_QUICK_OFFER),
                    on = profile.quickOfferEnabled
                ) { profile.setQuickOffer(!profile.quickOfferEnabled) }
                Gap(6)
                HudLabel(t(K.SETTINGS_QUICK_OFFER_SUB), Naval.muted)
                if (profile.quickOfferEnabled) {
                    // ranqueada só depois de aceitar a temporada corrente — mesma trava
                    // do seletor da tela Online
                    val rankedOpen = !state.seasonPopupNeeded
                    val kind = if (!rankedOpen) QuickOfferKind.CASUAL else profile.quickOfferKind
                    Gap(10)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = t(K.ONLINE_MODE_CASUAL),
                            selected = kind == QuickOfferKind.CASUAL,
                            modifier = Modifier.weight(1f)
                        ) { profile.chooseQuickOfferKind(QuickOfferKind.CASUAL) }
                        ModeChip(
                            label = t(K.ONLINE_MODE_RANKED),
                            selected = kind == QuickOfferKind.RANKED,
                            enabled = rankedOpen,
                            modifier = Modifier.weight(1f)
                        ) { profile.chooseQuickOfferKind(QuickOfferKind.RANKED) }
                        ModeChip(
                            label = t(K.SETTINGS_QUICK_OFFER_BOTH),
                            selected = kind == QuickOfferKind.BOTH,
                            enabled = rankedOpen,
                            modifier = Modifier.weight(1f)
                        ) { profile.chooseQuickOfferKind(QuickOfferKind.BOTH) }
                    }
                    if (!rankedOpen) {
                        Gap(6)
                        HudLabel(t(K.ONLINE_MODE_RANKED_LOCKED), Naval.muted)
                    }
                }
            }

            Gap(26)
            HudLabel(t(K.SETTINGS_LANGUAGE))
            Gap(10)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lang.entries.forEach { option ->
                    ModeChip(
                        label = option.label,
                        selected = I18n.lang == option,
                        modifier = Modifier.weight(1f)
                    ) {
                        I18n.lang = option
                        profile.setLang(option.code)
                    }
                }
            }

            Gap(26)
            HudLabel(t(K.SETTINGS_ABOUT))
            Gap(10)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudLabel(t(K.SETTINGS_VERSION), Naval.inkSoft)
                HudLabel(appVersionLabel, Naval.amberStrong)
            }
            Gap(10)
            SecondaryButton(t(K.SETTINGS_RELEASE_NOTES)) { state.screen = Screen.RELEASE_NOTES }
            Gap(8)
            SecondaryButton(t(K.SETTINGS_SHARE_STORE), t(K.SETTINGS_SHARE_STORE_SUB)) { shareStoreListing() }
            Gap(8)
            SecondaryButton(t(K.SETTINGS_FEEDBACK), t(K.SETTINGS_FEEDBACK_SUB)) { state.openFeedback(Screen.SETTINGS) }
        }

        Gap(14)
        SecondaryButton(t(K.BACK)) { state.screen = Screen.MENU }
    }
}

@Composable
private fun SettingsToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.lineSoft)
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NavalType.mono, color = Naval.ink)
        Box(
            Modifier
                .background(if (on) Naval.surface3 else Naval.surface)
                .border(1.dp, if (on) Naval.amber else Naval.line)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            HudLabel(
                if (on) t(K.SETTINGS_ON).uppercase() else t(K.SETTINGS_OFF).uppercase(),
                if (on) Naval.greenBright else Naval.muted
            )
        }
    }
}
