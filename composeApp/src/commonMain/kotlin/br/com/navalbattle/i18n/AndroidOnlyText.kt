package br.com.navalbattle.i18n

/**
 * Textos que só existem no Android e citam Google Play, beta ou teste fechado (#118). A Apple lê o binário
 * inteiro (guideline 2.3.10), então esses termos não podem estar numa string do `commonMain`: o texto mora
 * no `actual` do `androidMain`, e o do iPhone devolve vazio. Quem usa só mostra o texto quando ele existe.
 */
enum class AndroidOnlyKey {
    LAUNCH_TITLE, LAUNCH_BODY_TESTER, LAUNCH_BODY_NEW,
    DOUBLOON_BETA_CHARGE,
    PREMIO_TITLE, PREMIO_BODY, PREMIO_TAKE, PREMIO_TAKEN, PREMIO_DONE_NOTICE, PREMIO_UNAVAILABLE,
    BADGE_BETA_TESTER, BADGE_BETA_TESTER_DESC
}

/** O texto no idioma atual; vazio no iPhone. */
expect fun androidOnlyText(key: AndroidOnlyKey): String

/** Com um valor no lugar de `%s`. */
fun androidOnlyText(key: AndroidOnlyKey, arg: Any): String = androidOnlyText(key).replace("%s", arg.toString())
