package br.com.navalbattle.game

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paridade das listas de nomes (#58): `supabase/safety.sql` (servidor) e `NameFilter.TERMS` / `NameFilter.ALLOW`
 * (app) precisam ter os mesmos termos. Só na JVM, porque o simulador do iOS não lê o repositório.
 */
class NameTermsParityTest {

    private fun safetySql(): String {
        // o diretório de trabalho dos testes é o módulo composeApp; sobe até achar supabase/safety.sql
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "supabase/safety.sql")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        error("supabase/safety.sql não encontrado a partir de ${File("").absolutePath}")
    }

    private fun between(text: String, begin: String, end: String): String {
        val i = text.indexOf(begin)
        val j = text.indexOf(end)
        assertTrue(i >= 0 && j > i, "marcadores $begin / $end não encontrados em safety.sql")
        return text.substring(i + begin.length, j)
    }

    @Test
    fun termsMatchSql() {
        val block = between(safetySql(), "-- TERMS-BEGIN", "-- TERMS-END")
        val sql = Regex("""\('([^']+)','([a-z]+)'\)""").findAll(block).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue(sql.isNotEmpty(), "nenhum termo lido do safety.sql")
        assertEquals(sql.sortedBy { it.first }, NameFilter.TERMS.sortedBy { it.first })
    }

    @Test
    fun allowListMatchesSql() {
        val block = between(safetySql(), "-- ALLOW-BEGIN", "-- ALLOW-END")
        val sql = Regex("""\('([^']+)'\)""").findAll(block).map { it.groupValues[1] }.toList()
        assertTrue(sql.isNotEmpty(), "nenhuma exceção lida do safety.sql")
        assertEquals(sql.sorted(), NameFilter.ALLOW.sorted())
    }
}
