package br.com.navalbattle.game

import br.com.navalbattle.data.Protocol

/**
 * Uma linha do log da sala (`online_messages`) já com o lado de quem a gravou. O `id` é o
 * que dá a ordem: depois de gravada, a ordem por `id` é igual para os dois aparelhos.
 */
data class ReplayLine(val id: Long, val from: Side, val body: String)

/**
 * Reconstrói a partida em rede do começo, repassando `FLEET`, `ABIL` e `ACT` do log em
 * ordem de `id` com a mesma regra de [Match.act] (jogada fora da vez é ignorada). Os dois
 * aparelhos aplicam a mesma regra ao mesmo log e chegam ao mesmo estado (#67) — é a saída
 * quando eles discordaram de quem joga e os dois ficaram em AGUARDE.
 *
 * Devolve nulo se o log ainda não tem as duas frotas (a batalha nem abriu). Só linhas de
 * jogo entram; provocação, pausa e revanche não mexem no estado da partida.
 */
fun replayNetworkMatch(
    mode: GameMode,
    mySide: Side,
    nameOne: String,
    nameTwo: String,
    log: List<ReplayLine>,
    localId: String? = null
): Match? {
    val m = if (localId != null) Match(mode, Opponent.ONLINE, mySide, localId = localId)
    else Match(mode, Opponent.ONLINE, mySide)
    m.setName(Side.PLAYER, nameOne)
    m.setName(Side.ENEMY, nameTwo)
    val seen = HashSet<Long>()
    for (line in log.sortedBy { it.id }) {
        if (!seen.add(line.id)) continue
        val parts = Protocol.parts(line.body)
        when (parts.firstOrNull()) {
            Protocol.FLEET -> parts.getOrNull(1)?.let { m.applyFleetOf(line.from, FleetCodec.decode(it)) }

            Protocol.ABILITY -> {
                if (m.phase != Phase.BATTLE || m.turnOwner != line.from) continue
                val ability = parts.getOrNull(1)?.let { code -> Ability.entries.firstOrNull { it.code == code } }
                    ?: continue
                m.selectAbility(ability, ignoreCooldown = parts.getOrNull(2) == "1", fromRemote = line.from != mySide)
            }

            Protocol.ACT -> {
                val x = parts.getOrNull(1)?.toIntOrNull() ?: continue
                val y = parts.getOrNull(2)?.toIntOrNull() ?: continue
                m.act(Coord(x, y), by = line.from)
            }
        }
    }
    return if (m.phase == Phase.PLACEMENT) null else m
}

private const val RESYNC_AFTER_MS = 30_000L
private const val RESYNC_EVERY_MS = 15_000L

/**
 * Vigia dos dois em AGUARDE (#67): em batalha, sem a vez há [RESYNC_AFTER_MS] (20 s do
 * turno + 10 s de margem) e sem nenhuma linha do adversário nesse tempo, pede a
 * ressincronização — no máximo uma vez a cada [RESYNC_EVERY_MS]. Nunca durante queda de
 * rede, pausa ou contagem de queda do adversário: aí a espera tem outra explicação.
 * Disparar sem necessidade não faz mal, a reconstrução dá o mesmo estado.
 */
fun shouldResync(
    now: Long,
    waitingSince: Long?,
    lastOpponentLine: Long?,
    selfOffline: Boolean,
    paused: Boolean,
    dropLeft: Int?,
    lastResync: Long?
): Boolean {
    if (waitingSince == null || selfOffline || paused || dropLeft != null) return false
    if (now - waitingSince < RESYNC_AFTER_MS) return false
    if (lastOpponentLine != null && now - lastOpponentLine < RESYNC_AFTER_MS) return false
    if (lastResync != null && now - lastResync < RESYNC_EVERY_MS) return false
    return true
}
