package br.com.navalbattle.data

/**
 * Link da loja do aparelho para o texto de convite (#44, #88): ficha da Play no Android (com a origem
 * `convite` no referrer) e ficha da App Store no iPhone. O endereço de cada loja vive só no `actual` da
 * própria plataforma, para o binário do iPhone não carregar link nem nome da Play (Apple 2.3.10, 0.45.4).
 */
expect val storeInviteLink: String

/**
 * Link da Play para o placar da Batalha do Dia (#174), com a origem `batalha_do_dia` no referrer para o Play Console
 * mostrar as instalações vindas desse compartilhamento. Vazio no iPhone (o placar sai sem link, como em #169).
 */
expect val dailyBattleStoreLink: String
