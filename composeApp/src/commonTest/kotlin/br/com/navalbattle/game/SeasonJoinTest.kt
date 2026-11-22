package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** #118: todo toque no Passe de Temporada termina num resultado visivel; nunca um retorno mudo. */
class SeasonJoinTest {

    @Test
    fun precoDoToque() {
        assertEquals(0, seasonJoinPrice(premium = false, alreadyJoined = false, entryPrice = 6500, upgradePrice = 8000))
        assertEquals(6500, seasonJoinPrice(premium = true, alreadyJoined = false, entryPrice = 6500, upgradePrice = 8000))
        assertEquals(8000, seasonJoinPrice(premium = true, alreadyJoined = true, entryPrice = 6500, upgradePrice = 8000))
    }

    @Test
    fun semSaldoDizQuantoFalta() {
        assertEquals(SeasonResult.NoFunds(1500), seasonJoinPrecheck(price = 6500, credits = 5000, passLoaded = true))
        assertEquals(SeasonResult.NoFunds(6500), seasonJoinPrecheck(price = 6500, credits = 0, passLoaded = true))
    }

    @Test
    fun saldoExatoOuMaiorSegueParaACobranca() {
        assertNull(seasonJoinPrecheck(price = 6500, credits = 6500, passLoaded = true))
        assertNull(seasonJoinPrecheck(price = 6500, credits = 9000, passLoaded = true))
    }

    @Test
    fun passeGratuitoNuncaFicaSemSaldo() {
        assertNull(seasonJoinPrecheck(price = 0, credits = 0, passLoaded = true))
    }

    @Test
    fun passeNaoCarregadoNaoFicaMudo() {
        assertEquals(SeasonResult.Offline, seasonJoinPrecheck(price = 6500, credits = 9000, passLoaded = false))
        assertEquals(SeasonResult.Offline, seasonJoinPrecheck(price = 0, credits = 0, passLoaded = false))
    }

    @Test
    fun carteiraFechadaEOffline() {
        assertEquals(SeasonResult.Offline, seasonJoinOutcome(walletOk = false, joinedTier = null))
        assertEquals(SeasonResult.Offline, seasonJoinOutcome(walletOk = false, joinedTier = "premium"))
    }

    @Test
    fun redeOuServidorFalhandoEFalha() {
        assertEquals(SeasonResult.Failed, seasonJoinOutcome(walletOk = true, joinedTier = null))
    }

    @Test
    fun comSaldoEntra() {
        assertEquals(SeasonResult.Joined("premium"), seasonJoinOutcome(walletOk = true, joinedTier = "premium"))
        assertEquals(SeasonResult.Joined("free"), seasonJoinOutcome(walletOk = true, joinedTier = "free"))
    }
}
