package br.com.navalbattle.data

/**
 * Cursor da consulta de `online_messages` (#67). O `id` é `identity`: a ordem em que o
 * número é dado não é a ordem em que a linha fica visível, então pedir só `id > último`
 * podia pular para sempre uma linha de número menor que apareceu depois. Aqui a consulta
 * parte de um piso [lookback] consultas atrás (cerca de 2,4 s) e as linhas já entregues
 * são descartadas pelo conjunto de `id` vistos — cada linha sai uma vez só.
 */
class PollCursor(private val lookback: Int = 2) {
    private val seen = HashSet<Long>()
    private val history = ArrayDeque<Long>()
    private var maxId = 0L

    /** Valor de `id > ?` da próxima consulta. Zero até haver [lookback] consultas boas. */
    fun floor(): Long = if (history.size >= lookback) history.first() else 0L

    /** Registra o resultado de uma consulta boa e devolve só as linhas novas, em ordem de `id`. */
    fun accept(batch: List<OnlineMessage>): List<OnlineMessage> {
        val fresh = batch.filter { seen.add(it.id) }.sortedBy { it.id }
        batch.forEach { if (it.id > maxId) maxId = it.id }
        history.addLast(maxId)
        while (history.size > lookback) history.removeFirst()
        return fresh
    }

    /** Depois de baixar o log inteiro (ressincronização): tudo o que veio conta como visto. */
    fun resetTo(all: List<OnlineMessage>) {
        seen.clear()
        history.clear()
        maxId = 0L
        all.forEach { seen.add(it.id); if (it.id > maxId) maxId = it.id }
        // piso zerado: as duas próximas consultas baixam o log de novo e o conjunto descarta o que já veio
    }

    fun clear() {
        seen.clear()
        history.clear()
        maxId = 0L
    }
}
