package br.com.navalbattle.game

import br.com.navalbattle.i18n.K

/**
 * Os quatro lugares de anúncio premiado (#29). [code] é o nome no servidor (`supabase/ads.sql`).
 * Só estes: nada de intersticial, banner, anúncio na abertura nem durante a partida.
 */
enum class AdPlace(val code: String) {
    WIN("win"), CHECKIN("checkin"), CHALLENGE("challenge"), MILE("mile")
}

/** Como terminou o anúncio na tela (o prêmio em si só vem do servidor, pela confirmação do Google). */
enum class AdShowOutcome { EARNED, CLOSED_EARLY, UNAVAILABLE }

/** Estado do botão de um lugar enquanto o fluxo anda. */
enum class AdPhase { IDLE, LOADING, CONFIRMING }

/**
 * Regras puras do anúncio premiado, para testar sem aparelho. O servidor decide o prêmio e os
 * limites; aqui fica só o que o app precisa para mostrar ou esconder o botão e escolher o aviso.
 */
object AdRules {
    /** Unidade de anúncio premiado de teste do Google (debug, `.preview`, QA e a conta do John). */
    const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"

    /** Blocos reais no AdMob, um por lugar para medir cada um. Só no build de produção. */
    val REAL_UNITS: Map<AdPlace, String> = mapOf(
        AdPlace.WIN to "ca-app-pub-4691964664970287/1125915726",
        AdPlace.CHECKIN to "ca-app-pub-4691964664970287/6186670716",
        AdPlace.CHALLENGE to "ca-app-pub-4691964664970287/9676068597",
        AdPlace.MILE to "ca-app-pub-4691964664970287/4873589041"
    )

    /**
     * Escolhe a unidade do anúncio. [buildTest] = debug ou `.preview` → sempre exemplo. [serverTest]
     * = conta de teste do servidor (`ads_enabled = 'test'`): bloco real só em emulador (o Google
     * já trata emulador como aparelho de teste, então não há clique real), e no aparelho físico
     * segue o exemplo (#32). Conta comum (produção `'true'`) recebe o bloco real.
     */
    fun unitFor(place: AdPlace, buildTest: Boolean, serverTest: Boolean, emulator: Boolean): String = when {
        buildTest -> TEST_REWARDED_UNIT
        !serverTest -> REAL_UNITS.getValue(place)
        emulator -> REAL_UNITS.getValue(place)
        else -> TEST_REWARDED_UNIT
    }

    /**
     * Sinais de emulador do Android (Genymotion incluído). Na dúvida, não é emulador: o aparelho
     * físico nunca cai no bloco real pela conta de teste. Valores de `android.os.Build`.
     */
    fun isEmulatorBuild(
        fingerprint: String,
        model: String,
        hardware: String,
        product: String,
        manufacturer: String
    ): Boolean {
        val fp = fingerprint.lowercase()
        val md = model.lowercase()
        val hw = hardware.lowercase()
        val pr = product.lowercase()
        val mf = manufacturer.lowercase()
        return hw == "goldfish" || hw == "ranchu" ||
            fp.startsWith("generic") || fp.startsWith("unknown") ||
            md.contains("sdk_gphone") || md.contains("emulator") || md.contains("android sdk built for") ||
            pr.startsWith("sdk") || pr == "google_sdk" || pr == "vbox86p" ||
            mf == "genymotion"
    }

    /**
     * O botão "dobrar dobrões" do resultado: só em vitória contra a IA ou online que rendeu
     * dobrões ([credits] > 0, então já dentro do limite de 3 por adversário online), e nunca
     * em vitória sem combate, por ausência do outro, ou de quem abandonou. 2 jogadores e rede
     * local não pagam carreira, então também não.
     */
    fun winEligible(
        opponent: Opponent,
        victory: Boolean,
        credits: Int,
        wonWithoutCombat: Boolean,
        wonByAbsence: Boolean,
        forfeitedBySelf: Boolean
    ): Boolean =
        (opponent == Opponent.AI || opponent == Opponent.ONLINE) &&
            victory && credits > 0 && !wonWithoutCombat && !wonByAbsence && !forfeitedBySelf

    /** O que a vitória pede ao servidor: os dobrões dela, até o teto que o servidor informou. */
    fun winAmount(credits: Int, cap: Int): Int = minOf(credits, cap).coerceAtLeast(0)

    /**
     * Referência da vitória (cada uma dobra uma vez): id da sala no online, id gerado no
     * aparelho contra a IA. Mesmo formato que `ad_reward_start` aceita.
     */
    fun winRef(onlineMatchId: String?, localMatchId: String): String =
        if (onlineMatchId != null) "online:$onlineMatchId" else "ai:$localMatchId"

    /**
     * Aviso depois do anúncio. [serverStatus] é a situação do vale depois da espera
     * (applied | pending | expired | unknown); nulo quando o anúncio nem chegou ao fim.
     * Null de volta = sem aviso de anúncio (o crédito tem o próprio aviso de "+n").
     */
    fun notice(outcome: AdShowOutcome, serverStatus: String?): K? = when (outcome) {
        AdShowOutcome.UNAVAILABLE -> K.AD_UNAVAILABLE
        AdShowOutcome.CLOSED_EARLY -> K.AD_CLOSED_EARLY
        AdShowOutcome.EARNED -> when (serverStatus) {
            "applied" -> null
            else -> K.AD_DELAYED
        }
    }

    /** Aviso quando o servidor recusa o vale antes do anúncio (ok | off | limit | not_eligible | already). */
    fun startNotice(status: String): K? = when (status) {
        "ok" -> null
        "limit" -> K.AD_LIMIT
        else -> K.AD_UNAVAILABLE
    }
}
