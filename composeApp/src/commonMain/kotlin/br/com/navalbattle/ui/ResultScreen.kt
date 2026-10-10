package br.com.navalbattle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.data.LinkState
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.design.drawMedal
import br.com.navalbattle.design.drawShip
import br.com.navalbattle.game.Award
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Medal
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.Profile
import br.com.navalbattle.game.Rank
import br.com.navalbattle.game.isNetwork
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side

@Composable
fun ResultScreen(state: AppState, match: Match) {
    // em partida de rede o convidado joga do lado ENEMY do modelo: vitória, acerto e
    // frota restante são sempre do lado deste aparelho (mySide), nunca do PLAYER fixo —
    // senão o convidado reportava à ranqueada a vitória e os números do anfitrião
    val me = match.mySide
    val victory = match.winner == me
    val online = match.opponent == Opponent.ONLINE
    // mesmo aparelho ou rede local: os dois comandantes pelo nome, sem carreira.
    // Online tem o relatório completo, do ponto de vista de quem está neste aparelho
    val local = match.opponent == Opponent.LOCAL || match.opponent == Opponent.LAN
    val winnerSide = match.winner ?: Side.PLAYER

    // carreira (XP, medalhas, créditos) conta contra a IA e, desde a 0.14.0, online
    // também — com o limite diário por adversário (anti-farm). No local os dois usam
    // o mesmo perfil, então não conta. Fica num efeito para creditar uma única vez
    var award by remember(match) { mutableStateOf<Award?>(null) }
    var rewardCapped by remember(match) { mutableStateOf(false) }
    LaunchedEffect(match) {
        if (match.opponent == Opponent.AI) {
            award = state.profile.registerMatch(
                victory = victory,
                shotsFired = match.playerShots,
                hitsLanded = match.playerHits,
                shipsSunk = ShipClass.fleet.size - match.enemyBoard.remainingShips().size,
                turns = match.turnCount
            )
            // com conta conectada a carreira sobe sozinha depois de cada partida
        } else if (online) {
            if (!match.forfeitedBySelf) {
                if (state.claimOnlineReward()) {
                    award = state.profile.registerMatch(
                        victory = victory,
                        shotsFired = match.shotsOf(me),
                        hitsLanded = match.hitsOf(me),
                        shipsSunk = match.sunkBy(me),
                        turns = match.turnCount
                    )
                } else {
                    rewardCapped = true
                }
            }
            // ranqueada fecha o resultado no servidor (casual não mexe em nada lá).
            // Quem perdeu por ter ficado 60s em segundo plano agora TAMBÉM relata:
            // abandono é derrota cheia (acerto 0, sem alívio), não mais "partida que
            // não contou" — senão sair no meio virava saída grátis para não perder
            // ponto. Recompensa de carreira, essa sim, quem abandonou não leva
            if (match.forfeitedBySelf) {
                state.reportRankedResult(victory = false, accuracy = 0, shipsLeft = 0)
            } else {
                state.reportRankedResult(victory, match.accuracyOf(me), match.board(me).remainingShips().size)
            }
            // milhas da vitória: depois do relato, porque na ranqueada o servidor só
            // paga a quem ele mesmo registrou como vencedor
            if (victory && !match.forfeitedBySelf) state.awardWinMiles()
        }
        // desafio do dia: conta contra a IA e online, como a carreira (nunca quem abandonou)
        if ((match.opponent == Opponent.AI || online) && !match.forfeitedBySelf) state.recordDailyMatch(match)
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenTopBar(t(K.RESULT_TITLE), match.mode.label.uppercase())
        Gap(16)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                if (local) match.sideName(winnerSide).uppercase()
                else if (victory) t(K.RESULT_VICTORY).uppercase() else t(K.RESULT_DEFEAT).uppercase(),
                style = NavalType.display,
                color = if (local) commanderColor(winnerSide)
                else if (victory) Naval.amberStrong else Naval.danger
            )
            Gap(4)
            HudLabel(
                when {
                    local -> t(K.RESULT_WON_BATTLE)
                    victory -> t(K.CALL_VICTORY)
                    else -> t(K.CALL_DEFEAT_SUB)
                },
                Naval.inkSoft
            )

            Gap(20)
            if (local || online) {
                // encerrada a partida as duas frotas se revelam: onde estavam os navios
                // e todos os tiros que cada uma levou, na cor do seu dono
                HudLabel(t(K.RESULT_CHART))
                Gap(6)
                BoardView(
                    board = match.playerBoard,
                    skin = state.skin,
                    showShips = true,
                    sweep = false,
                    markTint = commanderColor(Side.PLAYER),
                    overlay = match.enemyBoard,
                    overlayTint = commanderColor(Side.ENEMY),
                    modifier = Modifier.fillMaxWidth()
                )
                Gap(8)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(commanderColor(side)))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                t(K.RESULT_FLEET_OF, match.sideName(side)).uppercase(),
                                style = NavalType.monoSmall,
                                color = commanderColor(side)
                            )
                        }
                    }
                }
            }

            if (local) {
                Gap(16)
                listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
                    HudLabel(match.sideName(side).uppercase(), commanderColor(side))
                    StatRow(
                        t(K.RESULT_SHOTS_HITS),
                        "${match.shotsOf(side)} / ${match.hitsOf(side)} · ${match.accuracyOf(side)}%"
                    )
                    StatRow(
                        t(K.RESULT_SHIPS_LEFT),
                        "${match.board(side).remainingShips().size} / ${ShipClass.fleet.size}"
                    )
                    Gap(8)
                }
                StatRow(t(K.TURNS), match.turnCount.toString())
            } else if (online) {
                Gap(22)
                Comparison(match)

                Gap(22)
                if (state.onlineMatchRanked) {
                    RankingBlock(state, match)
                } else {
                    HudLabel(t(K.RESULT_CASUAL_NOTE), Naval.muted)
                }

                // milhas náuticas: o custo da partida e, na vitória, o que entrou
                Gap(16)
                val gained = state.milesGained
                if (gained != null && gained > 0) {
                    MilesLabel(t(K.MILES_GAINED, gained), color = Naval.amberStrong, style = NavalType.mono, iconSize = 18.dp)
                } else {
                    MilesLabel(t(K.MILES_SPENT), color = Naval.muted)
                }

                if (rewardCapped) {
                    Gap(16)
                    HudLabel(t(K.RESULT_NO_REWARD_TODAY, Profile.ONLINE_REWARDS_PER_OPPONENT), Naval.muted)
                }
                award?.let { a -> CareerReport(state, match, a) }
            } else {
                StatRow(t(K.RESULT_SHOTS_FIRED), match.playerShots.toString())
                StatRow(t(K.RESULT_HITS), match.playerHits.toString())
                StatRow(t(K.ACCURACY), "${match.accuracy}%")
                StatRow(t(K.TURNS), match.turnCount.toString())

                award?.let { a -> CareerReport(state, match, a) }
            }
            Gap(16)
        }

        Gap(12)
        if (match.opponent.isNetwork()) {
            RematchSection(state, match)
        } else {
            PrimaryButton(t(K.RESULT_NEW_MATCH)) { state.newMatch(match.opponent) }
        }
        Gap(8)
        SecondaryButton(t(K.BACK_TO_DECK)) { state.quitToMenu() }
    }
}

/**
 * A parte de carreira do relatório — apuração do XP, condecorações, patente e a
 * frota de volta ao porto. Igual contra a IA e online: sempre do lado deste
 * aparelho ([Match.mySide]).
 */
@Composable
private fun CareerReport(state: AppState, match: Match, a: Award) {
    val afloat = match.board(match.mySide).remainingShips().size

    Gap(26)
    ScoreTally(a)

    Gap(26)
    MedalCase(
        Medal.earnedIn(
            award = a,
            ownShipsLeft = afloat,
            fleetSize = ShipClass.fleet.size,
            matchesBefore = state.profile.matches - 1,
            streakAfter = state.profile.streak
        )
    )

    Gap(26)
    CareerPanel(state, a)

    Gap(26)
    FleetRoll(match, state, afloat)
    Gap(8)
    HudLabel(t(K.RESULT_CREDITS_HINT), Naval.muted)
}

/**
 * Os dois comandantes lado a lado, número contra número — este aparelho sempre à
 * esquerda. O melhor valor de cada linha acende na cor de quem o fez.
 */
@Composable
private fun Comparison(match: Match) {
    val me = match.mySide
    val them = me.other()
    SectionHead(t(K.RESULT_COMPARE))
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1.4f))
        Text(
            match.sideName(me).uppercase(),
            style = NavalType.monoSmall,
            color = commanderColor(me),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            match.sideName(them).uppercase(),
            style = NavalType.monoSmall,
            color = commanderColor(them),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
    CompareRow(t(K.RESULT_SHOTS_FIRED), match.shotsOf(me), match.shotsOf(them), higherIsBetter = null)
    CompareRow(t(K.RESULT_HITS), match.hitsOf(me), match.hitsOf(them))
    CompareRow(t(K.ACCURACY), match.accuracyOf(me), match.accuracyOf(them), suffix = "%")
    CompareRow(
        t(K.RESULT_SHIPS_LEFT),
        match.board(me).remainingShips().size,
        match.board(them).remainingShips().size
    )
    CompareRow(t(K.RESULT_TALLY_SUNK), match.sunkBy(me), match.sunkBy(them))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Naval.lineSoft))
    StatRow(t(K.TURNS), match.turnCount.toString())
}

/** [higherIsBetter] nulo: linha neutra (mais tiros não é melhor nem pior por si só). */
@Composable
private fun CompareRow(label: String, mine: Int, theirs: Int, suffix: String = "", higherIsBetter: Boolean? = true) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Naval.lineSoft))
    val iLead = higherIsBetter != null && mine > theirs
    val theyLead = higherIsBetter != null && theirs > mine
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        HudLabel(label, Naval.muted, Modifier.weight(1.4f))
        Text(
            "$mine$suffix",
            style = NavalType.mono,
            color = if (iLead) Naval.greenBright else Naval.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$theirs$suffix",
            style = NavalType.mono,
            color = if (theyLead) Naval.danger else Naval.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Bloco de ranking da ranqueada: quantos pontos a partida deu e de onde eles
 * vieram (base Elo pela força do adversário + bônus de desempenho ou alívio),
 * pontos e posição da temporada antes → depois, e o retrospecto da temporada.
 * O "depois" vem sempre do servidor, que decide o vencedor; o "antes" da posição
 * foi lido ao conectar (ver [AppState.rankBefore]).
 */
@Composable
private fun RankingBlock(state: AppState, match: Match) {
    val outcome = state.rankedOutcome
    val seasonLabel = state.currentSeason?.let { t(seasonNameKey(it.seasonKey.substringAfterLast('-'))) }
        ?: outcome?.seasonName.orEmpty()
    SectionHead(t(K.RESULT_RANKING), if (seasonLabel.isBlank()) null else t(K.RESULT_RANKING_SEASON, seasonLabel))

    when {
        state.rankedReport == AppState.RankedReport.PENDING || state.rankedReport == AppState.RankedReport.IDLE ->
            HudLabel(t(K.RESULT_RANKING_PENDING), Naval.muted)

        outcome == null ->
            HudLabel(t(K.RESULT_RANKING_FAILED), Naval.danger)

        else -> {
            if (match.forfeitedBySelf) {
                HudLabel(t(K.RESULT_RANKING_ABANDON), Naval.danger)
                Gap(8)
            }
            // relato novo: o servidor diz em `accepted`; o total diferente de zero também
            // prova (relato repetido sempre volta com 0) e cobre uma leitura de booleano
            // que falhe em alguma plataforma
            val fresh = outcome.accepted || outcome.pointsDelta != 0
            if (!fresh) {
                HudLabel(t(K.RESULT_RANKING_REPEAT), Naval.muted)
                Gap(8)
            } else if (outcome.pointsDelta == 0 && outcome.basePoints == 0) {
                // anti-farm do servidor: passou do limite de ranqueadas do dia contra
                // este adversário — a partida conta para a carreira, não para o placar
                HudLabel(t(K.RESULT_RANKING_PAIR_LIMIT), Naval.muted)
                Gap(8)
            } else {
                // soma zero desde a 0.30.0: o total é a base Elo. O bônus só aparece em
                // relato antigo que ainda veio com ele
                val won = outcome.pointsDelta > 0
                SignedRow(t(K.RESULT_RANKING_BASE), outcome.basePoints, first = true)
                if (outcome.bonusPoints != 0) {
                    SignedRow(t(if (won) K.RESULT_RANKING_BONUS else K.RESULT_RANKING_MERCY), outcome.bonusPoints)
                }
            }

            Gap(10)
            val positive = outcome.pointsDelta >= 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Naval.surface2)
                    .border(1.dp, if (positive) Naval.amber else Naval.line)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudLabel(t(K.RESULT_RANKING_DELTA), if (positive) Naval.amberStrong else Naval.muted)
                Text(
                    signed(outcome.pointsDelta),
                    style = NavalType.display,
                    color = if (positive) Naval.amberStrong else Naval.danger
                )
            }

            Gap(10)
            val pointsBefore = if (fresh) outcome.seasonPoints - outcome.pointsDelta
            else state.rankBefore?.rating ?: outcome.seasonPoints
            StatRow(t(K.RESULT_RANKING_POINTS), "$pointsBefore → ${outcome.seasonPoints}")

            val before = state.rankBefore?.position
            val after = outcome.seasonPosition.toLong()
            StatRow(
                t(K.RESULT_RANKING_POSITION),
                "${before?.let { t(K.LEADERBOARD_POSITION, it) } ?: "—"} → ${t(K.LEADERBOARD_POSITION, after)}"
            )
            if (before != null && after in 1 until before) {
                HudLabel(t(K.RESULT_RANKING_UP, before - after), Naval.greenBright)
                Gap(4)
            }
            StatRow(t(K.RESULT_RANKING_RECORD), "${outcome.seasonMatches} · ${outcome.seasonWins}")
        }
    }
    Gap(10)
    SecondaryButton(t(K.RESULT_RANKING_VIEW)) { state.openLeaderboardFromResult() }
}

/** Linha da composição dos pontos — sinal explícito, verde soma, vermelho tira. */
@Composable
private fun SignedRow(label: String, value: Int, first: Boolean = false) {
    if (!first) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Naval.lineSoft))
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = NavalType.monoSmall, color = Naval.inkSoft, modifier = Modifier.weight(1f))
        Text(
            if (value == 0) "—" else signed(value),
            style = NavalType.button,
            color = when {
                value > 0 -> Naval.greenBright
                value < 0 -> Naval.danger
                else -> Naval.muted
            }
        )
    }
}

private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

/**
 * Revanche em rede: reaproveita a mesma ligação em vez de criar uma partida do zero —
 * os dois lados precisam tocar no botão antes de a partida recomeçar, senão um dos
 * dois ficaria parado numa tela de posicionamento vazia esperando o outro.
 */
@Composable
private fun RematchSection(state: AppState, match: Match) {
    // cada transporte guarda o próprio estado de ligação (linkState pra LAN,
    // onlineLinkState pra internet) — usar sempre linkState fazia toda partida
    // online cair aqui achando que a ligação tinha caído, mesmo conectada
    val connected = when (match.opponent) {
        Opponent.LAN -> state.linkState == LinkState.CONNECTED
        Opponent.ONLINE -> state.onlineLinkState == LinkState.CONNECTED
        else -> false
    }
    when {
        !connected ->
            HudLabel(t(K.RESULT_REMATCH_LOST_LINK), Naval.danger)

        state.rematchRequestedByMe ->
            PrimaryButton(t(K.RESULT_REMATCH_WAITING), enabled = false) {}

        else -> {
            if (state.rematchRequestedByOpponent) {
                HudLabel(
                    t(K.RESULT_REMATCH_INVITE, match.sideName(match.mySide.other())),
                    Naval.amberStrong
                )
                Gap(8)
            }
            PrimaryButton(t(K.RESULT_REMATCH)) { state.requestRematch() }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HudLabel(label, Naval.muted)
        Text(value, style = NavalType.mono, color = Naval.ink)
    }
}

/**
 * A conta do XP aberta, parcela por parcela. A fórmula sempre existiu em
 * [br.com.navalbattle.game.Profile.registerMatch]; o que faltava era mostrá-la —
 * a linha que ficou em zero aparece igual, porque é ela que diz onde dava para
 * ter ganho mais.
 */
@Composable
private fun ScoreTally(a: Award) {
    SectionHead(t(K.RESULT_TALLY))
    TallyRow(
        label = t(K.RESULT_TALLY_BASE),
        calc = if (a.victory) t(K.RESULT_TALLY_VICTORY) else t(K.RESULT_TALLY_DEFEAT),
        value = a.base,
        first = true
    )
    TallyRow(t(K.RESULT_TALLY_PRECISION), "${a.precision}% × 2", a.precisionBonus)
    TallyRow(t(K.RESULT_TALLY_SUNK), "${a.shipsSunk} × 15", a.sunkBonus)
    TallyRow(
        label = t(K.RESULT_TALLY_BLITZ),
        calc = t(K.RESULT_TALLY_BLITZ_CALC, a.turns, Profile.BLITZ_TURNS),
        value = a.blitzBonus
    )

    Gap(10)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, Naval.green)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            HudLabel(t(K.RESULT_TALLY_XP), Naval.green)
            Gap(2)
            Text("+${a.xp} XP", style = NavalType.display, color = Naval.greenBright)
        }
        Column(horizontalAlignment = Alignment.End) {
            HudLabel(t(K.RESULT_TALLY_CREDITS), Naval.muted)
            Gap(2)
            CoinLabel("+${a.credits}", style = NavalType.title, iconSize = 20.dp)
        }
    }
}

@Composable
private fun TallyRow(label: String, calc: String, value: Int, first: Boolean = false) {
    if (!first) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Naval.lineSoft))
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = NavalType.monoSmall, color = Naval.inkSoft)
            GapW(8)
            Text(calc, style = NavalType.monoSmall, color = Naval.muted)
        }
        Text(
            if (value == 0) "—" else "+$value",
            style = NavalType.button,
            color = if (value == 0) Naval.muted else Naval.greenBright
        )
    }
}

/** As seis condecorações — as ganhas em âmbar, as que faltam apagadas com o requisito. */
@Composable
private fun MedalCase(earned: Set<Medal>) {
    SectionHead(t(K.RESULT_MEDALS), "${earned.size} / ${Medal.entries.size}")
    Medal.entries.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { medal -> MedalChip(medal, medal in earned, Modifier.weight(1f)) }
        }
        Gap(8)
    }
}

@Composable
private fun MedalChip(medal: Medal, earned: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(if (earned) Naval.surface3 else Naval.surface2)
            .border(1.dp, if (earned) Naval.amber else Naval.line)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(Modifier.size(38.dp)) {
            drawMedal(medal, Offset(size.width / 2f, size.height / 2f), size.minDimension * 0.92f, earned)
        }
        Gap(6)
        Text(
            t(medal.key).uppercase(),
            style = NavalType.monoSmall,
            color = if (earned) Naval.inkSoft else Naval.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (!earned) {
            Gap(3)
            Text(
                medalRequirement(medal),
                style = NavalType.monoSmall,
                color = Naval.line,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun medalRequirement(medal: Medal): String = when (medal) {
    Medal.SHARPSHOOTER -> t(medal.requirementKey, Medal.SHARP_ACCURACY)
    Medal.BLITZ -> t(medal.requirementKey, Profile.BLITZ_TURNS)
    else -> t(medal.requirementKey)
}

/**
 * Patente com a barra cheia até onde a carreira estava e o trecho desta partida
 * emendado em verde na ponta — é o que mostra o quanto o combate empurrou.
 */
@Composable
private fun CareerPanel(state: AppState, a: Award) {
    val profile = state.profile
    SectionHead(t(K.RESULT_CAREER))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Naval.surface2)
            .border(1.dp, if (a.rankUp != null) Naval.amber else Naval.line)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(profile.rank.label.uppercase(), style = NavalType.title, color = Naval.amberStrong)
            Text("${profile.xp} XP", style = NavalType.mono, color = Naval.inkSoft)
        }
        Gap(10)
        val floor = profile.rank.xp
        val next = Rank.next(profile.xp)
        val span = ((next?.xp ?: profile.xp) - floor).coerceAtLeast(1)
        val now = ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)
        val before = ((profile.xp - a.xp - floor).toFloat() / span).coerceIn(0f, now)
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(Naval.bg)
                .border(1.dp, Naval.line)
        ) {
            // o ganho desta partida fica por baixo, ocupando até o total de agora;
            // a parte antiga entra por cima, então só a emenda aparece em verde
            Box(Modifier.fillMaxWidth(now).height(10.dp).background(Naval.greenBright))
            Box(Modifier.fillMaxWidth(before).height(10.dp).background(Naval.amber))
        }
        Gap(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HudLabel(t(K.RESULT_XP_THIS_MATCH, a.xp), Naval.greenBright)
            HudLabel(
                if (next == null) t(K.PROFILE_CAREER_DONE)
                else t(K.PROFILE_XP_TO, next.xp - profile.xp, next.label).uppercase(),
                Naval.muted
            )
        }
        a.rankUp?.let { r ->
            Gap(10)
            Text(t(K.RESULT_PROMOTED, r.label).uppercase(), style = NavalType.mono, color = Naval.amberStrong)
        }
    }
}

/** A frota no fim do combate: quem voltou e quem ficou no mar, com a arte de bordo. */
@Composable
private fun FleetRoll(match: Match, state: AppState, afloat: Int) {
    SectionHead(
        t(K.RESULT_YOUR_FLEET),
        t(K.RESULT_AFLOAT_OF, afloat, ShipClass.fleet.size),
        if (afloat == 0) Naval.danger else Naval.greenBright
    )
    ShipClass.fleet.forEach { type ->
        val ship = match.board(match.mySide).ships.firstOrNull { it.type == type }
        val sunk = ship == null || match.board(match.mySide).isSunk(ship)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 7.dp)
                .background(Naval.surface2)
                .border(1.dp, if (sunk) Naval.line else Naval.green)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(Modifier.size(width = 74.dp, height = 16.dp)) {
                drawShip(
                    type = type,
                    center = Offset(size.width / 2f, size.height / 2f),
                    lengthPx = size.width,
                    thicknessPx = size.height,
                    vertical = false,
                    skin = state.skin,
                    alpha = if (sunk) 0.4f else 1f
                )
            }
            GapW(10)
            Text(
                type.label.uppercase(),
                style = NavalType.monoSmall,
                color = if (sunk) Naval.muted else Naval.inkSoft,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (sunk) t(K.RESULT_SHIP_SUNK).uppercase() else t(K.RESULT_SHIP_AFLOAT).uppercase(),
                style = NavalType.monoSmall,
                color = if (sunk) Naval.danger else Naval.greenBright
            )
        }
    }
}

/** Cabeçalho de seção: rótulo, fio até a margem e um valor opcional à direita. */
@Composable
private fun SectionHead(label: String, trailing: String? = null, trailingColor: Color = Naval.amberStrong) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HudLabel(label, Naval.muted)
        GapW(10)
        Box(Modifier.weight(1f).height(1.dp).background(Naval.line))
        trailing?.let {
            GapW(10)
            Text(it.uppercase(), style = NavalType.monoSmall, color = trailingColor)
        }
    }
    Gap(10)
}
