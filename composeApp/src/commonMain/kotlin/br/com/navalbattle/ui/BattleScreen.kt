package br.com.navalbattle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.navalbattle.AppState
import br.com.navalbattle.Screen
import br.com.navalbattle.audio.Sfx
import br.com.navalbattle.audio.SoundPlayer
import br.com.navalbattle.design.Naval
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t
import br.com.navalbattle.design.NavalType
import br.com.navalbattle.game.Ability
import br.com.navalbattle.game.GameMode
import br.com.navalbattle.game.Impact
import br.com.navalbattle.game.Match
import br.com.navalbattle.game.Opponent
import br.com.navalbattle.game.isNetwork
import br.com.navalbattle.game.Phase
import br.com.navalbattle.game.ShipClass
import br.com.navalbattle.game.Side
import br.com.navalbattle.game.Taunt
import br.com.navalbattle.game.Tone
import kotlinx.coroutines.delay

private const val TURN_SECONDS = 20

/** Cor de identificação de cada comandante no modo local (mesmo aparelho). */
fun commanderColor(side: Side): Color =
    if (side == Side.PLAYER) Naval.commanderOne else Naval.commanderTwo

@Composable
fun BattleScreen(state: AppState, match: Match) {
    var secondsLeft by remember { mutableStateOf(TURN_SECONDS) }
    val haptics = LocalHapticFeedback.current
    val sound = remember { SoundPlayer() }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    val local = match.opponent == Opponent.LOCAL
    // "lan" no nome por herança do modo local — hoje cobre LAN e Online, os dois
    // "cada aparelho a própria tela" que trocam jogada por mensagem em vez de
    // mexer direto no Match do outro lado
    val lan = match.opponent.isNetwork()
    // aparelhos separados (IA ou rede) mostram a experiência cheia de single player:
    // frota própria sempre visível, alarme de bordo, tabuleiro alvo grande. Só o modo
    // local (mesmo aparelho, tela compartilhada) esconde a frota e reveza a visão.
    val solo = !local
    var confirmQuit by remember { mutableStateOf(false) }
    var showTaunts by remember { mutableStateOf(false) }

    // no modo local cada comandante enxerga só a própria memória de tiros: a carta
    // exibida é sempre a da frota que ele ataca. Em rede e contra a IA, cada aparelho
    // é sempre o mesmo lado do início ao fim da partida.
    var localView by remember { mutableStateOf(Side.PLAYER) }
    val viewSide = when {
        local -> localView
        lan -> match.mySide
        else -> Side.PLAYER
    }
    val myTurn = match.phase == Phase.BATTLE && match.turnOwner == viewSide

    // só o disparo mais recente anima — as marcas antigas ficam paradas na carta
    val playerImpact = match.playerImpact?.takeIf { it.id == match.lastImpactId }
    val enemyImpact = match.enemyImpact?.takeIf { it.id == match.lastImpactId }
    // impacto que EU causei (mira o tabuleiro alvo) e o que EU sofri (mira minha frota)
    val myImpact = if (viewSide == Side.PLAYER) playerImpact else enemyImpact
    val incomingImpact = if (viewSide == Side.PLAYER) enemyImpact else playerImpact

    // a carta só troca de dono depois que a jogada termina de tocar
    LaunchedEffect(match.turnOwner, match.phase) {
        if (local && match.phase == Phase.BATTLE && match.turnOwner != localView) {
            val tone = (if (localView == Side.PLAYER) match.playerImpact else match.enemyImpact)?.tone
            delay(if (tone == Tone.SUNK) 3400L else 1700L)
            if (match.phase == Phase.BATTLE) localView = match.turnOwner
        }
    }

    // quem acerta joga de novo (regra clássica da batalha naval), então o relógio do
    // turno reinicia a cada tiro — não só quando a vez muda de dono — daí a chave em
    // lastImpactId
    LaunchedEffect(match.turnOwner, match.turnCount, match.phase, viewSide, match.lastImpactId) {
        if (match.phase == Phase.BATTLE && myTurn) {
            secondsLeft = TURN_SECONDS
            while (secondsLeft > 0) {
                delay(1000)
                secondsLeft--
            }
            // em rede, quem escolhe a coordenada é o dono do turno, e ela viaja
            if (lan) match.pickTarget()?.let { state.fireShared(it) } else match.fireRandom()
        }
    }

    // só contra a IA existe turno automático do adversário; continua atirando
    // enquanto for acertando, do mesmo jeito que o humano
    LaunchedEffect(match.turnOwner, match.phase) {
        if (!local && !lan && match.phase == Phase.BATTLE && match.turnOwner == Side.ENEMY) {
            while (match.phase == Phase.BATTLE && match.turnOwner == Side.ENEMY) {
                delay(1500)
                match.enemyTurn()
            }
        }
    }

    // alarme de bordo: soa quando A MINHA frota é atingida, nunca no meu próprio tiro.
    // no modo local não há identidade fixa por aparelho, então o alarme fica desligado.
    val mySide = if (solo) viewSide else null
    LaunchedEffect(match.playerImpact?.id) {
        match.playerImpact?.let { imp ->
            playShot(sound, haptics, imp.tone, alarm = mySide != null && mySide != Side.PLAYER)
        }
    }
    LaunchedEffect(match.enemyImpact?.id) {
        match.enemyImpact?.let { imp ->
            playShot(sound, haptics, imp.tone, alarm = mySide != null && mySide != Side.ENEMY)
        }
    }

    LaunchedEffect(match.phase) {
        if (match.phase == Phase.RESULT) {
            delay(3800)
            state.screen = Screen.RESULT
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ScreenTopBar(
                    when {
                        local -> t(K.BATTLE_TURN_OF, match.sideName(viewSide)).uppercase()
                        // rede (LAN ou online): o nome do adversário chega pelo HELLO do
                        // protocolo — antes a tela mostrava só "alvo inimigo" sempre
                        lan -> t(K.BATTLE_VS, match.sideName(Side.ENEMY)).uppercase()
                        else -> t(K.BATTLE_ENEMY_TARGET)
                    },
                    "${t(K.TURN)} ${match.turnCount.toString().padStart(2, '0')}",
                    Modifier.weight(1f)
                )
                if (lan) {
                    GapW(8)
                    HudLabel(
                        "💬",
                        Naval.inkSoft,
                        Modifier
                            .border(1.dp, Naval.line)
                            .clickable { showTaunts = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                GapW(10)
                HudLabel(
                    t(K.QUIT),
                    Naval.danger,
                    Modifier
                        .border(1.dp, Naval.line)
                        .clickable { confirmQuit = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Gap(6)

            Text(
                if (myTurn) formatTime(secondsLeft) else "--:--",
                style = NavalType.timer,
                color = if (secondsLeft <= 5 && myTurn) Naval.danger else Naval.amberStrong,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            HudLabel(
                when {
                    local && myTurn -> t(K.BATTLE_MEMORY, match.sideName(viewSide.other())).uppercase()
                    local -> t(K.BATTLE_PASSING, match.sideName(match.turnOwner)).uppercase()
                    myTurn -> t(K.BATTLE_YOUR_TURN)
                    else -> t(K.WAIT)
                },
                // no modo local a cor é a da frota alvo, a mesma das marcas na carta
                if (local) commanderColor(viewSide.other()) else Naval.muted,
                Modifier.fillMaxWidth().padding(top = 2.dp)
            )

            Gap(10)
            if (local) {
                // cada comandante vê só os próprios tiros: a carta da frota que ele ataca
                BoardView(
                    board = match.board(viewSide.other()),
                    skin = state.skin,
                    showShips = false,
                    interactive = myTurn,
                    impact = myImpact,
                    markTint = commanderColor(viewSide.other()),
                    modifier = Modifier.fillMaxWidth()
                ) { coord -> match.act(coord) }
            } else {
                // IA e rede: sempre o mesmo alvo, do início ao fim da partida
                BoardView(
                    board = match.board(viewSide.other()),
                    skin = state.skin,
                    showShips = false,
                    interactive = myTurn,
                    impact = myImpact,
                    modifier = Modifier.fillMaxWidth()
                ) { coord -> if (lan) state.fireShared(coord) else match.act(coord) }
            }

            // mensagens ficam abaixo do tabuleiro para não cobrir a ação
            Box(
                Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CalloutBanner(match.callout)
            }

            // no modo local as habilidades ficam sozinhas, acima do placar — não há
            // painel de frota própria ali para dividir a faixa com elas
            if (match.mode == GameMode.TACTICAL && local) {
                AbilityColumn(state, match, Modifier.fillMaxWidth()) { ability, ignoreCooldown ->
                    match.selectAbility(ability, ignoreCooldown)
                }
            }

            when {
                local -> {
                    Spacer(Modifier.weight(1f))
                    Scoreboard(match)
                }
                // tático contra a IA ou em rede: frota própria à esquerda, habilidades
                // à direita, lado a lado — as duas coisas cabem na mesma faixa
                match.mode == GameMode.TACTICAL -> {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OwnFleetPanel(state, match, viewSide, incomingImpact, Modifier.weight(1.1f))
                        AbilityColumn(state, match, Modifier.weight(1f)) { ability, ignoreCooldown ->
                            if (lan) state.useAbilityShared(ability, ignoreCooldown)
                            else match.selectAbility(ability, ignoreCooldown)
                        }
                    }
                }
                // clássico contra a IA ou em rede: só a frota própria, tela cheia
                else -> OwnFleetPanel(state, match, viewSide, incomingImpact, Modifier.weight(1f))
            }
        }

        if (lan) {
            TauntBubble(
                match.lastTaunt,
                senderLabel = { side -> if (side == match.mySide) t(K.YOU) else match.sideName(side) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(top = 64.dp)
            )
        }

        if (confirmQuit) {
            QuitOverlay(
                onKeep = { confirmQuit = false },
                onQuit = { state.quitToMenu() }
            )
        }

        if (showTaunts) {
            TauntOverlay(
                onPick = { code -> state.sendTaunt(code); showTaunts = false },
                onClose = { showTaunts = false }
            )
        }
    }
}

/**
 * Frota própria, sempre quadrada e do tamanho do que sobra na faixa que o chamador
 * reservou. Usada contra a IA e na rede — nos dois casos ninguém mais está olhando
 * para este aparelho. Recebe o [modifier] pronto (já com o peso da faixa) em vez de
 * exigir um `ColumnScope` do chamador, porque no tático ela divide espaço com
 * [AbilityColumn] dentro de uma `Row`, e no clássico ocupa a coluna inteira sozinha.
 */
@Composable
private fun OwnFleetPanel(
    state: AppState,
    match: Match,
    viewSide: Side,
    incomingImpact: Impact?,
    modifier: Modifier = Modifier
) {
    val board = match.board(viewSide)
    val afloat = board.remainingShips().size
    Column(modifier) {
        Gap(6)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HudLabel(t(K.BATTLE_YOUR_FLEET), Naval.inkSoft)
            Text(
                "$afloat / ${ShipClass.fleet.size} ${t(K.SHIPS_AFLOAT)}" +
                    " · ${t(K.ACCURACY)} ${match.accuracyOf(viewSide)}%",
                style = NavalType.mono,
                color = if (afloat <= 2) Naval.danger else Naval.greenBright
            )
        }
        if (board.smokeActive) {
            HudLabel(t(K.BATTLE_SMOKE_ACTIVE), Naval.greenBright)
        }
        Gap(6)
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // quadrada, do tamanho do que sobrou — larga no clássico, mais estreita
            // no tático, onde ela divide a faixa com as habilidades
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            BoardView(
                board = board,
                skin = state.skin,
                showShips = true,
                sweep = false,
                impact = incomingImpact,
                modifier = Modifier.size(side)
            )
        }
    }
}

/**
 * Placar do modo local. A cor de cada comandante é a mesma que pinta os danos da
 * frota dele na carta, então dá para ler o mapa sem legenda extra.
 */
@Composable
private fun Scoreboard(match: Match) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Side.PLAYER, Side.ENEMY).forEach { side ->
            val color = commanderColor(side)
            val playing = match.phase == Phase.BATTLE && match.turnOwner == side
            val afloat = match.board(side).remainingShips().size
            Column(
                Modifier
                    .weight(1f)
                    .background(if (playing) Naval.surface3 else Naval.surface2)
                    .border(1.dp, if (playing) color else Naval.line)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(color))
                    GapW(6)
                    Text(
                        match.sideName(side).uppercase(),
                        style = NavalType.monoSmall,
                        color = color,
                        maxLines = 1
                    )
                }
                Gap(6)
                Text(
                    "$afloat / ${ShipClass.fleet.size}",
                    style = NavalType.title,
                    color = when {
                        afloat <= 1 -> Naval.danger
                        afloat <= 2 -> Naval.amberStrong
                        else -> Naval.ink
                    }
                )
                HudLabel(t(K.SHIPS_AFLOAT), Naval.muted)
                Gap(6)
                HudLabel("${t(K.ACCURACY)} ${match.accuracyOf(side)}%", Naval.muted)
                if (match.board(side).smokeActive) {
                    HudLabel(t(K.BATTLE_SMOKE_ACTIVE), Naval.greenBright)
                }
            }
        }
    }
}

/** Confirmação para abandonar a partida no meio — evita sair por toque acidental. */
@Composable
private fun QuitOverlay(onKeep: () -> Unit, onQuit: () -> Unit) {
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
            HudLabel(t(K.BATTLE_QUIT_EYEBROW), Naval.muted)
            Gap(8)
            Text(t(K.BATTLE_QUIT_TITLE).uppercase(), style = NavalType.display, color = Naval.ink)
            Gap(6)
            HudLabel(t(K.BATTLE_QUIT_WARN), Naval.muted)
            Gap(22)
            PrimaryButton(t(K.BATTLE_KEEP_PLAYING)) { onKeep() }
            Gap(8)
            SecondaryButton(t(K.QUIT_MATCH)) { onQuit() }
        }
    }
}

private suspend fun playShot(
    sound: SoundPlayer,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback,
    tone: Tone,
    alarm: Boolean
) {
    sound.play(Sfx.LAUNCH)
    delay(SHOT_TRAVEL_MS.toLong())
    sound.play(tone.toSfx())
    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    if (!alarm) return
    when (tone) {
        Tone.SUNK -> {
            delay(220)
            sound.play(Sfx.ALARM_CRITICAL)
        }

        Tone.HIT -> {
            delay(160)
            sound.play(Sfx.ALARM)
        }

        else -> Unit
    }
}

/**
 * Ícone + descrição de efeito para cada habilidade, uma por linha — em vez do código
 * curto de antes, que exigia já saber o que "REC" ou "SNR" queriam dizer. Rola por
 * conta própria: numa tela baixa (celular dobrável aberto na horizontal, tablet em
 * paisagem) ela encolhe pelo peso que o chamador reservou em vez de cortar a última
 * habilidade da lista.
 */
@Composable
private fun AbilityColumn(
    state: AppState,
    match: Match,
    modifier: Modifier = Modifier,
    onUse: (Ability, Boolean) -> Unit
) {
    val abilities = listOf(
        Ability.SONAR_PING,
        Ability.AIR_RECON,
        Ability.DOUBLE_BARRAGE,
        Ability.SMOKE
    )
    Column(modifier.verticalScroll(rememberScrollState())) {
        HudLabel(t(K.BATTLE_ABILITIES))
        Gap(8)
        abilities.forEach { ability ->
            val ready = match.abilityAvailable(ability)
            val charges = state.profile.chargesOf(ability)
            // cartucho só entra em jogo quando a recarga é o único motivo bloqueando
            val usingCharge = !ready && charges > 0 && match.abilityAvailable(ability, ignoreCooldown = true)
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AbilityButton(
                    code = ability.icon,
                    name = ability.shortName,
                    enabled = ready || usingCharge,
                    selected = match.pendingAbility == ability,
                    cooldown = match.abilityCooldown(ability)
                ) {
                    if (usingCharge) state.profile.consumeCharge(ability)
                    onUse(ability, usingCharge)
                }
                GapW(8)
                Column(Modifier.weight(1f)) {
                    Text(ability.description, style = NavalType.monoSmall, color = Naval.muted)
                    if (charges > 0) {
                        HudLabel(
                            t(K.BATTLE_ABILITY_CHARGE, charges.toString()),
                            if (usingCharge) Naval.amberStrong else Naval.muted
                        )
                    }
                }
            }
        }
        val hint = match.pendingAbility?.let { t(K.BATTLE_ABILITY_AIM, it.label) }
            ?: t(K.BATTLE_ABILITY_HINT)
        HudLabel(hint, if (match.pendingAbility != null) Naval.amberStrong else Naval.muted)
    }
}

private val TAUNT_EMOJIS = listOf("🔥", "💥", "😈", "😅", "🎯", "🏳️")
private val TAUNT_PHRASES = listOf(
    "FIRE" to K.TAUNT_FIRE,
    "NICE" to K.TAUNT_NICE_SHOT,
    "GG" to K.TAUNT_GG,
    "MERCY" to K.TAUNT_MERCY,
    "LUCKY" to K.TAUNT_LUCKY,
    "INCOMING" to K.TAUNT_INCOMING
)

/** Emoji chega pronto (é o próprio glifo); grito de guerra chega como código e traduz aqui. */
private fun tauntText(code: String): String =
    TAUNT_PHRASES.firstOrNull { it.first == code }?.let { t(it.second) } ?: code

/** Escolha de emoji ou grito de guerra para mandar ao outro aparelho, em rede local. */
@Composable
private fun TauntOverlay(onPick: (String) -> Unit, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Naval.bg.copy(alpha = 0.94f))
            .clickable(onClick = onClose)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(24.dp)
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clickable(enabled = false) {}
        ) {
            HudLabel(t(K.BATTLE_TAUNT_EYEBROW), Naval.muted)
            Gap(8)
            Text(t(K.BATTLE_TAUNT_TITLE).uppercase(), style = NavalType.display, color = Naval.ink)
            Gap(18)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TAUNT_EMOJIS.forEach { emoji ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Naval.surface2)
                            .border(1.dp, Naval.line)
                            .clickable { onPick(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, style = NavalType.title)
                    }
                }
            }
            Gap(18)
            TAUNT_PHRASES.forEach { (code, key) ->
                HudLabel(
                    t(key),
                    Naval.ink,
                    Modifier
                        .fillMaxWidth()
                        .background(Naval.surface2)
                        .border(1.dp, Naval.line)
                        .clickable { onPick(code) }
                        .padding(vertical = 12.dp, horizontal = 14.dp)
                )
                Gap(6)
            }
        }
    }
}

/** Bolha flutuante do que o outro comandante mandou — some sozinha depois de um tempo. */
@Composable
private fun TauntBubble(taunt: Taunt?, senderLabel: (Side) -> String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Taunt?>(null) }

    LaunchedEffect(taunt?.id) {
        if (taunt != null) {
            current = taunt
            visible = true
            delay(2600)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        val c = current
        if (c != null) {
            Row(
                Modifier
                    .background(Naval.bg.copy(alpha = 0.92f))
                    .border(1.dp, Naval.amber)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tauntText(c.code), style = NavalType.title, color = Naval.amberStrong)
                GapW(8)
                HudLabel(senderLabel(c.from), Naval.muted)
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

private fun Tone.toSfx(): Sfx = when (this) {
    Tone.HIT -> Sfx.HIT
    Tone.SUNK -> Sfx.SUNK
    else -> Sfx.MISS
}
