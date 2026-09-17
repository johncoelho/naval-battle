package br.com.navalbattle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/**
 * Primeira tela depois da abertura, uma única vez por aparelho enquanto o comandante
 * não tiver conta: escolher entrar/criar conta agora, ou seguir direto como convidado
 * (sem nuvem, sem amigos, sem modo online — [Profile.welcomeDone] evita que ela volte
 * a aparecer depois dessa escolha).
 */
@Composable
fun WelcomeScreen(state: AppState) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Gap(48)
                Text(t(K.WELCOME_TITLE_1).uppercase(), style = NavalType.display, color = Naval.ink, textAlign = TextAlign.Center)
                Text(t(K.WELCOME_TITLE_2).uppercase(), style = NavalType.display, color = Naval.amberStrong, textAlign = TextAlign.Center)
                Gap(18)
                Text(t(K.WELCOME_SUB), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
            }

            Column(Modifier.fillMaxWidth()) {
                PrimaryButton(t(K.WELCOME_LOGIN)) { state.screen = Screen.PROFILE }
                Gap(10)
                SecondaryButton(t(K.WELCOME_GUEST), t(K.WELCOME_GUEST_SUB)) {
                    state.profile.markWelcomeDone()
                    state.screen = Screen.MENU
                }
            }
        }
    }
}
