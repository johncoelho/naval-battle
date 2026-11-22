package br.com.navalbattle.game

/**
 * Resultado de um toque em "Entrar com N dobrões" (Passe de Temporada) ou no passe gratuito (#118). Todo toque
 * termina em um destes, mostrado num popup por cima de tudo: a Apple recusou a 0.47.0 por 2.1(b) porque o toque
 * sem saldo só escrevia uma linha no fim do cartão rolável, fora da tela do iPad, e o botão parecia "mudo".
 */
sealed interface SeasonResult {
    /** Faltam [missing] dobrões para o passe: o popup oferece a loja de Dobrões. */
    data class NoFunds(val missing: Int) : SeasonResult

    /** Sem rede, sem o passe carregado ou sem a carteira aberta: nada foi cobrado. */
    data object Offline : SeasonResult

    /** O servidor recusou ou a resposta se perdeu: nada mudou, dá para tentar de novo. */
    data object Failed : SeasonResult

    /** Entrou na temporada com o passe [tier] ("free" ou "premium"). */
    data class Joined(val tier: String) : SeasonResult
}

/** Altura (dp) do banner da temporada em tela baixa; em tela alta ele segue 16:9. */
const val SEASON_BANNER_COMPACT_DP = 120

/** Abaixo desta altura disponível (dp) o banner do Passe encolhe, para o CTA aparecer sem rolar (#118). */
private const val SEASON_COMPACT_BELOW_DP = 700f

/** Tela baixa: janela de iPhone 375x667 (inclusive dentro do iPad) e iPhones pequenos. */
fun seasonBannerCompact(availableHeightDp: Float): Boolean = availableHeightDp < SEASON_COMPACT_BELOW_DP

/**
 * Preço em dobrões do toque: o passe gratuito é de graça; o de Temporada custa a adesão ou, para quem já está
 * no gratuito, o upgrade.
 */
fun seasonJoinPrice(premium: Boolean, alreadyJoined: Boolean, entryPrice: Int, upgradePrice: Int): Int = when {
    !premium -> 0
    alreadyJoined -> upgradePrice
    else -> entryPrice
}

/**
 * Antes de falar com o servidor: sem o passe carregado ([passLoaded] falso) é [SeasonResult.Offline]; sem saldo
 * para o [price], [SeasonResult.NoFunds] com quanto falta. Null: pode seguir para a cobrança.
 */
fun seasonJoinPrecheck(price: Int, credits: Int, passLoaded: Boolean): SeasonResult? = when {
    !passLoaded -> SeasonResult.Offline
    price > 0 && credits < price -> SeasonResult.NoFunds(price - credits)
    else -> null
}

/**
 * Depois da chamada: carteira que não abriu ([walletOk] falso, só no passe pago) é [SeasonResult.Offline]; resposta
 * sem passe ([joinedTier] nulo) é [SeasonResult.Failed]; senão [SeasonResult.Joined]. Nunca devolve "nada".
 */
fun seasonJoinOutcome(walletOk: Boolean, joinedTier: String?): SeasonResult = when {
    !walletOk -> SeasonResult.Offline
    joinedTier == null -> SeasonResult.Failed
    else -> SeasonResult.Joined(joinedTier)
}
