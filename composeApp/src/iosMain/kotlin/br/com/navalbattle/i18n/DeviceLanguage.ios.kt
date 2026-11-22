package br.com.navalbattle.i18n

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/** iPhone: primeiro idioma da lista preferida do usuário, sem filtro (quem filtra pelo plist é `NSBundle.preferredLocalizations`). Só o primeiro conta: fr e depois es abre em inglês. */
actual fun deviceLanguage(): String? = NSLocale.preferredLanguages.firstOrNull() as? String
