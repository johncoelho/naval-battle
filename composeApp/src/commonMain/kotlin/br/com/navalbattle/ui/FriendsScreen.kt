package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.navalbattle.game.Rank
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.data.FriendProfile
import br.com.navalbattle.data.Friendship
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawAvatar
import br.com.navalbattle.design.drawInsignia
import br.com.navalbattle.game.Avatar
import br.com.navalbattle.game.Insignia
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Um amigo (ou pedido) já resolvido para a tela: quem é o outro lado e a amizade em si. */
private data class FriendEntry(val id: String, val name: String, val friendship: Friendship)

/**
 * Tela de amigos. Cada amigo é um cartão com monograma, nome e uma ação principal
 * clara (Jogar); o resto (perfil, remover) fica na folha de serviço, que abre ao
 * tocar no cartão — antes as quatro ações dividiam a linha com o nome, quebravam
 * no meio da palavra e empurravam o "Remover" para fora da tela. Pedidos recebidos
 * vêm primeiro, com destaque; os enviados ficam numa seção própria, separados dos
 * amigos. A busca roda sozinha enquanto digita (com uma pausa curta).
 */
@Composable
fun FriendsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var viewed by remember { mutableStateOf<FriendEntry?>(null) }
    var confirmRemove by remember { mutableStateOf<FriendEntry?>(null) }
    var sentTo by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        state.refreshFriendships()
        // presença e pedidos mudam com a tela aberta (aceite do outro lado, pedido novo):
        // relê tudo a cada 20s — antes só a presença, e o "pedido enviado" ficava preso
        while (true) {
            delay(20_000)
            state.refreshFriendships()
        }
    }
    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            state.searchCommander("")
            return@LaunchedEffect
        }
        delay(350)
        state.searchCommander(query.trim())
    }

    val myId = state.profile.accountId
    fun other(f: Friendship) = if (f.requesterId == myId)
        FriendEntry(f.addresseeId, f.addresseeUsername, f) else FriendEntry(f.requesterId, f.requesterUsername, f)

    val incoming = state.friendships.filter { it.status == "pending" && it.addresseeId == myId }.map { other(it) }
    val outgoing = state.friendships.filter { it.status == "pending" && it.requesterId == myId }.map { other(it) }
    // online primeiro (quem pode receber convite agora), depois por quem foi visto há menos tempo
    val friends = state.friendships.filter { it.status == "accepted" }.map { other(it) }
        .sortedWith(compareBy<FriendEntry>({ !state.friendOnline(it.id) }, {
            if (state.friendInMatch(it.id)) 0 else state.friendPresence[it.id]?.seenSecs?.takeIf { s -> s >= 0 } ?: Int.MAX_VALUE
        }, { it.name.lowercase() }))
    val onlineCount = friends.count { state.friendOnline(it.id) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(t(K.FRIENDS_TITLE).uppercase(), style = NavalType.title, color = Naval.ink, modifier = Modifier.weight(1f))
            HudLabel(t(K.FRIENDS_COUNT, friends.size), Naval.amberStrong)
        }
        Gap(14)
        val focus = LocalFocusManager.current
        val runSearch = {
            focus.clearFocus()
            if (query.trim().length >= 2) scope.launch { state.searchCommander(query.trim()) }
        }
        SearchField(query, onChange = { query = it }, onClear = { query = "" }, onSearch = { runSearch() })
        // status da busca: sem isso não dá para saber se procurou e não achou ou se nem buscou
        if (query.isNotBlank()) {
            val trimmed = query.trim()
            val found = state.friendResults.count { it.id != myId }
            Gap(6)
            when {
                trimmed.length < 2 -> HudLabel(t(K.FRIENDS_SEARCH_MIN), Naval.muted)
                state.friendSearching || state.friendSearchedFor != trimmed -> HudLabel(t(K.FRIENDS_SEARCHING), Naval.amberStrong)
                found == 0 -> HudLabel(t(K.FRIENDS_SEARCH_EMPTY), Naval.danger)
                found == 1 -> HudLabel(t(K.FRIENDS_SEARCH_FOUND_ONE), Naval.greenBright)
                else -> HudLabel(t(K.FRIENDS_SEARCH_FOUND, found), Naval.greenBright)
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // resultados da busca, logo abaixo do campo
            if (query.trim().length >= 2) {
                Gap(10)
                if (state.friendResults.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.friendResults.filter { it.id != myId }.forEach { hit ->
                            val known = state.friendships.any {
                                (it.requesterId == myId && it.addresseeId == hit.id) ||
                                    (it.addresseeId == myId && it.requesterId == hit.id)
                            }
                            PersonCard(hit.username, subtitle = null, avatarId = hit.avatar) {
                                if (known || hit.id in sentTo) {
                                    HudLabel(t(K.FRIENDS_REQUEST_SENT), Naval.muted)
                                } else {
                                    PillButton(t(K.FRIENDS_ADD), Naval.amber, Naval.amberInk) {
                                        sentTo = sentTo + hit.id
                                        scope.launch { state.sendFriendRequest(hit) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (incoming.isNotEmpty()) {
                SectionHeader(t(K.FRIENDS_REQUESTS), incoming.size, Naval.amberStrong)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    incoming.forEach { e ->
                        PersonCard(e.name, subtitle = t(K.FRIENDS_WANTS_TO_ADD), avatarId = state.friendAvatars[e.id], highlight = true) {
                            PillButton(t(K.FRIENDS_DECLINE), Naval.surface, Naval.inkSoft, border = Naval.line) {
                                scope.launch { state.respondFriendRequest(e.friendship, false) }
                            }
                            Spacer(Modifier.width(6.dp))
                            PillButton(t(K.FRIENDS_ACCEPT), Naval.greenBright, Naval.bg) {
                                scope.launch { state.respondFriendRequest(e.friendship, true) }
                            }
                        }
                    }
                }
            }

            SectionHeader(t(K.FRIENDS_LIST), friends.size, Naval.muted)
            if (friends.isNotEmpty()) {
                HudLabel(t(K.FRIENDS_ONLINE_COUNT, onlineCount), if (onlineCount > 0) Naval.greenBright else Naval.muted)
                Gap(6)
            }
            if (friends.isEmpty()) {
                EmptyFleet()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    friends.forEach { e ->
                        val online = state.friendOnline(e.id)
                        val invitable = state.friendInvitable(e.id)
                        val xp = state.friendPresence[e.id]?.xp ?: -1
                        PersonCard(
                            e.name,
                            subtitle = if (xp >= 0) t(Rank.of(xp).key) else null,
                            avatarId = state.friendAvatars[e.id],
                            online = online,
                            dim = !online,
                            onClick = {
                                viewed = e
                                scope.launch { state.loadFriendProfile(e.id) }
                            }
                        ) {
                            when {
                                invitable -> PillButton(t(K.FRIENDS_PLAY), Naval.amber, Naval.amberInk) {
                                    state.pickMode(ModePick.Friend(e.id))
                                }
                                state.friendInMatch(e.id) -> HudLabel(t(K.FRIENDS_BUSY), Naval.amberStrong)
                                online -> HudLabel(t(K.FRIENDS_NO_INVITES_SHORT), Naval.muted)
                                else -> HudLabel(lastSeenShort(state.friendPresence[e.id]?.seenSecs), Naval.muted)
                            }
                        }
                    }
                }
            }

            if (outgoing.isNotEmpty()) {
                SectionHeader(t(K.FRIENDS_SENT), outgoing.size, Naval.muted)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    outgoing.forEach { e ->
                        PersonCard(e.name, subtitle = t(K.FRIENDS_WAITING), avatarId = state.friendAvatars[e.id], dim = true) {
                            PillButton(t(K.FRIENDS_CANCEL), Naval.surface, Naval.inkSoft, border = Naval.line) {
                                scope.launch { state.removeFriendship(e.friendship) }
                            }
                        }
                    }
                }
            }
            Gap(16)
        }

        Gap(10)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.screen = Screen.MENU }
    }

    viewed?.let { e ->
        FriendProfileDialog(
            name = e.name,
            avatarId = state.viewedFriendProfile?.avatar?.ifBlank { null } ?: state.friendAvatars[e.id],
            loading = state.friendProfileLoading,
            profile = state.viewedFriendProfile,
            onInvite = if (state.friendInvitable(e.id)) {
                {
                    viewed = null
                    state.closeFriendProfile()
                    state.pickMode(ModePick.Friend(e.id))
                }
            } else null,
            onRemove = { confirmRemove = e },
            onClose = { viewed = null; state.closeFriendProfile() }
        )
    }

    confirmRemove?.let { e ->
        ConfirmRemoveDialog(
            name = e.name,
            onKeep = { confirmRemove = null },
            onRemove = {
                confirmRemove = null
                viewed = null
                state.closeFriendProfile()
                scope.launch { state.removeFriendship(e.friendship) }
            }
        )
    }
}

// ------------------------------------------------------------------ peças

/** Quanto tempo fora, curto para caber à direita do cartão: "há 5 min", "há 3 h", "Offline". */
@Composable
private fun lastSeenShort(seenSecs: Int?): String = when {
    seenSecs == null || seenSecs < 0 -> t(K.FRIENDS_OFFLINE)
    seenSecs < 3600 -> t(K.FRIENDS_SEEN_MIN, maxOf(1, seenSecs / 60))
    seenSecs < 86_400 -> t(K.FRIENDS_SEEN_HOURS, seenSecs / 3600)
    else -> t(K.FRIENDS_SEEN_DAYS, seenSecs / 86_400)
}

/** Cor do monograma, fixa por nome — cada amigo sempre com a mesma. */
private fun monogramColor(name: String): Color {
    val palette = listOf(Naval.amberStrong, Naval.commanderOne, Naval.commanderTwo, Naval.greenBright, Naval.danger, Naval.inkSoft)
    return palette[(name.lowercase().hashCode() and 0x7fffffff) % palette.size]
}

/**
 * Rosto do comandante: o avatar que ele escolheu no perfil (vem do servidor) dentro
 * do círculo com a cor dele; enquanto não carregou, ou sem avatar, a inicial do nome.
 */
@Composable
private fun PlayerBadge(name: String, avatarId: String?, dim: Boolean = false, size: Int = 40) {
    if (avatarId.isNullOrBlank()) {
        Monogram(name, dim, size)
        return
    }
    val color = monogramColor(name)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (dim) 0.06f else 0.12f))
            .border(1.5.dp, color.copy(alpha = if (dim) 0.4f else 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size((size * 0.78f).dp)) {
            drawAvatar(
                avatar = Avatar.of(avatarId),
                center = Offset(this.size.width / 2f, this.size.height / 2f),
                size = this.size.minDimension,
                // mesma cor de destaque do menu e do perfil — tingir com a cor do anel
                // deixava o rosto esverdeado; a cor de cada um fica no anel
                color = Naval.amberStrong.copy(alpha = if (dim) 0.5f else 1f)
            )
        }
    }
}

@Composable
private fun Monogram(name: String, dim: Boolean = false, size: Int = 40) {
    val color = monogramColor(name)
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (dim) 0.08f else 0.16f))
            .border(1.5.dp, color.copy(alpha = if (dim) 0.4f else 0.9f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = NavalType.title,
            color = color.copy(alpha = if (dim) 0.5f else 1f)
        )
    }
}

/**
 * Cartão de uma pessoa: monograma, nome em cima e legenda embaixo (empilhados, nunca
 * lado a lado), ações à direita. Nome longo corta com reticências em vez de quebrar.
 */
@Composable
private fun PersonCard(
    name: String,
    subtitle: String?,
    avatarId: String? = null,
    highlight: Boolean = false,
    dim: Boolean = false,
    online: Boolean = false,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlight) Naval.amber.copy(alpha = 0.08f) else Naval.surface2)
            .border(1.dp, if (highlight) Naval.amber else Naval.line)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            PlayerBadge(name, avatarId, dim)
            if (online) {
                // bolinha de "online agora" no canto do retrato
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Naval.greenBright)
                        .border(2.dp, Naval.surface2, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = NavalType.mono,
                color = if (dim) Naval.inkSoft else Naval.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Gap(2)
                Text(
                    it,
                    style = NavalType.monoSmall,
                    color = if (online) Naval.greenBright else Naval.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** Botão compacto do cartão — texto numa linha só, nunca quebrado no meio. */
@Composable
private fun PillButton(text: String, background: Color, content: Color, border: Color? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), style = NavalType.monoSmall, color = content, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    Gap(22)
    Row(verticalAlignment = Alignment.CenterVertically) {
        HudLabel(title.uppercase(), color)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(Naval.surface).border(1.dp, Naval.lineSoft).padding(horizontal = 6.dp, vertical = 1.dp)) {
            Text(count.toString(), style = NavalType.monoSmall, color = Naval.inkSoft)
        }
    }
    Gap(8)
}

@Composable
private fun EmptyFleet() {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Naval.lineSoft)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // radar vazio: nenhum contato no alcance
        Canvas(Modifier.size(56.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            drawCircle(Naval.green.copy(alpha = 0.6f), r - 1f, c, style = Stroke(1.5f))
            drawCircle(Naval.green.copy(alpha = 0.35f), r * 0.55f, c, style = Stroke(1.2f))
            drawLine(Naval.greenBright, c, Offset(c.x + r * 0.8f, c.y - r * 0.45f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
        Gap(10)
        Text(t(K.FRIENDS_EMPTY), style = NavalType.body, color = Naval.inkSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, onClear: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Row(
        Modifier
            .weight(1f)
            .background(Naval.surface2)
            .border(1.dp, if (value.isBlank()) Naval.line else Naval.amber)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // lupa desenhada
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension * 0.32f
            val c = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(Naval.muted, r, c, style = Stroke(2f))
            drawLine(Naval.muted, Offset(c.x + r * 0.72f, c.y + r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(24)) },
            singleLine = true,
            textStyle = NavalType.mono.copy(color = Naval.ink),
            cursorBrush = SolidColor(Naval.amberStrong),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f)
        ) { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) Text(t(K.FRIENDS_SEARCH_HINT), style = NavalType.mono, color = Naval.muted)
                inner()
            }
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            HudLabel("✕", Naval.inkSoft, Modifier.clickable(onClick = onClear).padding(4.dp))
        }
    }
    Spacer(Modifier.width(8.dp))
    // botão explícito: o resultado já aparece enquanto digita, mas sem botão parecia não buscar
    val ready = value.trim().length >= 2
    Box(
        Modifier
            .background(if (ready) Naval.amber else Naval.surface2)
            .border(1.dp, if (ready) Naval.amber else Naval.line)
            .clickable(enabled = ready, onClick = onSearch)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(t(K.FRIENDS_SEARCH_BUTTON).uppercase(), style = NavalType.button, color = if (ready) Naval.amberInk else Naval.muted, maxLines = 1)
    }
    }
}

@Composable
private fun FriendProfileDialog(
    name: String,
    avatarId: String?,
    loading: Boolean,
    profile: FriendProfile?,
    onInvite: (() -> Unit)?,
    onRemove: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.amber)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            HudLabel(t(K.FRIENDS_PROFILE_TITLE).uppercase(), Naval.muted)
            Gap(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerBadge(name, avatarId, size = 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = NavalType.title, color = Naval.amberStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    profile?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // patente (insígnia) ao lado do XP
                            Canvas(Modifier.size(16.dp)) {
                                drawInsignia(
                                    insignia = Insignia.of(it.insignia),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    size = size.minDimension,
                                    color = Naval.amberStrong
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            HudLabel("${it.xp} XP · ${t(K.LEADERBOARD_TITLE)} ${it.rankedRating}", Naval.muted)
                        }
                    }
                }
            }
            Gap(16)
            when {
                loading -> HudLabel(t(K.FRIENDS_PROFILE_LOADING), Naval.muted)
                profile == null -> HudLabel(t(K.FRIENDS_PROFILE_NOT_FOUND), Naval.danger)
                else -> {
                    val rate = if (profile.matches == 0) 0 else profile.wins * 100 / profile.matches
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigStatChip(t(K.PROFILE_MATCHES), profile.matches.toString(), Modifier.weight(1f))
                        BigStatChip(t(K.PROFILE_WINS), profile.wins.toString(), Modifier.weight(1f), Naval.greenBright)
                        BigStatChip(t(K.FRIENDS_WIN_RATE), "$rate%", Modifier.weight(1f))
                    }
                    Gap(8)
                    HudLabel("${t(K.PROFILE_BEST_STREAK)}: ${profile.bestStreak}", Naval.muted)
                }
            }
            Gap(20)
            if (onInvite != null) {
                PrimaryButton(t(K.FRIENDS_INVITE), modifier = Modifier.fillMaxWidth()) { onInvite() }
            } else {
                HudLabel(t(K.FRIENDS_INVITE_OFFLINE), Naval.muted)
            }
            Gap(8)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.FRIENDS_CLOSE), modifier = Modifier.weight(1f).fillMaxHeight()) { onClose() }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, Naval.danger.copy(alpha = 0.6f))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t(K.FRIENDS_REMOVE).uppercase(), style = NavalType.button, color = Naval.danger)
                }
            }
        }
    }
}

@Composable
private fun ConfirmRemoveDialog(name: String, onKeep: () -> Unit, onRemove: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.9f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onKeep() }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Naval.surface2)
                .border(1.dp, Naval.danger)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(20.dp)
        ) {
            Text(t(K.FRIENDS_REMOVE_CONFIRM, name), style = NavalType.mono, color = Naval.ink)
            Gap(6)
            HudLabel(t(K.FRIENDS_REMOVE_CONFIRM_SUB), Naval.muted)
            Gap(18)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(t(K.FRIENDS_KEEP), modifier = Modifier.weight(1f).fillMaxHeight()) { onKeep() }
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(Naval.danger).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Text(t(K.FRIENDS_REMOVE).uppercase(), style = NavalType.button, color = Naval.bg)
                }
            }
        }
    }
}

@Composable
private fun BigStatChip(label: String, value: String, modifier: Modifier = Modifier, color: Color = Naval.ink) {
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
