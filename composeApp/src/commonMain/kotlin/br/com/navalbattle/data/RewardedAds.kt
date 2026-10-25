package br.com.navalbattle.data

import br.com.navalbattle.game.AdAudience
import br.com.navalbattle.game.AdShowOutcome

/**
 * Anúncio premiado do AdMob (#29). O aparelho só mostra o anúncio; quem decide o prêmio é o
 * servidor, quando o Google confirma (SSV, Edge `ads-reward`). Implementação real só no Android;
 * no iPhone fica vazio até a parte da App Store (ATT, SKAdNetwork).
 *
 * Regra do John: nada na abertura do app. O SDK e o consentimento (UMP) só começam no primeiro
 * toque num botão de anúncio.
 */
expect class RewardedAds() {
    /** Falso no iPhone: nenhum botão de anúncio aparece. */
    val supported: Boolean

    /** Build de debug ou `.preview`: sempre a unidade de teste do Google. */
    val buildUsesTestAds: Boolean

    /** Verdadeiro quando o app roda em emulador (Android). Usado só pela conta de teste (#32). */
    val runsOnEmulator: Boolean

    /**
     * Consentimento (UMP) quando a região exige, carrega e mostra o anúncio [adUnitId]. [userId]
     * e [customData] (o vale do servidor) vão na verificação no servidor (SSV). [audience] (#49,
     * ECA Digital) vale para o UMP e para cada carregamento: menor ou idade desconhecida = não
     * personalizado, com as marcas de menor e a classificação máxima do conteúdo.
     */
    suspend fun show(adUnitId: String, userId: String, customData: String, audience: AdAudience): AdShowOutcome

    /** Ajustes → "Privacidade dos anúncios": reabre o formulário do UMP. Falso = a região não pede. */
    suspend fun openPrivacyOptions(audience: AdAudience): Boolean
}
