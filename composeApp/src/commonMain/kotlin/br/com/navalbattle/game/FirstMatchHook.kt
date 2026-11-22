package br.com.navalbattle.game

/** As três ações do cartão do fim da 1ª partida contra o adversário virtual (#135). */
enum class HookAction { CHALLENGE, REMATCH, INVITE }

/**
 * Gancho do fim da 1ª partida contra o adversário virtual (#135): um cartão na tela de resultado que dá motivo para
 * voltar (desafio de amanhã, revanche, chamar um amigo). Sem recompensa nova e sem tela extra. Regras puras.
 */
object FirstMatchHook {
    /**
     * Mostra só contra o adversário virtual, em vitória ou derrota (abandono não: a pessoa saiu), quando a carreira
     * tinha 0 partidas concluídas ANTES desta e o aparelho ainda não exibiu o gancho. A 1ª condição cobre reinstalação
     * com conta (a carreira volta do servidor); a 2ª, repetição no mesmo aparelho. Nunca em aparelho de teste automático.
     * A marca é gravada ao exibir, não ao tocar.
     */
    fun shouldShow(careerMatches: Int, hookSeen: Boolean, vsBot: Boolean, abandoned: Boolean, isTest: Boolean): Boolean =
        vsBot && !abandoned && !isTest && !hookSeen && careerMatches == 0

    /**
     * As ações na ordem em que aparecem. Vitória: Desafio, Revanche, Chamar amigo; derrota: Revanche, Desafio, Chamar
     * amigo (a revanche é o impulso natural). O desafio de amanhã é o lembrete diário, que exige conta e só existe no
     * Android; "Chamar amigo" usa o código da conta e, sem conta, o link da loja (que o iPhone não compartilha).
     * Sobra sempre a revanche.
     */
    fun actions(victory: Boolean, signedIn: Boolean, ios: Boolean): List<HookAction> {
        val order = if (victory) {
            listOf(HookAction.CHALLENGE, HookAction.REMATCH, HookAction.INVITE)
        } else {
            listOf(HookAction.REMATCH, HookAction.CHALLENGE, HookAction.INVITE)
        }
        return order.filter { a ->
            when (a) {
                HookAction.CHALLENGE -> signedIn && !ios
                HookAction.REMATCH -> true
                HookAction.INVITE -> signedIn || !ios
            }
        }
    }
}
