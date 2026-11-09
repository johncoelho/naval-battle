package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regras de turno e habilidades, numa partida de dois jogadores no mesmo aparelho (os dois
 * lados controlados pelo teste, sem IA) com frotas em posição conhecida.
 */
class MatchTest {

    /** Frota fixa: cada navio na horizontal, começando na coluna A, em linhas pares. */
    private fun fixedFleet(): List<Ship> = ShipClass.fleet.mapIndexed { i, type ->
        Ship(type, Coord(0, i * 2), Orientation.HORIZONTAL)
    }

    private fun battle(mode: GameMode = GameMode.CLASSIC): Match {
        val m = Match(mode, Opponent.LOCAL)
        m.playerBoard.clear()
        fixedFleet().forEach { m.playerBoard.place(it) }
        m.confirmPlacement()
        m.enemyBoard.clear()
        fixedFleet().forEach { m.enemyBoard.place(it) }
        m.confirmPlacement()
        assertEquals(Phase.BATTLE, m.phase)
        assertEquals(Side.PLAYER, m.turnOwner)
        return m
    }

    private val water = Coord(9, 9)

    @Test
    fun acertouJogaDeNovoErrouPassaAVez() {
        val m = battle()
        assertEquals(ShotResult.HIT, m.act(Coord(0, 0))?.result)
        assertEquals(Side.PLAYER, m.turnOwner, "acerto mantém a vez")
        assertEquals(ShotResult.MISS, m.act(water)?.result)
        assertEquals(Side.ENEMY, m.turnOwner, "erro passa a vez")
        assertEquals(1, m.turnCount, "o turno conta rodadas completas")
        m.act(water)
        assertEquals(Side.PLAYER, m.turnOwner)
        assertEquals(2, m.turnCount)
    }

    @Test
    fun tiroRepetidoNaoGastaAVez() {
        val m = battle()
        m.act(Coord(0, 0))
        assertNull(m.act(Coord(0, 0)))
        assertEquals(Side.PLAYER, m.turnOwner)
        assertEquals(1, m.playerShots)
    }

    @Test
    fun afundarAFrotaInteiraEncerraComVencedor() {
        val m = battle()
        fixedFleet().flatMap { it.cells }.forEach { m.act(it) }
        assertEquals(Phase.RESULT, m.phase)
        assertEquals(Side.PLAYER, m.winner)
        assertEquals(17, m.playerHits)
        assertEquals(100, m.accuracyOf(Side.PLAYER))
    }

    @Test
    fun barragemDuplaPerdoaUmErro() {
        val m = battle(GameMode.TACTICAL)
        m.selectAbility(Ability.DOUBLE_BARRAGE)
        m.act(water)
        assertEquals(Side.PLAYER, m.turnOwner, "com a barragem, o primeiro erro não passa a vez")
        m.act(Coord(8, 9))
        assertEquals(Side.ENEMY, m.turnOwner, "o segundo erro passa")
    }

    @Test
    fun barragemNaoSeGastaNumAcerto() {
        // regressão da 0.29.1: o tiro extra era consumido por um acerto, que já dá outro tiro
        val m = battle(GameMode.TACTICAL)
        m.selectAbility(Ability.DOUBLE_BARRAGE)
        assertEquals(ShotResult.HIT, m.act(Coord(0, 0))?.result)
        m.act(water)
        assertEquals(Side.PLAYER, m.turnOwner, "a barragem ainda valia depois do acerto")
        m.act(Coord(8, 9))
        assertEquals(Side.ENEMY, m.turnOwner)
    }

    @Test
    fun habilidadeEntraEmRecargaEFicaIndisponivel() {
        val m = battle(GameMode.TACTICAL)
        m.selectAbility(Ability.SONAR_PING)
        m.act(Coord(5, 5)) // sonar gasta a vez
        assertEquals(Side.ENEMY, m.turnOwner)
        m.act(water) // inimigo erra, volta para o jogador
        assertTrue(m.abilityCooldown(Ability.SONAR_PING) > 0, "sonar ainda em recarga")
        assertTrue(!m.abilityAvailable(Ability.SONAR_PING))
        assertTrue(m.abilityAvailable(Ability.SONAR_PING, ignoreCooldown = true), "cartucho avulso libera")
    }

    @Test
    fun habilidadeSomeQuandoONavioDonoAfunda() {
        val m = battle(GameMode.TACTICAL)
        m.act(water) // vez do inimigo
        // inimigo afunda o cruzador do jogador (linha 4, colunas A-C)
        listOf(Coord(0, 4), Coord(1, 4), Coord(2, 4)).forEach { m.act(it) }
        m.act(water) // inimigo erra, volta para o jogador
        assertEquals(Side.PLAYER, m.turnOwner)
        assertTrue(!m.abilityAvailable(Ability.SONAR_PING), "sonar é do cruzador afundado")
        assertTrue(m.abilityAvailable(Ability.AIR_RECON))
    }

    @Test
    fun abandonoDaVitoriaAQuemFicou() {
        val m = battle()
        m.abandon(Side.ENEMY)
        assertEquals(Phase.RESULT, m.phase)
        assertEquals(Side.ENEMY, m.winner)
    }

    @Test
    fun emRedeOAdversarioEhSempreOOutroLado() {
        // regressão da 0.29.1: quem entrava na sala via o próprio nome em "VS."
        val guest = Match(GameMode.CLASSIC, Opponent.ONLINE, mySide = Side.ENEMY)
        guest.setName(Side.ENEMY, "QA02")
        guest.setName(Side.PLAYER, "QA01")
        assertEquals("QA01", guest.sideName(guest.mySide.other()))
        val host = Match(GameMode.CLASSIC, Opponent.ONLINE, mySide = Side.PLAYER)
        host.setName(Side.PLAYER, "QA01")
        host.setName(Side.ENEMY, "QA02")
        assertEquals("QA02", host.sideName(host.mySide.other()))
    }

    @Test
    fun vitoriaSemCombateQuandoOAdversarioSaiNoPosicionamento() {
        // #6: saiu antes do primeiro tiro -> vence, mas sem combate
        val m = Match(GameMode.CLASSIC, Opponent.LOCAL)
        m.abandon(Side.PLAYER)
        assertTrue(m.wonWithoutCombat(Side.PLAYER))
        assertFalse(m.wonWithoutCombat(Side.ENEMY), "quem perdeu nunca e vitoria sem combate")
    }

    @Test
    fun vitoriaPorAusenciaNoPosicionamentoOnline() {
        // #85: o adversario aceitou e nao entrou; quem ficou vence sem combate, por ausencia
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE, mySide = Side.ENEMY)
        m.abandon(m.mySide, walkover = true)
        assertEquals(Phase.RESULT, m.phase)
        assertEquals(Side.ENEMY, m.winner)
        assertTrue(m.wonByAbsence)
        assertTrue(m.endedByWalkover)
        assertTrue(m.wonWithoutCombat(m.mySide), "sem XP, dobroes, milha nem desafio")
        assertFalse(m.forfeitedBySelf)
    }

    @Test
    fun derrotaPorAusenciaDeQuemNaoEntrou() {
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE, mySide = Side.PLAYER)
        m.lostByAbsence()
        assertEquals(Phase.RESULT, m.phase)
        assertEquals(Side.ENEMY, m.winner)
        assertTrue(m.forfeitedBySelf, "derrota sem recompensa de carreira")
        assertTrue(m.endedByWalkover)
        assertFalse(m.wonWithoutCombat(m.mySide))
        assertEquals(br.com.navalbattle.i18n.t(br.com.navalbattle.i18n.K.WO_LOST), m.callout?.main)
    }

    @Test
    fun abandonoComumNaoViraVitoriaPorAusencia() {
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE, mySide = Side.PLAYER)
        m.abandon(m.mySide)
        assertTrue(m.wonByAbsence)
        assertFalse(m.endedByWalkover)
    }

    @Test
    fun umTiroJaContaComoCombate() {
        val m = battle()
        m.act(water) // o jogador atira e erra
        m.abandon(Side.PLAYER) // o adversario desiste depois
        assertFalse(m.wonWithoutCombat(Side.PLAYER))
    }

    @Test
    fun habilidadeTambemContaComoCombate() {
        val m = battle(GameMode.TACTICAL)
        m.selectAbility(Ability.SONAR_PING)
        m.act(Coord(5, 5))
        m.abandon(Side.PLAYER)
        assertFalse(m.wonWithoutCombat(Side.PLAYER))
    }

    @Test
    fun vitoriaNormalNaoEhSemCombate() {
        val m = battle()
        fixedFleet().flatMap { it.cells }.forEach { m.act(it) }
        assertFalse(m.wonWithoutCombat(Side.PLAYER))
        assertFalse(m.wonWithoutCombat(Side.ENEMY))
    }
}

// ---- #67: validar quem joga ----

class MatchByTest {

    private fun fixedFleet(): List<Ship> = ShipClass.fleet.mapIndexed { i, type ->
        Ship(type, Coord(0, i * 2), Orientation.HORIZONTAL)
    }

    private fun battle(mode: GameMode = GameMode.CLASSIC): Match {
        val m = Match(mode, Opponent.ONLINE, Side.PLAYER)
        m.applyFleetOf(Side.PLAYER, fixedFleet())
        m.applyFleetOf(Side.ENEMY, fixedFleet())
        assertEquals(Phase.BATTLE, m.phase)
        return m
    }

    @Test
    fun jogadaDeQuemNaoTemAVezNaoMudaNada() {
        val m = battle()
        assertNull(m.act(Coord(0, 0), by = Side.ENEMY))
        assertEquals(Side.PLAYER, m.turnOwner)
        assertEquals(1, m.turnCount)
        assertEquals(0, m.playerShots)
        assertEquals(0, m.enemyShots)
        assertTrue(m.playerBoard.marks.isEmpty())
        assertTrue(m.enemyBoard.marks.isEmpty())
    }

    @Test
    fun jogadaDoDonoDaVezEAplicadaComBy() {
        val m = battle()
        assertEquals(ShotResult.HIT, m.act(Coord(0, 0), by = Side.PLAYER)?.result)
        assertEquals(1, m.playerShots)
        assertEquals(Mark.HIT, m.enemyBoard.marks[Coord(0, 0)])
    }

    @Test
    fun canActSoNaVezECasaLivre() {
        val m = battle()
        assertTrue(m.canAct(Side.PLAYER, Coord(0, 0)))
        assertFalse(m.canAct(Side.ENEMY, Coord(0, 0)), "fora da vez")
        m.act(Coord(0, 0), by = Side.PLAYER)
        assertFalse(m.canAct(Side.PLAYER, Coord(0, 0)), "casa já atirada")
        assertTrue(m.canAct(Side.PLAYER, Coord(1, 0)))
    }

    @Test
    fun canActFalsoForaDaBatalha() {
        val m = Match(GameMode.CLASSIC, Opponent.ONLINE, Side.PLAYER)
        assertFalse(m.canAct(Side.PLAYER, Coord(0, 0)))
    }

    @Test
    fun canActComRadarOuSonarArmadoAceitaCasaJaAtirada() {
        val m = battle(GameMode.TACTICAL)
        m.act(Coord(9, 9), by = Side.PLAYER) // erro: passa a vez
        m.act(Coord(9, 9), by = Side.ENEMY)
        assertFalse(m.canAct(Side.PLAYER, Coord(9, 9)), "sem habilidade, casa atirada não vale")
        m.selectAbility(Ability.SONAR_PING)
        assertEquals(Ability.SONAR_PING, m.pendingAbility)
        assertTrue(m.canAct(Side.PLAYER, Coord(9, 9)), "sonar armado vale em qualquer casa")
        m.selectAbility(Ability.SONAR_PING)
        m.selectAbility(Ability.AIR_RECON)
        assertTrue(m.canAct(Side.PLAYER, Coord(9, 9)), "radar armado também")
    }

    @Test
    fun actSemByContinuaComoAntesParaIaELocal() {
        val m = Match(GameMode.CLASSIC, Opponent.LOCAL)
        m.playerBoard.clear(); fixedFleet().forEach { m.playerBoard.place(it) }
        m.confirmPlacement()
        m.enemyBoard.clear(); fixedFleet().forEach { m.enemyBoard.place(it) }
        m.confirmPlacement()
        assertEquals(ShotResult.HIT, m.act(Coord(0, 0))?.result)
    }
}
