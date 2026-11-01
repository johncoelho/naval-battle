package br.com.navalbattle.game

/** Resultado de [br.com.navalbattle.AppState.signOut]: saiu, ou parou porque ainda há progresso só neste aparelho. */
enum class SignOutResult { DONE, UNSAVED }

/** O que fazer ao pedir para sair da conta (#64). */
enum class SignOutDecision { PROCEED, WARN }

/** Regra pura de sair da conta: nada é apagado enquanto houver progresso que a nuvem ainda não tem. */
object SignOutRules {
    /**
     * [careerSaved]: a carreira atual já está na nuvem (ou subiu agora). [awardsPending]: ainda há prêmio online
     * na fila do aparelho. [force]: o jogador confirmou "Sair mesmo assim".
     */
    fun decide(careerSaved: Boolean, awardsPending: Boolean, force: Boolean): SignOutDecision =
        if (force || (careerSaved && !awardsPending)) SignOutDecision.PROCEED else SignOutDecision.WARN
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
