package br.com.navalbattle.data

/** Como um comandante da busca se relaciona comigo — decide o que o cartão do resultado mostra. */
enum class FriendRelation { NONE, FRIEND, REQUEST_SENT, REQUEST_RECEIVED }

/**
 * Relação entre mim ([myId]) e [otherId] a partir das amizades vindas do servidor.
 * [pendingSend] são os ids para quem acabei de tocar em "Adicionar" e o servidor ainda não
 * respondeu (otimista). O servidor sempre vence: quem já é amigo aparece como amigo — antes
 * qualquer amizade, aceita ou não, virava "Pedido enviado" (issue #7). Só pedido `pending` do
 * outro lado vira "recebido"; linha com outro status (ex.: `declined`) segue como "enviado",
 * para não reabrir o Adicionar em cima de um pedido recusado.
 */
fun friendRelation(
    myId: String,
    otherId: String,
    friendships: List<Friendship>,
    pendingSend: Set<String> = emptySet()
): FriendRelation {
    val f = friendships.firstOrNull {
        (it.requesterId == myId && it.addresseeId == otherId) ||
            (it.addresseeId == myId && it.requesterId == otherId)
    }
    return when {
        f == null -> if (otherId in pendingSend) FriendRelation.REQUEST_SENT else FriendRelation.NONE
        f.status == "accepted" -> FriendRelation.FRIEND
        f.status == "pending" && f.requesterId == otherId -> FriendRelation.REQUEST_RECEIVED
        else -> FriendRelation.REQUEST_SENT
    }
}

/**
 * O pedido de amizade pendente que [otherId] me mandou, para aceitar direto do resultado
 * da busca (#20). Só devolve linha `pending` em que o outro pediu e eu sou o destinatário;
 * `declined`, `accepted` ou pedido enviado por mim devolvem null (cuidado da #7).
 */
fun pendingRequestFrom(myId: String, otherId: String, friendships: List<Friendship>): Friendship? =
    friendships.firstOrNull {
        it.status == "pending" && it.requesterId == otherId && it.addresseeId == myId
    }
