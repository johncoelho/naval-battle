package br.com.navalbattle.data

/** O `.ipa` não passa por revisão automática: nunca é aparelho de teste. */
actual fun isTestLabDevice(): Boolean = false
