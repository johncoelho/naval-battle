package br.com.navalbattle.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** #59: nome de guerra repetido não pode travar a sincronização da carreira. */
class NameSyncTest {

    private val userId = "ab12cd34-0000-0000-0000-000000000000"

    private fun career(name: String) = CloudProfile(
        username = name, insignia = "", avatar = "", langCode = "pt", xp = 120, credits = 0, matches = 4,
        wins = 2, shots = 40, hits = 20, sunk = 6, streak = 1, bestStreak = 2,
        owned = "std,br", equipped = "br", fleets = "std", fleet = "std"
    )

    private val taken = CloudResult.Fail("Esse nome de usuário já está em uso.", usernameTaken = true)
    private val network = CloudResult.Fail("Não consegui falar com o servidor do jogo.")

    /** As chamadas do teste nunca suspendem de verdade: roda até o fim na hora. */
    private fun <T> runNow(block: suspend () -> T): T {
        var out: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
        return out!!.getOrThrow()
    }

    /** Servidor falso: [takenNames] já são de outras contas (o teste compara em minúsculas só por simplicidade; o `text unique` de `profiles.username` distingue maiúsculas). */
    private fun sync(
        name: String, cloudName: String?, takenNames: Set<String>, tried: MutableList<String> = mutableListOf()
    ): NameSaveOutcome = runNow {
        saveProfileWithNameFallback(career(name), userId, cloudName) { p ->
            tried += p.username
            if (p.username.lowercase() in takenNames) taken else CloudResult.Ok(Unit)
        }
    }

    @Test
    fun reconheceDuplicateKeyDoUsername() {
        assertTrue(isUsernameTaken("""duplicate key value violates unique constraint "profiles_username_key""""))
        assertFalse(isUsernameTaken("""duplicate key value violates unique constraint "profiles_pkey""""))
        assertFalse(isUsernameTaken("Falha de conexão."))
        assertFalse(isUsernameTaken(null))
    }

    @Test
    fun candidatosSaoNomeComIdEDepoisNomeDaNuvem() {
        assertEquals(listOf("Maria-ab12", "Comandante-9f3a1c"), nameSyncCandidates("Maria", userId, "Comandante-9f3a1c"))
        assertEquals(listOf("Maria-ab12"), nameSyncCandidates("Maria", userId, null))
        assertEquals(listOf("Maria-ab12"), nameSyncCandidates("Maria", userId, "  "))
    }

    @Test
    fun candidatosCortamEm18ENuncaRepetemONomeAtual() {
        val longos = nameSyncCandidates("Maximiliano Alexandr", userId, null)
        assertTrue(longos.all { it.length <= 18 })
        assertEquals(listOf("Maximiliano Alexan", "Maximiliano A-ab12"), longos)
        // a nuvem já tem o mesmo nome: não adianta tentar de novo
        assertEquals(listOf("Maria-ab12"), nameSyncCandidates("Maria", userId, "Maria"))
    }

    @Test
    fun nomeLivreSobeSemMudar() {
        val tried = mutableListOf<String>()
        val r = sync("Maria", "Comandante-9f3a1c", emptySet(), tried)
        assertEquals("Maria", assertIs<NameSaveOutcome.Saved>(r).profile.username)
        assertEquals(listOf("Maria"), tried)
    }

    @Test
    fun nomeRepetidoUsaNomeMaisId() {
        val tried = mutableListOf<String>()
        val r = sync("Maria", "Comandante-9f3a1c", setOf("maria"), tried)
        assertEquals("Maria-ab12", assertIs<NameSaveOutcome.Saved>(r).profile.username)
        assertEquals(listOf("Maria", "Maria-ab12"), tried)
    }

    @Test
    fun seOFallbackTambemColidirVoltaAoNomeDaNuvem() {
        val r = sync("Maria", "Comandante-9f3a1c", setOf("maria", "maria-ab12"))
        val saved = assertIs<NameSaveOutcome.Saved>(r)
        assertEquals("Comandante-9f3a1c", saved.profile.username)
        assertEquals(120, saved.profile.xp)  // a carreira vai inteira, só o nome muda
    }

    @Test
    fun semNomeDaNuvemEComTudoOcupadoFalha() {
        assertEquals(NameSaveOutcome.Failed, sync("Maria", null, setOf("maria", "maria-ab12")))
    }

    @Test
    fun falhaDeRedeNaoRenomeiaNemTentaDeNovo() {
        val tried = mutableListOf<String>()
        val r = runNow {
            saveProfileWithNameFallback(career("Maria"), userId, "Comandante-9f3a1c") { p -> tried += p.username; network }
        }
        assertEquals(NameSaveOutcome.Failed, r)
        assertEquals(listOf("Maria"), tried)
    }

    @Test
    fun falhaDeRedeNoFallbackParaSemRenomear() {
        val tried = mutableListOf<String>()
        val r = runNow {
            saveProfileWithNameFallback(career("Maria"), userId, "Comandante-9f3a1c") { p ->
                tried += p.username
                if (p.username == "Maria") taken else network
            }
        }
        assertEquals(NameSaveOutcome.Failed, r)
        assertEquals(listOf("Maria", "Maria-ab12"), tried)
    }

    @Test
    fun candidatoOfensivoNaoEEnviado() {
        // "puta" é ofensivo (NameFilter): "puta-ab12" é pulado e o nome da nuvem fica
        val tried = mutableListOf<String>()
        val r = sync("puta", "Comandante-9f3a1c", setOf("puta"), tried)
        assertEquals("Comandante-9f3a1c", assertIs<NameSaveOutcome.Saved>(r).profile.username)
        assertEquals(listOf("puta", "Comandante-9f3a1c"), tried)
    }
}
