package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.FriendProfile
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawInsignia
import br.com.navalbattle.game.Insignia
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.launch

/**
 * Tela dedicada de amigos — antes vivia espremida dentro da tela Online, sem
 * botão de busca (disparava a cada tecla) nem jeito de ver o perfil de quem já
 * é amigo. Agora tem busca de verdade, seções claras e um popup de folha de
 * serviço pra cada amigo.
 */
@Composable
fun FriendsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var viewedName by remember { mutableStateOf("") }

    fun search() = scope.launch { state.searchCommander(query) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        ScreenTopBar(t(K.FRIENDS_TITLE), "")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Gap(18)
            HudLabel(t(K.FRIENDS_SEARCH_HINT))
            Gap(8)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CodeField(
                    value = query,
                    enabled = true,
                    placeholder = t(K.FRIENDS_SEARCH_HINT),
                    uppercase = false,
                    onChange = { query = it }
                )
            }
            Gap(8)
            SecondaryButton(t(K.FRIENDS_SEARCH_BUTTON), enabled = query.isNotBlank()) { search() }

            if (query.isNotBlank()) {
                Gap(14)
                if (state.friendResults.isEmpty()) {
                    HudLabel(t(K.FRIENDS_SEARCH_EMPTY), Naval.muted)
                } else {
                    val myId = state.profile.accountId
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
                                            query = ""
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val myId = state.profile.accountId
            val incoming = state.friendships.filter { it.status == "pending" && it.addresseeId == myId }
            if (incoming.isNotEmpty()) {
                Gap(22)
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
            Gap(22)
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
                            t(K.FRIENDS_VIEW_PROFILE),
                            Naval.inkSoft,
                            Modifier.clickable {
                                viewedName = name
                                scope.launch { state.loadFriendProfile(friendId) }
                            }
                        )
                        GapW(14)
                        HudLabel(
                            t(K.FRIENDS_INVITE),
                            Naval.amberStrong,
                            Modifier.clickable {
                                state.createOnlineRoom(invitedId = friendId)
                                state.screen = Screen.ONLINE
                            }
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
            Gap(16)
        }

        Gap(10)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
    }

    if (viewedName.isNotBlank()) {
        FriendProfileDialog(
            name = viewedName,
            loading = state.friendProfileLoading,
            profile = state.viewedFriendProfile,
            onClose = { viewedName = ""; state.closeFriendProfile() }
        )
    }
}

@Composable
private fun FriendProfileDialog(name: String, loading: Boolean, profile: FriendProfile?, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.88f))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.line)
                .padding(20.dp)
        ) {
            HudLabel(t(K.FRIENDS_PROFILE_TITLE), Naval.muted)
            Gap(6)
            Text(name.uppercase(), style = NavalType.title, color = Naval.amberStrong)
            Gap(16)
            when {
                loading -> HudLabel(t(K.FRIENDS_PROFILE_LOADING), Naval.muted)
                profile == null -> HudLabel(t(K.FRIENDS_PROFILE_NOT_FOUND), Naval.danger)
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(52.dp)) {
                            drawInsignia(
                                insignia = Insignia.of(profile.insignia),
                                center = Offset(size.width / 2f, size.height / 2f),
                                size = size.minDimension * 0.7f,
                                color = Naval.amberStrong
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            HudLabel("${profile.xp} XP", Naval.muted)
                            Gap(2)
                            HudLabel("${t(K.LEADERBOARD_TITLE)}: ${profile.rankedRating}", Naval.muted)
                        }
                    }
                    Gap(16)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigStatChip(t(K.PROFILE_MATCHES), profile.matches.toString(), Modifier.weight(1f))
                        BigStatChip(t(K.PROFILE_WINS), profile.wins.toString(), Modifier.weight(1f), Naval.greenBright)
                        BigStatChip(t(K.PROFILE_BEST_STREAK), profile.bestStreak.toString(), Modifier.weight(1f))
                    }
                }
            }
            Gap(20)
            PrimaryButton(t(K.BACK_TO_DECK)) { onClose() }
        }
    }
}

@Composable
private fun BigStatChip(label: String, value: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Naval.ink) {
    Column(
        modifier
            .background(Naval.surface)
            .border(1.dp, Naval.line)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(value, style = NavalType.title, color = color)
        Gap(2)
        HudLabel(label, Naval.muted)
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

@Composable
private fun CodeField(
    value: String,
    enabled: Boolean,
    placeholder: String,
    uppercase: Boolean,
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
