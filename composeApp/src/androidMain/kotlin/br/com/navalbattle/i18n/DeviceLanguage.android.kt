package br.com.navalbattle.i18n

import java.util.Locale

/** Android: idioma do app, que sem troca por app é o do sistema (`Locale.getDefault()`). */
actual fun deviceLanguage(): String? = Locale.getDefault().toLanguageTag()
