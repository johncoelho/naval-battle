package br.com.navalbattle.ui

/** Blocos da coluna rolável do relatório de fim de partida (#103). */
enum class ResultSection { HEADLINE, COMPARISON, CHART, LOCAL_STATS, RANKING, MILES, SOLO_STATS, CAREER }

/**
 * A ordem dos blocos da rolagem. Online, o Comparativo vem logo depois da manchete,
 * antes do mapa: com o anúncio no rodapé a janela encolhe e o que ficava no fim saía
 * da tela (#103). Não depende do anúncio.
 */
/** Partida contra o adversario virtual (#105): o mesmo relatorio da online, sem milhas (o bot nao gasta nem paga milha). */
fun botResultSections(): List<ResultSection> = listOf(
    ResultSection.HEADLINE, ResultSection.COMPARISON, ResultSection.CHART, ResultSection.RANKING, ResultSection.CAREER
)

fun resultSections(local: Boolean, online: Boolean): List<ResultSection> = when {
    local -> listOf(ResultSection.HEADLINE, ResultSection.CHART, ResultSection.LOCAL_STATS)
    online -> listOf(
        ResultSection.HEADLINE, ResultSection.COMPARISON, ResultSection.CHART,
        ResultSection.RANKING, ResultSection.MILES, ResultSection.CAREER
    )
    else -> listOf(ResultSection.HEADLINE, ResultSection.SOLO_STATS, ResultSection.CAREER)
}
