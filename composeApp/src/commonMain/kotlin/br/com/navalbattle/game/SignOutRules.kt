package br.com.navalbattle.game

/** Resultado de [br.com.navalbattle.AppState.signOut]: saiu, ou parou porque ainda há progresso só neste aparelho. */
enum class SignOutResult { DONE, UNSAVED }

/** Por que o "Sair da conta" avisou: falha de rede ou o servidor não aceitou (#73). */
enum class SignOutReason { OFFLINE, SERVER }

/** O que fazer ao pedir para sair da conta (#64). */
enum class SignOutDecision { PROCEED, WARN }

/** Regra pura de sair da conta: nada é apagado enquanto houver progresso que a nuvem ainda não tem. */
object SignOutRules {
    /**
     * [careerSaved]: a carreira atual já está na nuvem (ou subiu agora). [awardsUnsent]: há prêmio online na fila
     * que o servidor ainda não respondeu (rede, erro) ou que pode mudar em breve; prêmio que o servidor respondeu
     * `no_result` há tempo não conta (#73). [force]: o jogador confirmou "Sair mesmo assim".
     */
    fun decide(careerSaved: Boolean, awardsUnsent: Boolean, force: Boolean): SignOutDecision =
        if (force || (careerSaved && !awardsUnsent)) SignOutDecision.PROCEED else SignOutDecision.WARN

    /** Texto certo do aviso: rede só quando a falha foi de transporte; o resto é o servidor não aceitar. */
    fun reason(careerSaved: Boolean, careerNetworkFail: Boolean, awardsNetworkFail: Boolean): SignOutReason =
        if ((!careerSaved && careerNetworkFail) || awardsNetworkFail) SignOutReason.OFFLINE else SignOutReason.SERVER
}

/**
 * A carreira de um aparelho recém-instalado ou que acabou de sair de uma conta: convidado sem nome, patente
 * mais baixa, frota e pintura padrão e **zero dobrões** (diferente da instalação nova, que dá 500: sair da conta
 * não pode virar uma forma de ganhar o saldo inicial de novo).
 */
data class GuestCareer(
    val name: String,
    val insigniaId: String,
    val avatarId: String,
    val xp: Int,
    val credits: Int,
    val equipped: String,
    val equippedFleet: String,
    val owned: Set<String>,
    val ownedFleets: Set<String>
) {
    companion object {
        val DEFAULT = GuestCareer(
            name = "",
            insigniaId = Insignia.ANCHOR.id,
            avatarId = Avatar.COMMANDER_M.id,
            xp = 0,
            credits = 0,
            equipped = "br",
            equippedFleet = "std",
            owned = setOf("std", "br"),
            ownedFleets = setOf("std")
        )
    }
}
