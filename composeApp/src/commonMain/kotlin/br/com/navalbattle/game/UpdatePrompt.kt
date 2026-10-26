package br.com.navalbattle.game

import br.com.navalbattle.data.isNewerVersion

/**
 * Regras do aviso de atualização (#46): quando o popup volta depois de um "Depois" e de quanto em
 * quanto tempo vale consultar de novo ao voltar para o app. Puro, para testar sem tela.
 */
object UpdatePrompt {
    /** Marca do "Depois" quando só a loja diz que há atualização (nenhuma versão conhecida). */
    const val STORE_MARK = "store"

    /** Intervalo mínimo entre consultas no retorno ao app (folha de compra, login e avaliação também pausam o app). */
    private const val RECHECK_MILLIS = 5 * 60_000L

    /**
     * O popup aparece quando há atualização e o comandante ainda não dispensou ESTA versão: o
     * "Depois" vale até surgir uma versão maior que a dispensada.
     */
    fun shouldShowUpdate(available: Boolean, newestVersion: String?, dismissedVersion: String?): Boolean {
        if (!available) return false
        if (dismissedVersion == null) return true
        if (newestVersion == null) return false
        if (dismissedVersion == STORE_MARK) return true
        return isNewerVersion(newestVersion, dismissedVersion)
    }

    /** O que gravar quando o comandante toca "Depois": a versão vista, ou a marca da loja. */
    fun dismissMark(newestVersion: String?): String = newestVersion ?: STORE_MARK

    /** Maior versão entre as novidades publicadas (nulo quando não há nenhuma). */
    fun newestOf(versions: List<String>): String? =
        versions.fold<String, String?>(null) { best, v -> if (best == null || isNewerVersion(v, best)) v else best }

    /** Primeira consulta (nunca feita) ou já passaram 5 minutos da última. */
    fun shouldRecheck(lastCheckMillis: Long, now: Long): Boolean =
        lastCheckMillis <= 0L || now - lastCheckMillis >= RECHECK_MILLIS || now < lastCheckMillis
}
