package br.com.navalbattle.data

import br.com.navalbattle.game.NameFilter

/**
 * O servidor recusou o upsert da carreira porque `profiles.username` é único e o nome já é de outra
 * conta (erro `duplicate key value violates unique constraint "profiles_username_key"`). Só esse erro
 * justifica trocar o nome: rede ou sessão caída nunca renomeiam o comandante.
 */
fun isUsernameTaken(raw: String?): Boolean =
    raw != null && "duplicate key" in raw && "username" in raw

/** Resultado de subir a carreira com fallback de nome: o retrato que o servidor aceitou, ou falha. */
sealed class NameSaveOutcome {
    data class Saved(val profile: CloudProfile) : NameSaveOutcome()
    data object Failed : NameSaveOutcome()
}

/**
 * Nomes a tentar, em ordem, depois que [name] foi recusado por já existir: `nome-xxxx` com o início do
 * id (mesmo padrão do `handle_new_user`, corte em 18) e por fim o nome que a conta já tem na nuvem, que
 * o servidor sempre aceita. Nunca repete [name]. Mesma regra do login com a Apple ([appleNameCandidates]).
 */
fun nameSyncCandidates(name: String, userId: String, cloudName: String?): List<String> =
    (appleNameCandidates(name, userId) + listOfNotNull(cloudName?.takeIf { it.isNotBlank() }))
        .filter { it != name }
        .distinct()

/**
 * Sobe a carreira ([snap]) com o nome que o aparelho tem; se o servidor recusar por nome repetido, tenta
 * os [nameSyncCandidates]. Qualquer outra falha (rede, sessão) encerra sem renomear: a próxima
 * sincronização tenta de novo com o mesmo nome. O servidor continua sendo quem decide (filtro de nome e
 * unicidade); aqui só se evita ficar preso num nome que ele não aceita.
 */
suspend fun saveProfileWithNameFallback(
    snap: CloudProfile,
    userId: String,
    cloudName: String?,
    save: suspend (CloudProfile) -> CloudResult<Unit>
): NameSaveOutcome {
    var r = save(snap)
    if (r is CloudResult.Ok) return NameSaveOutcome.Saved(snap)
    if (!(r is CloudResult.Fail && r.usernameTaken)) return NameSaveOutcome.Failed
    for (candidate in nameSyncCandidates(snap.username, userId, cloudName)) {
        if (candidate != cloudName && NameFilter.isOffensive(candidate)) continue  // filtro de nome (#58)
        val attempt = snap.copy(username = candidate)
        r = save(attempt)
        if (r is CloudResult.Ok) return NameSaveOutcome.Saved(attempt)
        if (!(r is CloudResult.Fail && r.usernameTaken)) return NameSaveOutcome.Failed
    }
    return NameSaveOutcome.Failed
}
