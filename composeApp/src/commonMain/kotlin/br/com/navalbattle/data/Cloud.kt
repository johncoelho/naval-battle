package br.com.navalbattle.data

/**
 * Endereço do projeto Supabase. A chave anônima é pública por natureza — quem
 * protege os dados são as políticas de RLS da tabela, não o segredo da chave.
 * Enquanto estiver em branco o jogo roda inteiro no aparelho, sem conta.
 */
object SupabaseConfig {
    const val URL = "https://cwtslesnthbenxswdcbv.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImN3dHNsZXNudGhiZW54c3dkY2J2Iiwicm9sZSI6ImFub24i" +
        "LCJpYXQiOjE3ODkyNjc4MDksImV4cCI6MjEwNDg0MzgwOX0.nNN323ZRPocbcRVc4DPHNoWtgP2nVBjjojWIMNSZI_g"

    val isConfigured: Boolean get() = URL.isNotBlank() && ANON_KEY.isNotBlank()
}

/** Sessão autenticada devolvida pelo Supabase Auth. */
data class Session(
    val userId: String,
    val email: String,
    val username: String,
    val accessToken: String,
    val refreshToken: String
)

/** A carreira como ela viaja para a nuvem e volta. */
data class CloudProfile(
    val username: String,
    val insignia: String,
    val avatar: String,
    val langCode: String,
    val xp: Int,
    val credits: Int,
    val matches: Int,
    val wins: Int,
    val shots: Int,
    val hits: Int,
    val sunk: Int,
    val streak: Int,
    val bestStreak: Int,
    val owned: String,
    val equipped: String,
    val fleets: String,
    val fleet: String
)

/** Resultado de uma chamada à nuvem, com a mensagem já pronta para a tela. */
sealed class CloudResult<out T> {
    data class Ok<T>(val value: T) : CloudResult<T>()

    /**
     * [expired] marca a falha por sessão vencida: é o sinal para renovar o token
     * com o refresh guardado e repetir a chamada, sem incomodar o comandante.
     */
    data class Fail(val message: String, val expired: Boolean = false) : CloudResult<Nothing>()
}

/**
 * Uma sala de partida online — de amigo (com código) ou de partida rápida.
 * `guestId` nulo quer dizer que ainda não entrou ninguém.
 */
data class OnlineMatch(
    val id: String,
    val hostId: String,
    val guestId: String?,
    val hostName: String,
    val guestName: String?,
    val mode: String,
    val status: String,
    val isQuickMatch: Boolean,
    val inviteCode: String?,
    // nulo: convite aberto (partida rápida ou código); preenchido: convite mirado
    // num amigo específico — só ele pode entrar, e é ele quem recebe o banner
    val invitedId: String?,
    // ranqueada só pareia com outra ranqueada; convite de amigo é sempre casual
    val ranked: Boolean
)

/** A folha de serviço pública de um amigo — sem e-mail; com o avatar que ele escolheu. */
data class FriendProfile(
    val username: String,
    val insignia: String,
    val xp: Int,
    val matches: Int,
    val wins: Int,
    val bestStreak: Int,
    val rankedRating: Int,
    val avatar: String = ""
)

/** Uma linha do placar — geral ou de temporada, a mesma forma para os dois. */
data class LeaderboardEntry(
    val userId: String,
    val username: String,
    val insignia: String,
    val avatar: String,
    val rating: Int,
    val matches: Int,
    val wins: Int
)

/** A temporada corrente — as 4 estações do ano, calculadas no servidor. */
data class SeasonInfo(val seasonKey: String, val name: String)

/** Posição e pontuação do próprio comandante no placar — geral ou de temporada. */
data class MyRank(val position: Long, val rating: Int)

/**
 * O que o servidor devolve ao fechar uma partida ranqueada (ver
 * `record_ranked_result` em `supabase/online.sql`) — já com o vencedor decidido
 * lá, não pelo aparelho. [pointsDelta] = [basePoints] (Elo) + [bonusPoints]
 * (bônus de desempenho na vitória, alívio na derrota). [accepted] falso quando
 * este lado já tinha relatado antes: nada mudou, só veio o estado atual.
 */
data class RankedOutcome(
    val pointsDelta: Int,
    val seasonPoints: Int,
    val seasonPosition: Int,
    val seasonName: String,
    val seasonMatches: Int,
    val seasonWins: Int,
    val accepted: Boolean,
    val basePoints: Int,
    val bonusPoints: Int
)

/**
 * Situação da loja de dobrões simulada (ver `beta_store_status` em
 * `supabase/economy.sql`): só beta tester compra, até [limitCents] por dia
 * (Brasília), e o dia vira em [resetsInSeconds].
 */
data class BetaStoreStatus(
    val eligible: Boolean,
    val spentCents: Int,
    val limitCents: Int,
    val resetsInSeconds: Int
) {
    val remainingCents: Int get() = (limitCents - spentCents).coerceAtLeast(0)
}

/**
 * Passe da temporada corrente (ver `season_pass_status` em `supabase/season.sql`).
 * [tier] nulo = ainda não aderiu — e sem adesão não há ranqueada.
 */
data class SeasonPassStatus(
    val seasonKey: String,
    val seasonName: String,
    val tier: String?,
    val entryPrice: Int,
    val upgradePrice: Int,
    val passDoubloons: Int,
    val passMiles: Int
) {
    val joined: Boolean get() = tier != null
    val premium: Boolean get() = tier == "premium"
}

/** Resultado da adesão ou do upgrade: tier final, milhas bônus que entraram e o saldo de milhas. */
data class SeasonJoin(val tier: String, val milesBonus: Int, val price: Int, val miles: Int)

/** Fechamento de uma temporada encerrada para este comandante (ver `claim_season_end`). */
data class SeasonEnd(
    val seasonKey: String,
    val seasonName: String,
    val position: Int,
    val totalPlayers: Int,
    val points: Int,
    val matches: Int,
    val wins: Int,
    val doubloons: Int,
    val miles: Int
)

/** Resultado de uma compra simulada: os dobrões que entram e o gasto do dia já somado. */
data class BetaPurchase(val doubloons: Int, val spentCents: Int, val limitCents: Int)

/**
 * Saldo de milhas náuticas e as regras vigentes (ver `miles_status` em
 * `supabase/economy.sql`) — os números vêm do servidor, ajustáveis sem versão nova.
 */
/**
 * Diário de bordo do dia (ver `supabase/daily.sql`): trilha de 7 dias de check-in e
 * o desafio do dia. [today] é a data de Brasília vinda do servidor — é a chave do
 * progresso do desafio guardado no aparelho.
 */
data class DailyStatus(
    val today: String,
    val streak: Int,
    val checkedIn: Boolean,
    val checkinReward: Int,
    val weekBonus: Int,
    val mission: String,
    val missionTarget: Int,
    val challengeClaimed: Boolean,
    val challengeReward: Int,
    val resetsInSeconds: Int
)

/** Resultado do check-in: dia da trilha e dobrões a creditar (0 se já tinha feito). */
data class DailyCheckin(val streak: Int, val doubloons: Int, val weekCompleted: Boolean)

/** Novidades de uma versão publicada (tabela `app_releases`, ver `supabase/releases.sql`). */
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val notesPt: String,
    val notesEn: String,
    val notesEs: String
)

data class MilesStatus(
    val miles: Int,
    val daily: Int,
    val cap: Int,
    val resetsInSeconds: Int,
    val packSize: Int,
    val packPrice: Int,
    val rankedWin: Int,
    val casualWin: Int
)

/**
 * Retrato e patente públicos de um comandante qualquer — mostrado quando a
 * partida rápida encontra alguém e como nome do adversário durante o combate.
 * Não exige amizade, ao contrário de [FriendProfile].
 */
data class OpponentProfile(
    val username: String,
    val insignia: String,
    val avatar: String,
    val xp: Int,
    val rankedRating: Int
)

/**
 * Uma linha do ranking de troféus de uma temporada já encerrada — [tier] é
 * "ouro", "prata", "bronze" ou nulo (sem medalha), calculado no servidor a
 * partir da colocação final.
 */
data class SeasonTrophy(
    val userId: String,
    val username: String,
    val insignia: String,
    val avatar: String,
    val points: Int,
    val position: Long,
    val tier: String?
)

/** Uma jogada trocada na sala — o corpo é sempre uma linha do [Protocol]. */
data class OnlineMessage(val id: Long, val senderId: String, val body: String)

/**
 * Um feedback (bug ou melhoria) já avaliado no servidor. A recompensa só vem
 * preenchida quando [status] é "approved": créditos (bug) ou [rewardCharges]
 * cargas de [rewardAbilityCode] (melhoria). Ver `supabase/feedback.sql`.
 */
data class FeedbackUpdate(
    val id: String,
    val kind: String,
    val status: String,
    val rewardCredits: Int,
    val rewardAbilityCode: String?,
    val rewardCharges: Int,
    val reviewNote: String?
)

/** Um badge conquistado — o rótulo/ícone vêm do catálogo local, ver `game/Badge.kt`. */
data class UserBadge(val code: String, val earnedAt: String)

/** Resultado de uma busca por nome de comandante, para mandar pedido de amizade. */
data class CommanderHit(val id: String, val username: String, val avatar: String = "")

/** Pedido de amizade — pendente, aceito ou recusado. */
data class Friendship(
    val id: String,
    val requesterId: String,
    val addresseeId: String,
    val requesterUsername: String,
    val addresseeUsername: String,
    val status: String
)

/**
 * Conversa com o Supabase: contas, sincronização da carreira e o modo online
 * (salas de partida e amizades). Implementação nativa por plataforma — no
 * Android e no iOS via HTTP simples, sem biblioteca extra.
 */
expect class CloudApi() {
    suspend fun signUp(email: String, password: String, username: String): CloudResult<Session>
    suspend fun signIn(email: String, password: String): CloudResult<Session>

    /** Troca o token de identidade do Google (ver [GoogleAuth]) por uma sessão do jogo. */
    suspend fun signInWithGoogle(idToken: String): CloudResult<Session>
    suspend fun refresh(refreshToken: String): CloudResult<Session>
    suspend fun loadProfile(session: Session): CloudResult<CloudProfile?>
    suspend fun saveProfile(session: Session, profile: CloudProfile): CloudResult<Unit>

    /**
     * Troca a senha de quem já está logado. O Supabase aceita a troca com só o token
     * de sessão válido, mas exigimos a senha atual antes de chamar isto — reautenticando
     * com [signIn] — para ninguém trocar a senha de uma sessão esquecida aberta.
     */
    suspend fun updatePassword(session: Session, newPassword: String): CloudResult<Unit>

    /**
     * Se a conta tem senha (RPC `account_has_password`). Conta que entrou só com o
     * Google não tem, e o Perfil oferece "Criar senha" em vez de "Trocar senha".
     * Sem linha na resposta conta como "tem senha" (o comportamento de antes).
     */
    suspend fun accountHasPassword(session: Session): CloudResult<Boolean>

    /** Manda o e-mail de "esqueci minha senha" — link de recuperação do Supabase Auth. */
    suspend fun sendPasswordReset(email: String): CloudResult<Unit>

    // ---------------- modo online ----------------

    /**
     * Cria uma sala — de amigo (com [inviteCode]), de partida rápida, ou um convite
     * mirado num amigo específico ([invitedId]) que só ele pode aceitar.
     */
    suspend fun createOnlineMatch(
        session: Session,
        mode: String,
        quick: Boolean,
        inviteCode: String?,
        hostName: String,
        invitedId: String? = null,
        ranked: Boolean = false
    ): CloudResult<OnlineMatch>

    /** Procura uma sala de partida rápida aberta por outra pessoa — do mesmo tipo (ranqueada ou não). */
    suspend fun findQuickMatch(session: Session, mode: String, ranked: Boolean): CloudResult<OnlineMatch?>

    /**
     * Sala de partida rápida esperando adversário, de QUALQUER modo, para o balão
     * "aceitar partida rápida" fora da tela Online — [casual] e [ranked] dizem que
     * tipos o comandante aceita (os dois juntos = "Ambas"). Nunca devolve a própria
     * sala nem convite mirado num amigo; [ignored] são as salas que ele já
     * dispensou com "Agora não" nesta sessão. Via `find_quick_offer` no banco,
     * que também descarta sala parada há mais de 10 minutos pelo relógio do servidor.
     */
    suspend fun findQuickOffer(
        session: Session,
        casual: Boolean,
        ranked: Boolean,
        ignored: Set<String>
    ): CloudResult<OnlineMatch?>

    /** "Agora não" no balão de partida rápida: a sala passa para outro comandante disponível. */
    suspend fun declineQuickOffer(session: Session, matchId: String): CloudResult<Unit>

    /** Procura a sala de um código de convite, se ainda estiver esperando alguém. */
    suspend fun findMatchByCode(session: Session, code: String): CloudResult<OnlineMatch?>

    /**
     * Convite mirado ainda esperando resposta, se algum amigo mandou um pra este
     * comandante — usado pelo banner "fulano te convidou" fora da tela Online.
     */
    suspend fun findPendingInvite(session: Session): CloudResult<OnlineMatch?>

    /**
     * Guarda o token de push deste aparelho (supabase/push.sql). Sem sessão o aparelho
     * fica anônimo e recebe só os avisos gerais; com sessão, também os da conta.
     */
    suspend fun registerPushToken(session: Session?, token: String, platform: String): CloudResult<Unit>

    /** Apaga a conta e tudo dela no servidor (supabase/account.sql). Não tem volta. */
    suspend fun deleteAccount(session: Session): CloudResult<Unit>

    /** Tira o token deste aparelho da conta (ao sair dela). */
    suspend fun unregisterPushToken(session: Session, token: String): CloudResult<Unit>

    /** Recusa um convite mirado sem entrar na sala — o anfitrião para de esperar. */
    suspend fun declineOnlineInvite(session: Session, matchId: String): CloudResult<Unit>

    /**
     * Tenta entrar numa sala como convidado. Devolve nulo quando outra pessoa
     * entrou primeiro (a escrita só grava se a sala ainda estiver vazia).
     */
    suspend fun joinOnlineMatch(session: Session, matchId: String, guestName: String): CloudResult<OnlineMatch?>

    /** Consulta o estado atual da sala — usado para saber se o convidado já entrou. */
    suspend fun getOnlineMatch(session: Session, matchId: String): CloudResult<OnlineMatch?>

    /** Marca a sala como encerrada (partida terminou ou alguém abandonou). */
    suspend fun closeOnlineMatch(session: Session, matchId: String, status: String): CloudResult<Unit>

    /**
     * Grava uma jogada na sala — o corpo é uma linha do [Protocol]. [clientSeq] numera
     * as jogadas deste lado: reenviar a mesma não duplica (índice único no banco).
     */
    suspend fun sendOnlineMessage(session: Session, matchId: String, body: String, clientSeq: Int): CloudResult<Unit>

    /**
     * Bate o ponto de presença na sala e devolve há quantos segundos o adversário
     * bateu o dele (nulo: ele ainda não bateu nenhuma vez — app sem presença).
     */
    suspend fun onlineHeartbeat(session: Session, matchId: String): CloudResult<Int?>

    /** Traz as jogadas novas da sala, mais recentes que [afterId]. */
    suspend fun pollOnlineMessages(session: Session, matchId: String, afterId: Long): CloudResult<List<OnlineMessage>>

    // ---------------- amigos ----------------

    /** Procura comandantes pelo início do nome, para mandar pedido de amizade. */
    suspend fun searchCommander(session: Session, query: String): CloudResult<List<CommanderHit>>

    suspend fun sendFriendRequest(
        session: Session,
        addresseeId: String,
        myUsername: String,
        theirUsername: String
    ): CloudResult<Unit>

    suspend fun respondFriendRequest(session: Session, friendshipId: String, accept: Boolean): CloudResult<Unit>

    suspend fun removeFriendship(session: Session, friendshipId: String): CloudResult<Unit>

    /** Todas as amizades do comandante — pendentes e aceitas, dos dois lados. */
    suspend fun listFriendships(session: Session): CloudResult<List<Friendship>>

    /** Folha de serviço pública de um amigo (exige amizade aceita) — para a tela de perfil dele. */
    /** Avatar de cada pessoa da lista de amigos (aceitos e pedidos), por id. */
    suspend fun friendAvatars(session: Session): CloudResult<Map<String, String>>

    /**
     * Bate o ponto de presença (supabase/presence.sql): [state] é "online" (jogo aberto),
     * "in_match" (em partida — não recebe convite) ou "away" (fechou/minimizou o jogo).
     */
    suspend fun touchPresence(session: Session, state: String, acceptInvites: Boolean, friendOnlinePush: Boolean): CloudResult<Unit>

    /** Presença de cada amigo, por id. */
    suspend fun friendPresence(session: Session): CloudResult<Map<String, FriendPresence>>

    /** Ranqueada entre amigos liberada (chave do servidor, ligada no teste fechado). */
    suspend fun friendRankedAllowed(session: Session): CloudResult<Boolean>

    suspend fun friendProfile(session: Session, friendId: String): CloudResult<FriendProfile?>

    /** Retrato e patente públicos de qualquer comandante — não exige amizade. */
    suspend fun opponentProfile(session: Session, userId: String): CloudResult<OpponentProfile?>

    // ---------------- ranqueada e temporadas ----------------

    /** A temporada corrente (uma das 4 estações do ano), calculada no servidor. */
    suspend fun currentSeason(session: Session): CloudResult<SeasonInfo>

    /** Placar geral (histórico completo, nunca zera). */
    suspend fun leaderboardOverall(session: Session, limit: Int = 50): CloudResult<List<LeaderboardEntry>>

    /** Placar da temporada corrente (reinicia sozinho a cada nova estação). */
    suspend fun leaderboardSeason(session: Session, limit: Int = 50): CloudResult<List<LeaderboardEntry>>

    /** Posição e pontos do próprio comandante — geral (season=false) ou da temporada (season=true). */
    suspend fun myRank(session: Session, season: Boolean): CloudResult<MyRank?>

    /**
     * Ranking de troféus de uma temporada já encerrada. [seasonKey] nulo pega a
     * última temporada fechada automaticamente.
     */
    suspend fun seasonTrophies(session: Session, seasonKey: String? = null): CloudResult<List<SeasonTrophy>>

    /**
     * Fecha o resultado de uma partida ranqueada — idempotente por lado. [accuracy]
     * (0–100) e [shipsLeft] (navios da própria frota ainda de pé) pesam no bônus do
     * vencedor; abandono vai como [won] falso e [accuracy] 0. Nulo quando a sala
     * não é ranqueada ou o comandante não está nela (o servidor não devolve linha).
     */
    suspend fun recordRankedResult(
        session: Session,
        matchId: String,
        won: Boolean,
        accuracy: Int,
        shipsLeft: Int
    ): CloudResult<RankedOutcome?>

    // ---------------- feedback e badges ----------------

    /**
     * Manda um bug ou sugestão — cai numa fila revisada manualmente, nunca creditada
     * na hora. Falha com "feedback_limit" na mensagem quando já há 5 pendentes.
     */
    suspend fun submitFeedback(
        session: Session,
        kind: String,
        message: String,
        appVersion: String,
        platform: String,
        lang: String
    ): CloudResult<Unit>

    /** Feedbacks já avaliados que ainda não foram reivindicados, os mais antigos primeiro. */
    suspend fun pendingFeedbackUpdates(session: Session): CloudResult<List<FeedbackUpdate>>

    /**
     * Reivindica um feedback avaliado e devolve a recompensa — nulo quando já foi
     * reivindicado (por este ou outro aparelho). Só o que volta daqui é aplicado.
     */
    suspend fun claimFeedback(session: Session, feedbackId: String): CloudResult<FeedbackUpdate?>

    /** Badges conquistados pelo comandante — concedidos só pelo servidor. */
    suspend fun myBadges(session: Session): CloudResult<List<UserBadge>>

    /** Loja simulada: quem pode comprar, quanto já gastou hoje e quando o dia vira. */
    suspend fun betaStoreStatus(session: Session): CloudResult<BetaStoreStatus>

    /** Compra simulada de um pacote — o servidor confere badge e limite; o app credita o que voltar. */
    suspend fun buyBetaPack(session: Session, pack: String): CloudResult<BetaPurchase>

    /** Saldo de milhas (já com a recarga do dia aplicada) e as regras. */
    suspend fun milesStatus(session: Session): CloudResult<MilesStatus>

    /**
     * Últimas versões publicadas para [platform] ("android"/"ios"), da mais nova para a
     * mais antiga. Leitura pública — funciona também sem conta.
     */
    suspend fun recentReleases(platform: String): CloudResult<List<AppRelease>>

    /** Diário de bordo de hoje: trilha de check-in e desafio do dia. */
    suspend fun dailyStatus(session: Session): CloudResult<DailyStatus>

    /** Faz o check-in do dia (uma vez por dia, travado no servidor). */
    suspend fun dailyCheckin(session: Session): CloudResult<DailyCheckin>

    /** Resgata o desafio do dia — devolve os dobrões (0 se já tinha resgatado). */
    suspend fun claimDailyChallenge(session: Session, mission: String, progress: Int): CloudResult<Int>

    /** Gasta 1 milha ao começar a partida online [matchId] — uma vez por sala. Devolve o saldo. */
    suspend fun spendMile(session: Session, matchId: String): CloudResult<Int>

    /** Milhas da vitória na partida [matchId] (ranqueada só se o servidor registrou a vitória). */
    suspend fun awardWinMiles(session: Session, matchId: String): CloudResult<Int>

    /** Soma [packs] pacotes de milhas — os dobrões já foram descontados no aparelho. */
    suspend fun buyMiles(session: Session, packs: Int): CloudResult<Int>

    /** Passe da temporada corrente e os preços vigentes. */
    suspend fun seasonPassStatus(session: Session): CloudResult<SeasonPassStatus>

    /** Adere à temporada ('free' ou 'premium') ou faz upgrade de free para premium. */
    suspend fun joinSeason(session: Session, tier: String): CloudResult<SeasonJoin>

    /** Resgata o resultado da última temporada encerrada ainda não vista — nulo se não houver. */
    suspend fun claimSeasonEnd(session: Session): CloudResult<SeasonEnd?>
}

/** Segundos desde a última batida de ponto (-1 = nunca) e se estava em partida. */
data class FriendPresence(val seenSecs: Int, val inMatch: Boolean, val acceptsInvites: Boolean = true, val xp: Int = -1)
