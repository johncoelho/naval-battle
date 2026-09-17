package br.com.navalbattle.data

// Sem equivalente à Play Core In-App Update API configurado para o alvo iOS ainda —
// nunca acusa versão nova, para não mostrar um aviso que não leva a lugar nenhum.
actual suspend fun checkUpdateAvailable(): Boolean = false

actual fun openStoreListing() {}
