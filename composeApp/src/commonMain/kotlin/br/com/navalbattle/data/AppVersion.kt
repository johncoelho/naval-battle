package br.com.navalbattle.data

/**
 * Texto curto com a versão do build em execução (ex: "0.1.1 (3)"), lido direto do
 * empacotamento nativo de cada plataforma — nunca hardcoded aqui, pra sempre bater
 * com o que foi de fato instalado. Usado pra confirmar em segundos, olhando a tela,
 * se uma atualização publicada já chegou no aparelho ou não.
 */
expect val appVersionLabel: String

