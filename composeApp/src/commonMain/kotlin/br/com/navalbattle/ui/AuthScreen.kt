package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.SupabaseConfig
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import kotlinx.coroutines.launch

/**
 * Conta do comandante. A carreira continua sendo gravada no aparelho; a conta
 * serve para ela viver na nuvem e voltar em qualquer celular.
 */
@Composable
fun AuthScreen(state: AppState) {
    var creating by remember { mutableStateOf(!state.profile.signedIn) }
    var email by remember { mutableStateOf(state.profile.accountEmail) }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(state.profile.name) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        ScreenTopBar("CONTA DO COMANDANTE", if (state.profile.signedIn) "CONECTADO" else "LOCAL")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(18)
            Text("SUA CARREIRA", style = NavalType.display, color = Naval.ink)
            Text("EM QUALQUER MAR", style = NavalType.display, color = Naval.amberStrong)
            Gap(8)
            HudLabel(
                "PATENTE, CRÉDITOS E FROTAS GUARDADOS NA BASE DO JOGO",
                Naval.muted
            )

            if (state.profile.signedIn) {
                Gap(24)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Naval.surface2)
                        .border(1.dp, Naval.green)
                        .padding(14.dp)
                ) {
                    HudLabel("CONECTADO COMO", Naval.muted)
                    Gap(6)
                    Text(state.profile.accountEmail, style = NavalType.mono, color = Naval.ink)
                    Gap(4)
                    HudLabel("${state.profile.name.uppercase()} · ${state.profile.rank.label.uppercase()}", Naval.inkSoft)
                }
                Gap(14)
                PrimaryButton("Sincronizar agora", enabled = !busy) {
                    busy = true; failed = false; message = "Sincronizando…"
                    scope.launch {
                        val ok = state.syncNow()
                        busy = false; failed = !ok
                        message = if (ok) "Carreira sincronizada." else "Não consegui sincronizar agora."
                    }
                }
                Gap(8)
                SecondaryButton("Sair da conta") {
                    state.profile.signOut()
                    message = "Sessão encerrada. A carreira continua neste aparelho."
                }
            } else {
                Gap(20)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip("Criar conta", creating, Modifier.weight(1f)) { creating = true; message = null }
                    ModeChip("Entrar", !creating, Modifier.weight(1f)) { creating = false; message = null }
                }

                Gap(18)
                if (creating) {
                    Field("Nome de usuário", username, KeyboardType.Text, false) { username = it.take(18) }
                    Gap(12)
                }
                Field("E-mail", email, KeyboardType.Email, false) { email = it.trim().take(120) }
                Gap(12)
                Field("Senha", password, KeyboardType.Password, true) { password = it.take(64) }
                Gap(6)
                HudLabel("MÍNIMO DE 6 CARACTERES", Naval.muted)

                Gap(20)
                PrimaryButton(
                    if (creating) "Criar conta" else "Entrar",
                    enabled = !busy && email.isNotBlank() && password.length >= 6 &&
                        (!creating || username.isNotBlank()),
                    subtitle = if (busy) "aguarde" else null
                ) {
                    busy = true; failed = false; message = null
                    scope.launch {
                        val result = if (creating) {
                            state.createAccount(email, password, username)
                        } else {
                            state.signIn(email, password)
                        }
                        busy = false
                        failed = !result.first
                        message = result.second
                        if (result.first) password = ""
                    }
                }
            }

            message?.let {
                Gap(14)
                HudLabel(it, if (failed) Naval.danger else Naval.greenBright)
            }

            if (!SupabaseConfig.isConfigured) {
                Gap(18)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Naval.surface2)
                        .border(1.dp, Naval.line)
                        .padding(14.dp)
                ) {
                    HudLabel("SERVIDOR AINDA NÃO LIGADO", Naval.amberStrong)
                    Gap(6)
                    HudLabel(
                        "A CARREIRA ESTÁ SENDO GRAVADA NESTE APARELHO. QUANDO A BASE ENTRAR, " +
                            "ELA SOBE PARA A CONTA SEM PERDER NADA.",
                        Naval.muted
                    )
                }
            }

            Gap(18)
            HudLabel("O LOGIN COM GOOGLE USA O MESMO E-MAIL E CAI NA MESMA CONTA", Naval.muted)
            Gap(16)
        }

        Gap(8)
        SecondaryButton("Voltar ao deque") { state.screen = Screen.MENU }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    type: KeyboardType,
    secret: Boolean,
    onChange: (String) -> Unit
) {
    Column {
        HudLabel(label.uppercase(), Naval.muted)
        Gap(6)
        Box(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, if (value.isBlank()) Naval.line else Naval.green)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = NavalType.button.copy(color = Naval.ink),
                cursorBrush = SolidColor(Naval.amberStrong),
                visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            ) { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(label, style = NavalType.button, color = Naval.muted)
                    }
                    inner()
                }
            }
        }
    }
}
