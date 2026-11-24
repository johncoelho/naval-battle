package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Countries
import br.com.navalbattle.game.CountryChoice
import br.com.navalbattle.game.Profile
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.deviceLanguage
import br.com.navalbattle.i18n.t

/**
 * Campo "Nacionalidade" do Perfil (#124). Nunca escolheu: mostra a sugestão da região do aparelho, ainda não gravada,
 * com Confirmar / Trocar / Prefiro não mostrar. Escolheu: a bandeira e o nome, com Trocar. A lista abre em
 * [CountryPickerPopup], por cima da tela (quem chama guarda o estado do popup).
 */
@Composable
fun CountrySection(profile: Profile, onOpenPicker: () -> Unit) {
    val choice = profile.countryChoice
    val chosen = Countries.of(CountryChoice.shown(choice))
    val suggestion = if (choice == CountryChoice.UNSET) Countries.suggestion(Countries.regionOf(deviceLanguage())) else null

    HudLabel(t(K.PROFILE_COUNTRY))
    Gap(6)
    when {
        chosen != null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CountryLine("${chosen.flag}  ${chosen.name(I18n.lang)}", Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallAction(t(K.COUNTRY_CHANGE), onOpenPicker)
        }

        suggestion != null -> {
            CountryLine(t(K.COUNTRY_SUGGESTED, "${suggestion.flag}  ${suggestion.name(I18n.lang)}"), Modifier.fillMaxWidth())
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.COUNTRY_CONFIRM), modifier = Modifier.weight(1f)) { profile.chooseCountry(suggestion.code) }
                SecondaryButton(t(K.COUNTRY_CHANGE), modifier = Modifier.weight(1f), onClick = onOpenPicker)
            }
            Gap(8)
            SecondaryButton(t(K.COUNTRY_NONE)) { profile.chooseCountry(CountryChoice.NONE) }
        }

        else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CountryLine(if (choice == CountryChoice.NONE) t(K.COUNTRY_NONE) else t(K.COUNTRY_HIDDEN), Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallAction(t(K.COUNTRY_CHOOSE), onOpenPicker)
        }
    }
    Gap(6)
    HudLabel(t(K.COUNTRY_HINT), Naval.muted)
}

@Composable
private fun CountryLine(text: String, modifier: Modifier) {
    Text(
        text,
        style = NavalType.button,
        color = Naval.ink,
        maxLines = 1,
        modifier = modifier
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    )
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    HudLabel(
        text.uppercase(),
        Naval.amberStrong,
        Modifier
            .border(1.dp, Naval.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    )
}

/**
 * Lista fechada de países com busca (#124): "Prefiro não mostrar" primeiro, depois os países na ordem alfabética do
 * idioma. Escolher grava na hora e fecha; o véu engole o toque e só "Voltar" fecha sem mudar.
 */
@Composable
fun CountryPickerPopup(profile: Profile, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = Countries.search(query, I18n.lang)
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.94f))
            .consumeVeilTouch()
            .navalSafeArea()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .navalPanelWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .padding(16.dp)
        ) {
            HudLabel(t(K.PROFILE_COUNTRY).uppercase(), Naval.muted)
            Gap(10)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface)
                    .border(1.dp, Naval.line)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (query.isEmpty()) Text(t(K.COUNTRY_SEARCH), style = NavalType.button, color = Naval.muted)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(30) },
                    singleLine = true,
                    textStyle = NavalType.button.copy(color = Naval.ink),
                    cursorBrush = SolidColor(Naval.amberStrong),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Gap(10)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                item {
                    PickerRow(t(K.COUNTRY_NONE), profile.countryChoice == CountryChoice.NONE) {
                        profile.chooseCountry(CountryChoice.NONE)
                        onClose()
                    }
                }
                items(list, key = { it.code }) { c ->
                    PickerRow("${c.flag}  ${c.name(I18n.lang)}", profile.countryChoice == c.code) {
                        profile.chooseCountry(c.code)
                        onClose()
                    }
                }
                if (list.isEmpty()) item {
                    Gap(8)
                    HudLabel(t(K.COUNTRY_EMPTY), Naval.muted)
                }
            }
            Gap(12)
            SecondaryButton(t(K.BACK), onClick = onClose)
        }
    }
}

@Composable
private fun PickerRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = NavalType.body,
        color = if (selected) Naval.amberStrong else Naval.ink,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 11.dp)
    )
}
