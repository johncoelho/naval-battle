package br.com.navalbattle.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.navalbattle.data.CloudProfile
import br.com.navalbattle.data.Prefs
import br.com.navalbattle.data.Session
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/** Patente do comandante. A carreira sobe por XP ganho em combate. */
enum class Rank(val key: K, val xp: Int) {
    RECRUTA(K.RANK_RECRUIT, 0),
    MARINHEIRO(K.RANK_SAILOR, 300),
    CABO(K.RANK_CORPORAL, 800),
    SARGENTO(K.RANK_SERGEANT, 1600),
    TENENTE(K.RANK_LIEUTENANT, 2800),
    CAPITAO_CORVETA(K.RANK_CORVETTE, 4500),
    CAPITAO_FRAGATA(K.RANK_FRIGATE, 7000),
    CAPITAO_MAR_GUERRA(K.RANK_CAPTAIN, 10500),
    CONTRA_ALMIRANTE(K.RANK_REAR_ADMIRAL, 15000),
    ALMIRANTE(K.RANK_ADMIRAL, 21000);

    val label: String get() = t(key)

    companion object {
        fun of(xp: Int): Rank = entries.last { xp >= it.xp }
        fun next(xp: Int): Rank? = entries.firstOrNull { it.xp > xp }
    }
}

/** Insígnia escolhida no perfil, desenhada no Canvas — nenhuma imagem no APK. */
enum class Insignia(val id: String, val key: K) {
    ANCHOR("anc", K.INSIGNIA_ANCHOR),
    TRIDENT("tri", K.INSIGNIA_TRIDENT),
    STAR("str", K.INSIGNIA_STAR),
    WHEEL("whl", K.INSIGNIA_WHEEL),
    WAVES("wav", K.INSIGNIA_WAVES),
    SKULL("skl", K.INSIGNIA_SKULL);

    val label: String get() = t(key)

    companion object {
        fun of(id: String): Insignia = entries.firstOrNull { it.id == id } ?: ANCHOR
    }
}

/**
 * Retrato do comandante, desenhado no Canvas — mostrado no topo do menu e do perfil.
 * A patente é a mesma para os dois retratos do par: escolher entre eles é escolher
 * o rosto, não o posto. Os identificadores gravados são os de sempre, para que
 * ninguém perca a escolha já feita quando a arte muda.
 */
enum class Avatar(val id: String, val key: K, val woman: Boolean) {
    COMMANDER_M("om1", K.AVATAR_COMMANDER, false),
    COMMANDER_F("of1", K.AVATAR_COMMANDER, true),
    OFFICER_M("om2", K.AVATAR_OFFICER, false),
    OFFICER_F("of2", K.AVATAR_OFFICER, true),
    BOATSWAIN_M("cpm", K.AVATAR_BOATSWAIN, false),
    BOATSWAIN_F("cpf", K.AVATAR_BOATSWAIN, true),
    AVIATOR_M("avm", K.AVATAR_AVIATOR, false),
    AVIATOR_F("avf", K.AVATAR_AVIATOR, true);

    val label: String get() = t(key)

    companion object {
        fun of(id: String): Avatar = entries.firstOrNull { it.id == id } ?: COMMANDER_M
    }
}

/**
 * Resultado de uma partida, do ponto de vista da carreira. Carrega as parcelas do
 * XP separadas porque a tela de resultado mostra a conta sendo feita, linha a linha —
 * inclusive a parcela que ficou em zero, que é o que ensina onde dá para melhorar.
 */
data class Award(
    val xp: Int,
    val credits: Int,
    val victory: Boolean,
    val rankUp: Rank?,
    val base: Int,
    val precision: Int,
    val precisionBonus: Int,
    val shipsSunk: Int,
    val sunkBonus: Int,
    val blitzBonus: Int,
    val turns: Int
)

/**
 * Carreira do comandante: identidade, patente, créditos e estatísticas de todas as
 * partidas. Vive fora da partida e é gravada a cada mudança.
 */
class Profile(private val prefs: Prefs) {

    var name by mutableStateOf(prefs.getString(K_NAME, ""))
        private set
    var insignia by mutableStateOf(Insignia.of(prefs.getString(K_INSIGNIA, Insignia.ANCHOR.id)))
        private set
    var avatar by mutableStateOf(Avatar.of(prefs.getString(K_AVATAR, Avatar.COMMANDER_M.id)))
        private set

    var xp by mutableStateOf(prefs.getInt(K_XP, 0))
        private set

    /**
     * Dobrões. Com a carteira aberta ([walletOpen]) é só o que o servidor devolveu (cache para mostrar
     * offline; muda apenas por [setServerBalance]). Sem conta (convidado) é a carteira local, usada
     * só para cosméticos: a IA não paga dobrões, então ela só encolhe, e é levada uma vez para a
     * conta no primeiro login (#34).
     */
    var credits by mutableStateOf(prefs.getInt(K_CREDITS, 500))
        private set

    /** Conta cuja carteira de dobrões já foi aberta no servidor neste aparelho (vazio = nenhuma). */
    private var walletFor by mutableStateOf(prefs.getString(K_WALLET, ""))

    /** A carteira da conta atual está aberta no servidor: o saldo é dele e o aparelho não grava. */
    val walletOpen: Boolean get() = walletFor.isNotBlank() && walletFor == accountId

    var matches by mutableStateOf(prefs.getInt(K_MATCHES, 0))
        private set
    var wins by mutableStateOf(prefs.getInt(K_WINS, 0))
        private set
    var shots by mutableStateOf(prefs.getInt(K_SHOTS, 0))
        private set
    var hits by mutableStateOf(prefs.getInt(K_HITS, 0))
        private set
    var sunk by mutableStateOf(prefs.getInt(K_SUNK, 0))
        private set
    var streak by mutableStateOf(prefs.getInt(K_STREAK, 0))
        private set
    var bestStreak by mutableStateOf(prefs.getInt(K_BEST_STREAK, 0))
        private set

    /** Camuflagens já conquistadas, além das que vêm inclusas. */
    var owned by mutableStateOf(prefs.getString(K_OWNED, "std,br").split(",").filter { it.isNotBlank() }.toSet())
        private set
    var equipped by mutableStateOf(prefs.getString(K_EQUIPPED, "br"))
        private set

    /** Linhas de casco já conquistadas. A padrão vem com o jogo. */
    var ownedFleets by mutableStateOf(prefs.getString(K_FLEETS, "std").split(",").filter { it.isNotBlank() }.toSet())
        private set
    var equippedFleet by mutableStateOf(prefs.getString(K_FLEET, "std"))
        private set

    /**
     * Cartuchos avulsos de habilidade comprados na loja: liberam um uso mesmo com a
     * habilidade em recarga, mas se gastam — não voltam sozinhos como o cooldown normal.
     * Só neste aparelho: não viaja para a nuvem, do mesmo jeito que a trilha e o idioma.
     */
    private var abilityCharges by mutableStateOf(decodeCharges(prefs.getString(K_ABILITY_CHARGES, "")))

    fun chargesOf(ability: Ability): Int = abilityCharges[ability.code] ?: 0

    /**
     * Único jeito de mudar o saldo de quem tem carteira: grava o que o servidor devolveu (#34). O
     * aparelho nunca soma nem subtrai por conta própria.
     */
    fun setServerBalance(balance: Int) {
        credits = balance
        prefs.putInt(K_CREDITS, balance)
    }

    /** Carteira aberta no servidor para a conta [uid]: daqui em diante o saldo é dele. */
    fun markWalletOpen(uid: String) {
        walletFor = uid
        prefs.putString(K_WALLET, uid)
    }

    /**
     * Itens que o servidor guarda (pintura e casco comprados, pintura da temporada): ele é a verdade
     * para conta com carteira. O que estiver equipado e não for mais da conta volta ao padrão.
     */
    fun adoptServerItems(ownedCsv: String, fleetsCsv: String) {
        owned = ownedCsv.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std", "br") }
        ownedFleets = fleetsCsv.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std") }
        if (equipped !in owned) equipped = "br"
        if (equippedFleet !in ownedFleets) equippedFleet = "std"
        prefs.putString(K_OWNED, owned.joinToString(","))
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
        prefs.putString(K_EQUIPPED, equipped)
        prefs.putString(K_FLEET, equippedFleet)
    }

    /** O servidor cobrou e entregou uma pintura/casco: marca como da conta (sem mexer no saldo). */
    fun markOwned(liveryId: String) {
        owned = owned + liveryId
        prefs.putString(K_OWNED, owned.joinToString(","))
    }

    fun markFleetOwned(fleetId: String) {
        ownedFleets = ownedFleets + fleetId
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
    }

    /** Cartucho avulso que o servidor cobrou. Só neste aparelho. */
    fun addCharge(ability: Ability) = grantAbilityCharge(ability)

    /**
     * Compras do convidado (sem conta): só cosméticos, pagos da carteira local. Com conta o saldo é do
     * servidor ([walletOpen]) e estas funções não se aplicam.
     */
    fun guestBuyAbilityCharge(ability: Ability, price: Int): Boolean {
        if (walletOpen || credits < price) return false
        credits -= price
        abilityCharges = abilityCharges + (ability.code to (chargesOf(ability) + 1))
        prefs.putInt(K_CREDITS, credits)
        persistCharges()
        return true
    }

    /** Recompensa por feedback aprovado (melhoria aceita) — presenteia uma carga da habilidade. */
    fun grantAbilityCharge(ability: Ability) {
        abilityCharges = abilityCharges + (ability.code to (chargesOf(ability) + 1))
        persistCharges()
    }

    fun consumeCharge(ability: Ability) {
        val left = chargesOf(ability)
        if (left <= 0) return
        abilityCharges = if (left <= 1) abilityCharges - ability.code else abilityCharges + (ability.code to left - 1)
        persistCharges()
    }

    private fun persistCharges() {
        prefs.putString(K_ABILITY_CHARGES, abilityCharges.entries.joinToString(",") { "${it.key}:${it.value}" })
    }

    private fun decodeCharges(raw: String): Map<String, Int> =
        raw.split(",").filter { it.isNotBlank() }.mapNotNull { entry ->
            val parts = entry.split(":")
            val count = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            (parts.getOrNull(0) ?: return@mapNotNull null) to count
        }.toMap()

    // ---------------- conta na nuvem ----------------

    var accountEmail by mutableStateOf(prefs.getString(K_EMAIL, ""))
        private set
    var accountId by mutableStateOf(prefs.getString(K_UID, ""))
        private set
    private var accessToken = prefs.getString(K_TOKEN, "")
    private var refreshToken = prefs.getString(K_REFRESH, "")

    val signedIn: Boolean get() = accountId.isNotBlank()

    /**
     * Marcado quando o comandante escolhe "jogar como convidado" na tela de boas-vindas —
     * evita que essa tela volte a aparecer a cada abertura enquanto ele não criar conta.
     */
    var welcomeDone: Boolean by mutableStateOf(prefs.getInt(K_WELCOME_DONE, 0) == 1)
        private set

    /** Primeira partida guiada feita ou pulada (#43): nunca mais é oferecida sozinha. */
    var guidedDone: Boolean by mutableStateOf(prefs.getInt(K_GUIDED_DONE, 0) == 1)
        private set

    fun markGuidedDone() {
        guidedDone = true
        prefs.putInt(K_GUIDED_DONE, 1)
    }

    /** Presente de boas-vindas à espera de uma conta (convidado fez a guiada, #43). */
    var welcomePending: Boolean by mutableStateOf(prefs.getInt(K_WELCOME_PENDING, 0) == 1)
        private set

    fun savePendingWelcome(value: Boolean) {
        welcomePending = value
        prefs.putInt(K_WELCOME_PENDING, if (value) 1 else 0)
    }

    fun markWelcomeDone() {
        welcomeDone = true
        prefs.putInt(K_WELCOME_DONE, 1)
    }

    /**
     * Último relato de aparelho de teste aceito pelo servidor ("conta|versão", #23):
     * fica no aparelho para relatar uma vez só por conta e versão.
     */
    var deviceCheckSent: String = prefs.getString(K_DEVICE_CHECK, "")
        set(value) {
            field = value
            prefs.putString(K_DEVICE_CHECK, value)
        }

    /** Preferência de trilha: fica no aparelho, não viaja para a nuvem. */
    var musicOn: Boolean = prefs.getInt(K_MUSIC, 1) == 1
        private set

    fun setMusic(on: Boolean) {
        musicOn = on
        prefs.putInt(K_MUSIC, if (on) 1 else 0)
    }

    /**
     * Preferência de efeitos sonoros (tiro, acerto, alarme) — separada da trilha. É
     * estado observável: sem isso o interruptor dos Ajustes não redesenhava ao tocar
     * (só mudava ao sair e voltar), embora a preferência já fosse gravada.
     */
    var sfxOn by mutableStateOf(prefs.getInt(K_SFX, 1) == 1)
        private set

    /** A dica de milhas e dobrões do deque já foi dispensada (aparece uma única vez). */
    var currencyTipSeen by mutableStateOf(prefs.getInt(K_CURRENCY_TIP, 0) == 1)
        private set

    fun markCurrencyTipSeen() {
        currencyTipSeen = true
        prefs.putInt(K_CURRENCY_TIP, 1)
    }

    fun setSfx(on: Boolean) {
        sfxOn = on
        prefs.putInt(K_SFX, if (on) 1 else 0)
    }

    /**
     * Roda a playlist do deque de comando: cada abertura do app toca a próxima faixa
     * da lista, em ciclo — ["count] é o tamanho atual da playlist, que cresce aos
     * poucos conforme mais músicas entram, sem perder a posição do rodízio. [count]
     * é o tamanho atual da playlist.
     */
    fun rollThemeTrack(count: Int): Int {
        if (count <= 0) return 0
        val cursor = prefs.getInt(K_THEME_TRACK, 0)
        prefs.putInt(K_THEME_TRACK, cursor + 1)
        return cursor % count
    }

    /**
     * Última temporada ranqueada que o comandante aceitou no popup — local, não
     * viaja pra nuvem. Enquanto for diferente da temporada corrente (vinda do
     * servidor), o popup de "nova temporada" volta a aparecer e a ranqueada
     * fica bloqueada, mesma ideia de season pass de outros jogos do gênero.
     */
    var acceptedSeasonKey: String by mutableStateOf(prefs.getString(K_SEASON_ACCEPTED, ""))
        private set

    fun acceptSeason(seasonKey: String) {
        acceptedSeasonKey = seasonKey
        prefs.putString(K_SEASON_ACCEPTED, seasonKey)
    }

    /**
     * Lembrete de avaliação na loja — aparece a cada tantas partidas, não só uma
     * vez, mas para de vez se o comandante disser que não quer mais ver.
     */
    var feedbackOptedOut: Boolean by mutableStateOf(prefs.getInt(K_FEEDBACK_OPT_OUT, 0) == 1)
        private set
    var feedbackNextPromptAt: Int by mutableStateOf(prefs.getInt(K_FEEDBACK_NEXT_AT, 5))
        private set

    fun deferFeedbackPrompt() {
        feedbackNextPromptAt += 15
        prefs.putInt(K_FEEDBACK_NEXT_AT, feedbackNextPromptAt)
    }

    fun optOutFeedback() {
        feedbackOptedOut = true
        prefs.putInt(K_FEEDBACK_OPT_OUT, 1)
    }

    /**
     * Disponível para partida rápida: com isso ligado (Ajustes, só com conta), o
     * jogo mostra em qualquer tela um balão quando alguém abre uma sala de partida
     * rápida esperando adversário. Desligado por padrão. Fica no aparelho.
     */
    var quickOfferEnabled: Boolean by mutableStateOf(prefs.getInt(K_QUICK_OFFER, 0) == 1)
        private set

    /** Que tipo de sala o balão oferece: casual, ranqueada ou as duas. */
    var quickOfferKind: QuickOfferKind by mutableStateOf(QuickOfferKind.of(prefs.getString(K_QUICK_OFFER_KIND, "")))
        private set

    /**
     * Recebe convites de amigos para partida online (balão em qualquer tela e push).
     * Ligado por padrão; desligado, os amigos veem "não aceita convites".
     */
    var acceptInvites: Boolean by mutableStateOf(prefs.getInt(K_ACCEPT_INVITES, 1) == 1)
        private set

    fun changeAcceptInvites(on: Boolean) {
        acceptInvites = on
        prefs.putInt(K_ACCEPT_INVITES, if (on) 1 else 0)
    }

    /**
     * Push "amigo online, chamar para batalha" (#19). Ligado por padrão; vai ao servidor
     * pelo touch_presence. Com os convites desligados o servidor também não avisa.
     */
    var friendOnlinePush: Boolean by mutableStateOf(prefs.getInt(K_FRIEND_ONLINE_PUSH, 1) == 1)
        private set

    fun changeFriendOnlinePush(on: Boolean) {
        friendOnlinePush = on
        prefs.putInt(K_FRIEND_ONLINE_PUSH, if (on) 1 else 0)
    }

    fun setQuickOffer(on: Boolean) {
        quickOfferEnabled = on
        prefs.putInt(K_QUICK_OFFER, if (on) 1 else 0)
    }

    fun chooseQuickOfferKind(kind: QuickOfferKind) {
        quickOfferKind = kind
        prefs.putString(K_QUICK_OFFER_KIND, kind.id)
    }

    /**
     * Anti-farm das recompensas online: XP, medalhas e créditos de uma partida
     * contra outra pessoa só valem até [ONLINE_REWARDS_PER_OPPONENT] vezes por dia
     * contra o MESMO adversário — senão dois amigos combinavam dezenas de partidas
     * relâmpago só para encher a carreira. A contagem fica no aparelho, por dia
     * (UTC) e por id do adversário, e zera sozinha quando o dia vira.
     *
     * Devolve true e já conta a partida quando ainda cabe recompensa hoje.
     */
    fun claimOnlineReward(opponentId: String, day: Long): Boolean {
        val raw = prefs.getString(K_ONLINE_REWARDS, "")
        val storedDay = raw.substringBefore('|', "").toLongOrNull()
        val counts = if (storedDay == day) decodeCharges(raw.substringAfter('|', "")) else emptyMap()
        val used = counts[opponentId] ?: 0
        if (used >= ONLINE_REWARDS_PER_OPPONENT) return false
        val next = counts + (opponentId to used + 1)
        prefs.putString(K_ONLINE_REWARDS, "$day|" + next.entries.joinToString(",") { "${it.key}:${it.value}" })
        return true
    }

    fun rememberSession(session: Session) {
        accountId = session.userId
        accountEmail = session.email
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        prefs.putString(K_UID, accountId)
        prefs.putString(K_EMAIL, accountEmail)
        prefs.putString(K_TOKEN, accessToken)
        prefs.putString(K_REFRESH, refreshToken)
        // sem nome nenhum ainda (nem local, nem vindo do servidor): usa o alias do
        // e-mail (a parte antes do @) em vez de deixar em branco — melhor um nome
        // imperfeito do que cair no "Comandante" genérico logo na primeira sessão
        if (name.isBlank()) {
            rename(session.username.ifBlank { session.email.substringBefore('@') })
        }
    }

    fun currentSession(): Session? {
        if (!signedIn) return null
        return Session(accountId, accountEmail, displayName, accessToken, refreshToken)
    }

    // ---------------- desafio do dia (Diário de bordo) ----------------

    /**
     * Progresso do desafio do dia, contado no aparelho a cada partida (contra a IA e
     * online) e zerado quando a data de Brasília vinda do servidor muda. [dailyVersion]
     * é o que a tela observa — os números em si moram nas preferências.
     */
    var dailyVersion by mutableStateOf(0)
        private set

    fun dailyProgress(day: String, mission: String): Int {
        dailyVersion
        if (day.isBlank() || prefs.getString(K_DAILY_DAY, "") != day) return 0
        return when (mission) {
            "PLAY_1" -> prefs.getInt(K_DAILY_PLAYED, 0)
            "WIN_1" -> prefs.getInt(K_DAILY_WON, 0)
            "SINK_5" -> prefs.getInt(K_DAILY_SUNK, 0)
            "ABILITY_2" -> prefs.getInt(K_DAILY_ABILITIES, 0)
            "ACCURACY_60" -> prefs.getInt(K_DAILY_BEST_ACCURACY, 0)
            else -> 0
        }
    }

    fun recordDailyMatch(day: String, won: Boolean, sunk: Int, abilities: Int, accuracy: Int) {
        if (day.isBlank()) return
        if (prefs.getString(K_DAILY_DAY, "") != day) {
            prefs.putString(K_DAILY_DAY, day)
            listOf(K_DAILY_PLAYED, K_DAILY_WON, K_DAILY_SUNK, K_DAILY_ABILITIES, K_DAILY_BEST_ACCURACY)
                .forEach { prefs.putInt(it, 0) }
        }
        prefs.putInt(K_DAILY_PLAYED, prefs.getInt(K_DAILY_PLAYED, 0) + 1)
        if (won) prefs.putInt(K_DAILY_WON, prefs.getInt(K_DAILY_WON, 0) + 1)
        prefs.putInt(K_DAILY_SUNK, prefs.getInt(K_DAILY_SUNK, 0) + sunk)
        prefs.putInt(K_DAILY_ABILITIES, prefs.getInt(K_DAILY_ABILITIES, 0) + abilities)
        prefs.putInt(K_DAILY_BEST_ACCURACY, maxOf(prefs.getInt(K_DAILY_BEST_ACCURACY, 0), accuracy))
        dailyVersion++
    }

    /** Último modo escolhido (Clássico/Tático) — vem marcado na próxima escolha. */
    val lastMode: String get() = prefs.getString(K_LAST_MODE, "")

    fun setLastMode(mode: String) {
        prefs.putString(K_LAST_MODE, mode)
    }

    /** Lembrete diário às 19h (notificação local) — ligado por padrão, desliga nos Ajustes. */
    var reminderOn by mutableStateOf(prefs.getInt(K_REMINDER, 1) == 1)
        private set

    fun setReminder(on: Boolean) {
        reminderOn = on
        prefs.putInt(K_REMINDER, if (on) 1 else 0)
    }

    // Marcas locais do aviso de retorno (#42), em dias da data de Brasília (0 = nunca)
    val lastOpenDay: Int get() = prefs.getInt(K_LAST_OPEN_DAY, 0)
    val lastReturnNoticeDay: Int get() = prefs.getInt(K_RETURN_NOTICE_DAY, 0)
    val lastReturnBonusDay: Int get() = prefs.getInt(K_RETURN_BONUS_DAY, 0)
    val pendingReturnDay: Int get() = prefs.getInt(K_RETURN_PENDING_DAY, 0)

    fun markOpenDay(day: Int) = prefs.putInt(K_LAST_OPEN_DAY, day)

    fun markReturnNotice(day: Int) = prefs.putInt(K_RETURN_NOTICE_DAY, day)

    fun markReturnBonus(day: Int) = prefs.putInt(K_RETURN_BONUS_DAY, day)

    /** Dia do aviso de retorno agendado (0 = nenhum): vira "enviado" na próxima abertura. */
    fun setPendingReturnDay(day: Int) = prefs.putInt(K_RETURN_PENDING_DAY, day)

    fun signOut() {
        // o saldo da conta fica com a conta: o convidado seguinte não herda dobrões (nem os leva para outra conta)
        if (walletOpen) {
            credits = 0
            prefs.putInt(K_CREDITS, 0)
        }
        walletFor = ""
        prefs.putString(K_WALLET, "")
        accountId = ""; accountEmail = ""; accessToken = ""; refreshToken = ""
        prefs.putString(K_UID, ""); prefs.putString(K_EMAIL, "")
        prefs.putString(K_TOKEN, ""); prefs.putString(K_REFRESH, "")
    }

    /**
     * A carreira como ela vai para a nuvem. Os dobrões NÃO vão: com carteira o servidor ignora a
     * gravação e o campo só serve para ler (por isso fica em zero aqui e o upsert não o envia).
     */
    fun snapshot(): CloudProfile = CloudProfile(
        username = displayName,
        insignia = insignia.id,
        avatar = avatar.id,
        langCode = langCode,
        xp = xp,
        credits = 0,
        matches = matches,
        wins = wins,
        shots = shots,
        hits = hits,
        sunk = sunk,
        streak = streak,
        bestStreak = bestStreak,
        owned = owned.joinToString(","),
        equipped = equipped,
        fleets = ownedFleets.joinToString(","),
        fleet = equippedFleet
    )

    /** Adota a carreira que veio da nuvem e grava tudo no aparelho. */
    fun adopt(remote: CloudProfile) {
        name = remote.username.ifBlank { name }
        insignia = Insignia.of(remote.insignia)
        avatar = Avatar.of(remote.avatar)
        if (remote.langCode.isNotBlank()) langCode = remote.langCode
        xp = remote.xp
        credits = remote.credits  // com carteira é o saldo do servidor; sem ela, o último que ele guardou
        matches = remote.matches
        wins = remote.wins
        shots = remote.shots
        hits = remote.hits
        sunk = remote.sunk
        streak = remote.streak
        bestStreak = remote.bestStreak
        owned = remote.owned.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std", "br") }
        equipped = remote.equipped.ifBlank { "br" }
        ownedFleets = remote.fleets.split(",").filter { it.isNotBlank() }.toSet().ifEmpty { setOf("std") }
        equippedFleet = remote.fleet.ifBlank { "std" }
        persistAll()
    }

    private fun persistAll() {
        prefs.putString(K_NAME, name)
        prefs.putString(K_INSIGNIA, insignia.id)
        prefs.putString(K_AVATAR, avatar.id)
        prefs.putString(K_LANG, langCode)
        prefs.putInt(K_XP, xp)
        prefs.putInt(K_CREDITS, credits)
        prefs.putInt(K_MATCHES, matches)
        prefs.putInt(K_WINS, wins)
        prefs.putInt(K_SHOTS, shots)
        prefs.putInt(K_HITS, hits)
        prefs.putInt(K_SUNK, sunk)
        prefs.putInt(K_STREAK, streak)
        prefs.putInt(K_BEST_STREAK, bestStreak)
        prefs.putString(K_OWNED, owned.joinToString(","))
        prefs.putString(K_EQUIPPED, equipped)
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
        prefs.putString(K_FLEET, equippedFleet)
    }

    val rank: Rank get() = Rank.of(xp)
    val losses: Int get() = matches - wins
    val accuracy: Int get() = if (shots == 0) 0 else (hits * 100) / shots
    val winRate: Int get() = if (matches == 0) 0 else (wins * 100) / matches

    /** Quanto falta, de 0 a 1, para a próxima patente. */
    val rankProgress: Float
        get() {
            val next = Rank.next(xp) ?: return 1f
            val floor = rank.xp
            return ((xp - floor).toFloat() / (next.xp - floor)).coerceIn(0f, 1f)
        }

    /**
     * Em branco: "Convidado" para quem ainda não tem conta, "Comandante" para quem
     * já entrou mas por algum motivo ficou sem nome (login sempre tenta preencher
     * um, ver [rememberSession] — este é só um segurança).
     */
    val displayName: String get() = name.ifBlank { t(if (signedIn) K.COMMANDER else K.GUEST) }

    fun rename(value: String) {
        name = value.trim().take(18)
        prefs.putString(K_NAME, name)
    }

    /** Idioma escolhido — acompanha a carreira na nuvem, igual ao resto do perfil. */
    var langCode: String by mutableStateOf(prefs.getString(K_LANG, "pt"))
        private set

    fun setLang(code: String) {
        langCode = code
        prefs.putString(K_LANG, code)
    }

    fun chooseInsignia(value: Insignia) {
        insignia = value
        prefs.putString(K_INSIGNIA, value.id)
    }

    fun chooseAvatar(value: Avatar) {
        avatar = value
        prefs.putString(K_AVATAR, value.id)
    }

    fun owns(liveryId: String): Boolean = liveryId in owned

    fun ownsFleet(fleetId: String): Boolean = fleetId in ownedFleets

    /** Compra de convidado: camuflagem paga da carteira local. Falso quando não há saldo (ou há conta). */
    fun guestBuy(liveryId: String, price: Int): Boolean {
        if (owns(liveryId)) return true
        if (walletOpen || credits < price) return false
        credits -= price
        owned = owned + liveryId
        prefs.putInt(K_CREDITS, credits)
        prefs.putString(K_OWNED, owned.joinToString(","))
        return true
    }

    /** Compra de convidado: linha de casco paga da carteira local. */
    fun guestBuyFleet(fleetId: String, price: Int): Boolean {
        if (ownsFleet(fleetId)) return true
        if (walletOpen || credits < price) return false
        credits -= price
        ownedFleets = ownedFleets + fleetId
        prefs.putInt(K_CREDITS, credits)
        prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
        return true
    }

    fun equip(liveryId: String) {
        if (!owns(liveryId)) return
        equipped = liveryId
        prefs.putString(K_EQUIPPED, liveryId)
    }

    fun equipFleet(fleetId: String) {
        if (!ownsFleet(fleetId)) return
        equippedFleet = fleetId
        prefs.putString(K_FLEET, fleetId)
    }

    /**
     * Fecha uma partida contra a IA ou online: soma XP e estatísticas. Dobrões NÃO: contra a IA são
     * zero em qualquer resultado ([Wallet.aiCredits]) e online quem paga é o servidor
     * (`award_online_match`), então [Award.credits] volta sempre em zero. Online passa antes pelo
     * limite diário de [claimOnlineReward] (XP e medalhas). Partidas locais não valem carreira —
     * os dois jogam no mesmo perfil do aparelho.
     */
    fun registerMatch(victory: Boolean, shotsFired: Int, hitsLanded: Int, shipsSunk: Int, turns: Int): Award {
        val before = rank
        // vitória rende o dobro; precisão e economia de turnos entram como bônus
        val base = if (victory) 220 else 80
        val precision = if (shotsFired == 0) 0 else (hitsLanded * 100) / shotsFired
        val precisionBonus = precision * 2
        val sunkBonus = shipsSunk * 15
        val blitzBonus = if (victory && turns <= BLITZ_TURNS) 120 else 0
        val bonus = precisionBonus + sunkBonus + blitzBonus
        val gainedXp = base + bonus
        val gainedCredits = Wallet.aiCredits(victory)

        xp += gainedXp
        matches += 1
        if (victory) wins += 1
        shots += shotsFired
        hits += hitsLanded
        sunk += shipsSunk
        streak = if (victory) streak + 1 else 0
        if (streak > bestStreak) bestStreak = streak

        prefs.putInt(K_XP, xp)
        prefs.putInt(K_MATCHES, matches)
        prefs.putInt(K_WINS, wins)
        prefs.putInt(K_SHOTS, shots)
        prefs.putInt(K_HITS, hits)
        prefs.putInt(K_SUNK, sunk)
        prefs.putInt(K_STREAK, streak)
        prefs.putInt(K_BEST_STREAK, bestStreak)

        return Award(
            xp = gainedXp,
            credits = gainedCredits,
            victory = victory,
            rankUp = rank.takeIf { it != before },
            base = base,
            precision = precision,
            precisionBonus = precisionBonus,
            shipsSunk = shipsSunk,
            sunkBonus = sunkBonus,
            blitzBonus = blitzBonus,
            turns = turns
        )
    }

    /**
     * Zera a carreira — pedido explícito do comandante no perfil. Dobrões e itens (pinturas, cascos,
     * cartuchos) são da conta, não da carreira: ficam ([keepWallet]); só a exclusão da conta
     * ([deleteAccount]) zera tudo (#34).
     */
    fun reset(keepWallet: Boolean = true) {
        val keepCredits = credits
        val keepOwned = owned
        val keepFleets = ownedFleets
        val keepCharges = abilityCharges
        val keepWalletFor = walletFor
        val keepUid = prefs.getString(K_UID, "")
        val keepEmail = prefs.getString(K_EMAIL, "")
        val keepToken = prefs.getString(K_TOKEN, "")
        val keepRefresh = prefs.getString(K_REFRESH, "")
        prefs.clear()
        name = ""
        insignia = Insignia.ANCHOR
        xp = 0
        matches = 0; wins = 0; shots = 0; hits = 0; sunk = 0
        streak = 0; bestStreak = 0
        equipped = "br"
        equippedFleet = "std"
        avatar = Avatar.COMMANDER_M
        if (keepWallet) {
            credits = keepCredits
            owned = keepOwned
            ownedFleets = keepFleets
            abilityCharges = keepCharges
            walletFor = keepWalletFor
            prefs.putInt(K_CREDITS, credits)
            prefs.putString(K_OWNED, owned.joinToString(","))
            prefs.putString(K_FLEETS, ownedFleets.joinToString(","))
            prefs.putString(K_WALLET, walletFor)
            persistCharges()
            // a sessão não é carreira: o reset do perfil não pode deslogar nem soltar a carteira da conta
            if (keepUid.isNotBlank()) {
                prefs.putString(K_UID, keepUid); prefs.putString(K_EMAIL, keepEmail)
                prefs.putString(K_TOKEN, keepToken); prefs.putString(K_REFRESH, keepRefresh)
            }
        } else {
            credits = 500
            owned = setOf("std", "br")
            ownedFleets = setOf("std")
            abilityCharges = emptyMap()
            walletFor = ""
        }
    }

    companion object {
        /** Vitoria ate este numero de turnos rende o bonus de ataque relampago. */
        const val BLITZ_TURNS = 40

        /** Partidas online recompensadas por dia contra o mesmo adversário (ver [claimOnlineReward]). */
        const val ONLINE_REWARDS_PER_OPPONENT = 3

        private const val K_NAME = "name"
        private const val K_INSIGNIA = "insignia"
        private const val K_XP = "xp"
        private const val K_CREDITS = "credits"
        private const val K_WALLET = "wallet_for"
        private const val K_MATCHES = "matches"
        private const val K_WINS = "wins"
        private const val K_SHOTS = "shots"
        private const val K_HITS = "hits"
        private const val K_SUNK = "sunk"
        private const val K_STREAK = "streak"
        private const val K_BEST_STREAK = "best_streak"
        private const val K_OWNED = "owned"
        private const val K_EQUIPPED = "equipped"
        private const val K_FLEETS = "fleets"
        private const val K_FLEET = "fleet"
        private const val K_ABILITY_CHARGES = "ability_charges"
        private const val K_UID = "uid"
        private const val K_EMAIL = "email"
        private const val K_TOKEN = "token"
        private const val K_REFRESH = "refresh"
        private const val K_MUSIC = "music"
        private const val K_SFX = "sfx"
        private const val K_CURRENCY_TIP = "currency_tip_seen"
        private const val K_REMINDER = "daily_reminder"
        private const val K_LAST_OPEN_DAY = "last_open_day"
        private const val K_RETURN_NOTICE_DAY = "last_return_notice_day"
        private const val K_RETURN_BONUS_DAY = "last_return_bonus_day"
        private const val K_RETURN_PENDING_DAY = "return_pending_day"
        private const val K_LAST_MODE = "last_mode"
        private const val K_DAILY_DAY = "daily_day"
        private const val K_DAILY_PLAYED = "daily_played"
        private const val K_DAILY_WON = "daily_won"
        private const val K_DAILY_SUNK = "daily_sunk"
        private const val K_DAILY_ABILITIES = "daily_abilities"
        private const val K_DAILY_BEST_ACCURACY = "daily_best_accuracy"
        private const val K_LANG = "lang"
        private const val K_AVATAR = "avatar"
        private const val K_WELCOME_DONE = "welcome_done"
        private const val K_GUIDED_DONE = "guided_done"
        private const val K_WELCOME_PENDING = "welcome_pending"
        private const val K_THEME_TRACK = "theme_track"
        private const val K_SEASON_ACCEPTED = "season_accepted"
        private const val K_FEEDBACK_OPT_OUT = "feedback_opt_out"
        private const val K_FEEDBACK_NEXT_AT = "feedback_next_at"
        private const val K_QUICK_OFFER = "quick_offer"
        private const val K_ACCEPT_INVITES = "accept_invites"
        private const val K_FRIEND_ONLINE_PUSH = "friend_online_push"
        private const val K_QUICK_OFFER_KIND = "quick_offer_kind"
        private const val K_ONLINE_REWARDS = "online_rewards"
        private const val K_DEVICE_CHECK = "device_check_sent"
    }
}

/** Tipos de sala que o balão de partida rápida oferece (Ajustes). */
enum class QuickOfferKind(val id: String) {
    CASUAL("casual"),
    RANKED("ranked"),
    BOTH("both");

    companion object {
        fun of(id: String): QuickOfferKind = entries.firstOrNull { it.id == id } ?: CASUAL
    }
}
