package br.com.navalbattle.data

/**
 * Relógio de parede em milissegundos, usado só para medir quanto tempo se passou
 * com o app em segundo plano numa partida online — os dois lados contam o prazo
 * de 60s pelo próprio relógio, sem precisar sincronizar nada com o outro aparelho.
 */
expect fun nowMillis(): Long
