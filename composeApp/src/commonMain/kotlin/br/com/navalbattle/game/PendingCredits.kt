package br.com.navalbattle.game

/** Compra conferida pelo servidor (linha de `dobroes_pending`). */
data class PendingCredit(val token: String, val doubloons: Int)

/**
 * O que fazer com a entrega do servidor: [total] a somar agora, o conjunto [delivered]
 * já atualizado (guardado no aparelho) e [toAck] os tokens a confirmar no servidor.
 */
data class CreditPlan(val total: Int, val delivered: Set<String>, val toAck: List<String>)

/**
 * Guarda contra soma dupla (#15): cada token é somado uma vez no aparelho, mesmo que o
 * servidor devolva de novo (ack perdido na queda de rede). Todo token pendente é
 * confirmado, porque o servidor é idempotente no ack.
 */
object PendingCredits {
    fun plan(pending: List<PendingCredit>, delivered: Set<String>): CreditPlan {
        var total = 0
        val seen = delivered.toMutableSet()
        val ack = LinkedHashSet<String>()
        for (p in pending) {
            ack += p.token
            if (seen.add(p.token)) total += p.doubloons
        }
        return CreditPlan(total, seen, ack.toList())
    }
}
