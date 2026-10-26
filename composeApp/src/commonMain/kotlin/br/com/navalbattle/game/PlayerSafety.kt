package br.com.navalbattle.game

/**
 * Denunciar e bloquear jogador (#58). Os CÓDIGOS são combinados com o servidor (`supabase/safety.sql`,
 * `player_reports.reason` / `context`) e não podem mudar: `PlayerSafetyTest` os trava.
 */
enum class ReportReason(val code: String) {
    NAME("name"),
    ABUSE("abuse"),
    CHEAT("cheat"),
    OTHER("other")
}

/** De onde partiu a denúncia. Não existe chat no app, então não há contexto de chat. */
enum class ReportContext(val code: String) {
    RANKING("ranking"),
    SEARCH("search"),
    FRIENDS("friends"),
    MATCH("match"),
    RESULT("result")
}

enum class ReportOutcome { OK, ALREADY, LIMIT, SELF, INVALID, ERROR }

enum class BlockOutcome { OK, SELF, INVALID, ERROR }

/** Resposta de `set_username`. */
enum class UsernameOutcome { OK, INVALID, OFFENSIVE, TAKEN, ERROR }

fun parseReportResult(raw: String?): ReportOutcome = when (raw?.trim()) {
    "ok" -> ReportOutcome.OK
    "already" -> ReportOutcome.ALREADY
    "limit" -> ReportOutcome.LIMIT
    "self" -> ReportOutcome.SELF
    "invalid" -> ReportOutcome.INVALID
    else -> ReportOutcome.ERROR
}

fun parseBlockResult(raw: String?): BlockOutcome = when (raw?.trim()) {
    "ok" -> BlockOutcome.OK
    "self" -> BlockOutcome.SELF
    "invalid" -> BlockOutcome.INVALID
    else -> BlockOutcome.ERROR
}

fun parseUsernameResult(raw: String?): UsernameOutcome = when (raw?.trim()) {
    "ok" -> UsernameOutcome.OK
    "invalid" -> UsernameOutcome.INVALID
    "offensive" -> UsernameOutcome.OFFENSIVE
    "taken" -> UsernameOutcome.TAKEN
    else -> UsernameOutcome.ERROR
}

/**
 * Que nome de outro jogador mostrar: o que veio (ranking, busca, HELLO do protocolo), a não ser que seja
 * ofensivo. App antigo ou cliente alterado pode mandar qualquer coisa; aí vai o [fallback].
 */
fun displayOther(raw: String, fallback: String): String = NameFilter.safeDisplay(raw, fallback)
