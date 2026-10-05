package br.com.navalbattle.data

/**
 * Texto curto com a versão do build em execução (ex: "0.1.1 (3)"), lido direto do
 * empacotamento nativo de cada plataforma — nunca hardcoded aqui, pra sempre bater
 * com o que foi de fato instalado. Usado pra confirmar em segundos, olhando a tela,
 * se uma atualização publicada já chegou no aparelho ou não.
 */
expect val appVersionLabel: String

/** Só o nome da versão instalada ("0.18.0") — comparado com as novidades publicadas. */
expect val appVersionName: String

/** Verdadeiro se [candidate] ("0.18.0") é mais nova que [installed]. */
fun isNewerVersion(candidate: String, installed: String): Boolean {
    val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/** "android" ou "ios" — vai junto do feedback, pra saber onde reproduzir um bug. */
expect val platformName: String

