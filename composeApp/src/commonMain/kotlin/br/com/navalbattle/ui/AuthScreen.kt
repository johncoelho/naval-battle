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
import br.com.navalbattle.data.GoogleAuthConfig
import br.com.navalbattle.data.SupabaseConfig
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
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
    var showForgot by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        ScreenTopBar(t(K.AUTH_TITLE), if (state.profile.signedIn) t(K.AUTH_CONNECTED) else t(K.AUTH_LOCAL))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(18)
            Text(t(K.AUTH_HEAD_1).uppercase(), style = NavalType.display, color = Naval.ink)
            Text(t(K.AUTH_HEAD_2).uppercase(), style = NavalType.display, color = Naval.amberStrong)
            Gap(8)
            HudLabel(
                t(K.AUTH_SUB),
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
                    HudLabel(t(K.AUTH_CONNECTED_AS), Naval.muted)
                    Gap(6)
                    Text(state.profile.accountEmail, style = NavalType.mono, color = Naval.ink)
                    Gap(4)
                    HudLabel("${state.profile.displayName.uppercase()} · ${state.profile.rank.label.uppercase()}", Naval.inkSoft)
                }
                Gap(14)
                PrimaryButton(t(K.AUTH_SYNC_NOW), enabled = !busy) {
                    busy = true; failed = false; message = t(K.AUTH_SYNCING)
                    scope.launch {
                        val ok = state.syncNow()
                        busy = false; failed = !ok
                        message = if (ok) t(K.AUTH_SYNCED) else t(K.AUTH_SYNC_FAIL)
                    }
                }
                Gap(8)
                SecondaryButton(t(K.AUTH_CHANGE_PASSWORD)) {
                    showChangePassword = !showChangePassword
                    message = null
                }
                if (showChangePassword) {
                    Gap(10)
                    ChangePasswordCard(
                        onSubmit = { current, new ->
                            busy = true; failed = false; message = null
                            scope.launch {
                                val result = state.changePassword(current, new)
                                busy = false
                                failed = !result.first
                                message = result.second
                                if (result.first) showChangePassword = false
                            }
                        },
                        onCancel = { showChangePassword = false; message = null },
                        busy = busy
                    )
                }
                Gap(8)
                SecondaryButton(t(K.AUTH_SIGN_OUT)) {
                    state.profile.signOut()
                    message = t(K.AUTH_SIGNED_OUT)
                }
            } else {
                Gap(20)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(t(K.MENU_CREATE_ACCOUNT), creating, Modifier.weight(1f)) { creating = true; message = null }
                    ModeChip(t(K.AUTH_SIGN_IN), !creating, Modifier.weight(1f)) { creating = false; message = null }
                }

                Gap(18)
                if (creating) {
                    Field(t(K.AUTH_USERNAME), username, KeyboardType.Text, false) { username = it.take(18) }
                    Gap(12)
                }
                Field(t(K.AUTH_EMAIL), email, KeyboardType.Email, false) { email = it.trim().take(120) }
                Gap(12)
                Field(t(K.AUTH_PASSWORD), password, KeyboardType.Password, true) { password = it.take(64) }
                Gap(6)
                if (creating) {
                    HudLabel(t(K.AUTH_MIN_CHARS), Naval.muted)
                } else {
                    HudLabel(
                        t(K.AUTH_FORGOT),
                        Naval.amberStrong,
                        Modifier.clickable { showForgot = !showForgot; message = null }
                    )
                }

                if (showForgot && !creating) {
                    Gap(14)
                    ForgotPasswordCard(
                        email = email,
                        busy = busy,
                        onSend = {
                            busy = true; failed = false; message = null
                            scope.launch {
                                val result = state.forgotPassword(email)
                                busy = false
                                failed = !result.first
                                message = result.second
                            }
                        }
                    )
                }

                Gap(20)
                PrimaryButton(
                    if (creating) t(K.MENU_CREATE_ACCOUNT) else t(K.AUTH_SIGN_IN),
                    enabled = !busy && email.isNotBlank() && password.length >= 6 &&
                        (!creating || username.isNotBlank()),
                    subtitle = if (busy) t(K.AUTH_WAIT) else null
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

                if (GoogleAuthConfig.isConfigured) {
                    Gap(10)
                    SecondaryButton(t(K.AUTH_GOOGLE), enabled = !busy) {
                        busy = true; failed = false; message = null
                        scope.launch {
                            val result = state.signInWithGoogle()
                            busy = false
                            // cancelou a caixa de seleção de conta: não é erro, só volta calado
                            if (!result.first && result.second.isBlank()) return@launch
                            failed = !result.first
                            message = result.second
                        }
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
                    HudLabel(t(K.AUTH_NO_SERVER), Naval.amberStrong)
                    Gap(6)
                    HudLabel(t(K.AUTH_NO_SERVER_SUB), Naval.muted)
                }
            }

            if (GoogleAuthConfig.isConfigured) {
                Gap(18)
                HudLabel(t(K.AUTH_GOOGLE_HINT), Naval.muted)
            }
            Gap(16)
        }

        Gap(8)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
    }
}

/** Formulário de "esqueci minha senha": um e-mail, um botão, sem senha nenhuma envolvida. */
@Composable
private fun ForgotPasswordCard(email: String, busy: Boolean, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(14.dp)
    ) {
        HudLabel(t(K.AUTH_RESET_TITLE), Naval.amberStrong)
        Gap(6)
        HudLabel(t(K.AUTH_RESET_HINT), Naval.muted)
        Gap(12)
        PrimaryButton(t(K.AUTH_RESET_SEND), enabled = !busy && email.isNotBlank(), onClick = onSend)
    }
}

/**
 * Trocar senha exige a senha atual — reautenticamos com ela antes de aceitar a
 * troca, para ninguém trocar a senha de uma sessão esquecida aberta no aparelho.
 */
@Composable
private fun ChangePasswordCard(
    onSubmit: (current: String, new: String) -> Unit,
    onCancel: () -> Unit,
    busy: Boolean
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(14.dp)
    ) {
        HudLabel(t(K.AUTH_CHANGE_PASSWORD), Naval.amberStrong)
        Gap(12)
        Field(t(K.AUTH_CURRENT_PASSWORD), current, KeyboardType.Password, true) { current = it.take(64) }
        Gap(10)
        Field(t(K.AUTH_NEW_PASSWORD), new, KeyboardType.Password, true) { new = it.take(64) }
        Gap(10)
        Field(t(K.AUTH_CONFIRM_PASSWORD), confirm, KeyboardType.Password, true) { confirm = it.take(64) }
        Gap(6)
        HudLabel(t(K.AUTH_MIN_CHARS), Naval.muted)
        localError?.let {
            Gap(8)
            HudLabel(it, Naval.danger)
        }
        Gap(14)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(t(K.AUTH_CANCEL), modifier = Modifier.weight(1f), enabled = !busy, onClick = onCancel)
            PrimaryButton(
                t(K.SAVE),
                modifier = Modifier.weight(1f),
                enabled = !busy && current.isNotBlank() && new.length >= 6 && confirm.isNotBlank()
            ) {
                if (new != confirm) {
                    localError = t(K.AUTH_PASSWORD_MISMATCH)
                } else {
                    localError = null
                    onSubmit(current, new)
                }
            }
        }
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
