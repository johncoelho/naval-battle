package br.com.navalbattle

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import br.com.navalbattle.audio.AppForeground
import br.com.navalbattle.audio.Music
import br.com.navalbattle.audio.MusicPlayer
import br.com.navalbattle.audio.THEME_PLAYLIST
import br.com.navalbattle.data.BetaStoreStatus
import br.com.navalbattle.data.CloudApi
import br.com.navalbattle.data.MilesStatus
import br.com.navalbattle.data.AppRelease
import br.com.navalbattle.data.appVersionName
import br.com.navalbattle.data.isNewerVersion
import br.com.navalbattle.data.DailyReminder
import br.com.navalbattle.data.DailyStatus
import br.com.navalbattle.data.SeasonEnd
import br.com.navalbattle.data.SeasonPassStatus
import br.com.navalbattle.data.CloudProfile
import br.com.navalbattle.data.CloudResult
import br.com.navalbattle.data.CommanderHit
import br.com.navalbattle.data.FeedbackUpdate
import br.com.navalbattle.data.FriendProfile
import br.com.navalbattle.data.Friendship
import br.com.navalbattle.data.GoogleAuth
import br.com.navalbattle.data.GoogleAuthResult
import br.com.navalbattle.data.LanGame
import br.com.navalbattle.data.LanLink
import br.com.navalbattle.data.LeaderboardEntry
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.data.MyRank
import br.com.navalbattle.data.OnlineLink
import br.com.navalbattle.data.OnlineMatch
import br.com.navalbattle.data.OpponentProfile
import br.com.navalbattle.data.SeasonInfo
import br.com.navalbattle.data.SeasonTrophy
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Protocol
import br.com.navalbattle.data.RankedOutcome
import br.com.navalbattle.data.Session
import br.com.navalbattle.data.appVersionLabel
import br.com.navalbattle.data.checkUpdateAvailable
import br.com.navalbattle.data.platformName
import br.com.navalbattle.data.nowMillis
import br.com.navalbattle.data.openStoreListing
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.DoubloonPack
import br.com.navalbattle.game.Badge
import br.com.navalbattle.game.EarnedBadge
import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.FleetCodec
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.isNetwork
import br.com.navalbattle.game.Profile
import br.com.navalbattle.game.QuickOfferKind
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.LanScreen
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.HandoffScreen
import br.com.navalbattle.ui.FeedbackFormScreen
import br.com.navalbattle.ui.FeedbackPopup
import br.com.navalbattle.ui.DailyPopup
import br.com.navalbattle.ui.FeedbackRewardPopup
import br.com.navalbattle.ui.FriendsScreen
import br.com.navalbattle.ui.InviteBanner
import br.com.navalbattle.ui.MilesPopup
import br.com.navalbattle.ui.RankedLockedPrompt
import br.com.navalbattle.ui.SeasonEndPopup
import br.com.navalbattle.ui.LeaderboardScreen
import br.com.navalbattle.ui.OnlineWaitingDialog
import br.com.navalbattle.ui.OpponentFoundPopup
import br.com.navalbattle.ui.MenuScreen
import br.com.navalbattle.ui.NamesScreen
import br.com.navalbattle.ui.OnlineScreen
import br.com.navalbattle.ui.PlacementScreen
import br.com.navalbattle.ui.ProfileScreen
import br.com.navalbattle.ui.QuickOfferBanner
import br.com.navalbattle.ui.QuickOfferNotice
import br.com.navalbattle.ui.SettingsScreen
import br.com.navalbattle.ui.ReleaseNotesScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.SeasonPopup
import br.com.navalbattle.ui.ShipyardScreen
import br.com.navalbattle.ui.StoreScreen
import br.com.navalbattle.ui.SplashScreen
import br.com.navalbattle.ui.UpdatePopup
import br.com.navalbattle.ui.WelcomeScreen

enum class Screen {
    SPLASH, WELCOME, MENU, SHIPYARD, STORE, PROFILE, SETTINGS, RELEASE_NOTES, LAN, ONLINE, NAMES,
    PLACEMENT, HANDOFF, BATTLE, RESULT, FRIENDS, LEADERBOARD, FEEDBACK
}

class AppState(val profile: Profile, private val cloud: CloudApi) {
    var screen by mutableStateOf(Screen.SPLASH)

    /** Aba em que a Loja abre: 0 cascos, 1 camuflagens, 2 habilidades (o Estaleiro manda direto pra aba certa). */
    var storeAisle by mutableStateOf(0)

    fun openStore(aisle: Int = 0) {
        storeAisle = aisle
        screen = Screen.STORE
    }
    var mode by mutableStateOf(GameMode.CLASSIC)
    var match by mutableStateOf<Match?>(null)

    /** O visual em uso — linha de casco e camuflagem — vem do perfil gravado no aparelho. */
    val skin: Skin
        get() = Skin(Paint.of(profile.equipped), FleetLine.of(profile.equippedFleet))

    /** Quem deve pegar o aparelho para posicionar a própria frota. */
    var handoffSide by mutableStateOf(Side.PLAYER)

    /** Trilha ligada. Os efeitos de combate continuam tocando de qualquer jeito. */
    var musicOn by mutableStateOf(profile.musicOn)

    /** Já existe uma versão mais nova publicada na faixa em que o comandante está. */
    var updateAvailable by mutableStateOf(false)

    /** Novidades das versões publicadas depois da instalada — a janela de atualização lista. */
    var updateNotes by mutableStateOf<List<AppRelease>>(emptyList())
        private set

    /**
     * Junta a tabela de novidades do servidor com a loja. Android: avisa quando a Play
     * confirma a atualização para este aparelho (a janela não aparece enquanto o
     * Google revisa) — ou, instalado fora da Play, quando a tabela tem versão nova.
     * iPhone: a tabela decide (o .ipa já está no site quando a versão é marcada lá).
     */
    suspend fun checkForUpdate() {
        val notes = (cloud.recentReleases(platformName) as? CloudResult.Ok)?.value.orEmpty()
            .filter { isNewerVersion(it.versionName, appVersionName) }
        val store = checkUpdateAvailable()
        updateNotes = notes
        updateAvailable = when {
            store != null -> store
            else -> notes.isNotEmpty()
        }
    }

    /**
     * Para onde ir depois da abertura: direto ao deque para quem já tem conta ou já
     * escolheu jogar como convidado; senão, pergunta uma vez na tela de boas-vindas.
     */
    fun afterSplash() {
        screen = if (profile.signedIn || profile.welcomeDone) Screen.MENU else Screen.WELCOME
    }

    fun newMatch(opponent: Opponent) {
        opponentProfile = null
        match = Match(mode, opponent)
        // no modo local os dois se identificam antes de posicionar as frotas
        screen = if (opponent == Opponent.LOCAL) Screen.NAMES else Screen.PLACEMENT
    }

    /**
     * Única troca de mãos do jogo: cobre a tela entre o posicionamento de um
     * comandante e o do outro. A batalha em si corre toda na mesma tela.
     */
    fun handoffToPlacement(side: Side) {
        handoffSide = side
        screen = Screen.HANDOFF
    }

    fun quitToMenu() {
        // avisa o outro aparelho antes de fechar, para ele não ficar esperando a
        // vez de alguém que já saiu — quem ficou leva a vitória na hora
        when (match?.opponent) {
            Opponent.LAN -> {
                link.send(Protocol.QUIT)
                // send() escreve numa thread à parte; um respiro curto garante que o
                // aviso saia no fio antes de fecharmos o socket embaixo dele
                val scope = uiScope
                if (scope != null) {
                    scope.launch { delay(200); closeLink() }
                } else {
                    closeLink()
                }
            }

            Opponent.ONLINE -> {
                // sair de uma ranqueada antes do fim é derrota cheia (critério 4 de
                // record_ranked_result) — o relato usa o id da sala já fotografado,
                // então segue mesmo com o link fechado logo abaixo
                val m = match
                if (m != null && m.phase != Phase.RESULT && onlineMatchRanked && !rankedResultSent) {
                    uiScope?.launch { reportRankedResult(victory = false, accuracy = 0, shipsLeft = 0) }
                }
                onlineLink.send(Protocol.QUIT)
                onlineLink.finish("abandoned")
                closeOnline()
            }

            else -> Unit
        }
        match = null
        screen = Screen.MENU
    }

    // ---------------- partida na rede local ----------------

    /** Corrotina da interface, para trazer as mensagens da rede para a thread da tela. */
    var uiScope: CoroutineScope? = null

    var linkState by mutableStateOf(LinkState.IDLE)
        private set
    var foundGames by mutableStateOf<List<LanGame>>(emptyList())
        private set

    /** Revanche em rede: os dois lados precisam pedir antes de a partida recomeçar. */
    var rematchRequestedByMe by mutableStateOf(false)
        private set
    var rematchRequestedByOpponent by mutableStateOf(false)
        private set

    private val link = LanLink()

    private fun onMain(block: () -> Unit) {
        val scope = uiScope
        if (scope == null) block() else scope.launch { block() }
    }

    /**
     * Anuncia a partida no Wi-Fi e espera alguém entrar. Quem hospeda joga primeiro.
     * [gameName] é só o rótulo que aparece na busca do outro aparelho — o nome do
     * comandante em si viaja à parte, no aperto de mão inicial.
     */
    fun hostGame(gameName: String) {
        link.close()
        linkState = LinkState.HOSTING
        val label = gameName.trim().take(24).ifBlank { t(K.LAN_DEFAULT_NAME, profile.displayName) }
        link.host(
            name = label,
            onState = { s -> onMain { onLinkState(s, Side.PLAYER) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun searchGames() {
        link.close()
        foundGames = emptyList()
        linkState = LinkState.SEARCHING
        link.search(
            onFound = { list -> onMain { foundGames = list } },
            onState = { s -> onMain { linkState = s } }
        )
    }

    fun joinGame(game: LanGame) {
        link.join(
            game = game,
            onState = { s -> onMain { onLinkState(s, Side.ENEMY) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun closeLink() {
        link.close()
        linkState = LinkState.IDLE
        foundGames = emptyList()
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
    }

    /** Conectou: abre a partida deste lado e se apresenta ao adversário. */
    private fun onLinkState(state: LinkState, side: Side) {
        linkState = state
        if (state != LinkState.CONNECTED) return
        startLanMatch(side)
    }

    /** Abre uma partida em rede — na primeira ligação e em toda revanche seguinte. */
    private fun startLanMatch(side: Side) {
        opponentProfile = null
        val m = Match(mode, Opponent.LAN, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        link.send(Protocol.hello(profile.displayName, mode.name))
        screen = Screen.PLACEMENT
    }

    // ---------------- partida online (internet) ----------------

    var onlineLinkState by mutableStateOf(LinkState.IDLE)
        private set

    /** Código da sala de amigo, para mostrar na tela enquanto espera alguém entrar. */
    var onlineCode by mutableStateOf<String?>(null)
        private set

    /**
     * true quando a sala aberta mirou um amigo específico — nesse caso o código é só
     * controle interno (o convidado recebe o convite direto por popup, sem precisar
     * digitar nada), então a tela de espera não deve mostrá-lo.
     */
    var onlineInvitedFriend by mutableStateOf(false)
        private set

    var friendResults by mutableStateOf<List<CommanderHit>>(emptyList())
        private set
    var friendships by mutableStateOf<List<Friendship>>(emptyList())
        private set

    private val onlineLink: OnlineLink by lazy { OnlineLink(cloud, uiScope!!) { renew() } }

    /**
     * Cria uma sala de amigo — quem cria sempre joga primeiro. Com [invitedId], mira
     * a sala num amigo específico: ele vê o convite como banner em qualquer tela do
     * jogo, em vez de precisar digitar um código.
     */
    fun createOnlineRoom(invitedId: String? = null) {
        val session = profile.currentSession() ?: return
        if (!hasMileOrPrompt()) return
        onlineLink.close()
        onlineCode = null
        onlineInvitedFriend = invitedId != null
        onlineLink.createRoom(
            session = session,
            mode = mode.name,
            invitedId = invitedId,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onCode = { code -> onMain { onlineCode = code } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /**
     * Procura uma partida rápida aberta; se não achar, fica esperando a própria.
     * Ranqueada só pareia com outra ranqueada — o toggle [rankedMode] decide, mas
     * só vale de verdade se a temporada corrente já foi aceita (ver [seasonPopupNeeded]).
     */
    fun startQuickMatchOnline() {
        val session = profile.currentSession() ?: return
        if (!hasMileOrPrompt()) return
        onlineLink.close()
        onlineCode = null
        onlineInvitedFriend = false
        onlineLink.quickMatch(
            session = session,
            mode = mode.name,
            ranked = rankedMode && !seasonPopupNeeded,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /** Entra numa sala de amigo pelo código que ele compartilhou por fora do jogo. */
    fun joinOnlineByCode(code: String) {
        val session = profile.currentSession() ?: return
        if (!hasMileOrPrompt()) return
        onlineLink.close()
        onlineLink.joinByCode(
            session = session,
            code = code,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    fun closeOnline() {
        onlineLink.close()
        onlineLinkState = LinkState.IDLE
        onlineCode = null
        onlineInvitedFriend = false
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
        selfPausedAtMillis = null
        stopOpponentPauseWatch()
    }

    /** Cancela a busca de partida rápida ou a sala aberta esperando alguém aceitar. */
    fun cancelOnlineWait() {
        if (onlineLinkState != LinkState.SEARCHING && onlineLinkState != LinkState.HOSTING) return
        onlineLink.finish("abandoned")
        closeOnline()
    }

    private fun onOnlineState(state: LinkState, side: Side) {
        onlineLinkState = state
        if (state != LinkState.CONNECTED) return
        startOnlineMatch(side)
    }

    private fun startOnlineMatch(side: Side) {
        // o modo é o da sala (quem a abriu escolheu) — o convite e o balão mostram
        // esse modo antes de aceitar, então a partida tem que bater com o que foi dito
        onlineLink.roomMode?.let { room -> GameMode.entries.firstOrNull { it.name == room }?.let { mode = it } }
        val m = Match(mode, Opponent.ONLINE, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        rankedResultSent = false
        rankedOutcome = null
        rankedReport = RankedReport.IDLE
        rankBefore = null
        // a sala é fotografada aqui: quando o adversário sai, o closeOnline zera o
        // OnlineLink (id da sala, ranqueada, adversário) antes da tela de resultado
        // abrir — sem isso quem vencia por abandono nunca relatava a vitória
        onlineMatchId = onlineLink.matchId
        onlineMatchRanked = onlineLink.ranked
        onlineOpponentId = onlineLink.opponentId
        opponentProfile = null
        milesGained = null
        spendMileFor(onlineMatchId)
        if (onlineMatchRanked) uiScope?.launch { loadRankBefore() }
        onlineLink.send(Protocol.hello(profile.displayName, mode.name))
        onlineLink.opponentId?.let { id -> uiScope?.launch { loadOpponentProfile(id) } }
        screen = Screen.PLACEMENT
    }

    /** Procura comandantes pelo início do nome, para mandar pedido de amizade. */
    suspend fun searchCommander(query: String) {
        if (query.isBlank()) {
            friendResults = emptyList()
            return
        }
        val session = profile.currentSession() ?: return
        val r = cloud.searchCommander(session, query)
        friendResults = (r as? CloudResult.Ok)?.value.orEmpty()
    }

    suspend fun sendFriendRequest(hit: CommanderHit) {
        val session = profile.currentSession() ?: return
        cloud.sendFriendRequest(session, hit.id, profile.displayName, hit.username)
        refreshFriendships()
    }

    suspend fun respondFriendRequest(friendship: Friendship, accept: Boolean) {
        val session = profile.currentSession() ?: return
        cloud.respondFriendRequest(session, friendship.id, accept)
        refreshFriendships()
    }

    suspend fun removeFriendship(friendship: Friendship) {
        val session = profile.currentSession() ?: return
        cloud.removeFriendship(session, friendship.id)
        refreshFriendships()
    }

    /** Avatar escolhido por cada pessoa da lista de amigos, por id — vazio até carregar. */
    var friendAvatars by mutableStateOf<Map<String, String>>(mapOf("u1" to "of1", "u3" to "avm", "u4" to "om2", "u5" to "cpf", "u6" to "om1", "u7" to "avf"))
        private set

    suspend fun refreshFriendships() {
        val session = profile.currentSession() ?: return
        val r = cloud.listFriendships(session)
        friendships = (r as? CloudResult.Ok)?.value.orEmpty()
        (cloud.friendAvatars(session) as? CloudResult.Ok)?.value?.let { friendAvatars = it }
    }

    // ---------------- convite de amigo mirado (banner fora da tela Online) ----------------

    /** Convite pendente de algum amigo, se houver — vira o banner "fulano te convidou". */
    var pendingInvite by mutableStateOf<OnlineMatch?>(null)
        private set

    /**
     * Checa se algum amigo mandou convite mirado — só quando o comandante não está
     * em partida nenhuma e não há convite já mostrado na tela, senão trocaríamos o
     * banner debaixo do dedo de quem está lendo o de agora.
     */
    suspend fun pollPendingInvite() {
        if (!profile.signedIn || match != null || pendingInvite != null) return
        val session = profile.currentSession() ?: return
        val found = (cloud.findPendingInvite(session) as? CloudResult.Ok)?.value ?: return
        pendingInvite = found
    }

    /** Aceita o convite do banner: entra direto na sala, sem precisar do código. */
    fun acceptInvite() {
        val invite = pendingInvite ?: return
        val session = profile.currentSession() ?: return
        // sem milha o convite continua na tela: dá pra comprar no popup e aceitar
        if (!hasMileOrPrompt()) return
        pendingInvite = null
        onlineLink.close()
        onlineCode = null
        onlineLink.acceptInvite(
            session = session,
            matchId = invite.id,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /** Recusa sem entrar — o anfitrião para de esperar em vez de ficar preso na sala. */
    fun declineInvite() {
        val invite = pendingInvite ?: return
        pendingInvite = null
        val session = profile.currentSession() ?: return
        val scope = uiScope ?: return
        scope.launch { cloud.declineOnlineInvite(session, invite.id) }
    }

    // ---------------- partida rápida aceita de qualquer tela (balão) ----------------

    /** Sala de partida rápida alheia em exibição no balão, se houver. */
    var quickOffer by mutableStateOf<OnlineMatch?>(null)
        private set

    /** Aviso curto depois de um "Aceitar" que chegou tarde — some sozinho. */
    var quickOfferNotice by mutableStateOf<String?>(null)
        private set

    /** Salas dispensadas com "Agora não" (ou já tentadas) — só nesta sessão do app. */
    private val ignoredOffers = mutableSetOf<String>()

    /** "Depois" do popup de atualização — aqui para o balão saber se o popup está na tela. */
    var updatePopupDismissed by mutableStateOf(false)

    /**
     * Algum outro popup ocupando a tela agora — o balão nunca disputa espaço com
     * eles (mesmas condições que cada popup usa para se mostrar).
     */
    private val otherPopupShowing: Boolean
        get() = blockingPopupShowing || dailyPopupShowing

    /** Os popups que sempre têm a vez — o Diário de bordo espera eles saírem. */
    private val blockingPopupShowing: Boolean
        get() {
            if (pendingInvite != null) return true
            if (updateAvailable && !updatePopupDismissed) return true
            if (seasonPopupShowing || seasonEnd != null || rankedLockedPrompt) return true
            if (feedbackReward != null) return true
            return feedbackPopupNeeded && !updateAvailable && !seasonPopupShowing
        }

    /** Que tipos de sala o comandante aceita agora — ranqueada só com a temporada aceita. */
    private fun quickOfferTypes(): Pair<Boolean, Boolean> {
        val kind = profile.quickOfferKind
        val casual = kind == QuickOfferKind.CASUAL || kind == QuickOfferKind.BOTH
        val ranked = (kind == QuickOfferKind.RANKED || kind == QuickOfferKind.BOTH) &&
            currentSeason != null && !seasonPopupNeeded
        return casual to ranked
    }

    /**
     * Roda junto com o polling de convite (a cada 4s). Só procura com a opção ligada
     * nos Ajustes, app em primeiro plano, fora de qualquer partida, sem estar
     * procurando/hospedando/entrando numa sala e sem outro popup na tela. Com o
     * balão já aberto, só confere se a sala continua esperando — se alguém entrou
     * ou o anfitrião desistiu, o balão some sozinho.
     */
    suspend fun pollQuickOffer() {
        val blocked = !profile.signedIn || !profile.quickOfferEnabled || !AppForeground.active ||
            match != null || otherPopupShowing ||
            onlineLinkState == LinkState.SEARCHING || onlineLinkState == LinkState.HOSTING ||
            onlineLinkState == LinkState.CONNECTING
        if (blocked) {
            quickOffer = null
            return
        }
        val session = profile.currentSession() ?: return
        val shown = quickOffer
        if (shown != null) {
            val fresh = (cloud.getOnlineMatch(session, shown.id) as? CloudResult.Ok)?.value
            if (fresh == null || fresh.status != "waiting" || fresh.guestId != null) quickOffer = null
            return
        }
        val (casual, ranked) = quickOfferTypes()
        if (!casual && !ranked) return
        val found = (cloud.findQuickOffer(session, casual, ranked, ignoredOffers.toSet()) as? CloudResult.Ok)?.value ?: return
        // o estado pode ter mudado durante a chamada (entrou numa partida, abriu popup)
        if (match == null && !otherPopupShowing && quickOffer == null) quickOffer = found
    }

    /**
     * Aceita a sala do balão: assume o modo dela (Clássico ou Tático, venha de onde
     * vier) e entra pelo mesmo caminho do convidado da partida rápida. Se outra
     * pessoa entrou primeiro, mostra "Essa partida já começou" e segue a vida.
     */
    fun acceptQuickOffer() {
        val room = quickOffer ?: return
        val session = profile.currentSession() ?: return
        if (!hasMileOrPrompt()) return
        quickOffer = null
        ignoredOffers += room.id
        mode = GameMode.entries.firstOrNull { it.name == room.mode } ?: GameMode.CLASSIC
        onlineLink.close()
        onlineCode = null
        onlineInvitedFriend = false
        onlineLink.joinQuickOffer(
            session = session,
            room = room,
            onState = { s, side -> onMain { onOnlineState(s, side) } },
            onTaken = { onMain { quickOfferNotice = t(K.QUICK_OFFER_TAKEN) } },
            onLine = { line -> onMain { onLine(line) } }
        )
    }

    /** "Agora não": esconde esta sala até o app ser fechado. */
    fun dismissQuickOffer() {
        val room = quickOffer ?: return
        ignoredOffers += room.id
        quickOffer = null
    }

    fun clearQuickOfferNotice() {
        quickOfferNotice = null
    }

    // ---------------- ranqueada e temporadas ----------------

    /** Casual (padrão) ou ranqueada — só afeta partida rápida; convite de amigo é sempre casual. */
    var rankedMode by mutableStateOf(false)

    var currentSeason by mutableStateOf<SeasonInfo?>(null)
        private set

    // ---------------- passe de temporada ----------------

    /** Passe da temporada corrente, vindo do servidor (ver `supabase/season.sql`). */
    var seasonPass by mutableStateOf<SeasonPassStatus?>(null)
        private set

    /**
     * Verdadeiro enquanto o comandante não aderiu à temporada corrente — sem adesão
     * (Passe Gratuito ou Passe de Temporada) não há ranqueada.
     */
    val seasonPopupNeeded: Boolean
        get() = profile.signedIn && seasonPass?.joined == false

    val canPlayRanked: Boolean get() = seasonPass?.joined == true

    /** "Agora não" no convite da temporada nova: some até o app ser aberto de novo. */
    var seasonPopupDismissed by mutableStateOf(false)

    /** Banner aberto pelo chip "Temporada de…" do menu (para ver o passe ou fazer upgrade). */
    var seasonPassOpen by mutableStateOf(false)

    /** Aviso de que a ranqueada exige adesão — aparece ao tocar em Ranqueada sem passe. */
    var rankedLockedPrompt by mutableStateOf(false)

    val seasonPopupShowing: Boolean
        get() = profile.signedIn && seasonPass != null &&
            (seasonPassOpen || (seasonPopupNeeded && !seasonPopupDismissed))

    var seasonBusy by mutableStateOf(false)
        private set
    var seasonNotice by mutableStateOf<String?>(null)
        private set

    /** Fechamento da temporada que acabou (posição e prêmio) — vira o popup de fim de temporada. */
    var seasonEnd by mutableStateOf<SeasonEnd?>(null)
        private set

    suspend fun loadSeason() {
        if (!profile.signedIn) return
        val session = profile.currentSession() ?: return
        currentSeason = (cloud.currentSeason(session) as? CloudResult.Ok)?.value
        loadSeasonPass()
    }

    suspend fun loadSeasonPass() {
        if (!profile.signedIn) {
            seasonPass = null
            return
        }
        (authed { session -> cloud.seasonPassStatus(session) } as? CloudResult.Ok)?.value?.let { seasonPass = it }
    }

    /**
     * Adere à temporada (Passe Gratuito ou de Temporada) ou faz upgrade para o de Temporada.
     * O preço sai dos dobrões do aparelho antes de pedir ao servidor e volta se ele
     * recusar; o passe pago entrega os dobrões extras e a camuflagem da estação aqui,
     * e as milhas bônus no servidor.
     */
    suspend fun joinSeason(premium: Boolean) {
        val pass = seasonPass ?: return
        if (seasonBusy) return
        val price = when {
            !premium -> 0
            pass.joined -> pass.upgradePrice
            else -> pass.entryPrice
        }
        if (price > 0 && profile.credits < price) {
            seasonNotice = t(K.SEASON_NO_DOUBLOONS, price - profile.credits)
            return
        }
        seasonBusy = true
        seasonNotice = null
        if (price > 0) profile.spendCredits(price)
        val r = authed { session -> cloud.joinSeason(session, if (premium) "premium" else "free") }
        seasonBusy = false
        val joined = (r as? CloudResult.Ok)?.value
        if (joined == null) {
            if (price > 0) profile.grantCredits(price)
            seasonNotice = t(K.SEASON_JOIN_FAILED)
            return
        }
        // o servidor não cobrou nada (já tinha esse passe): devolve
        if (price > 0 && joined.price == 0) profile.grantCredits(price)
        if (premium && joined.price > 0) {
            profile.grantCredits(pass.passDoubloons)
            Paint.ofSeason(pass.seasonKey)?.let { profile.buy(it.id, 0) }
        }
        miles = miles?.copy(miles = joined.miles)
        seasonPass = pass.copy(tier = joined.tier)
        seasonNotice = if (joined.tier == "premium") t(K.SEASON_JOINED_PREMIUM) else t(K.SEASON_JOINED_FREE)
        seasonPopupDismissed = true
    }

    fun closeSeasonPopup() {
        seasonPassOpen = false
        seasonPopupDismissed = true
        seasonNotice = null
    }

    /** Ranqueada sem adesão: em vez de travar calado, explica e oferece o passe. */
    fun openSeasonFromRanked() {
        rankedLockedPrompt = false
        seasonNotice = null
        seasonPassOpen = true
    }

    /** Resgata (uma vez) o resultado da temporada que acabou e credita os dobrões do prêmio. */
    suspend fun checkSeasonEnd() {
        if (!profile.signedIn || seasonEnd != null) return
        val end = (authed { session -> cloud.claimSeasonEnd(session) } as? CloudResult.Ok)?.value ?: return
        profile.grantCredits(end.doubloons)
        seasonEnd = end
        loadMiles()
    }

    fun ackSeasonEnd() {
        seasonEnd = null
    }

    /** Lembrete de avaliação na loja — a cada tantas partidas, ver [Profile.feedbackNextPromptAt]. */
    val feedbackPopupNeeded: Boolean
        get() = !profile.feedbackOptedOut && profile.matches >= profile.feedbackNextPromptAt

    /** Se a partida em andamento é ranqueada — soma pontos quando terminar. */
    var onlineMatchRanked by mutableStateOf(false)
        private set

    /** Id da sala e do adversário da partida online atual, fotografados ao conectar. */
    private var onlineMatchId: String? = null
    var onlineOpponentId: String? = null
        private set

    /** Retrato e patente do adversário da sala online atual — carregado ao conectar. */
    var opponentProfile by mutableStateOf<OpponentProfile?>(null)
        private set

    private suspend fun loadOpponentProfile(opponentId: String) {
        val session = profile.currentSession() ?: return
        opponentProfile = (cloud.opponentProfile(session, opponentId) as? CloudResult.Ok)?.value
    }

    private var rankedResultSent = false

    /** Andamento do relato ranqueado, para o bloco de ranking da tela de resultado. */
    enum class RankedReport { IDLE, PENDING, DONE, FAILED }

    var rankedReport by mutableStateOf(RankedReport.IDLE)
        private set

    /** O que o servidor devolveu ao fechar a partida ranqueada — nulo até lá. */
    var rankedOutcome by mutableStateOf<RankedOutcome?>(null)
        private set

    /** Posição e pontos da temporada ANTES da partida, lidos ao conectar. */
    var rankBefore by mutableStateOf<MyRank?>(null)
        private set

    private suspend fun loadRankBefore() {
        rankBefore = (authed { session -> cloud.myRank(session, season = true) } as? CloudResult.Ok)?.value
    }

    /**
     * Fecha o resultado ranqueado uma única vez por partida (a flag evita reenvio).
     * [accuracy] e [shipsLeft] (a própria frota, não a do adversário) pesam no bônus
     * do vencedor — mas quem decide o vencedor é o servidor, pelo primeiro relato
     * da sala (ver `record_ranked_result` em `supabase/online.sql`). Abandono
     * (desistir ou estourar os 60s em segundo plano) relata derrota com acerto 0.
     */
    suspend fun reportRankedResult(victory: Boolean, accuracy: Int, shipsLeft: Int) {
        if (rankedResultSent || !onlineMatchRanked) return
        val matchId = onlineMatchId ?: return
        if (profile.currentSession() == null) return
        rankedResultSent = true
        rankedReport = RankedReport.PENDING
        // uma partida ranqueada, turno a turno, facilmente passa da 1h de vida do
        // token — sem o `authed`, o envio falhava calado com o token vencido e o
        // placar nunca chegava a subir (nem a cair) pra ninguém
        val r = authed { session -> cloud.recordRankedResult(session, matchId, victory, accuracy, shipsLeft) }
        val outcome = (r as? CloudResult.Ok)?.value
        rankedOutcome = outcome
        rankedReport = if (outcome != null) RankedReport.DONE else RankedReport.FAILED
        loadMyRank(leaderboardSeasonMode)
    }

    /** "Ver ranking" do resultado: sai da sala como o "Voltar ao deque" e abre a temporada. */
    fun openLeaderboardFromResult() {
        quitToMenu()
        leaderboardSeasonMode = true
        screen = Screen.LEADERBOARD
    }

    /**
     * Recompensa (XP, medalhas, créditos) de uma partida online — só se ainda couber
     * no limite diário contra este adversário (ver [Profile.claimOnlineReward]).
     */
    fun claimOnlineReward(): Boolean {
        val opponent = onlineOpponentId ?: return false
        return profile.claimOnlineReward(opponent, nowMillis() / MILLIS_PER_DAY)
    }

    // ---------------- milhas náuticas ----------------

    /** Saldo e regras das milhas, vindos do servidor (ver `supabase/economy.sql`). Nulo sem conta. */
    var miles by mutableStateOf<MilesStatus?>(null)
        private set

    /** Popup de milhas aberto: pelo selo do topo do menu ou por falta de milha ao entrar online. */
    var milesPopup by mutableStateOf(false)

    /** Milhas ganhas na última vitória online — a tela de resultado mostra. */
    var milesGained by mutableStateOf<Int?>(null)
        private set

    var milesNotice by mutableStateOf<String?>(null)
        private set

    // ---------------- Diário de bordo (check-in e desafio do dia) ----------------

    /** Trilha de check-in e desafio de hoje, vindos do servidor (ver `supabase/daily.sql`). */
    var daily by mutableStateOf<DailyStatus?>(null)
        private set

    /** Balão aberto pelo chip do menu. */
    var dailyPopupOpen by mutableStateOf(false)

    /** Fechado nesta entrada do app — volta a aparecer na próxima vez que ele abrir. */
    private var dailyAutoDismissed by mutableStateOf(false)

    var dailyBusy by mutableStateOf(false)
        private set
    var dailyNotice by mutableStateOf<String?>(null)
        private set

    val dailyProgress: Int
        get() = daily?.let { profile.dailyProgress(it.today, it.mission) } ?: 0

    val dailyChallengeDone: Boolean
        get() = daily?.let { dailyProgress >= it.missionTarget } ?: false

    /** Tem prêmio esperando: check-in de hoje ou desafio cumprido e não resgatado. */
    val dailyPending: Boolean
        get() {
            val d = daily ?: return false
            return !d.checkedIn || (!d.challengeClaimed && dailyChallengeDone)
        }

    /**
     * O balão aparece sozinho toda vez que o comandante entra no app com prêmio
     * esperando (só no menu e sem outro popup na frente), ou quando ele toca no chip.
     */
    val dailyPopupShowing: Boolean
        get() = profile.signedIn && daily != null && match == null &&
            (dailyPopupOpen || (dailyPending && !dailyAutoDismissed && screen == Screen.MENU && !blockingPopupShowing))

    suspend fun loadDaily() {
        if (!profile.signedIn) {
            daily = null
            DailyReminder.cancel()
            return
        }
        (authed { session -> cloud.dailyStatus(session) } as? CloudResult.Ok)?.value?.let { daily = it }
        scheduleReminder()
    }

    /** App voltou ao primeiro plano: o balão pode aparecer de novo. */
    fun onAppEntered() {
        dailyAutoDismissed = false
    }

    suspend fun dailyCheckin() {
        if (dailyBusy || daily?.checkedIn != false) return
        dailyBusy = true
        dailyNotice = null
        val r = authed { session -> cloud.dailyCheckin(session) }
        dailyBusy = false
        val done = (r as? CloudResult.Ok)?.value
        if (done == null) {
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        profile.grantCredits(done.doubloons)
        daily = daily?.copy(checkedIn = true, streak = done.streak)
        dailyNotice = when {
            done.weekCompleted -> t(K.DAILY_WEEK_DONE, done.doubloons)
            done.doubloons > 0 -> t(K.DAILY_GOT, done.doubloons)
            else -> null
        }
        // momento em que o comandante está engajado: pede a notificação aqui, não na abertura
        if (profile.reminderOn) DailyReminder.requestPermission()
        scheduleReminder()
    }

    suspend fun claimDailyChallenge() {
        val d = daily ?: return
        if (dailyBusy || !d.checkedIn || d.challengeClaimed || !dailyChallengeDone) return
        dailyBusy = true
        dailyNotice = null
        val r = authed { session -> cloud.claimDailyChallenge(session, d.mission, dailyProgress) }
        dailyBusy = false
        val pay = (r as? CloudResult.Ok)?.value
        if (pay == null) {
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        profile.grantCredits(pay)
        daily = d.copy(challengeClaimed = true)
        if (pay > 0) dailyNotice = t(K.DAILY_GOT, pay)
    }

    fun closeDailyPopup() {
        dailyPopupOpen = false
        dailyAutoDismissed = true
        dailyNotice = null
    }

    /** Fim de partida: soma ao desafio do dia (com a data de hoje fresca do servidor). */
    suspend fun recordDailyMatch(m: Match) {
        loadDaily()
        val d = daily ?: return
        val wasDone = dailyChallengeDone
        val me = m.mySide
        profile.recordDailyMatch(
            day = d.today,
            won = m.winner == me,
            sunk = m.sunkBy(me),
            abilities = m.abilitiesUsedBy(me),
            accuracy = m.accuracyOf(me)
        )
        // acabou de cumprir: o balão volta a aparecer no menu com o "Resgatar"
        if (!wasDone && dailyChallengeDone && !d.challengeClaimed) dailyAutoDismissed = false
    }

    fun setReminder(on: Boolean) {
        profile.setReminder(on)
        if (on) DailyReminder.requestPermission()
        scheduleReminder()
    }

    private fun scheduleReminder() {
        val d = daily
        if (!profile.reminderOn || !profile.signedIn || d == null) {
            if (!profile.reminderOn || !profile.signedIn) DailyReminder.cancel()
            return
        }
        DailyReminder.schedule(t(K.REMINDER_TITLE), t(K.REMINDER_BODY, d.checkinReward), skipToday = d.checkedIn)
    }

    suspend fun loadMiles() {
        if (profile.currentSession() == null) {
            miles = null
            return
        }
        (authed { session -> cloud.milesStatus(session) } as? CloudResult.Ok)?.value?.let { miles = it }
    }

    /**
     * Toda partida online custa 1 milha. Sem milha, abre o popup (com a compra) em vez
     * de entrar. Sem saldo carregado ainda (rede lenta), deixa passar — o servidor
     * cobra na entrada de qualquer jeito.
     */
    private fun hasMileOrPrompt(): Boolean {
        val m = miles ?: return true
        if (m.miles >= 1) return true
        milesNotice = null
        milesPopup = true
        return false
    }

    private fun spendMileFor(matchId: String?) {
        val id = matchId ?: return
        uiScope?.launch {
            (authed { session -> cloud.spendMile(session, id) } as? CloudResult.Ok)?.value?.let { left ->
                miles = miles?.copy(miles = left)
            }
        }
    }

    /** Milhas da vitória online — chamado pela tela de resultado depois do relato ranqueado. */
    suspend fun awardWinMiles() {
        val id = onlineMatchId ?: return
        val before = miles?.miles
        val r = authed { session -> cloud.awardWinMiles(session, id) }
        val after = (r as? CloudResult.Ok)?.value ?: return
        miles = miles?.copy(miles = after)
        milesGained = before?.let { (after - it).coerceAtLeast(0) }
    }

    /** Compra um pacote de milhas com dobrões; devolve os dobrões se o servidor recusar. */
    suspend fun buyMilesPack() {
        val m = miles ?: return
        if (profile.credits < m.packPrice) {
            milesNotice = t(K.MILES_NO_DOUBLOONS, m.packPrice - profile.credits)
            return
        }
        if (m.miles + m.packSize > m.cap) {
            milesNotice = t(K.MILES_CAP_REACHED, m.cap)
            return
        }
        profile.spendCredits(m.packPrice)
        val r = authed { session -> cloud.buyMiles(session, 1) }
        val after = (r as? CloudResult.Ok)?.value
        if (after == null) {
            profile.grantCredits(m.packPrice)
            milesNotice = t(K.MILES_BUY_FAILED)
        } else {
            miles = m.copy(miles = after)
            milesNotice = t(K.MILES_BOUGHT, m.packSize)
        }
    }

    // ---------------- loja de dobrões (simulada para beta testers) ----------------

    var betaStore by mutableStateOf<BetaStoreStatus?>(null)
        private set

    var betaStoreNotice by mutableStateOf<String?>(null)
        private set

    var betaStoreBusy by mutableStateOf(false)
        private set

    suspend fun loadBetaStore() {
        if (profile.currentSession() == null) {
            betaStore = null
            return
        }
        (authed { session -> cloud.betaStoreStatus(session) } as? CloudResult.Ok)?.value?.let { betaStore = it }
    }

    /**
     * Compra simulada: o servidor confere badge e limite do dia e registra; só os
     * dobrões que voltam de lá entram no saldo (mesmo padrão do feedback).
     */
    suspend fun buyBetaPack(pack: DoubloonPack) {
        if (betaStoreBusy) return
        betaStoreBusy = true
        val r = authed { session -> cloud.buyBetaPack(session, pack.code) }
        betaStoreBusy = false
        when (r) {
            is CloudResult.Ok -> {
                profile.grantCredits(r.value.doubloons)
                betaStore = betaStore?.copy(spentCents = r.value.spentCents, limitCents = r.value.limitCents)
                betaStoreNotice = t(K.DOUBLOON_BOUGHT, r.value.doubloons)
            }
            is CloudResult.Fail -> {
                betaStoreNotice = when {
                    r.message.contains("store_limit") -> t(K.DOUBLOON_LIMIT_REACHED)
                    r.message.contains("store_not_beta") -> t(K.DOUBLOON_BETA_ONLY)
                    else -> t(K.DOUBLOON_BUY_FAILED)
                }
                loadBetaStore()
            }
        }
    }

    // ---------------- placar ----------------

    var leaderboardSeasonMode by mutableStateOf(true)
    var leaderboardEntries by mutableStateOf<List<LeaderboardEntry>>(emptyList())
        private set
    var myRank by mutableStateOf<MyRank?>(null)
        private set
    var seasonTrophies by mutableStateOf<List<SeasonTrophy>>(emptyList())
        private set

    suspend fun loadLeaderboard(season: Boolean) {
        val session = profile.currentSession() ?: return
        val r = if (season) cloud.leaderboardSeason(session) else cloud.leaderboardOverall(session)
        leaderboardEntries = (r as? CloudResult.Ok)?.value.orEmpty()
        loadMyRank(season)
    }

    /** Ranking de troféus da última temporada fechada — ouro, prata e bronze. */
    suspend fun loadSeasonTrophies() {
        val session = profile.currentSession() ?: return
        seasonTrophies = (cloud.seasonTrophies(session) as? CloudResult.Ok)?.value.orEmpty()
    }

    private suspend fun loadMyRank(season: Boolean) {
        val session = profile.currentSession() ?: return
        myRank = (cloud.myRank(session, season) as? CloudResult.Ok)?.value
    }

    // ---------------- feedback e badges ----------------

    var feedbackSending by mutableStateOf(false)
    var feedbackSent by mutableStateOf(false)
    /** Mensagem do último envio que falhou (limite de pendentes ou rede) — nula quando deu certo. */
    var feedbackError by mutableStateOf<String?>(null)

    /** Para onde o "Voltar" da tela de feedback leva — Ajustes ou Perfil, quem abriu. */
    var feedbackReturn by mutableStateOf(Screen.SETTINGS)

    /**
     * Para onde voltar depois de entrar na conta, quando o perfil foi aberto só para
     * isso (cartão Online do menu sem login). Nulo = perfil aberto normalmente.
     */
    var loginReturn by mutableStateOf<Screen?>(null)

    /** Abre o perfil já na seção de conta; ao entrar, volta para [from]. */
    fun openLogin(from: Screen) {
        loginReturn = from
        screen = Screen.PROFILE
    }

    fun openFeedback(from: Screen) {
        feedbackReturn = from
        feedbackSent = false
        feedbackError = null
        screen = Screen.FEEDBACK
    }

    /** Manda um bug ou sugestão — precisa de conta, senão não tem pra quem devolver a recompensa. */
    suspend fun submitFeedback(kind: String, message: String) {
        if (profile.currentSession() == null) return
        feedbackSending = true
        feedbackError = null
        val result = authed { s ->
            cloud.submitFeedback(s, kind, message, appVersionLabel, platformName, I18n.lang.code)
        }
        feedbackSending = false
        feedbackSent = result is CloudResult.Ok
        if (result is CloudResult.Fail) {
            feedbackError = t(if ("feedback_limit" in result.message) K.FEEDBACK_FORM_LIMIT else K.FEEDBACK_FORM_ERROR)
        }
    }

    /** Feedback avaliado (aprovado ou recusado) em exibição no popup, já reivindicado e aplicado. */
    var feedbackReward by mutableStateOf<FeedbackUpdate?>(null)
        private set

    private val feedbackQueue = ArrayDeque<FeedbackUpdate>()

    /**
     * Checa se algum feedback foi avaliado — mesmo padrão de polling de [loadSeason],
     * sem push de verdade; roda na abertura e ao voltar do segundo plano. Todos os
     * avaliados entram numa fila e aparecem um popup por vez.
     */
    suspend fun checkFeedbackRewards() {
        if (feedbackReward != null || feedbackQueue.isNotEmpty()) return
        val session = profile.currentSession() ?: return
        val updates = (cloud.pendingFeedbackUpdates(session) as? CloudResult.Ok)?.value.orEmpty()
        feedbackQueue.addAll(updates)
        showNextFeedbackReward()
    }

    /**
     * Reivindica no servidor PRIMEIRO e só aplica a recompensa que voltou de lá — se o
     * app morrer no meio, ou outro aparelho já tiver reivindicado, nada é dado duas vezes.
     */
    private suspend fun showNextFeedbackReward() {
        while (feedbackQueue.isNotEmpty()) {
            val next = feedbackQueue.removeFirst()
            val claimed = (authed { s -> cloud.claimFeedback(s, next.id) } as? CloudResult.Ok)?.value ?: continue
            if (claimed.status == "approved") {
                if (claimed.rewardCredits > 0) profile.grantCredits(claimed.rewardCredits)
                Ability.entries.firstOrNull { it.code == claimed.rewardAbilityCode }?.let { ability ->
                    repeat(claimed.rewardCharges.coerceAtLeast(1)) { profile.grantAbilityCharge(ability) }
                }
            }
            feedbackReward = claimed
            return
        }
    }

    /** Fecha o popup atual e mostra o próximo da fila, se houver. */
    fun ackFeedbackReward() {
        val shown = feedbackReward ?: return
        feedbackReward = null
        // um feedback aprovado pode ter acabado de render o badge de Colaborador
        uiScope?.launch {
            if (shown.status == "approved") loadBadges()
            showNextFeedbackReward()
        }
    }

    var badges by mutableStateOf<List<EarnedBadge>>(emptyList())
        private set

    suspend fun loadBadges() {
        val session = profile.currentSession() ?: run { badges = emptyList(); return }
        val rows = (cloud.myBadges(session) as? CloudResult.Ok)?.value ?: return
        badges = rows.mapNotNull { row -> Badge.of(row.code)?.let { EarnedBadge(it, row.earnedAt) } }
    }

    // ---------------- perfil de amigo ----------------

    var viewedFriendProfile by mutableStateOf<FriendProfile?>(null)
        private set
    var friendProfileLoading by mutableStateOf(false)
        private set

    suspend fun loadFriendProfile(friendId: String) {
        val session = profile.currentSession() ?: return
        friendProfileLoading = true
        viewedFriendProfile = (cloud.friendProfile(session, friendId) as? CloudResult.Ok)?.value
        friendProfileLoading = false
    }

    fun closeFriendProfile() {
        viewedFriendProfile = null
    }

    // ---------------- ações compartilhadas entre LAN e online ----------------

    private fun sendToOpponent(line: String) {
        when (match?.opponent) {
            Opponent.LAN -> link.send(line)
            Opponent.ONLINE -> onlineLink.send(line)
            else -> Unit
        }
    }

    /** Uma linha chegou do outro aparelho — mesmo protocolo para LAN e online. */
    private fun onLine(line: String) {
        val m = match ?: return
        val parts = Protocol.parts(line)
        when (parts.firstOrNull()) {
            Protocol.HELLO -> parts.getOrNull(1)?.let { m.setName(m.mySide.other(), it) }

            Protocol.FLEET -> parts.getOrNull(1)
                ?.let { m.applyRemoteFleet(FleetCodec.decode(it)) }

            Protocol.ABILITY -> parts.getOrNull(1)
                ?.let { code -> Ability.entries.firstOrNull { it.code == code } }
                ?.let { m.selectAbility(it, ignoreCooldown = parts.getOrNull(2) == "1", fromRemote = true) }

            Protocol.ACT -> {
                val x = parts.getOrNull(1)?.toIntOrNull() ?: return
                val y = parts.getOrNull(2)?.toIntOrNull() ?: return
                m.act(Coord(x, y))
            }

            Protocol.TAUNT -> parts.getOrNull(1)?.let { m.sendTaunt(m.mySide.other(), it) }

            Protocol.REMATCH -> {
                rematchRequestedByOpponent = true
                maybeStartRematch()
            }

            Protocol.QUIT -> {
                m.abandon(m.mySide)
                if (m.opponent == Opponent.LAN) closeLink() else closeOnline()
            }

            Protocol.PAUSE -> startOpponentPauseWatch()
            Protocol.RESUME -> stopOpponentPauseWatch()

            Protocol.DROP -> if (m.opponent == Opponent.ONLINE) {
                m.lostByDisconnect()
                closeOnline()
            }
        }
    }

    // ---------------- conexão instável (online) ----------------

    /** Este aparelho perdeu o servidor no meio da partida online — a tela avisa. */
    val onlineSelfOffline: Boolean
        get() = match?.opponent == Opponent.ONLINE && match?.phase == Phase.BATTLE && onlineLink.selfOffline

    /**
     * Segundos até a vitória por queda do adversário — nulo enquanto ele estiver
     * aparecendo (ou já avisou que pausou, que tem contagem própria). Os primeiros
     * [DROP_GRACE_SECONDS] sem sinal não contam: uma oscilação curta não assusta ninguém.
     */
    val opponentDropSecondsLeft: Int?
        get() {
            val m = match ?: return null
            if (m.opponent != Opponent.ONLINE || m.phase != Phase.BATTLE || opponentPaused) return null
            val away = onlineLink.opponentAwaySeconds ?: return null
            if (away < DROP_GRACE_SECONDS) return null
            return (DROP_GRACE_SECONDS + DROP_TIMEOUT_SECONDS - away).coerceAtLeast(0)
        }

    /** O prazo acabou com o adversário fora: vitória, e o aviso fica na sala para ele. */
    fun claimDropVictory() {
        val m = match ?: return
        if (m.opponent != Opponent.ONLINE || m.phase != Phase.BATTLE) return
        if (opponentDropSecondsLeft != 0) return
        onlineLink.send(Protocol.DROP)
        onlineLink.finish("abandoned")
        m.abandon(m.mySide)
        closeOnline()
    }

    // ---------------- pausa em segundo plano (online) ----------------

    /** Instante em que ESTE aparelho foi para segundo plano — nulo enquanto ativo. */
    private var selfPausedAtMillis: Long? = null
    private var opponentPausedAtMillis: Long? = null
    private var opponentPauseJob: Job? = null

    /** Se o adversário avisou que pausou — a tela de batalha mostra a contagem. */
    var opponentPaused by mutableStateOf(false)
        private set
    var opponentPauseSecondsLeft by mutableStateOf(PAUSE_TIMEOUT_SECONDS)
        private set

    /**
     * Chamado sempre que o app inteiro entra ou sai de primeiro plano (ver
     * `AppForeground` e o `LaunchedEffect` em [App]). Só importa durante o combate
     * de uma partida online — cada lado mede os 60s pelo próprio relógio ([nowMillis]),
     * então não precisa confiar em nenhuma mensagem do outro para decidir a própria
     * desistência, só para avisar a interface do adversário.
     */
    fun onForegroundChanged(active: Boolean) {
        val m = match ?: return
        if (m.opponent != Opponent.ONLINE || m.phase != Phase.BATTLE) return
        if (!active) {
            if (selfPausedAtMillis != null) return
            selfPausedAtMillis = nowMillis()
            onlineLink.send(Protocol.PAUSE)
        } else {
            val pausedAt = selfPausedAtMillis ?: return
            selfPausedAtMillis = null
            if (nowMillis() - pausedAt >= PAUSE_TIMEOUT_MS) {
                m.forfeitByTimeout()
            } else {
                onlineLink.send(Protocol.RESUME)
            }
        }
    }

    private fun startOpponentPauseWatch() {
        opponentPausedAtMillis = nowMillis()
        opponentPaused = true
        opponentPauseSecondsLeft = PAUSE_TIMEOUT_SECONDS
        opponentPauseJob?.cancel()
        opponentPauseJob = uiScope?.launch {
            while (opponentPauseSecondsLeft > 0) {
                delay(1000)
                opponentPauseSecondsLeft--
            }
            if (opponentPaused) match?.abandon(match!!.mySide)
            opponentPaused = false
        }
    }

    private fun stopOpponentPauseWatch() {
        opponentPauseJob?.cancel()
        opponentPauseJob = null
        opponentPaused = false
    }

    /** Dispara e conta ao adversário — os dois aparelhos resolvem o mesmo tiro. */
    fun fireShared(coord: Coord) {
        val m = match ?: return
        if (m.opponent.isNetwork()) sendToOpponent(Protocol.act(coord.x, coord.y))
        m.act(coord)
    }

    fun useAbilityShared(ability: Ability, ignoreCooldown: Boolean = false) {
        val m = match ?: return
        if (m.opponent.isNetwork()) sendToOpponent(Protocol.ability(ability.code, ignoreCooldown))
        m.selectAbility(ability, ignoreCooldown)
    }

    /** Manda a própria frota assim que ela é confirmada. */
    fun sendFleet() {
        val m = match ?: return
        if (!m.opponent.isNetwork()) return
        sendToOpponent(Protocol.fleet(FleetCodec.encode(m.board(m.mySide).ships)))
    }

    /** Emoji ou grito de guerra: decoração pura, não passa pela lógica da partida. */
    fun sendTaunt(code: String) {
        val m = match ?: return
        if (!m.opponent.isNetwork()) return
        sendToOpponent(Protocol.taunt(code))
        m.sendTaunt(m.mySide, code)
    }

    /**
     * Pede revanche na mesma ligação: os dois lados precisam pedir para a partida
     * recomeçar, senão um dos dois ficaria esperando sem saber que o outro já saiu
     * da tela de resultado.
     */
    fun requestRematch() {
        val m = match ?: return
        val connected = when (m.opponent) {
            Opponent.LAN -> linkState == LinkState.CONNECTED
            Opponent.ONLINE -> onlineLinkState == LinkState.CONNECTED
            else -> false
        }
        if (!connected) return
        rematchRequestedByMe = true
        sendToOpponent(Protocol.REMATCH)
        maybeStartRematch()
    }

    private fun maybeStartRematch() {
        if (!rematchRequestedByMe || !rematchRequestedByOpponent) return
        val old = match ?: return
        rematchRequestedByMe = false
        rematchRequestedByOpponent = false
        when (old.opponent) {
            Opponent.LAN -> startLanMatch(old.mySide)
            Opponent.ONLINE -> startOnlineMatch(old.mySide)
            else -> Unit
        }
    }

    // ---------------- conta e sincronização ----------------

    /** Cria a conta e já sobe a carreira que existir neste aparelho. */
    suspend fun createAccount(email: String, password: String, username: String): Pair<Boolean, String> =
        when (val r = cloud.signUp(email, password, username)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                profile.rename(username)
                cloud.saveProfile(r.value, profile.snapshot())
                true to t(K.AUTH_CREATED)
            }

            is CloudResult.Fail -> false to r.message
        }

    /**
     * Entra na conta. Se a nuvem tiver carreira mais avançada, ela vence; se a deste
     * aparelho estiver na frente, é ela que sobe. Nunca se perde o maior progresso.
     */
    suspend fun signIn(email: String, password: String): Pair<Boolean, String> =
        when (val r = cloud.signIn(email, password)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                true to merged
            }

            is CloudResult.Fail -> false to r.message
        }

    private val googleAuth = GoogleAuth()

    /**
     * Entra com a conta Google: pede a credencial ao sistema e troca o token de
     * identidade por uma sessão no Supabase. Mesmo gatilho de fusão de carreira do
     * login por e-mail — se o comandante já tinha conta pelo e-mail do Google, cai
     * na mesma carreira (ver `supabase/schema.sql`, `on_auth_user_created`).
     */
    suspend fun signInWithGoogle(): Pair<Boolean, String> = when (val g = googleAuth.signIn()) {
        is GoogleAuthResult.Cancelled -> false to ""
        is GoogleAuthResult.Fail -> false to g.message
        is GoogleAuthResult.Ok -> when (val r = cloud.signInWithGoogle(g.idToken)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                true to merged
            }

            is CloudResult.Fail -> false to r.message
        }
    }

    /** Último retrato já gravado na nuvem — evita regravar a mesma carreira. */
    private var lastPushed: CloudProfile? = null

    /**
     * Sincronização automática: fica olhando a carreira inteira (patente, créditos,
     * estatísticas, cosméticos, retrato e idioma) e grava na nuvem pouco depois de
     * cada mudança. Não existe mais botão de sincronizar — o `collectLatest` com
     * `delay` faz o papel de debounce, então uma rajada de mudanças (comprar e
     * equipar em seguida, por exemplo) vira uma gravação só.
     */
    suspend fun autoSync() {
        snapshotFlow { profile.snapshot() }
            .distinctUntilChanged()
            .collectLatest { snap ->
                delay(SYNC_DEBOUNCE_MS)
                if (!profile.signedIn || snap == lastPushed) return@collectLatest
                if (authed { session -> cloud.saveProfile(session, snap) } is CloudResult.Ok) {
                    lastPushed = snap
                }
            }
    }

    /**
     * Na abertura do app, com conta conectada: renova a sessão e traz o que estiver
     * na nuvem. É o que garante token válido antes da primeira sincronização do dia.
     */
    suspend fun resumeSession() {
        if (!profile.signedIn) return
        renew()
        mergeWithCloud()
    }

    /**
     * Roda [block] com a sessão atual e, se ela tiver vencido, renova o token com o
     * refresh guardado e repete uma vez. O comandante não vê nada disso.
     */
    private suspend fun <T> authed(block: suspend (Session) -> CloudResult<T>): CloudResult<T> {
        val session = profile.currentSession()
            ?: return CloudResult.Fail(t(K.AUTH_SIGN_IN_TO_SYNC))
        val first = block(session)
        if (first !is CloudResult.Fail || !first.expired) return first

        val renewed = renew() ?: return CloudResult.Fail(t(K.AUTH_EXPIRED))
        return block(renewed)
    }

    /** Troca o refresh token guardado por uma sessão nova. */
    private suspend fun renew(): Session? {
        val current = profile.currentSession() ?: return null
        return when (val r = cloud.refresh(current.refreshToken)) {
            is CloudResult.Ok -> {
                profile.rememberSession(r.value)
                r.value
            }
            // refresh recusado: a conta continua no aparelho, só a sessão caiu
            is CloudResult.Fail -> null
        }
    }

    /**
     * Troca a senha de quem já está logado. Reautentica com a senha atual antes —
     * é o que impede alguém que ache o celular destravado de trocar a senha sem
     * saber a de verdade — e só então chama a troca com o token confirmado.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String): Pair<Boolean, String> {
        val email = profile.accountEmail
        if (email.isBlank()) return false to t(K.AUTH_SIGN_IN_TO_SYNC)

        return when (val reauth = cloud.signIn(email, currentPassword)) {
            is CloudResult.Fail -> false to reauth.message
            is CloudResult.Ok -> {
                profile.rememberSession(reauth.value)
                when (val r = cloud.updatePassword(reauth.value, newPassword)) {
                    is CloudResult.Ok -> true to t(K.AUTH_PASSWORD_CHANGED)
                    is CloudResult.Fail -> false to r.message
                }
            }
        }
    }

    /** "Esqueci minha senha": manda o link de recuperação para o e-mail informado. */
    suspend fun forgotPassword(email: String): Pair<Boolean, String> =
        when (val r = cloud.sendPasswordReset(email)) {
            is CloudResult.Ok -> true to t(K.AUTH_RESET_SENT)
            is CloudResult.Fail -> false to r.message
        }

    /** Devolve a mensagem da fusão, ou nulo quando nem isso foi possível. */
    private suspend fun mergeWithCloud(): String? =
        when (val remote = authed { session -> cloud.loadProfile(session) }) {
            is CloudResult.Ok -> {
                val cloudProfile = remote.value
                if (cloudProfile != null && cloudProfile.xp > profile.xp) {
                    profile.adopt(cloudProfile)
                    lastPushed = cloudProfile
                    t(K.AUTH_RESTORED)
                } else {
                    val snap = profile.snapshot()
                    authed { session -> cloud.saveProfile(session, snap) }
                    lastPushed = snap
                    t(K.AUTH_UPLOADED)
                }
            }

            is CloudResult.Fail -> null
        }

    private companion object {
        /** Espera depois da última mudança antes de gravar — junta rajadas numa só. */
        const val SYNC_DEBOUNCE_MS = 1500L
        const val PAUSE_TIMEOUT_SECONDS = 60
        const val DROP_GRACE_SECONDS = 10
        const val DROP_TIMEOUT_SECONDS = 60
        const val PAUSE_TIMEOUT_MS = PAUSE_TIMEOUT_SECONDS * 1000L
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

@Composable
fun App() {
    val profile = remember { Profile(Prefs()) }
    val cloud = remember { CloudApi() }
    val state = remember { AppState(profile, cloud) }
    // idioma escolhido pelo comandante, aplicado antes de a primeira tela desenhar
    remember(profile.langCode) { I18n.lang = Lang.of(profile.langCode); profile.langCode }
    val scope = rememberCoroutineScope()
    state.uiScope = scope
    val music = remember { MusicPlayer() }
    // playlist do deque: uma faixa por abertura do app, em rodízio — fixada uma vez
    // por sessão (remember), pra não trocar de música sozinha a cada recomposição
    val theme = remember { THEME_PLAYLIST[profile.rollThemeTrack(THEME_PLAYLIST.size)] }

    // a trilha acompanha a tela: tema no deque, faixa de combate na batalha (com o mar
    // por baixo), e a variante intensa quando algum lado fica com um navio só
    val inBattle = state.screen == Screen.BATTLE
    val lastStand = inBattle && state.match?.lastStand == true
    LaunchedEffect(state.screen, state.musicOn, AppForeground.active, lastStand) {
        profile.setMusic(state.musicOn)
        if (!state.musicOn || !AppForeground.active) {
            music.stop()
        } else {
            music.play(
                when {
                    lastStand -> Music.BATTLE_INTENSE
                    inBattle -> Music.BATTLE
                    else -> theme
                }
            )
            music.ambient(inBattle)
        }
    }
    DisposableEffect(Unit) { onDispose { music.release() } }

    // com conta conectada, a abertura já renova a sessão e busca o que há na nuvem
    LaunchedEffect(Unit) { state.resumeSession() }

    // daí em diante a carreira sobe sozinha a cada mudança — não existe botão de
    // sincronizar, o comandante nunca precisa lembrar de gravar nada
    LaunchedEffect(Unit) { state.autoSync() }

    // partida online em segundo plano: avisa o adversário e mede os 60s de prazo
    // (ver AppState.onForegroundChanged) — nas outras variantes de partida, o
    // próprio sistema já suspende as corrotinas de turno enquanto o app não está
    // em primeiro plano, então não precisa de aviso nenhum
    LaunchedEffect(AppForeground.active) { state.onForegroundChanged(AppForeground.active) }

    // avisa se já existe uma versão mais nova publicada, sem precisar de servidor de push —
    // também ao voltar do segundo plano, senão quem deixa o app aberto nunca via o aviso
    LaunchedEffect(AppForeground.active) {
        if (AppForeground.active) state.checkForUpdate()
    }

    // temporada ranqueada corrente — vem do servidor pra não depender do relógio
    // do aparelho; se for diferente da última aceita, o popup de nova temporada aparece
    LaunchedEffect(Unit) { state.loadSeason() }

    // badges e feedback avaliado: mesmo padrão sem push de verdade — checa na
    // abertura e toda vez que o app volta do segundo plano. O popup de recompensa
    // aparece por conta própria via FeedbackRewardPopup, olhando state.feedbackReward
    LaunchedEffect(AppForeground.active) {
        if (AppForeground.active) {
            state.loadBadges()
            state.checkFeedbackRewards()
            // a temporada pode virar com o app aberto: relê o passe e o fechamento
            state.loadSeason()
            state.checkSeasonEnd()
            // Diário de bordo: toda entrada no app pode mostrar o balão de novo
            state.onAppEntered()
        }
    }

    // milhas náuticas: recarga do dia vem do servidor — relê ao abrir, ao voltar do
    // segundo plano, ao entrar/sair da conta e ao voltar ao deque depois de partida
    LaunchedEffect(AppForeground.active, state.profile.signedIn, state.screen == Screen.MENU) {
        if (AppForeground.active) {
            state.loadMiles()
            state.loadDaily()
        }
    }

    // convite de amigo mirado: com o app aberto e fora de partida, checa de tempos em
    // tempos se alguém convidou — é o que alimenta o banner em qualquer tela do jogo
    // — no mesmo laço, o balão de partida rápida para quem marcou nos Ajustes que
    // está disponível (ver AppState.pollQuickOffer, que tem as próprias travas)
    LaunchedEffect(Unit) {
        while (true) {
            state.pollPendingInvite()
            state.pollQuickOffer()
            delay(4000)
        }
    }

    NavalTheme {
        val focusManager = LocalFocusManager.current
        Box(
            Modifier.fillMaxSize().background(Naval.bg)
                // no iOS o teclado não tem botão de recolher (diferente do "voltar" do
                // Android) — sem isso ele fica preso na tela pra sempre, escondendo até
                // o botão de fechar por baixo dele
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            when (state.screen) {
                Screen.SPLASH -> SplashScreen(state)
                Screen.WELCOME -> WelcomeScreen(state)
                Screen.MENU -> MenuScreen(state)
                Screen.SHIPYARD -> ShipyardScreen(state)
                Screen.STORE -> StoreScreen(state)
                Screen.PROFILE -> ProfileScreen(state)
                Screen.SETTINGS -> SettingsScreen(state)
                Screen.RELEASE_NOTES -> ReleaseNotesScreen(state)
                Screen.LAN -> LanScreen(state)
                Screen.ONLINE -> OnlineScreen(state)
                Screen.NAMES -> state.match?.let { NamesScreen(state, it) }
                Screen.PLACEMENT -> state.match?.let { PlacementScreen(state, it) }
                Screen.HANDOFF -> state.match?.let { HandoffScreen(state, it) }
                Screen.BATTLE -> state.match?.let { BattleScreen(state, it) }
                Screen.RESULT -> state.match?.let { ResultScreen(state, it) }
                Screen.FRIENDS -> FriendsScreen(state)
                Screen.LEADERBOARD -> LeaderboardScreen(state)
                Screen.FEEDBACK -> FeedbackFormScreen(state)
            }
            OnlineWaitingDialog(state)
            state.pendingInvite?.let { invite -> InviteBanner(state, invite) }
            // o polling já trava o balão nas situações proibidas; a checagem aqui
            // cobre o intervalo de até 4s entre uma rodada e outra
            state.quickOffer?.let { offer ->
                if (state.match == null && state.pendingInvite == null) QuickOfferBanner(state, offer)
            }
            state.quickOfferNotice?.let { notice -> QuickOfferNotice(state, notice) }
            if (state.screen == Screen.PLACEMENT) OpponentFoundPopup(state)
            UpdatePopup(state)
            if (state.match == null) SeasonEndPopup(state)
            if (state.match == null) SeasonPopup(state)
            RankedLockedPrompt(state)
            DailyPopup(state)
            FeedbackPopup(state)
            if (state.match == null) FeedbackRewardPopup(state)
            // por último: abre por cima do convite quando falta milha para aceitar
            MilesPopup(state)
        }
    }
}
