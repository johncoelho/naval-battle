package br.com.navalbattle.game

/** Compra conferida pelo servidor (linha de `dobroes_pending`). */
data class PendingCredit(val token: String, val doubloons: Int)

/**
 * O que fazer com a entrega do servidor: [total] a somar agora, o conjunto [delivered]
 * já atualizado (guardado no aparelho) e [toAck] os tokens a confirmar no servidor.
 */
data class CreditPlan(val total: Int, val delivered: Set<String>, val toAck: List<String>)

/**
 * Guarda contra aviso repetido (#15, #34): cada token conta uma vez no aparelho, mesmo que o
 * servidor devolva de novo (ack perdido na queda de rede). Todo token pendente é confirmado,
 * porque o servidor é idempotente no ack. Com a carteira no servidor o saldo já foi creditado lá:
 * [CreditPlan.total] só alimenta o aviso "+N dobrões" e o conjunto [CreditPlan.delivered] é o que a
 * abertura da carteira (`dobroes_open`) manda para não somar de novo o que o aparelho já somou.
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
