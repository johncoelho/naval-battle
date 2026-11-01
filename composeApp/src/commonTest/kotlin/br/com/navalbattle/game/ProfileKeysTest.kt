package br.com.navalbattle.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O que fica no aparelho quando a conta sai (#64): lista de permissões, o resto é carreira. */
class ProfileKeysTest {
    private val keep = ProfileKeys.KEEP_ON_SIGN_OUT

    @Test
    fun carreiraESessaoNaoFicam() {
        listOf(
            "name", "xp", "avatar", "insignia", "fleet", "fleets", "owned", "equipped", "credits", "wallet_for",
            "ability_charges", "uid", "email", "token", "refresh", "online_rewards", "welcome_pending", "season_accepted",
            "matches", "wins", "shots", "hits", "sunk", "streak", "best_streak", "online_awards_pending", "charge_request_pending"
        ).forEach { assertFalse(it in keep, "$it nao devia ficar ao sair da conta") }
    }

    @Test
    fun progressoDoDiaNaoFicaMasOLembreteSim() {
        assertTrue(keep.none { it.startsWith("daily_") && it != "daily_reminder" })
        assertTrue("daily_reminder" in keep)
    }

    @Test
    fun preferenciasDoAparelhoFicam() {
        listOf("lang", "music", "sfx", "daily_reminder", "last_mode", "theme_track", "guided_done").forEach {
            assertTrue(it in keep, "$it devia ficar")
        }
        assertTrue(launchNoticeKey(null) in keep)
    }

    @Test
    fun cadaChaveTemUmTipoSo() {
        assertTrue(ProfileKeys.KEEP_INTS.intersect(ProfileKeys.KEEP_STRINGS.toSet()).isEmpty())
    }

    @Test
    fun lotePreservaOQueExisteEIgnoraAusentes() {
        val strings = mapOf("lang" to "es", "last_mode" to "TACTICAL", "name" to "Capita")
        val ints = mapOf("music" to 0, "sfx" to 1, "xp" to 900)
        val batch = ProfileKeys.keptOnSignOut({ k, d -> strings[k] ?: d }, { k, d -> ints[k] ?: d })
        assertEquals(mapOf("lang" to "es", "last_mode" to "TACTICAL"), batch.strings)
        assertEquals(mapOf("music" to 0, "sfx" to 1), batch.ints)  // 0 e valor valido; xp e carreira
    }
}
