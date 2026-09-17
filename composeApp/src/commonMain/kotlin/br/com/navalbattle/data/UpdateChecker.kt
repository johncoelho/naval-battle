package br.com.navalbattle.data

/**
 * Avisa quando já existe uma versão mais nova publicada na Play Store para a faixa
 * em que o comandante está inscrito — sem precisar de servidor de push nenhum, é a
 * própria loja quem sabe disso. No Android usa a Play Core In-App Update API; no
 * iOS ainda não existe equivalente configurado, então sempre devolve falso.
 */
expect suspend fun checkUpdateAvailable(): Boolean

/** Abre a ficha do jogo na loja, de onde o comandante atualiza. */
expect fun openStoreListing()
