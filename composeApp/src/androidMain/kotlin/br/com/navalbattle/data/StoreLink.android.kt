package br.com.navalbattle.data

private const val STORE_PAGE = "https://play.google.com/store/apps/details?id=aigamesfactory.navalbattleclassic"

/** A Play registra o `referrer` na ficha e o Play Console mostra as instalações por origem (#88). */
actual val storeInviteLink: String = STORE_PAGE + "&referrer=utm_source%3Dconvite%26utm_medium%3Dorganic"
