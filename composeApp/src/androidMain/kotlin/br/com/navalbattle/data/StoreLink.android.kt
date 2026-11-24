package br.com.navalbattle.data

private const val STORE_PAGE = "https://play.google.com/store/apps/details?id=aigamesfactory.navalbattleclassic"

/** A Play registra o `referrer` na ficha e o Play Console mostra as instalações por origem (#88). */
actual val storeInviteLink: String = STORE_PAGE + "&referrer=utm_source%3Dconvite%26utm_medium%3Dorganic"

/** Origem do placar da Batalha do Dia (#174): o `&` do referrer vai codificado, como no convite. */
actual val dailyBattleStoreLink: String =
    STORE_PAGE + "&referrer=utm_source%3Dbatalha_do_dia%26utm_medium%3Dorganic%26utm_campaign%3Dbatalha_do_dia"
