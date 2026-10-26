package br.com.navalbattle

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import br.com.navalbattle.data.BuyOutcome
import br.com.navalbattle.data.DobroesStoreStatus
import br.com.navalbattle.data.StoreBilling
import br.com.navalbattle.data.AdValeStatus
import br.com.navalbattle.data.AdsStatus
import br.com.navalbattle.data.RewardedAds
import br.com.navalbattle.data.StoreProduct
import br.com.navalbattle.data.CloudApi
import br.com.navalbattle.data.MilesStatus
import br.com.navalbattle.data.AppRelease
import br.com.navalbattle.data.appVersionName
import br.com.navalbattle.data.isNewerVersion
import br.com.navalbattle.data.DailyReminder
import br.com.navalbattle.data.ReminderMessage
import br.com.navalbattle.data.ReminderTexts
import br.com.navalbattle.game.ReminderKind
import br.com.navalbattle.game.ReturnReminder
import br.com.navalbattle.game.epochDayOf
import br.com.navalbattle.data.PushInbox
import br.com.navalbattle.data.DailyStatus
import br.com.navalbattle.data.SeasonEnd
import br.com.navalbattle.data.SeasonPassStatus
import br.com.navalbattle.data.CloudProfile
import br.com.navalbattle.data.CloudResult
import br.com.navalbattle.data.CommanderHit
import br.com.navalbattle.data.FeedbackUpdate
import br.com.navalbattle.data.FriendProfile
import br.com.navalbattle.data.Friendship
import br.com.navalbattle.data.GameServices
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
import br.com.navalbattle.data.AgeSignals
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.PrefsBatch
import br.com.navalbattle.data.Protocol
import br.com.navalbattle.data.FriendPresence
import br.com.navalbattle.data.PushMessaging
import br.com.navalbattle.data.RankedOutcome
import br.com.navalbattle.data.Session
import br.com.navalbattle.data.appVersionLabel
import br.com.navalbattle.data.checkUpdateAvailable
import br.com.navalbattle.data.platformName
import br.com.navalbattle.data.deviceCheckKey
import br.com.navalbattle.data.StoreReview
import br.com.navalbattle.data.isTestLabDevice
import br.com.navalbattle.data.nowMillis
import br.com.navalbattle.data.openStoreListing
import br.com.navalbattle.data.send
import br.com.navalbattle.design.FleetLine
import br.com.navalbattle.design.Paint
import br.com.navalbattle.design.Skin
import br.com.navalbattle.design.Naval
import br.com.navalbattle.design.NavalTheme
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.AchievementCareer
import br.com.navalbattle.game.AchievementEvent
import br.com.navalbattle.game.LaunchNoticeDecision
import br.com.navalbattle.game.LaunchVariant
import br.com.navalbattle.game.launchNoticeDecision
import br.com.navalbattle.game.launchNoticeKey
import br.com.navalbattle.game.achievementsFor
import br.com.navalbattle.game.backfill
import br.com.navalbattle.game.leaderboardScore
import br.com.navalbattle.game.AdPhase
import br.com.navalbattle.game.AdPlace
import br.com.navalbattle.game.AdRules
import br.com.navalbattle.game.AgeBand
import br.com.navalbattle.game.AgeRules
import br.com.navalbattle.game.ReviewPrompt
import br.com.navalbattle.game.AdShowOutcome
import br.com.navalbattle.game.DoubloonPack
import br.com.navalbattle.game.Badge
import br.com.navalbattle.game.EarnedBadge
import br.com.navalbattle.game.Coord
import br.com.navalbattle.game.FleetCodec
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.GuidedEvent
import br.com.navalbattle.game.GuidedStep
import br.com.navalbattle.game.Rank
import br.com.navalbattle.game.ReferralInvite
import br.com.navalbattle.game.ReferralOutcome
import br.com.navalbattle.game.isValidReferralCodeShape
import br.com.navalbattle.game.normalizeReferralCode
import br.com.navalbattle.game.referralOutcome
import br.com.navalbattle.game.referralUnseenTotal
import br.com.navalbattle.game.WelcomeGift
import br.com.navalbattle.game.WeeklyOutcome
import br.com.navalbattle.game.WeeklyStatus
import br.com.navalbattle.game.weeklyCanClaim
import br.com.navalbattle.game.weeklyOutcome
import br.com.navalbattle.game.WelcomeOutcome
import br.com.navalbattle.game.nextGuidedStep
import br.com.navalbattle.game.shouldOfferGuided
import br.com.navalbattle.game.welcomeOutcome
import br.com.navalbattle.game.welcomeShouldRetry
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.PendingCredit
import br.com.navalbattle.game.ChargeRequest
import br.com.navalbattle.game.OnlineAwardQueue
import br.com.navalbattle.game.PendingAward
import br.com.navalbattle.game.PendingCredits
import br.com.navalbattle.game.QuickPractice
import br.com.navalbattle.game.UpdatePrompt
import br.com.navalbattle.game.Wallet
import br.com.navalbattle.game.PremioState
import br.com.navalbattle.game.avisoCompra
import br.com.navalbattle.game.avisoPremio
import br.com.navalbattle.game.premioState
import br.com.navalbattle.game.vitrineVisivel
import br.com.navalbattle.game.isNetwork
import br.com.navalbattle.game.Profile
import br.com.navalbattle.game.QuickOfferKind
import br.com.navalbattle.i18n.I18n
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.Lang
import br.com.navalbattle.i18n.t
import br.com.navalbattle.game.Side
import br.com.navalbattle.ui.LanScreen
import br.com.navalbattle.ui.ScreenFrame
import br.com.navalbattle.ui.SystemBackHandler
import br.com.navalbattle.ui.BattleScreen
import br.com.navalbattle.ui.HandoffScreen
import br.com.navalbattle.ui.FeedbackFormScreen
import br.com.navalbattle.ui.FeedbackPopup
import br.com.navalbattle.ui.DailyPopup
import br.com.navalbattle.ui.ModePick
import br.com.navalbattle.ui.ModePickerSheet
import br.com.navalbattle.ui.FeedbackRewardPopup
import br.com.navalbattle.ui.GuidedOfferPopup
import br.com.navalbattle.ui.WelcomeGiftPopup
import br.com.navalbattle.ui.FriendsScreen
import br.com.navalbattle.ui.InviteBanner
import br.com.navalbattle.ui.LaunchNoticePopup
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
import br.com.navalbattle.ui.ReferralPopup
import br.com.navalbattle.ui.SettingsScreen
import br.com.navalbattle.ui.ReleaseNotesScreen
import br.com.navalbattle.ui.ResultScreen
import br.com.navalbattle.ui.SeasonPopup
import br.com.navalbattle.ui.ShipyardScreen
import br.com.navalbattle.ui.StoreScreen
import br.com.navalbattle.ui.SplashScreen
import br.com.navalbattle.ui.UpdatePopup
import br.com.navalbattle.ui.WelcomeScreen

/** Amigo visto há até 2 minutos conta como online (o app bate o ponto a cada minuto). */
const val ONLINE_SECS = 120

enum class Screen {
    SPLASH, WELCOME, MENU, SHIPYARD, STORE, PROFILE, SETTINGS, RELEASE_NOTES, LAN, ONLINE, NAMES,
    PLACEMENT, HANDOFF, BATTLE, RESULT, FRIENDS, LEADERBOARD, FEEDBACK
}

/** Tela que o toque num push abre, pelo `kind` que a Edge Function manda (#19); nulo = só abre o jogo. */
internal fun screenForPushKind(kind: String): Screen? = when (kind) {
    "friend_online" -> Screen.FRIENDS
    else -> null
}

/** Chave (no Prefs do aparelho) dos tokens de dobrões já somados (#15). */
private const val DELIVERED_KEY = "dobroes_delivered"
private const val AWARDS_KEY = "online_awards_pending"
private const val CHARGE_REQUEST_KEY = "charge_request_pending"

/** Situação da recompensa de dobrões da partida online na tela de resultado (#34). */
enum class OnlineAwardUi { NONE, CHECKING, PAID, LIMIT, CAPPED, RETRY }

/** Faixa etária guardada (ADULT, TEEN, CHILD, UNKNOWN) e a hora da leitura (#49). Nunca a idade. */
private const val AGE_BAND_KEY = "age_band"
private const val AGE_CHECKED_KEY = "age_checked_at"
private const val REVIEW_LAST_KEY = "review_last_ask"
private const val REVIEW_COUNT_KEY = "review_ask_count"
private const val REVIEW_SETTLE_MS = 800L

/** Espera da confirmação do Google depois do anúncio (#29): 10 consultas a cada 2 s, cerca de 20 s. */
private const val AD_CONFIRM_TRIES = 10
private const val AD_CONFIRM_STEP_MS = 2000L

/** Qual popup de convite (#44) está aberto e com que dados. */
enum class ReferralPopupKind { ACCEPTED, INVITEE_PAID, INVITER_PAID }

data class ReferralPopupData(val kind: ReferralPopupKind, val name: String, val amount: Int)

class AppState(val profile: Profile, private val cloud: CloudApi) {
    var screen by mutableStateOf(Screen.SPLASH)

    /** Aba em que a Loja abre: 0 cascos, 1 camuflagens, 2 habilidades (o Estaleiro manda direto pra aba certa). */
    var storeAisle by mutableStateOf(0)

    /** Dia corrido de Brasília (UTC-3) — chave da oferta do dia, a mesma o dia todo. */
    private val brtDay: Long get() = (nowMillis() - 3 * 3_600_000L) / 86_400_000L

    /** Camuflagem com desconto hoje (nula se o comandante já tem todas). */
    val dailyOffer: Paint?
        get() = Paint.dailyOffer(brtDay) { profile.owns(it) }

    fun openStore(aisle: Int = 0) {
        storeAisle = aisle
        screen = Screen.STORE
    }
    var mode by mutableStateOf(GameMode.entries.firstOrNull { it.name == profile.lastMode } ?: GameMode.CLASSIC)

    /** Etapa "Escolha o modo" aberta — de onde veio decide o que acontece depois. */
    var modePick by mutableStateOf<ModePick?>(null)

    /** Abre a escolha de modo antes de começar a forma de jogar escolhida no deque. */
    fun pickMode(target: ModePick) {
        modePick = target
    }

    /** Muda o modo e guarda como o último usado (vem marcado da próxima vez). */
    fun chooseMode(value: GameMode) {
        mode = value
        profile.setLastMode(value.name)
    }

    fun confirmMode(value: GameMode) {
        val target = modePick ?: return
        modePick = null
        chooseMode(value)
        when (target) {
            ModePick.Ai -> newMatch(Opponent.AI)
            ModePick.Local -> newMatch(Opponent.LOCAL)
            ModePick.LAN -> screen = Screen.LAN
            is ModePick.Friend -> {
                val ranked = friendRanked && friendRankedAllowed
                // ranqueada exige adesão à temporada: sem passe, mostra o aviso da temporada
                if (ranked && !canPlayRanked) {
                    rankedLockedPrompt = true
                    return
                }
                createOnlineRoom(invitedId = target.friendId, ranked = ranked)
                screen = Screen.ONLINE
            }
        }
    }
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
        // no retorno ao app vale no máximo uma consulta a cada 5 min (UpdatePrompt)
        val now = nowMillis()
        if (!UpdatePrompt.shouldRecheck(lastUpdateCheckAt, now)) return
        lastUpdateCheckAt = now
        val notes = (cloud.recentReleases(platformName) as? CloudResult.Ok)?.value.orEmpty()
            .filter { isNewerVersion(it.versionName, appVersionName) }
        val store = checkUpdateAvailable()
        updateNotes = notes
        newestUpdateVersion = UpdatePrompt.newestOf(notes.map { it.versionName })
        updateAvailable = when {
            store != null -> store
            else -> notes.isNotEmpty()
        }
    }

    private var lastUpdateCheckAt = 0L

    /** Maior versão nova vista na última consulta; nula quando só a loja avisa. */
    var newestUpdateVersion by mutableStateOf<String?>(null)
        private set

    /** O popup de atualização deve estar na tela: há versão nova e ela ainda não foi dispensada. */
    val updatePopupPending: Boolean
        get() = UpdatePrompt.shouldShowUpdate(updateAvailable, newestUpdateVersion, dismissedUpdateVersion)

    /** "Depois" / "Atualizar": só volta quando surgir versão maior que esta. */
    fun dismissUpdatePopup() {
        dismissedUpdateVersion = UpdatePrompt.dismissMark(newestUpdateVersion)
    }

    /** Toque na faixa do menu: mostra o popup de novo. */
    fun reopenUpdatePopup() {
        dismissedUpdateVersion = null
    }

    /**
     * Para onde ir depois da abertura: direto ao deque para quem já tem conta ou já
     * escolheu jogar como convidado; senão, pergunta uma vez na tela de boas-vindas.
     */
    fun afterSplash() {
        screen = if (profile.signedIn || profile.welcomeDone) Screen.MENU else Screen.WELCOME
        maybeOfferGuided()
    }

    // ---------------- primeira partida guiada e presente de boas-vindas (#43) ----------------

    /** Passo da guiada em curso; nulo fora dela (ou depois de pular/terminar). */
    var guidedStep by mutableStateOf<GuidedStep?>(null)
        private set

    /** "Primeira missão" aberta no menu: começar ou pular. */
    var guidedOffer by mutableStateOf(false)
        private set

    /** Presente pago agora: o popup mostra o valor devolvido pelo servidor. */
    var welcomeGiftPaid by mutableStateOf<WelcomeGift?>(null)
        private set

    /** Convidado terminou a guiada: o popup diz para entrar na conta; o resgate fica guardado. */
    var welcomeGuestNotice by mutableStateOf(false)
        private set

    private var guidedReplay = false

    /** Chamado ao chegar ao menu: oferece a guiada a quem nunca jogou e ainda não a fez nem pulou. */
    fun maybeOfferGuided() {
        if (screen != Screen.MENU || match != null) return
        if (shouldOfferGuided(profile.matches, profile.guidedDone)) guidedOffer = true
    }

    /** Começa a guiada: Clássico contra a IA no nível fácil, sem mexer no último modo escolhido. */
    fun startGuidedMatch(replay: Boolean = false) {
        guidedOffer = false
        guidedReplay = replay
        opponentProfile = null
        mode = GameMode.CLASSIC
        match = Match(GameMode.CLASSIC, Opponent.AI, easyAi = true)
        guidedStep = GuidedStep.PLACE
        screen = Screen.PLACEMENT
    }

    /** "Pular", na oferta ou em qualquer passo: encerra as dicas e entrega o presente. */
    fun skipGuided() {
        guidedOffer = false
        if (guidedStep == null) {
            // pulou na oferta, sem partida: só marca e resgata
            finishGuided()
        } else {
            guidedEvent(GuidedEvent.SKIPPED)
        }
    }

    fun guidedEvent(event: GuidedEvent) {
        val current = guidedStep ?: return
        val next = nextGuidedStep(current, event)
        if (next == GuidedStep.DONE) finishGuided() else guidedStep = next
    }

    private fun finishGuided() {
        guidedStep = null
        if (guidedReplay) {
            guidedReplay = false
            return
        }
        profile.markGuidedDone()
        uiScope?.launch { runWelcomeClaim() }
    }

    fun closeWelcomePopups() {
        welcomeGiftPaid = null
        welcomeGuestNotice = false
    }

    /** Pede o presente ao servidor. Convidado guarda o pedido para o primeiro login. */
    suspend fun runWelcomeClaim() {
        if (!profile.signedIn) {
            profile.savePendingWelcome(true)
            welcomeGuestNotice = true
            return
        }
        if (!ensureWallet()) {
            profile.savePendingWelcome(true)
            return
        }
        val gift = (authed { session -> cloud.claimWelcomeGift(session) } as? CloudResult.Ok)?.value
        val outcome = welcomeOutcome(gift)
        if (welcomeShouldRetry(outcome)) {
            profile.savePendingWelcome(true)
            return
        }
        profile.savePendingWelcome(false)
        if (outcome == WelcomeOutcome.PAID && gift != null) {
            profile.setServerBalance(gift.balance)
            loadMiles()
            welcomeGiftPaid = gift
        }
    }

    /** Depois de entrar ou abrir o app com conta: resgata o presente que ficou pendente. */
    private suspend fun claimPendingWelcome() {
        if (profile.welcomePending && profile.signedIn) runWelcomeClaim()
    }

    // ---------------- convide um amigo (#44) ----------------

    /** Código de convite desta conta; nulo até o servidor responder (ou sem conta). */
    var referralCode by mutableStateOf<String?>(null)
        private set

    /** Convites que esta conta fez e o que já rendeu (`referral_status`). */
    var referralInvites by mutableStateOf<List<ReferralInvite>>(emptyList())
        private set

    /** Popup aberto: convite aceito, convidado premiado ou quem convidou premiado. */
    var referralPopup by mutableStateOf<ReferralPopupData?>(null)
        private set

    suspend fun loadReferralCode() {
        if (!profile.signedIn || referralCode != null) return
        referralCode = (authed { s -> cloud.referralMyCode(s) } as? CloudResult.Ok)?.value?.takeIf { it.isNotBlank() }
    }

    /** Digita o código de um amigo. A forma errada nem vai à rede; o servidor decide o resto e cria a amizade. */
    suspend fun redeemReferral(text: String): ReferralOutcome {
        if (!isValidReferralCodeShape(text)) return ReferralOutcome.INVALID
        val r = (authed { s -> cloud.referralRedeem(s, normalizeReferralCode(text)) } as? CloudResult.Ok)?.value
        val outcome = referralOutcome(r)
        if (outcome == ReferralOutcome.OK && r != null) {
            referralPopup = ReferralPopupData(ReferralPopupKind.ACCEPTED, r.inviterName, 0)
            refreshFriendships()
        }
        return outcome
    }

    /** Depois da recompensa online: o servidor paga o convite se esta sala valer (1ª partida com combate). */
    private suspend fun settleReferral(matchId: String) {
        val r = (authed { s -> cloud.referralSettle(s, matchId) } as? CloudResult.Ok)?.value ?: return
        if (r.status == "paid" && r.doubloons > 0) {
            profile.setServerBalance(r.balance)
            referralPopup = ReferralPopupData(ReferralPopupKind.INVITEE_PAID, "", r.doubloons)
        }
    }

    /** Ao abrir o menu com conta: lê os convites feitos e avisa dos prêmios ainda não vistos. */
    suspend fun loadReferralStatus() {
        if (!profile.signedIn) return
        val list = (authed { s -> cloud.referralStatus(s) } as? CloudResult.Ok)?.value ?: return
        referralInvites = list
        val total = referralUnseenTotal(list)
        if (total > 0) {
            refreshBalance()
            if (referralPopup == null) referralPopup = ReferralPopupData(ReferralPopupKind.INVITER_PAID, "", total)
        }
    }

    fun closeReferralPopup() {
        val popup = referralPopup ?: return
        referralPopup = null
        if (popup.kind == ReferralPopupKind.INVITER_PAID) {
            referralInvites = referralInvites.map { it.copy(unseen = false) }
            uiScope?.launch { authed { s -> cloud.referralAck(s) } }
        }
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
    /** Mesmo aparelho, horizontal: troca o quadro de cada comandante de lado ("⇄"). */
    var localSidesSwapped by mutableStateOf(false)

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

            else -> if (practiceWhileSearching) {
                // sair do treino para o menu fecha a sala rápida
                onlineLink.finish("abandoned")
                closeOnline()
            }
        }
        match = null
        guidedStep = null
        guidedReplay = false
        screen = Screen.MENU
        askReviewSoon()
    }

    // ---------------- avaliação na Play (#45) ----------------

    private val reviewPrefs = Prefs()

    /** Vitória que acabou de ser contada na carreira; o pedido só sai ao voltar ao menu. */
    private var reviewCandidate: Boolean? = null  // nulo = sem vitória pendente; valor = subiu de patente

    /** Nova partida (resultado novo): descarta vitória pendente de antes, para derrota nunca herdar o pedido. */
    fun clearReviewCandidate() {
        reviewCandidate = null
    }

    /** A tela de resultado avisa: a vitória já entrou na carreira (e se subiu de patente). */
    fun noteReviewCandidate(rankedUp: Boolean) {
        reviewCandidate = rankedUp
    }

    /**
     * Ao voltar ao menu depois de uma vitória, avalia [ReviewPrompt.shouldAsk] e pede a avaliação
     * da Play. Espera o menu assentar; com outro popup na tela, fora do menu ou com o app em
     * segundo plano, desiste sem gravar nada (a próxima vitória tenta de novo). As marcas vão
     * antes do pedido; nada bloqueia o jogo e a falta de retorno é normal.
     */
    private fun askReviewSoon() {
        val rankedUp = reviewCandidate ?: return
        reviewCandidate = null
        if (!StoreReview.supported) return
        uiScope?.launch {
            delay(REVIEW_SETTLE_MS)
            if (screen != Screen.MENU || match != null || !AppForeground.active) return@launch
            if (otherPopupShowing || dailyPopupOpen) return@launch
            val now = nowMillis()
            val ask = ReviewPrompt.shouldAsk(
                totalWins = profile.wins,
                rankedUp = rankedUp,
                wonLast = true,
                lastAskMillis = reviewPrefs.getString(REVIEW_LAST_KEY, "").toLongOrNull() ?: 0L,
                askCount = reviewPrefs.getInt(REVIEW_COUNT_KEY, 0),
                now = now,
                isTest = isTestLabDevice()
            )
            if (!ask) return@launch
            reviewPrefs.putBatch(PrefsBatch().apply {
                putString(REVIEW_LAST_KEY, now.toString())
                putInt(REVIEW_COUNT_KEY, reviewPrefs.getInt(REVIEW_COUNT_KEY, 0) + 1)
            })
            StoreReview.request {}
        }
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

    /** O adversário saiu da partida (QUIT) — o relatório diz isso em vez de "ligação caiu". */
    var opponentQuit by mutableStateOf(false)
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
        opponentQuit = false
        val m = Match(mode, Opponent.LAN, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        link.send(Protocol.hello(profile.displayName, mode.name))
        screen = Screen.PLACEMENT
    }

    // ---------------- partida online (internet) ----------------

    var onlineLinkState by mutableStateOf(LinkState.IDLE)
        private set

    /** A última sala de convite falhou porque o amigo recusou (lido junto com FAILED). */
    val onlineInviteDeclined: Boolean get() = onlineLink.inviteDeclined

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
    fun createOnlineRoom(invitedId: String? = null, ranked: Boolean = false) {
        val session = profile.currentSession() ?: return
        if (!hasMileOrPrompt()) return
        onlineLink.close()
        onlineCode = null
        onlineInvitedFriend = invitedId != null
        onlineLink.createRoom(
            session = session,
            mode = mode.name,
            invitedId = invitedId,
            ranked = ranked,
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
        practiceWhileSearching = false
        practiceSwitchSide = null
        quickSearchStartedAt = nowMillis()
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
        quickSearchStartedAt = 0L
        practiceWhileSearching = false
        practiceSwitchSide = null
        practiceLines.clear()
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
        if (state == LinkState.FAILED && practiceWhileSearching) {
            // a busca caiu durante o treino: o treino vira partida comum contra a IA
            closeOnline()
            return
        }
        if (state != LinkState.CONNECTED) return
        if (practiceWhileSearching) {
            // alguém entrou durante o treino contra a IA (#41): aviso de 3 s e troca para a online
            beginPracticeSwitch(side)
            return
        }
        if (match?.opponent == Opponent.AI && quickSearchStartedAt == 0L) {
            // CONNECTED que chegou depois do "Parar de procurar": fecha a sala e não troca de tela
            onlineLink.finish("abandoned")
            closeOnline()
            return
        }
        startOnlineMatch(side)
    }

    // ---------------- treino contra a IA enquanto a partida rápida procura (#41) ----------------

    /** Quando a busca da partida rápida começou (0 = sem busca rápida); a oferta do treino nasce aos 20 s. */
    var quickSearchStartedAt by mutableStateOf(0L)
        private set

    /** Treinando contra a IA com a sala rápida ainda aberta. */
    var practiceWhileSearching by mutableStateOf(false)
        private set

    /** Lado do convidado que entrou durante o treino: enquanto não nulo, o aviso de 3 s está na tela. */
    var practiceSwitchSide by mutableStateOf<Side?>(null)
        private set

    /** Mensagens do adversário que chegam durante o aviso — repetidas na partida online depois da troca. */
    private val practiceLines = mutableListOf<String>()

    /** A sala rápida segue aberta (OnlineLink não é tocado) e abre uma partida normal contra a IA. */
    fun startPracticeWhileSearching() {
        if (onlineLinkState != LinkState.SEARCHING && onlineLinkState != LinkState.HOSTING) return
        if (!QuickPractice.practiceOfferVisible(quickSearchStartedAt, nowMillis(), quickSearchStartedAt > 0L)) return
        practiceWhileSearching = true
        newMatch(Opponent.AI)
    }

    /** "Parar de procurar": fecha a sala; o treino vira partida comum contra a IA. */
    fun stopSearchKeepPractice() {
        if (!practiceWhileSearching) return
        onlineLink.finish("abandoned")
        closeOnline()
    }

    private fun beginPracticeSwitch(side: Side) {
        practiceSwitchSide = side
        practiceLines.clear()
        onlineLink.opponentId?.let { id -> uiScope?.launch { loadOpponentProfile(id) } }
        uiScope?.launch {
            delay(QuickPractice.switchDelayMillis)
            // se a sala fechou nesse meio-tempo (Parar de procurar, sair), não troca
            if (practiceSwitchSide != side || onlineLinkState != LinkState.CONNECTED) return@launch
            val buffered = practiceLines.toList()
            practiceLines.clear()
            practiceWhileSearching = false
            practiceSwitchSide = null
            // o treino é descartado: nenhum registerMatch (só a tela de resultado grava)
            if (QuickPractice.onOpponentJoined(true) == QuickPractice.JoinAction.AUTO_SWITCH) {
                startOnlineMatch(side)
                buffered.forEach { onLine(it) }
            }
        }
    }

    private fun startOnlineMatch(side: Side) {
        // o modo é o da sala (quem a abriu escolheu) — o convite e o balão mostram
        // esse modo antes de aceitar, então a partida tem que bater com o que foi dito
        onlineLink.roomMode?.let { room -> GameMode.entries.firstOrNull { it.name == room }?.let { mode = it } }
        val m = Match(mode, Opponent.ONLINE, mySide = side)
        m.setName(side, profile.displayName)
        match = m
        opponentQuit = false
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
    /** Busca em andamento e o último termo já respondido — a tela mostra o status da busca. */
    var friendSearching by mutableStateOf(false)
        private set
    var friendSearchedFor by mutableStateOf("")
        private set

    suspend fun searchCommander(query: String) {
        if (query.isBlank()) {
            friendResults = emptyList()
            friendSearchedFor = ""
            return
        }
        val session = profile.currentSession() ?: return
        friendSearching = true
        val r = cloud.searchCommander(session, query)
        friendResults = (r as? CloudResult.Ok)?.value.orEmpty()
        friendSearchedFor = query
        friendSearching = false
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
    var friendAvatars by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    suspend fun refreshFriendships() {
        val session = profile.currentSession() ?: return
        val r = cloud.listFriendships(session)
        friendships = (r as? CloudResult.Ok)?.value.orEmpty()
        (cloud.friendAvatars(session) as? CloudResult.Ok)?.value?.let { friendAvatars = it }
        (cloud.friendRankedAllowed(session) as? CloudResult.Ok)?.value?.let { friendRankedAllowed = it }
        refreshFriendPresence()
    }

    /** Presença de cada amigo; com o jogo aberto há até [ONLINE_SECS] conta como no jogo. */
    var friendPresence by mutableStateOf<Map<String, FriendPresence>>(emptyMap())
        private set

    private fun friendInGame(id: String): Boolean = friendPresence[id]?.seenSecs?.let { it in 0..ONLINE_SECS } == true

    /** Logado e com o jogo aberto, fora de partida — online na lista. */
    fun friendOnline(id: String): Boolean = friendInGame(id) && friendPresence[id]?.inMatch != true

    /** Online e aceitando convites (não desligou nos Ajustes) — só para esse dá para convidar. */
    fun friendInvitable(id: String): Boolean = friendOnline(id) && friendPresence[id]?.acceptsInvites != false

    /** Logado e jogando agora: não recebe convite até a partida acabar. */
    fun friendInMatch(id: String): Boolean = friendInGame(id) && friendPresence[id]?.inMatch == true

    suspend fun refreshFriendPresence() {
        val session = profile.currentSession() ?: return
        (cloud.friendPresence(session) as? CloudResult.Ok)?.value?.let { friendPresence = it }
    }

    /** Bate o ponto de presença: online, em partida ou fora do jogo (ver supabase/presence.sql). */
    suspend fun touchPresence(state: String) {
        val session = profile.currentSession() ?: return
        cloud.touchPresence(session, state, profile.acceptInvites, profile.friendOnlinePush)
    }

    /** Servidor libera ranqueada entre amigos (teste fechado) — chave friend_ranked_enabled. */
    var friendRankedAllowed by mutableStateOf(false)
        private set

    /** Escolha Casual/Ranqueada do convite de amigo, na etapa "Escolha o modo". */
    var friendRanked by mutableStateOf(false)

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
        // desligado nos Ajustes: nenhum balão de convite em tela nenhuma
        if (!profile.signedIn || !profile.acceptInvites || match != null || pendingInvite != null) return
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
    /** Último token de push salvo e a conta de então — evita regravar a cada volta ao app. */
    private var pushRegistered: String? = null
    private var pushRegisteredFor: String? = null

    /**
     * Registra o push deste aparelho (Android; no iPhone o token é nulo e nada acontece).
     * Sem conta o aparelho recebe os avisos gerais (versão nova, novidades); entrando,
     * passa a receber também convites e amizade.
     */
    suspend fun registerPush() {
        val token = PushMessaging.token() ?: return
        val account = if (profile.signedIn) profile.accountId else ""
        if (token == pushRegistered && account == pushRegisteredFor) return
        val r = if (profile.signedIn) {
            authed { session -> cloud.registerPushToken(session, token, PushMessaging.platform) }
        } else {
            cloud.registerPushToken(null, token, PushMessaging.platform)
        }
        if (r is CloudResult.Ok) {
            pushRegistered = token
            pushRegisteredFor = account
        }
    }

    /**
     * Relata ao servidor se este aparelho é dos testes automáticos do Google (#23),
     * uma vez por conta e versão. Falha de rede não marca nada como enviado: tenta de
     * novo na próxima abertura. No iPhone vai sempre falso (base de comparação).
     */
    suspend fun reportDeviceCheck() {
        if (!profile.signedIn) return
        val key = deviceCheckKey(profile.accountId, appVersionName)
        if (profile.deviceCheckSent == key) return
        val r = authed { session ->
            cloud.reportDeviceCheck(session, isTestLabDevice(), appVersionName, platformName)
        }
        if (r is CloudResult.Ok) profile.deviceCheckSent = key
    }

    /**
     * Exclui a conta de vez (App Store 5.1.1): apaga tudo no servidor e zera a carreira
     * guardada no aparelho. Devolve falso se o servidor não confirmou — aí nada muda.
     */
    suspend fun deleteAccount(): Boolean {
        val r = authed { session -> cloud.deleteAccount(session) }
        if (r !is CloudResult.Ok) return false
        pushRegistered = null
        pushRegisteredFor = null
        friendships = emptyList()
        profile.signOut()
        profile.reset(keepWallet = false)
        return true
    }

    /** Sai da conta e para de receber os avisos dela neste aparelho. */
    fun signOut() {
        val session = profile.currentSession()
        val token = pushRegistered
        pushRegisteredFor = null
        if (session != null && token != null) uiScope?.launch { cloud.unregisterPushToken(session, token) }
        hasPassword = null
        profile.signOut()
    }

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

    /** Versão dispensada com "Depois" (#46) — aqui para o balão saber se o popup está na tela. Só na sessão. */
    var dismissedUpdateVersion by mutableStateOf<String?>(null)

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
            if (updatePopupPending) return true
            if (seasonPopupShowing || seasonEnd != null || rankedLockedPrompt) return true
            if (feedbackReward != null) return true
            if (guidedOffer || welcomeGiftPaid != null || welcomeGuestNotice) return true
            if (referralPopup != null) return true
            if (launchNoticeShowing) return true
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
            // a sala fica reservada para este comandante por 60s no servidor: sem resposta
            // em 50s o balão fecha sozinho e a vez passa para outro disponível
            if (nowMillis() - quickOfferShownAt > 50_000) {
                dismissQuickOffer()
                return
            }
            val fresh = (cloud.getOnlineMatch(session, shown.id) as? CloudResult.Ok)?.value
            if (fresh == null || fresh.status != "waiting" || fresh.guestId != null) quickOffer = null
            return
        }
        val (casual, ranked) = quickOfferTypes()
        if (!casual && !ranked) return
        val found = (cloud.findQuickOffer(session, casual, ranked, ignoredOffers.toSet()) as? CloudResult.Ok)?.value ?: return
        // o estado pode ter mudado durante a chamada (entrou numa partida, abriu popup)
        if (match == null && !otherPopupShowing && quickOffer == null) {
            quickOffer = found
            quickOfferShownAt = nowMillis()
        }
    }

    private var quickOfferShownAt = 0L

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

    /** "Agora não" (ou prazo vencido): a sala vai para o próximo disponível e não volta aqui. */
    fun dismissQuickOffer() {
        val room = quickOffer ?: return
        ignoredOffers += room.id
        quickOffer = null
        val session = profile.currentSession() ?: return
        uiScope?.launch { cloud.declineQuickOffer(session, room.id) }
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
     * O passe pago é cobrado no servidor (carteira, #34): ele debita o preço, credita os dobrões
     * extras, põe a camuflagem da estação nos itens e soma as milhas bônus. Sem saldo ou sem rede
     * nada muda; o aparelho só mostra o que o servidor devolve.
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
        // o passe pago mexe em dobrões: a carteira precisa estar aberta (senão o servidor não cobraria)
        if (premium && !ensureWallet()) {
            seasonBusy = false
            seasonNotice = t(K.WALLET_OFFLINE)
            return
        }
        val r = authed { session -> cloud.joinSeason(session, if (premium) "premium" else "free") }
        seasonBusy = false
        val joined = (r as? CloudResult.Ok)?.value
        if (joined == null) {
            refreshBalance()
            seasonNotice = t(K.SEASON_JOIN_FAILED)
            return
        }
        // saldo e a pintura da estação vêm do servidor (ele cobrou, creditou e entregou)
        if (premium) refreshBalance()
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
        // passe ainda não carregado (rede lenta, login recente): busca agora — o banner
        // abre assim que ele chegar, em vez de o botão não fazer nada
        if (seasonPass == null) uiScope?.launch { loadSeason() }
    }

    /** Resgata (uma vez) o resultado da temporada que acabou e credita os dobrões do prêmio. */
    suspend fun checkSeasonEnd() {
        if (!profile.signedIn || seasonEnd != null) return
        if (!ensureWallet()) return  // sem carteira o servidor não credita: tenta de novo na próxima vez
        val end = (authed { session -> cloud.claimSeasonEnd(session) } as? CloudResult.Ok)?.value ?: return
        refreshBalance()
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
        // Play Games (#48): espelho do placar da temporada; vitória é a que o servidor decidiu
        // (pontos-base de Elo positivos), não o que este aparelho achava
        if (outcome != null && outcome.accepted) {
            if (outcome.basePoints > 0) GameServices.send(achievementsFor(AchievementEvent.RankedWin))
            if (GameServices.supported) GameServices.submitRanked(leaderboardScore(outcome.seasonPoints))
        }
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

    /** Desafio da semana (#47), vindo do servidor junto com o diário; null se o servidor não o oferece. */
    var weekly by mutableStateOf<WeeklyStatus?>(null)
        private set

    var weeklyBusy by mutableStateOf(false)
        private set

    /** Meta da semana batida e prêmio ainda não resgatado. */
    val weeklyReady: Boolean
        get() = weekly?.let { weeklyCanClaim(it.progress, it.target, it.claimed) } ?: false

    /** Tem prêmio esperando: check-in de hoje ou desafio cumprido e não resgatado. */
    val dailyPending: Boolean
        get() {
            val d = daily ?: return false
            return !d.checkedIn || (!d.challengeClaimed && dailyChallengeDone) || weeklyReady
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
            weekly = null
            DailyReminder.cancel()
            return
        }
        (authed { session -> cloud.dailyStatus(session) } as? CloudResult.Ok)?.value?.let {
            daily = it
            noteOpened(it)
        }
        loadWeekly()
        scheduleReminder()
    }

    /** Lê o desafio da semana (o servidor conta o progresso); falha de rede mantém o que já estava na tela. */
    private suspend fun loadWeekly() {
        val r = authed { session -> cloud.weeklyStatus(session) } as? CloudResult.Ok ?: return
        weekly = r.value
    }

    /** Resgata o prêmio da semana: o servidor confere, paga e devolve o saldo (o aparelho nunca credita). */
    suspend fun claimWeekly() {
        val w = weekly ?: return
        if (weeklyBusy || dailyBusy || !weeklyCanClaim(w.progress, w.target, w.claimed)) return
        weeklyBusy = true
        dailyNotice = null
        if (!ensureWallet()) {
            weeklyBusy = false
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        val claim = (authed { session -> cloud.claimWeekly(session) } as? CloudResult.Ok)?.value
        weeklyBusy = false
        when (weeklyOutcome(claim)) {
            WeeklyOutcome.PAID -> {
                claim?.let { profile.setServerBalance(it.balance) }
                loadMiles()
                weekly = w.copy(claimed = true, progress = w.target)
                dailyNotice = claim?.let { t(K.WEEKLY_GOT, it.doubloons, it.miles) }
            }
            WeeklyOutcome.DONE_BEFORE -> weekly = w.copy(claimed = true)
            WeeklyOutcome.NOT_DONE -> loadWeekly()
            WeeklyOutcome.NOT_ELIGIBLE -> weekly = null
            WeeklyOutcome.NO_WALLET, WeeklyOutcome.OFFLINE -> dailyNotice = t(K.DAILY_FAILED)
        }
    }

    /**
     * Aviso de retorno (#42): grava o dia de hoje como "último dia aberto" e, se o aviso de retorno
     * agendado já passou sem o jogador abrir o app, guarda que ele saiu (no máximo 1 a cada 7 dias).
     */
    private fun noteOpened(d: DailyStatus) {
        val today = epochDayOf(d.today) ?: return
        val pending = profile.pendingReturnDay
        if (pending != 0 && ReturnReminder.noticeSettled(pending, today)) {
            profile.markReturnNotice(pending)
            profile.setPendingReturnDay(0)
        }
        profile.markOpenDay(today)
    }

    /** App voltou ao primeiro plano: o balão pode aparecer de novo. */
    fun onAppEntered() {
        dailyAutoDismissed = false
    }

    // ---------------- Play Games (#48) ----------------

    /** Liga o Play Games se estiver configurado (senão nada acontece); recupera a carreira ao autenticar. */
    fun startGameServices() {
        GameServices.start {
            GameServices.send(
                backfill(AchievementCareer(profile.wins, profile.sunk, profile.rank, daily?.streak ?: 0))
            )
        }
    }

    /** Conquistas do fim de uma partida que valeu carreira; [rankUp] vem do [Award]. */
    fun reportMatchAchievements(victory: Boolean, online: Boolean, shipsLost: Int, shipsSunk: Int, rankUp: Rank?) {
        if (!GameServices.supported) return
        GameServices.send(achievementsFor(AchievementEvent.MatchEnd(victory, online, shipsLost, shipsSunk)))
        rankUp?.let { GameServices.send(achievementsFor(AchievementEvent.RankUp(it))) }
    }

    suspend fun dailyCheckin() {
        if (dailyBusy || daily?.checkedIn != false) return
        dailyBusy = true
        dailyNotice = null
        if (!ensureWallet()) {
            dailyBusy = false
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        val r = authed { session -> cloud.dailyCheckin(session) }
        dailyBusy = false
        val done = (r as? CloudResult.Ok)?.value
        if (done == null) {
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        refreshBalance()  // o servidor creditou
        daily = daily?.copy(checkedIn = true, streak = done.streak, returnReady = false)
        GameServices.send(achievementsFor(AchievementEvent.CheckIn(if (done.weekCompleted) maxOf(done.streak, 7) else done.streak)))
        val checkinNotice = when {
            done.weekCompleted -> t(K.DAILY_WEEK_DONE, done.doubloons)
            done.doubloons > 0 -> t(K.DAILY_GOT, done.doubloons)
            else -> null
        }
        // prêmio de retorno (#42): o servidor já creditou os dobrões e as milhas
        val returnNotice = when {
            done.returnDoubloons > 0 -> t(K.DAILY_RETURN_GOT, done.returnDoubloons, done.returnMiles)
            done.returnMiles > 0 -> t(K.DAILY_RETURN_GOT_MILES, done.returnMiles)
            else -> null
        }
        if (returnNotice != null) {
            daily?.let { epochDayOf(it.today) }?.let { profile.markReturnBonus(it) }
            loadMiles()
        }
        dailyNotice = listOfNotNull(checkinNotice, returnNotice).joinToString("\n").ifEmpty { null }
        // momento em que o comandante está engajado: pede a notificação aqui, não na abertura
        if (profile.reminderOn) DailyReminder.requestPermission()
        scheduleReminder()
        // o "dobrar check-in" (#29) só existe depois do check-in de hoje
        loadAds()
    }

    suspend fun claimDailyChallenge() {
        val d = daily ?: return
        if (dailyBusy || !d.checkedIn || d.challengeClaimed || !dailyChallengeDone) return
        dailyBusy = true
        dailyNotice = null
        if (!ensureWallet()) {
            dailyBusy = false
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        val r = authed { session -> cloud.claimDailyChallenge(session, d.mission, dailyProgress) }
        dailyBusy = false
        val pay = (r as? CloudResult.Ok)?.value
        if (pay == null) {
            dailyNotice = t(K.DAILY_FAILED)
            return
        }
        refreshBalance()  // o servidor creditou
        daily = d.copy(challengeClaimed = true)
        if (pay > 0) dailyNotice = t(K.DAILY_GOT, pay)
        loadAds()
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
        val today = epochDayOf(d.today) ?: return
        val plan = ReturnReminder.plan(
            today = today,
            lastOpenDay = profile.lastOpenDay.takeIf { it > 0 },
            lastReturnNoticeDay = profile.lastReturnNoticeDay.takeIf { it > 0 },
            lastReturnBonusDay = profile.lastReturnBonusDay.takeIf { it > 0 },
            reminderOn = profile.reminderOn,
            signedIn = profile.signedIn,
            checkedInToday = d.checkedIn
        )
        val returnSlot = plan.firstOrNull { it.kind == ReminderKind.RETURN || it.kind == ReminderKind.RETURN_NO_PRIZE }
        profile.setPendingReturnDay(returnSlot?.let { today + it.dayOffset } ?: 0)
        val title = t(K.REMINDER_TITLE)
        DailyReminder.schedule(
            plan,
            ReminderTexts(
                daily = ReminderMessage(title, t(K.REMINDER_BODY, d.checkinReward)),
                away = ReminderMessage(title, t(K.REMINDER_AWAY_BODY)),
                returnPrize = ReminderMessage(t(K.REMINDER_RETURN_TITLE), t(K.REMINDER_RETURN_BODY)),
                returnNoPrize = ReminderMessage(t(K.REMINDER_RETURN_TITLE), t(K.REMINDER_RETURN_NO_PRIZE_BODY))
            )
        )
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

    /** Compra um pacote de milhas com dobrões: o servidor debita (sem saldo ou rede, nada muda). */
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
        if (!ensureWallet()) {
            milesNotice = t(K.WALLET_OFFLINE)
            return
        }
        val r = authed { session -> cloud.buyMiles(session, 1) }
        val after = (r as? CloudResult.Ok)?.value
        refreshBalance()
        if (after == null) {
            milesNotice = t(K.MILES_BUY_FAILED)
        } else {
            miles = m.copy(miles = after)
            milesNotice = t(K.MILES_BOUGHT, m.packSize)
        }
    }

    // ---------------- anúncio premiado opcional (#29) ----------------

    private val rewardedAds = RewardedAds()
    private val ageSignals = AgeSignals()
    private val agePrefs = Prefs()

    /**
     * Faixa etária para o anúncio (#49, ECA Digital). Vale 24 h no Prefs (só a faixa e a hora,
     * nunca idade nem installId); vencida, pergunta à Play. Roda no primeiro toque num anúncio,
     * porque o pedido de acesso pode abrir o aviso da Play. A idade não sai do aparelho.
     */
    private suspend fun ensureAgeBand(): AgeBand {
        val checkedAt = agePrefs.getString(AGE_CHECKED_KEY, "").toLongOrNull() ?: 0L
        val now = nowMillis()
        if (!AgeRules.checkDue(checkedAt, now)) return AgeRules.parse(agePrefs.getString(AGE_BAND_KEY, ""))
        val reading = ageSignals.read()
        // falha (erro, tempo esgotado): vale UNKNOWN só neste anúncio e pergunta de novo no próximo
        if (AgeRules.shouldCache(reading)) {
            agePrefs.putBatch(PrefsBatch().apply {
                putString(AGE_BAND_KEY, reading.band.name)
                putString(AGE_CHECKED_KEY, now.toString())
            })
        }
        return reading.band
    }

    /** Só a leitura guardada, sem abrir o aviso da Play (Ajustes → Privacidade dos anúncios). */
    private fun cachedAgeBand(): AgeBand = AgeRules.parse(agePrefs.getString(AGE_BAND_KEY, ""))

    /** O que cada lugar ainda pode hoje (servidor). Nulo: sem conta, iPhone ou servidor fora. */
    var adsStatus by mutableStateOf<AdsStatus?>(null)
        private set

    private var adPhases by mutableStateOf<Map<AdPlace, AdPhase>>(emptyMap())
    private var adNotices by mutableStateOf<Map<AdPlace, String>>(emptyMap())

    /** Vitórias já dobradas (ou com o prêmio a caminho) nesta sessão: o botão some daquela tela. */
    private var adWinsDone by mutableStateOf<Set<String>>(emptySet())

    fun adPhase(place: AdPlace): AdPhase = adPhases[place] ?: AdPhase.IDLE

    fun adNotice(place: AdPlace): String? = adNotices[place]

    fun clearAdNotice(place: AdPlace) {
        adNotices = adNotices - place
    }

    /** O servidor libera este lugar agora (ou o fluxo dele já está andando). */
    fun adAvailable(place: AdPlace): Boolean {
        if (adPhase(place) != AdPhase.IDLE) return true
        val s = adsStatus ?: return false
        if (!rewardedAds.supported || !s.enabled) return false
        return when (place) {
            AdPlace.WIN -> s.winCan
            AdPlace.CHECKIN -> s.checkinCan
            AdPlace.CHALLENGE -> s.challengeCan
            AdPlace.MILE -> s.mileCan
        }
    }

    /** Saldo zero e o limite de anúncio de milha de hoje já acabou: o aviso aparece no lugar do botão. */
    val adMileLimit: Boolean
        get() = rewardedAds.supported && adsStatus?.enabled == true && adsStatus?.mileLimit == true &&
            adPhase(AdPlace.MILE) == AdPhase.IDLE

    /** Referência desta vitória para o "dobrar dobrões": sala no online, id do aparelho contra a IA. */
    fun adWinRef(m: Match): String =
        AdRules.winRef(if (m.opponent == Opponent.ONLINE) onlineMatchId else null, m.localId)

    /** Botão de dobrar a vitória: regra do app ([AdRules.winEligible]) e o servidor liberando. */
    fun adWinShows(m: Match, credits: Int, ref: String): Boolean =
        ref !in adWinsDone && adAvailable(AdPlace.WIN) && AdRules.winEligible(
            opponent = m.opponent,
            victory = m.winner == m.mySide,
            credits = credits,
            wonWithoutCombat = m.wonWithoutCombat(m.mySide),
            wonByAbsence = m.wonByAbsence,
            forfeitedBySelf = m.forfeitedBySelf
        )

    /** Dobrões que a vitória pede: os dela, até o teto que o servidor informou hoje. */
    fun adWinAmount(credits: Int): Int = AdRules.winAmount(credits, adsStatus?.winCap ?: 0)

    /** Só lê o servidor (RPC). O SDK de anúncio não é tocado aqui: só no toque do botão. */
    suspend fun loadAds() {
        if (!rewardedAds.supported || profile.currentSession() == null) {
            adsStatus = null
            return
        }
        adsStatus = (authed { s -> cloud.adsStatus(s, platformName) } as? CloudResult.Ok)?.value
    }

    /**
     * Toque num botão de anúncio. Vale no servidor, anúncio (UMP antes, se a região pedir),
     * espera da confirmação do Google (até ~20 s) e crédito pelo caminho de sempre. Roda no
     * escopo do app: sair da tela não corta o fluxo nem prende o "Continuar".
     */
    fun watchAd(place: AdPlace, ref: String? = null, amount: Int? = null) {
        if (adPhase(place) != AdPhase.IDLE) return
        val scope = uiScope ?: return
        adPhases = adPhases + (place to AdPhase.LOADING)
        adNotices = adNotices - place
        scope.launch {
            try {
                runAd(place, ref, amount)
            } finally {
                adPhases = adPhases - place
            }
            loadAds()
        }
    }

    private suspend fun runAd(place: AdPlace, ref: String?, amount: Int?) {
        val session = profile.currentSession() ?: return
        val start = (authed { s -> cloud.adRewardStart(s, place.code, ref, amount) } as? CloudResult.Ok)?.value
        val vale = start?.vale
        if (start == null || start.status != "ok" || vale == null) {
            if (start?.status == "already" && ref != null) adWinsDone = adWinsDone + ref
            adNotices = adNotices + (place to t(start?.let { AdRules.startNotice(it.status) } ?: K.AD_UNAVAILABLE))
            return
        }
        val unit = AdRules.unitFor(place, rewardedAds.buildUsesTestAds, adsStatus?.testAds ?: true, rewardedAds.runsOnEmulator)
        val audience = AgeRules.audience(ensureAgeBand())
        val outcome = rewardedAds.show(unit, session.userId, vale, audience)
        var confirmed: AdValeStatus? = null
        if (outcome == AdShowOutcome.EARNED) {
            // assistiu até o fim: a vitória não volta a oferecer, mesmo se a confirmação atrasar
            if (ref != null) adWinsDone = adWinsDone + ref
            adPhases = adPhases + (place to AdPhase.CONFIRMING)
            confirmed = waitAdConfirmation(vale)
        }
        val serverStatus = confirmed?.status
        val k = AdRules.notice(outcome, serverStatus)
        val text = when {
            k != null -> t(k)
            place == AdPlace.MILE -> t(K.AD_MILE_DONE)
            // o que o servidor creditou de fato (o teto do dia pode ter cortado na aplicação)
            else -> t(K.DOUBLOON_BOUGHT, confirmed?.amount ?: start.amount)
        }
        adNotices = adNotices + (place to text)
        if (serverStatus == "applied") {
            if (place == AdPlace.MILE) loadMiles() else recoverPending()
        }
    }

    /** Consulta o vale até o Google confirmar (applied) ou o prazo passar. */
    private suspend fun waitAdConfirmation(vale: String): AdValeStatus {
        var last = AdValeStatus("pending", 0)
        repeat(AD_CONFIRM_TRIES) {
            val st = (authed { s -> cloud.adRewardStatus(s, vale) } as? CloudResult.Ok)?.value
            if (st != null) last = st
            if (last.status == "applied" || last.status == "expired" || last.status == "unknown") return last
            delay(AD_CONFIRM_STEP_MS)
        }
        return last
    }

    var adPrivacyNotice by mutableStateOf<String?>(null)
        private set

    /** Ajustes: "Privacidade dos anúncios" só aparece com anúncio ligado no servidor. */
    val adPrivacyShows: Boolean
        get() = rewardedAds.supported && adsStatus?.enabled == true

    fun openAdPrivacy() {
        val scope = uiScope ?: return
        adPrivacyNotice = null
        scope.launch {
            if (!rewardedAds.openPrivacyOptions(AgeRules.audience(cachedAgeBand()))) adPrivacyNotice = t(K.AD_PRIVACY_NONE)
        }
    }

    // ---------------- loja de dobrões (Google Play, #15) ----------------

    private val billing = StoreBilling()
    private val dobroesPrefs = Prefs()
    private var recovering = false

    // ---------------- carteira de dobrões no servidor (#34) ----------------

    private var walletOpening = false

    /** Compra da loja em andamento (o botão fica ocupado até o servidor responder). */
    var storeBusy by mutableStateOf(false)
        private set

    /**
     * Garante a carteira aberta no servidor para a conta atual (uma vez por conta). Leva o saldo do
     * aparelho em [local] e os tokens que ele já somou: o servidor fica com o MAIOR saldo (até 200.000)
     * e não soma de novo o que já foi somado. Falso sem conta ou sem rede: nada que mexa em dobrões
     * roda até abrir (o app novo nunca grava saldo local de conta).
     */
    suspend fun ensureWallet(local: Int? = null): Boolean {
        if (profile.walletOpen) return true
        if (!profile.signedIn || walletOpening) return false
        walletOpening = true
        try {
            val plan = Wallet.openPlan(local ?: profile.credits, deliveredTokens())
            val state = (authed { s -> cloud.dobroesOpen(s, plan.local, plan.delivered) } as? CloudResult.Ok)?.value
                ?: return false
            profile.markWalletOpen(profile.accountId)
            profile.setServerBalance(state.balance)
            profile.adoptServerItems(state.owned, state.fleets)
            return true
        } finally {
            walletOpening = false
        }
    }

    /** Pede ao servidor o saldo e os itens e grava no aparelho (único jeito de o saldo mudar). */
    suspend fun refreshBalance() {
        if (!profile.walletOpen) return
        val state = (authed { s -> cloud.dobroesBalance(s) } as? CloudResult.Ok)?.value ?: return
        if (!state.wallet) return
        profile.setServerBalance(state.balance)
        profile.adoptServerItems(state.owned, state.fleets)
    }

    /**
     * Compra na loja (pintura, casco ou cartucho) pelo servidor: ele debita e entrega o item. Sem
     * débito otimista. Devolve ok | owned | insufficient | not_found | offline. Convidado (sem conta)
     * paga da carteira local, só cosméticos.
     */
    suspend fun buyStoreItem(kind: String, id: String, price: Int): String {
        if (storeBusy) return "busy"
        storeBusy = true
        try {
            if (!profile.signedIn) {
                val ok = when (kind) {
                    "paint" -> profile.guestBuy(id, price)
                    "fleet" -> profile.guestBuyFleet(id, price)
                    else -> Ability.entries.firstOrNull { it.code == id }?.let { profile.guestBuyAbilityCharge(it, price) } ?: false
                }
                return if (ok) "ok" else "insufficient"
            }
            if (!ensureWallet()) return "offline"
            // cartucho: id de idempotência do aparelho, guardado até o servidor responder (resposta perdida não cobra 2×)
            val request = if (kind == "charge") {
                ChargeRequest.pick(dobroesPrefs.getString(CHARGE_REQUEST_KEY, ""), id, newRequestId())
                    .also { dobroesPrefs.putString(CHARGE_REQUEST_KEY, ChargeRequest.encode(id, it)) }
            } else null
            val r = (authed { s -> cloud.dobroesBuy(s, kind, id, request) } as? CloudResult.Ok)?.value ?: return "offline"
            if (r.status == "no_wallet") return "offline"
            if (request != null && ChargeRequest.finished(r.status)) dobroesPrefs.putString(CHARGE_REQUEST_KEY, "")
            profile.setServerBalance(r.balance)
            profile.adoptServerItems(r.owned, r.fleets)
            if (r.status == "ok" && kind == "charge") {
                Ability.entries.firstOrNull { it.code == id }?.let { profile.addCharge(it) }
            }
            return r.status
        } finally {
            storeBusy = false
        }
    }

    /** Nova tela de resultado: zera o estado da recompensa de dobrões da partida anterior. */
    fun resetOnlineAward() {
        onlineAward = OnlineAwardUi.NONE
        onlineAwardAmount = 0
    }

    private fun newRequestId(): String =
        buildString { repeat(32) { append("0123456789abcdef"[kotlin.random.Random.Default.nextInt(16)]) } }

    /** Estado da recompensa de dobrões da partida online no resultado. */
    var onlineAward by mutableStateOf(OnlineAwardUi.NONE)
        private set
    var onlineAwardAmount by mutableStateOf(0)
        private set

    private var awardQueue: List<PendingAward>
        get() = OnlineAwardQueue.decode(dobroesPrefs.getString(AWARDS_KEY, ""))
        set(value) = dobroesPrefs.putString(AWARDS_KEY, OnlineAwardQueue.encode(value))

    /**
     * Pede ao servidor a recompensa de dobrões da partida online atual. Guarda na fila antes de
     * enviar: se o app fechar ou a rede cair, a próxima abertura reenvia (o servidor paga uma vez
     * por sala). Contra a IA não existe isso: são zero dobrões.
     */
    suspend fun awardOnlineDobroes(shots: Int, hits: Int, sunk: Int, turns: Int, victory: Boolean) {
        val id = onlineMatchId ?: return
        if (!profile.signedIn) return
        onlineAward = OnlineAwardUi.CHECKING
        onlineAwardAmount = 0
        awardQueue = OnlineAwardQueue.add(awardQueue, PendingAward(id, shots, hits, sunk, turns, victory))
        flushOnlineAwards(current = id)
        if (onlineAward == OnlineAwardUi.CHECKING) onlineAward = OnlineAwardUi.RETRY
    }

    /** Reenvia as recompensas online que o servidor ainda não confirmou (abertura do app). */
    suspend fun flushOnlineAwards(current: String? = null) {
        if (awardQueue.isEmpty() || !ensureWallet()) return
        for (a in awardQueue) {
            val r = (authed { s ->
                cloud.awardOnlineMatch(s, a.matchId, a.shots, a.hits, a.sunk, a.turns, a.victory)
            } as? CloudResult.Ok)?.value ?: continue
            if (!OnlineAwardQueue.settled(r.status) || r.status == "no_wallet") continue
            awardQueue = OnlineAwardQueue.remove(awardQueue, a.matchId)
            if (a.matchId == current) {
                onlineAwardAmount = r.amount
                onlineAward = when (r.status) {
                    "limit" -> OnlineAwardUi.LIMIT
                    "capped" -> OnlineAwardUi.CAPPED
                    else -> OnlineAwardUi.PAID
                }
            }
            if (r.amount > 0) refreshBalance()
            settleReferral(a.matchId)
        }
    }

    var dobroesStatus by mutableStateOf<DobroesStoreStatus?>(null)
        private set

    var dobroesProducts by mutableStateOf<List<StoreProduct>>(emptyList())
        private set

    var dobroesNotice by mutableStateOf<String?>(null)
        private set

    var dobroesBusy by mutableStateOf(false)
        private set

    /** Prêmio Beta de hoje (#28): nulo enquanto a situação da conta ainda não chegou. No iPhone também vale (#30). */
    val premioView: PremioState?
        get() {
            val s = dobroesStatus ?: return null
            return premioState(s.beta, s.premioEnabled, s.premioClaimed)
        }

    /** Vitrine paga visível: só com a Play (não no iPhone, #30), e com venda real ou vitrine beta (#28). */
    val vitrineAberta: Boolean
        get() {
            val s = dobroesStatus ?: return false
            return vitrineVisivel(billing.available, s.realEnabled, s.vitrineBeta)
        }

    /**
     * Abrir a aba: situação da conta (Prêmio) e, com a Play, preços dos pacotes e compras que
     * ficaram para trás. No iPhone a aba só mostra o Prêmio (#30).
     */
    suspend fun loadDobroesStore() {
        if (profile.currentSession() == null) {
            dobroesStatus = null
            return
        }
        if (billing.available) dobroesProducts = billing.products()
        refreshDobroesStatus()
        recoverPending()
    }

    private suspend fun refreshDobroesStatus() {
        (authed { session -> cloud.dobroesStoreStatus(session) } as? CloudResult.Ok)?.value?.let { dobroesStatus = it }
    }

    /**
     * Compra pela folha da Play. A Play confirma; o servidor confere e credita, e o saldo
     * só entra por [recoverPending] (uma vez por token).
     */
    suspend fun buyDobroesPack(pack: DoubloonPack) {
        val session = profile.currentSession() ?: return
        if (dobroesBusy) return
        dobroesBusy = true
        dobroesNotice = null
        try {
            when (val outcome = billing.buy(pack.code, session.userId)) {
                is BuyOutcome.Purchased -> when (val v = authed { s -> cloud.dobroesVerify(s, outcome.productId, outcome.token, "vitrine") }) {
                    // o saldo entra em recoverPending (aviso "dobrões entraram")
                    is CloudResult.Ok -> dobroesNotice = avisoCompra(v.value.status)?.let { t(it) }
                    is CloudResult.Fail -> dobroesNotice = t(K.DOUBLOON_BUY_FAILED)
                }
                BuyOutcome.Cancelled -> Unit
                BuyOutcome.Pending -> dobroesNotice = t(K.DOUBLOON_PENDING)
                is BuyOutcome.Failed -> dobroesNotice = t(K.DOUBLOON_BUY_FAILED)
            }
        } finally {
            dobroesBusy = false
        }
        recoverPending()
        refreshDobroesStatus()
    }

    /**
     * Prêmio Beta (#28): pede o resgate ao servidor, sem abrir a folha da Play. O saldo entra por
     * [recoverPending], uma vez por token.
     */
    suspend fun claimPremio() {
        if (dobroesBusy || profile.currentSession() == null) return
        dobroesBusy = true
        dobroesNotice = null
        try {
            dobroesNotice = when (val r = authed { s -> cloud.dobroesClaimPremio(s) }) {
                is CloudResult.Ok -> avisoPremio(r.value)?.let { t(it) }
                is CloudResult.Fail -> t(K.DOUBLOON_BUY_FAILED)
            }
        } finally {
            dobroesBusy = false
        }
        recoverPending()
        refreshDobroesStatus()
    }

    /**
     * Atualiza o saldo com o que o servidor creditou (compra, Prêmio, anúncio) e avisa "+N" uma vez
     * por token (#34: o aparelho não soma mais nada, só mostra). Antes repete a conferência das
     * compras que a Play já confirmou (queda de rede, app fechado no meio). Roda ao abrir a aba, ao
     * voltar para o app e ao abrir o jogo com conta.
     */
    suspend fun recoverPending() {
        if (recovering || profile.currentSession() == null) return
        recovering = true
        try {
            // só a Play tem recibos para repetir; o Prêmio (token premio:) chega direto por dobroes_pending (#30)
            if (billing.available) billing.purchasedTokens().forEach { p ->
                authed { s -> cloud.dobroesVerify(s, p.productId, p.token, "vitrine") }
            }
            // sem carteira aberta o servidor não creditou: a abertura soma o que está pendente (sem duplicar)
            if (!ensureWallet()) return
            val pending = (authed { s -> cloud.dobroesPending(s) } as? CloudResult.Ok)?.value ?: return
            val plan = PendingCredits.plan(
                pending.map { PendingCredit(it.purchaseToken, it.doubloons) },
                deliveredTokens()
            )
            // o conjunto de avisados fica guardado: se a confirmação se perder, o aviso não repete
            dobroesPrefs.putString(DELIVERED_KEY, plan.delivered.joinToString(","))
            refreshBalance()
            if (plan.total > 0) dobroesNotice = t(K.DOUBLOON_BOUGHT, plan.total)
            plan.toAck.forEach { token -> authed { s -> cloud.dobroesAck(s, token) } }
        } finally {
            recovering = false
        }
    }

    private fun deliveredTokens(): Set<String> =
        dobroesPrefs.getString(DELIVERED_KEY, "").split(',').filter { it.isNotBlank() }.toSet()

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

    /** Posição na temporada para o cartão do dia do deque (nula sem partida na temporada). */
    var menuSeasonPosition by mutableStateOf<Long?>(null)
        private set

    /** Pedidos de amizade esperando resposta — selo do atalho Amigos no deque. */
    val incomingFriendRequests: Int
        get() = friendships.count { it.status == "pending" && it.addresseeId == profile.accountId }

    /** O que o deque mostra além do perfil: posição na temporada e pedidos de amizade. */
    suspend fun loadMenuExtras() {
        if (!profile.signedIn) {
            menuSeasonPosition = null
            return
        }
        refreshFriendships()
        if (seasonPass?.joined == true) {
            menuSeasonPosition = (authed { session -> cloud.myRank(session, season = true) } as? CloudResult.Ok)
                ?.value?.position?.takeIf { it > 0 }
        }
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
        // com carteira o servidor credita a recompensa no resgate; sem ela não resgata (tenta depois)
        if (!ensureWallet()) return
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
                if (claimed.rewardCredits > 0) refreshBalance()
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

    // ---------------- aviso de lançamento (#36) ----------------

    private val launchPrefs = Prefs()

    /** Variação do aviso de lançamento pendente de "Entendi"; nulo = nada a mostrar. */
    var launchNotice by mutableStateOf<LaunchVariant?>(null)
        private set

    /** Só no deque, nunca em partida, por cima de convite ou do popup de atualização. */
    val launchNoticeShowing: Boolean
        get() = launchNotice != null && screen == Screen.MENU && match == null &&
            pendingInvite == null && !updatePopupPending

    /**
     * Pergunta ao servidor se o aviso vale para esta conta (interruptor, plataforma, já visto).
     * O aviso é só do Android: no iPhone nem pergunta. Falha de rede = nada aparece.
     */
    suspend fun checkLaunchNotice() {
        if (platformName == "ios" || launchNotice != null) return
        val session = profile.currentSession()
        val signedIn = session != null
        val status = (
            if (signedIn) authed { s -> cloud.launchNoticeStatus(s, platformName) }
            else cloud.launchNoticeStatus(null, platformName)
            ) as? CloudResult.Ok
        val localSeen = launchPrefs.getInt(launchNoticeKey(session?.userId), 0) == 1
        when (val decision = launchNoticeDecision(status?.value, signedIn, localSeen)) {
            LaunchNoticeDecision.Hide -> Unit
            LaunchNoticeDecision.AckOnly -> authed { s -> cloud.launchNoticeAck(s) }
            is LaunchNoticeDecision.Show -> launchNotice = decision.variant
        }
    }

    /** "Entendi": some na hora, grava a marca local e avisa o servidor (se falhar, o AckOnly reenvia). */
    fun ackLaunchNotice() {
        if (launchNotice == null) return
        launchNotice = null
        val session = profile.currentSession()
        launchPrefs.putInt(launchNoticeKey(session?.userId), 1)
        if (session != null) uiScope?.launch { authed { s -> cloud.launchNoticeAck(s) } }
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
        if (practiceSwitchSide != null) {
            // durante o aviso a partida ainda é a da IA: guarda e repete depois da troca
            practiceLines += line
            return
        }
        val m = match ?: return
        val parts = Protocol.parts(line)
        when (parts.firstOrNull()) {
            Protocol.HELLO -> {
                parts.getOrNull(1)?.let { m.setName(m.mySide.other(), it) }
                // rede local: quem entra joga no modo de quem abriu a partida (no online
                // isso já vem da sala). Só troca antes de qualquer frota ir a bordo
                val hostMode = parts.getOrNull(2)?.let { code -> GameMode.entries.firstOrNull { it.name == code } }
                if (hostMode != null && hostMode != m.mode && m.opponent == Opponent.LAN &&
                    m.mySide == Side.ENEMY && m.phase == Phase.PLACEMENT && m.fleetsReady == 0
                ) {
                    val hostName = parts.getOrNull(1)
                    mode = hostMode
                    startLanMatch(Side.ENEMY)
                    hostName?.let { match?.setName(Side.PLAYER, it) }
                }
            }

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

            // revanche online: o anfitrião abriu a sala nova — entra nela pelo id
            Protocol.ROOM -> {
                val roomId = parts.getOrNull(1) ?: return
                if (m.opponent != Opponent.ONLINE || !rematchRequestedByMe) return
                val session = profile.currentSession() ?: return
                rematchRequestedByMe = false
                rematchRequestedByOpponent = false
                onlineLink.acceptInvite(
                    session, roomId,
                    onState = { s, side -> onMain { onOnlineState(s, side) } },
                    onLine = { line -> onMain { onLine(line) } }
                )
            }

            Protocol.QUIT -> {
                m.abandon(m.mySide)
                opponentQuit = true
                if (m.opponent == Opponent.LAN) closeLink() else closeOnline()
                // só a tela de batalha leva sozinha ao relatório: quem ainda estava
                // posicionando a frota ficava preso numa partida que já tinha acabado
                if (screen == Screen.PLACEMENT || screen == Screen.HANDOFF) screen = Screen.RESULT
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
        // revanche online é uma partida nova: custa 1 milha como qualquer outra
        if (m.opponent == Opponent.ONLINE && !hasMileOrPrompt()) return
        rematchRequestedByMe = true
        sendToOpponent(Protocol.REMATCH)
        maybeStartRematch()
    }

    private fun maybeStartRematch() {
        if (!rematchRequestedByMe || !rematchRequestedByOpponent) return
        val old = match ?: return
        when (old.opponent) {
            Opponent.LAN -> {
                rematchRequestedByMe = false
                rematchRequestedByOpponent = false
                startLanMatch(old.mySide)
            }
            // online: a revanche vai para uma sala nova. Quem abriu a sala antiga cria a
            // nova e manda o id ([Protocol.ROOM]); o outro lado segue "aguardando" até
            // a linha chegar e entra por ela — os dois começam pelo caminho normal de
            // partida online (milha cobrada, ranqueada valendo de novo)
            Opponent.ONLINE -> {
                if (old.mySide != Side.PLAYER) return
                rematchRequestedByMe = false
                rematchRequestedByOpponent = false
                // a espera da sala nova não mostra código: o da sala antiga confundia
                onlineCode = null
                onlineLink.openRematchRoom(
                    mode.name,
                    onState = { s, side -> onMain { onOnlineState(s, side) } },
                    onLine = { line -> onMain { onLine(line) } }
                )
            }
            else -> Unit
        }
    }

    // ---------------- conta e sincronização ----------------

    /** Cria a conta e já sobe a carreira que existir neste aparelho. */
    suspend fun createAccount(email: String, password: String, username: String): Pair<Boolean, String> =
        when (val r = cloud.signUp(email, password, username)) {
            is CloudResult.Ok -> {
                val local = profile.credits
                profile.rememberSession(r.value)
                profile.rename(username)
                cloud.saveProfile(r.value, profile.snapshot())
                ensureWallet(local)
                claimPendingWelcome()
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
                val local = profile.credits  // carteira do convidado: vai para a conta uma vez
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                ensureWallet(local)
                claimPendingWelcome()
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
                val local = profile.credits  // carteira do convidado: vai para a conta uma vez
                profile.rememberSession(r.value)
                val merged = mergeWithCloud() ?: t(K.AUTH_CONNECTED_NO_SYNC)
                ensureWallet(local)
                claimPendingWelcome()
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
        // o saldo do aparelho antes da fusão (adotar a nuvem o sobrescreve): é o que a abertura leva
        val local = profile.credits
        mergeWithCloud()
        ensureWallet(local)
        claimPendingWelcome()
        flushOnlineAwards()
        recoverPending()
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

    /**
     * Se a conta logada tem senha (#9). Nulo = não sabemos (sem rede, erro, ou ainda
     * não carregou) e conta como "tem senha", que é o comportamento de antes.
     * Carregado ao abrir o Perfil com [loadHasPassword]; zera ao sair da conta.
     */
    var hasPassword by mutableStateOf<Boolean?>(null)

    suspend fun loadHasPassword() {
        if (!profile.signedIn) { hasPassword = null; return }
        hasPassword = (authed { session -> cloud.accountHasPassword(session) } as? CloudResult.Ok)?.value
    }

    /**
     * Cria a primeira senha de uma conta que entrou só com o Google. Não pede senha
     * atual (não existe): a sessão Google aberta é a prova de identidade, e o
     * "Secure password change" do Auth está desligado, então não exige login recente.
     */
    suspend fun createPassword(newPassword: String): Pair<Boolean, String> =
        when (val r = authed { session -> cloud.updatePassword(session, newPassword) }) {
            is CloudResult.Ok -> {
                hasPassword = true
                true to t(K.AUTH_PASSWORD_CREATED)
            }
            is CloudResult.Fail -> false to r.message
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
                    lastPushed = cloudProfile.copy(credits = 0)  // o snapshot não leva dobrões
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
    // Play Games (#48): inerte sem app_id e IDs; nunca abre tela de login própria
    LaunchedEffect(Unit) { state.startGameServices() }

    // daí em diante a carreira sobe sozinha a cada mudança — não existe botão de
    // sincronizar, o comandante nunca precisa lembrar de gravar nada
    LaunchedEffect(Unit) { state.autoSync() }

    // partida online em segundo plano: avisa o adversário e mede os 60s de prazo
    // (ver AppState.onForegroundChanged) — nas outras variantes de partida, o
    // próprio sistema já suspende as corrotinas de turno enquanto o app não está
    // em primeiro plano, então não precisa de aviso nenhum
    LaunchedEffect(AppForeground.active) { state.onForegroundChanged(AppForeground.active) }
    // voltou para o app: confere compras que ficaram pendentes (queda de rede, app fechado)
    LaunchedEffect(AppForeground.active) { if (AppForeground.active) state.recoverPending() }

    // avisa se já existe uma versão mais nova publicada, sem precisar de servidor de push —
    // também ao voltar do segundo plano, senão quem deixa o app aberto nunca via o aviso
    LaunchedEffect(AppForeground.active) {
        if (AppForeground.active) state.checkForUpdate()
    }

    // temporada ranqueada corrente — vem do servidor pra não depender do relógio
    // do aparelho; se for diferente da última aceita, o popup de nova temporada aparece
    // também ao entrar na conta: quem fazia login com o app já aberto ficava sem o passe
    // (e sem ranqueada) até reabrir — no iPhone, para sempre (ver MainViewController)
    LaunchedEffect(state.profile.signedIn) { state.loadSeason() }

    // badges e feedback avaliado: mesmo padrão sem push de verdade — checa na
    // abertura e toda vez que o app volta do segundo plano. O popup de recompensa
    // aparece por conta própria via FeedbackRewardPopup, olhando state.feedbackReward
    LaunchedEffect(AppForeground.active, state.profile.signedIn) {
        if (AppForeground.active) {
            state.loadBadges()
            state.checkFeedbackRewards()
            state.checkLaunchNotice()
            // a temporada pode virar com o app aberto: relê o passe e o fechamento
            state.loadSeason()
            state.checkSeasonEnd()
            // Diário de bordo: toda entrada no app pode mostrar o balão de novo
            state.onAppEntered()
            // pedido de amizade que chegou com o app minimizado: acende o selo de Amigos
            state.refreshFriendships()
        }
    }

    // push que chega com o jogo aberto (o sistema não mostra): relê as amizades para o
    // selo do deque e a tela de Amigos refletirem pedido recebido/aceito na hora
    LaunchedEffect(Unit) {
        PushInbox.events.collect { kind ->
            if (kind.startsWith("friend")) state.refreshFriendships()
        }
    }

    // push tocado (#19): leva à tela do aviso ("amigo online" -> Amigos). Espera a
    // abertura terminar e nunca tira o jogador de uma partida em andamento
    val openedPush by PushInbox.opened.collectAsState()
    LaunchedEffect(openedPush, state.screen) {
        val kind = openedPush ?: return@LaunchedEffect
        if (state.screen == Screen.SPLASH) return@LaunchedEffect
        PushInbox.consumeOpened()
        val target = screenForPushKind(kind) ?: return@LaunchedEffect
        if (state.match == null && state.profile.signedIn) {
            state.screen = target
            state.refreshFriendships()
        }
    }

    // notificações (push e lembrete): pede a permissão uma vez, ao chegar no menu — o
    // sistema só mostra o pedido enquanto a pessoa não respondeu
    LaunchedEffect(state.screen == Screen.MENU) {
        if (state.screen == Screen.MENU) DailyReminder.requestPermission()
    }

    // presença para a lista de amigos: com o jogo aberto bate o ponto a cada minuto
    // ("em partida" durante a partida — aí não recebe convite); ao fechar ou minimizar
    // avisa na hora que saiu, em vez de esperar o ponto vencer
    val inMatch = state.match != null
    LaunchedEffect(AppForeground.active, state.profile.signedIn, inMatch, state.profile.acceptInvites, state.profile.friendOnlinePush) {
        if (!state.profile.signedIn) return@LaunchedEffect
        if (!AppForeground.active) {
            state.touchPresence("away")
            return@LaunchedEffect
        }
        while (true) {
            state.touchPresence(if (inMatch) "in_match" else "online")
            delay(60_000)
        }
    }

    // milhas náuticas: recarga do dia vem do servidor — relê ao abrir, ao voltar do
    // segundo plano, ao entrar/sair da conta e ao voltar ao deque depois de partida
    LaunchedEffect(AppForeground.active, state.profile.signedIn, state.screen == Screen.MENU) {
        if (AppForeground.active) {
            state.loadMiles()
            state.loadDaily()
            if (state.screen == Screen.MENU) state.loadReferralStatus()
            state.registerPush()
            state.reportDeviceCheck()
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
            // "Voltar" do Android: antes fechava o app de qualquer tela. Agora volta para
            // onde o botão Voltar da tela levaria; dentro da partida não faz nada, para um
            // gesto sem querer não derrubar a batalha. No menu e na abertura segue saindo.
            val backTarget: Screen? = when (state.screen) {
                Screen.SHIPYARD, Screen.STORE, Screen.PROFILE, Screen.SETTINGS, Screen.LAN,
                Screen.ONLINE, Screen.FRIENDS, Screen.LEADERBOARD -> Screen.MENU
                Screen.RELEASE_NOTES -> Screen.SETTINGS
                Screen.FEEDBACK -> state.feedbackReturn
                else -> null
            }
            val inMatch = state.screen in setOf(
                Screen.NAMES, Screen.PLACEMENT, Screen.HANDOFF, Screen.BATTLE, Screen.RESULT
            )
            SystemBackHandler(enabled = state.modePick != null || backTarget != null || inMatch) {
                when {
                    state.modePick != null -> state.modePick = null
                    backTarget != null -> state.screen = backTarget
                }
            }
            ScreenFrame(state) {
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
            }
            OnlineWaitingDialog(state)
            state.pendingInvite?.let { invite -> InviteBanner(state, invite) }
            // o polling já trava o balão nas situações proibidas; a checagem aqui
            // cobre o intervalo de até 4s entre uma rodada e outra
            state.quickOffer?.let { offer ->
                if (state.match == null && state.pendingInvite == null) QuickOfferBanner(state, offer)
            }
            state.quickOfferNotice?.let { notice -> QuickOfferNotice(state, notice) }
            if (state.screen == Screen.PLACEMENT || state.practiceSwitchSide != null) OpponentFoundPopup(state)
            UpdatePopup(state)
            if (state.match == null) SeasonEndPopup(state)
            if (state.match == null) SeasonPopup(state)
            RankedLockedPrompt(state)
            ModePickerSheet(state)
            DailyPopup(state)
            GuidedOfferPopup(state)
            WelcomeGiftPopup(state)
            ReferralPopup(state)
            FeedbackPopup(state)
            if (state.match == null) FeedbackRewardPopup(state)
            // aviso de lançamento: por cima da temporada; o convite dela aparece embaixo ao tocar "Entendi"
            LaunchNoticePopup(state)
            // por último: abre por cima do convite quando falta milha para aceitar
            MilesPopup(state)
        }
    }
}
