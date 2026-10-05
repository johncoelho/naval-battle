package br.com.navalbattle.data

/**
 * Pergunta à loja se já existe versão mais nova para este aparelho — no Android pela
 * Play Core In-App Update API (respeita a faixa de teste e o andamento da revisão).
 * Nulo quando não dá para saber: instalado fora da Play (APK direto) ou iPhone, que
 * não tem loja — aí vale só a tabela de novidades do servidor (`app_releases`).
 */
expect suspend fun checkUpdateAvailable(): Boolean?

/**
 * Leva à atualização: no Android, a ficha do jogo na Play Store; no iPhone, a parte
 * do iPhone na página do jogo, onde fica o .ipa novo.
 */
expect fun openStoreListing()

/** Abre o seletor de compartilhamento do aparelho com o link da loja pronto. */
expect fun shareStoreListing()
