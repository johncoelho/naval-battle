package br.com.navalbattle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.CommanderHit
import br.com.navalbattle.data.Friendship
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Sala de amigo ou partida rápida pela internet — mesma ideia da [LanScreen], só que
 * o transporte é o [br.com.navalbattle.data.OnlineLink] em vez de rede local. Exige
 * conta conectada, porque a sala e as amizades vivem presas ao comandante no Supabase.
 */
@Composable
fun OnlineScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var codeInput by remember { mutableStateOf("") }
    var friendQuery by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { if (state.screen == Screen.MENU) state.closeOnline() }
    }

    LaunchedEffect(Unit) {
        if (state.profile.signedIn) state.refreshFriendships()
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        ScreenTopBar(t(K.ONLINE_TITLE), onlineStateLabel(state.onlineLinkState))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Gap(18)
            Text(t(K.ONLINE_HEAD_1).uppercase(), style = NavalType.display, color = Naval.ink)
            Text(t(K.ONLINE_HEAD_2).uppercase(), style = NavalType.display, color = Naval.amberStrong)
            Gap(8)
            HudLabel(t(K.ONLINE_SUB), Naval.muted)

            if (!state.profile.signedIn) {
                Gap(20)
                OnlinePanel(Naval.amber) {
                    HudLabel(t(K.ONLINE_SIGN_IN_REQUIRED), Naval.amberStrong)
                    Gap(10)
                    PrimaryButton(t(K.ONLINE_SIGN_IN_GO)) { state.screen = Screen.PROFILE }
                }
                Gap(16)
                return@Column
            }

            Gap(20)
            PrimaryButton(
                t(K.ONLINE_QUICK),
                subtitle = t(K.ONLINE_QUICK_SUB),
                enabled = state.onlineLinkState == LinkState.IDLE || state.onlineLinkState == LinkState.FAILED
            ) { state.startQuickMatchOnline() }
            Gap(8)
            SecondaryButton(
                t(K.ONLINE_CREATE_ROOM),
                subtitle = t(K.ONLINE_CREATE_ROOM_SUB),
                enabled = state.onlineLinkState == LinkState.IDLE || state.onlineLinkState == LinkState.FAILED
            ) { state.createOnlineRoom() }

            when (state.onlineLinkState) {
                LinkState.SEARCHING -> {
                    Gap(20)
                    OnlinePanel(Naval.amber) {
                        HudLabel(t(K.ONLINE_SEARCHING), Naval.amberStrong)
                        Gap(6)
                        HudLabel(t(K.ONLINE_SEARCHING_SUB), Naval.muted)
                    }
                }

                LinkState.HOSTING -> {
                    Gap(20)
                    OnlinePanel(Naval.green) {
                        HudLabel(t(K.ONLINE_WAITING_GUEST), Naval.greenBright)
                        state.onlineCode?.let { code ->
                            Gap(12)
                            HudLabel(t(K.ONLINE_YOUR_CODE), Naval.muted)
                            Gap(6)
                            Text(code, style = NavalType.display, color = Naval.amberStrong)
                            Gap(6)
                            HudLabel(t(K.ONLINE_SHARE_HINT), Naval.muted)
                        }
                    }
                }

                LinkState.CONNECTING -> {
                    Gap(20)
                    OnlinePanel(Naval.amber) { HudLabel(t(K.ONLINE_CONNECTING), Naval.amberStrong) }
                }

                LinkState.FAILED -> {
                    Gap(20)
                    OnlinePanel(Naval.danger) {
                        HudLabel(t(K.ONLINE_FAILED), Naval.danger)
                        Gap(6)
                        HudLabel(t(K.ONLINE_FAILED_SUB), Naval.muted)
                    }
                }

                else -> Unit
            }

            Gap(20)
            HudLabel(t(K.ONLINE_JOIN_TITLE), Naval.muted)
            Gap(6)
            CodeField(codeInput, enabled = state.onlineLinkState == LinkState.IDLE || state.onlineLinkState == LinkState.FAILED) {
                codeInput = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(5)
            }
            Gap(6)
            HudLabel(t(K.ONLINE_JOIN_HINT), Naval.muted)
            Gap(10)
            SecondaryButton(
                t(K.ONLINE_JOIN_BUTTON),
                enabled = codeInput.length == 5 &&
                    (state.onlineLinkState == LinkState.IDLE || state.onlineLinkState == LinkState.FAILED)
            ) { state.joinOnlineByCode(codeInput) }

            Gap(24)
            OnlinePanel(Naval.line) {
                HudLabel(t(K.ONLINE_HOW), Naval.inkSoft)
                Gap(8)
                OnlineStep("1", t(K.ONLINE_STEP_1))
                OnlineStep("2", t(K.ONLINE_STEP_2))
                OnlineStep("3", t(K.ONLINE_STEP_3))
            }

            Gap(28)
            FriendsSection(state, scope, friendQuery, onQueryChange = { friendQuery = it })
            Gap(16)
        }

        Gap(8)
        SecondaryButton(t(K.BACK_TO_DECK)) {
            state.closeOnline()
            state.screen = Screen.MENU
        }
    }
}

@Composable
private fun FriendsSection(
    state: AppState,
    scope: kotlinx.coroutines.CoroutineScope,
    query: String,
    onQueryChange: (String) -> Unit
) {
    if (!state.profile.signedIn) return
    val myId = state.profile.accountId

    HudLabel(t(K.FRIENDS_TITLE))
    Gap(10)
    CodeField(query, enabled = true, placeholder = t(K.FRIENDS_SEARCH_HINT), uppercase = false) {
        onQueryChange(it)
        scope.launch { state.searchCommander(it) }
    }

    if (state.friendResults.isNotEmpty()) {
        Gap(10)
        state.friendResults.forEach { hit ->
            val alreadyKnown = state.friendships.any {
                (it.requesterId == myId && it.addresseeId == hit.id) ||
                    (it.addresseeId == myId && it.requesterId == hit.id)
            }
            FriendRow(hit.username) {
                if (alreadyKnown) {
                    HudLabel(t(K.FRIENDS_REQUEST_SENT), Naval.muted)
                } else {
                    HudLabel(
                        t(K.FRIENDS_ADD),
                        Naval.amberStrong,
                        Modifier.clickable {
                            scope.launch {
                                state.sendFriendRequest(hit)
                                onQueryChange("")
                            }
                        }
                    )
                }
            }
        }
    }

    val incoming = state.friendships.filter { it.status == "pending" && it.addresseeId == myId }
    if (incoming.isNotEmpty()) {
        Gap(18)
        HudLabel(t(K.FRIENDS_REQUESTS), Naval.muted)
        Gap(8)
        incoming.forEach { f ->
            FriendRow(f.requesterUsername) {
                HudLabel(
                    t(K.FRIENDS_ACCEPT),
                    Naval.greenBright,
                    Modifier.clickable { scope.launch { state.respondFriendRequest(f, true) } }
                )
                GapW(14)
                HudLabel(
                    t(K.FRIENDS_DECLINE),
                    Naval.danger,
                    Modifier.clickable { scope.launch { state.respondFriendRequest(f, false) } }
                )
            }
        }
    }

    val accepted = state.friendships.filter { it.status == "accepted" }
    val outgoing = state.friendships.filter { it.status == "pending" && it.requesterId == myId }
    Gap(18)
    HudLabel(t(K.FRIENDS_LIST), Naval.muted)
    Gap(8)
    if (accepted.isEmpty() && outgoing.isEmpty()) {
        HudLabel(t(K.FRIENDS_EMPTY), Naval.muted)
    } else {
        outgoing.forEach { f ->
            val name = if (f.requesterId == myId) f.addresseeUsername else f.requesterUsername
            FriendRow(name) { HudLabel("(${t(K.FRIENDS_SENT_TAG)})", Naval.muted) }
        }
        accepted.forEach { f ->
            val friendId = if (f.requesterId == myId) f.addresseeId else f.requesterId
            val name = if (f.requesterId == myId) f.addresseeUsername else f.requesterUsername
            FriendRow(name) {
                HudLabel(
                    t(K.FRIENDS_INVITE),
                    Naval.amberStrong,
                    Modifier.clickable { state.createOnlineRoom(invitedId = friendId) }
                )
                GapW(14)
                HudLabel(
                    t(K.FRIENDS_REMOVE),
                    Naval.danger,
                    Modifier.clickable { scope.launch { state.removeFriendship(f) } }
                )
            }
        }
    }
}

@Composable
private fun FriendRow(name: String, actions: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(Naval.surface2)
            .border(1.dp, Naval.line)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name.uppercase(), style = NavalType.mono, color = Naval.ink)
        Row(content = actions)
    }
}

private fun onlineStateLabel(state: LinkState): String = t(
    when (state) {
        LinkState.IDLE -> K.LAN_READY
        LinkState.HOSTING -> K.LAN_STATE_ANNOUNCING
        LinkState.SEARCHING -> K.LAN_STATE_SEARCHING
        LinkState.CONNECTING -> K.LAN_STATE_CONNECTING
        LinkState.CONNECTED -> K.ONLINE_CONNECTED
        LinkState.FAILED -> K.LAN_STATE_FAILED
    }
)

@Composable
private fun CodeField(
    value: String,
    enabled: Boolean,
    placeholder: String = t(K.ONLINE_JOIN_HINT),
    uppercase: Boolean = true,
    onChange: (String) -> Unit
) {
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
            enabled = enabled,
            singleLine = true,
            textStyle = (if (uppercase) NavalType.mono else NavalType.button).copy(color = Naval.ink),
            cursorBrush = SolidColor(Naval.amberStrong),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        ) { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(placeholder, style = NavalType.button, color = Naval.muted)
                }
                inner()
            }
        }
    }
}

@Composable
private fun OnlinePanel(border: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, border)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun OnlineStep(number: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(number, style = NavalType.mono, color = Naval.amberStrong)
        GapW(10)
        HudLabel(text, Naval.inkSoft)
    }
}
