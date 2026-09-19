package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/** Uma versão publicada e o resumo do que mudou nela, em uma linha por idioma. */
private data class ReleaseNote(val version: String, val key: K)

/**
 * O que já foi publicado, da mais recente para a mais antiga — resumo curto de cada
 * versão, não o changelog técnico inteiro (esse fica em CHANGELOG.md, para quem lê
 * código). Cresce a cada release; ver [K] para o texto nos três idiomas.
 */
private val releaseNotes = listOf(
    ReleaseNote("0.25.0", K.RN_V0250),
    ReleaseNote("0.24.0", K.RN_V0240),
    ReleaseNote("0.23.0", K.RN_V0230),
    ReleaseNote("0.22.0", K.RN_V0220),
    ReleaseNote("0.21.0", K.RN_V0210),
    ReleaseNote("0.20.0", K.RN_V0200),
)

@Composable
fun ReleaseNotesScreen(state: AppState) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar(t(K.RELEASE_NOTES_TITLE), "")
        Gap(18)

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            releaseNotes.forEach { note ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Naval.surface2)
                        .border(1.dp, Naval.lineSoft)
                        .padding(14.dp)
                ) {
                    HudLabel(note.version, Naval.amberStrong)
                    Gap(6)
                    HudLabel(t(note.key), Naval.inkSoft)
                }
            }
        }

        Gap(14)
        SecondaryButton(t(K.BACK)) { state.screen = Screen.SETTINGS }
    }
}
