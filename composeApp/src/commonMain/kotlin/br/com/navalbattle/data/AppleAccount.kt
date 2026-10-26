package br.com.navalbattle.data

/** Quanto o nome do comandante pode ter (o mesmo corte de `Profile.rename`). */
private const val NAME_LIMIT = 18

/** Resultado de Excluir conta: a Apple pode pedir reautenticação, e cancelá-la cancela a exclusão. */
enum class DeleteAccountResult { DONE, FAILED, APPLE_CANCELLED }

/**
 * Nomes a tentar, em ordem, quando o servidor recusa por nome repetido (`profiles.username` é único):
 * o nome puro e `nome-xxxx` com o início do id, o mesmo padrão do `handle_new_user`. Corte em 18.
 */
fun appleNameCandidates(given: String, userId: String): List<String> {
    val name = given.trim().take(NAME_LIMIT)
    if (name.isEmpty()) return emptyList()
    return listOf(name, name.take(NAME_LIMIT - 5).trimEnd() + "-" + userId.take(4))
}

/** E-mail "ocultar meu e-mail" da Apple (`...@privaterelay.appleid.com`): não é o e-mail pessoal. */
fun isAppleRelayEmail(email: String): Boolean =
    email.trim().lowercase().endsWith("@privaterelay.appleid.com")

/** Excluir conta só precisa revogar na Apple se a conta tem a identidade `apple`. */
fun shouldRevokeApple(providers: List<String>): Boolean =
    providers.any { it.trim().equals("apple", ignoreCase = true) }

/**
 * Nome inicial de uma conta nova da Apple: a Apple manda o `givenName` só na primeira vez, e o
 * `handle_new_user` deu à conta o prefixo do e-mail (no relay, algo como `x7k2ab9qzd`). Só troca
 * se o convidado do aparelho nunca escolheu nome ([localName] em branco, lido antes do login) e a
 * carreira da nuvem é virgem (xp 0, 0 partidas, nome igual ao prefixo do e-mail); conta antiga
 * vinculada nunca é renomeada. Nulo = não mexe.
 */
fun appleInitialName(
    givenName: String?,
    email: String,
    cloudName: String?,
    cloudXp: Int,
    cloudMatches: Int,
    localName: String
): String? {
    val given = givenName?.trim().orEmpty()
    if (localName.isNotBlank()) return null
    if (given.isEmpty() || cloudName == null) return null
    if (cloudXp != 0 || cloudMatches != 0) return null
    if (!cloudName.trim().equals(email.trim().substringBefore('@'), ignoreCase = true)) return null
    return given.take(NAME_LIMIT)
}
