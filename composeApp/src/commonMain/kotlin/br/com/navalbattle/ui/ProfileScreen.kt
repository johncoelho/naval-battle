package br.com.navalbattle.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
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
import br.com.navalbattle.design.drawAvatar
import br.com.navalbattle.design.drawInsignia
import br.com.navalbattle.game.Avatar
import br.com.navalbattle.game.Insignia
import br.com.navalbattle.game.Rank
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.Lang
import kotlinx.coroutines.launch

/**
 * Perfil e conta numa tela só: identidade (retrato, nome, insígnia), a conta na
 * nuvem (entrar/criar/gerir — o que antes vivia em AuthScreen) e a folha de serviço.
 * Acessível pelo retrato no canto do menu, sem precisar de uma tela separada.
 */
@Composable
fun ProfileScreen(state: AppState) {
    val profile = state.profile
    var name by remember { mutableStateOf(profile.name) }
    var confirmReset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // mudou identidade: sobe para a conta, se houver uma conectada
    fun sync() = scope.launch { state.pushQuietly() }

    // entrada do retrato: cresce com uma pequena "quicada" ao abrir a tela, em vez
    // de aparecer estático — o único lugar do perfil que tinha zero movimento
    val portraitScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) { portraitScale.animateTo(1f, tween(420, easing = EaseOutBack)) }
    val ringRotation by rememberInfiniteTransition(label = "portraitRing").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "portraitRingAngle"
    )

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenTopBar(t(K.PROFILE_TITLE), "◆ ${profile.credits}")
            Gap(14)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // retrato, nome e patente
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(
                        Modifier
                            .size(78.dp)
                            .scale(portraitScale.value)
                    ) {
                        // anel de marcações girando devagar por trás do retrato — o
                        // único toque de vida contínua na tela, sutil o bastante
                        // para não distrair de nome/patente/estatísticas
                        rotate(ringRotation) {
                            val r = size.minDimension / 2f - 2.dp.toPx()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            repeat(12) { i ->
                                val a = (i / 12f) * 2f * kotlin.math.PI.toFloat()
                                val inner = if (i % 3 == 0) r - 6.dp.toPx() else r - 3.dp.toPx()
                                drawLine(
                                    color = Naval.amber.copy(alpha = 0.4f),
                                    start = Offset(center.x + inner * kotlin.math.cos(a), center.y + inner * kotlin.math.sin(a)),
                                    end = Offset(center.x + r * kotlin.math.cos(a), center.y + r * kotlin.math.sin(a)),
                                    strokeWidth = 1.2.dp.toPx()
                                )
                            }
                        }
                        drawAvatar(
                            avatar = profile.avatar,
                            center = Offset(size.width / 2f, size.height / 2f),
                            size = size.minDimension * 0.9f,
                            color = Naval.amberStrong
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.rank.label.uppercase(), style = NavalType.title, color = Naval.amberStrong)
                        Gap(2)
                        Text(profile.displayName, style = NavalType.body, color = Naval.ink)
                        Gap(6)
                        HudLabel("${profile.xp} XP", Naval.muted)
                    }
                }

                Gap(12)
                RankBar(profile.xp, profile.rankProgress)

                Gap(22)
                AccountSection(state)

                Gap(22)
                HudLabel(t(K.PROFILE_NAME))
                Gap(6)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .background(Naval.surface2)
                            .border(1.dp, Naval.line)
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { if (it.length <= 18) name = it },
                            singleLine = true,
                            textStyle = NavalType.button.copy(color = Naval.ink),
                            cursorBrush = SolidColor(Naval.amberStrong),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    HudLabel(
                        t(K.SAVE),
                        if (name.trim() != profile.name) Naval.amberStrong else Naval.muted,
                        Modifier
                            .border(1.dp, Naval.line)
                            .clickable { profile.rename(name); name = profile.name; sync() }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    )
                }

                Gap(22)
                HudLabel(t(K.PROFILE_AVATAR))
                Gap(8)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Avatar.entries.forEach { option ->
                        val chosen = profile.avatar == option
                        val borderColor by animateColorAsState(if (chosen) Naval.amber else Naval.line)
                        val scale by animateFloatAsState(if (chosen) 1f else 0.92f, tween(220))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .scale(scale)
                                .background(if (chosen) Naval.surface3 else Naval.surface)
                                .border(1.dp, borderColor)
                                .clickable { profile.chooseAvatar(option) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(38.dp)) {
                                drawAvatar(
                                    avatar = option,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    size = size.minDimension * 0.92f,
                                    color = if (chosen) Naval.amberStrong else Naval.inkSoft
                                )
                            }
                        }
                    }
                }
                Gap(6)
                HudLabel(profile.avatar.label.uppercase(), Naval.muted)

                Gap(22)
                HudLabel(t(K.PROFILE_INSIGNIA))
                Gap(8)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Insignia.entries.forEach { option ->
                        val chosen = profile.insignia == option
                        val borderColor by animateColorAsState(if (chosen) Naval.amber else Naval.line)
                        val scale by animateFloatAsState(if (chosen) 1f else 0.92f, tween(220))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(54.dp)
                                .scale(scale)
                                .background(if (chosen) Naval.surface3 else Naval.surface)
                                .border(1.dp, borderColor)
                                .clickable { profile.chooseInsignia(option); sync() },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(Modifier.size(38.dp)) {
                                drawInsignia(
                                    insignia = option,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    size = size.minDimension * 0.62f,
                                    color = if (chosen) Naval.amberStrong else Naval.inkSoft
                                )
                            }
                        }
                    }
                }
                Gap(6)
                HudLabel(profile.insignia.label.uppercase(), Naval.muted)

                Gap(22)
                HudLabel(t(K.PROFILE_LANGUAGE))
                Gap(8)
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

                Gap(22)
                HudLabel(t(K.PROFILE_RECORD))
                Gap(8)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigStat(t(K.PROFILE_MATCHES), profile.matches.toString(), Modifier.weight(1f))
                    BigStat(t(K.PROFILE_WINS), profile.wins.toString(), Modifier.weight(1f), Naval.greenBright)
                    BigStat(t(K.PROFILE_LOSSES), profile.losses.toString(), Modifier.weight(1f), Naval.danger)
                }
                Gap(10)
                StatLine(t(K.PROFILE_WINRATE), "${profile.winRate}%")
                StatLine(t(K.PROFILE_SHOT_ACC), "${profile.accuracy}%")
                StatLine(t(K.PROFILE_SHOTS_HITS), "${profile.shots} / ${profile.hits}")
                StatLine(t(K.PROFILE_SUNK), profile.sunk.toString())
                StatLine(t(K.PROFILE_STREAK), "${profile.streak} ${t(K.PROFILE_WINS_SUFFIX)}")
                StatLine(t(K.PROFILE_BEST_STREAK), "${profile.bestStreak} ${t(K.PROFILE_WINS_SUFFIX)}")
                StatLine(t(K.PROFILE_HULLS_OWNED), profile.ownedFleets.size.toString())
                StatLine(t(K.PROFILE_CAMOS_OWNED), profile.owned.size.toString())

                Gap(18)
                HudLabel(t(K.PROFILE_NEXT_RANKS), Naval.muted)
                Gap(6)
                Rank.entries.filter { it.xp > profile.xp }.take(3).forEach { r ->
                    StatLine(r.label.uppercase(), "${r.xp - profile.xp} XP")
                }
                if (Rank.next(profile.xp) == null) {
                    HudLabel(t(K.PROFILE_MAX_RANK), Naval.amberStrong)
                }

                Gap(20)
                HudLabel(
                    t(K.PROFILE_RESET),
                    Naval.danger,
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, Naval.line)
                        .clickable { confirmReset = true }
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                )
                Gap(16)
            }

            Gap(10)
            PrimaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
        }

        if (confirmReset) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Naval.bg.copy(alpha = 0.94f))
                    .clickable(enabled = false) {}
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(Modifier.fillMaxWidth()) {
                    HudLabel(t(K.PROFILE_RESET_EYEBROW), Naval.muted)
                    Gap(8)
                    Text(t(K.PROFILE_RESET_TITLE).uppercase(), style = NavalType.display, color = Naval.ink)
                    Gap(6)
                    HudLabel(t(K.PROFILE_RESET_WARN), Naval.muted)
                    Gap(22)
                    PrimaryButton(t(K.PROFILE_RESET_KEEP)) { confirmReset = false }
                    Gap(8)
                    SecondaryButton(t(K.PROFILE_RESET_DO)) {
                        profile.reset()
                        name = profile.name
                        confirmReset = false
                    }
                }
            }
        }
    }
}

/**
 * A conta na nuvem, embutida no Perfil — entrar/criar para quem ainda não tem, ou
 * gerir (sincronizar, trocar senha, sair) para quem já está conectado. Antes vivia
 * na tela separada AuthScreen; juntar as duas evita duas paradas para a mesma coisa.
 */
@Composable
private fun AccountSection(state: AppState) {
    val profile = state.profile
    var creating by remember { mutableStateOf(!profile.signedIn) }
    var email by remember { mutableStateOf(profile.accountEmail) }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(profile.name) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, if (profile.signedIn) Naval.green else Naval.line)
            .padding(14.dp)
    ) {
        HudLabel(
            if (profile.signedIn) t(K.AUTH_ACCOUNT_ON) else t(K.AUTH_NO_ACCOUNT),
            if (profile.signedIn) Naval.greenBright else Naval.amberStrong
        )
        Gap(4)
        HudLabel(
            if (profile.signedIn) profile.accountEmail.uppercase() else t(K.AUTH_CREATE_HINT),
            Naval.muted
        )

        if (profile.signedIn) {
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
                profile.signOut()
                message = t(K.AUTH_SIGNED_OUT)
            }
        } else {
            Gap(14)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(t(K.MENU_CREATE_ACCOUNT), creating, Modifier.weight(1f)) { creating = true; message = null }
                ModeChip(t(K.AUTH_SIGN_IN), !creating, Modifier.weight(1f)) { creating = false; message = null }
            }

            Gap(14)
            if (creating) {
                Field(t(K.AUTH_USERNAME), username, KeyboardType.Text, false) { username = it.take(18) }
                Gap(10)
            }
            Field(t(K.AUTH_EMAIL), email, KeyboardType.Email, false) { email = it.trim().take(120) }
            Gap(10)
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
                Gap(12)
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

            Gap(16)
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
                Gap(8)
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
                Gap(8)
                HudLabel(t(K.AUTH_GOOGLE_HINT), Naval.muted)
            }

            if (!SupabaseConfig.isConfigured) {
                Gap(14)
                HudLabel(t(K.AUTH_NO_SERVER), Naval.amberStrong)
                Gap(6)
                HudLabel(t(K.AUTH_NO_SERVER_SUB), Naval.muted)
            }
        }

        message?.let {
            Gap(12)
            HudLabel(it, if (failed) Naval.danger else Naval.greenBright)
        }
    }
}

/** Formulário de "esqueci minha senha": um e-mail, um botão, sem senha nenhuma envolvida. */
@Composable
private fun ForgotPasswordCard(email: String, busy: Boolean, onSend: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface)
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
            .background(Naval.surface)
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

/** Barra de progresso até a próxima patente. */
@Composable
private fun RankBar(xp: Int, progress: Float) {
    val next = Rank.next(xp)
    // a barra some e some de novo com valores diferentes (troca de patente, reset de
    // carreira) — anima até o valor novo em vez de saltar direto, como um medidor de verdade
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0.02f, 1f),
        animationSpec = tween(600)
    )
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Naval.surface3)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(4.dp)
                    .background(Naval.amber)
            )
        }
        Gap(6)
        HudLabel(
            if (next == null) t(K.PROFILE_CAREER_DONE) else t(K.PROFILE_XP_TO, next.xp - xp, next.label).uppercase(),
            Naval.muted
        )
    }
}

@Composable
private fun BigStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Naval.ink
) {
    Column(
        modifier
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(value, style = NavalType.title, color = color)
        Gap(2)
        HudLabel(label, Naval.muted)
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudLabel(label, Naval.muted)
        Text(value, style = NavalType.mono, color = Naval.ink)
    }
}
