package br.com.navalbattle.game

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paridade da lista de países (#124): `public.country_codes()` em `supabase/country.sql` (o gatilho ignora o que
 * estiver fora) e [Countries.all] no app precisam ter os mesmos códigos. Só na JVM: o simulador não lê o repositório.
 */
class CountryParityTest {
    private fun countrySql(): String {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "supabase/country.sql")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        error("supabase/country.sql não encontrado a partir de ${File("").absolutePath}")
    }

    @Test
    fun listaDoServidorIgualADoApp() {
        val sql = countrySql()
        val i = sql.indexOf("select array[")
        val j = sql.indexOf("]::text[]", i)
        assertTrue(i >= 0 && j > i, "array de country_codes() não encontrado")
        val codes = Regex("'([A-Z]{2})'").findAll(sql.substring(i, j)).map { it.groupValues[1] }.toList()
        assertEquals(codes.size, codes.toSet().size, "código repetido no SQL")
        assertEquals(Countries.all.map { it.code }.sorted(), codes.sorted())
    }
}
