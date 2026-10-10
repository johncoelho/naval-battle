package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
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
}
