package br.com.navalbattle.i18n

/**
 * Idioma do aparelho como etiqueta (`en`, `es-MX`, `pt-BR`, `zh-Hans-CN`), ou nulo se o sistema não disser.
 * Quem escolhe o [Lang] é [Lang.fromDevice] (#131).
 */
expect fun deviceLanguage(): String?
