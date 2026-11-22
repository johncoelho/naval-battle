package br.com.navalbattle.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** iPhone: primeiro idioma da lista preferida do sistema (já filtrada pelos `CFBundleLocalizations` do app). */
actual fun deviceLanguage(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
