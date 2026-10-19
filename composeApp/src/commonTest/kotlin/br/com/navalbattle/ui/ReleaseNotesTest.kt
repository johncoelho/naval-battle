package br.com.navalbattle.ui

import br.com.navalbattle.data.appVersionName
import br.com.navalbattle.i18n.K
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #31: a tela Novidades ficou parada na 0.25.0 porque ninguém acrescentava a linha ao subir
 * a minor. Estes testes quebram quando isso acontece de novo.
 */
class ReleaseNotesTest {

    /** "0.34.0" -> "0.34" (só major.minor: patch não exige linha nova). */
    private fun majorMinor(version: String): String = version.split(".").take(2).joinToString(".")

    @Test
    fun primeiraLinhaEhAMinorInstalada() {
        // No simulador iOS o teste roda dentro do host do XCTest: NSBundle.mainBundle não é o app,
        // então a versão lida ali não é a instalada (cai no "0"). Nesse caso a checagem fica com o
        // Android, onde BuildConfig é a versão real do build, e com o release-check da CI, que
        // garante Info.plist == versionName.
        val instalada = appVersionName
        if (!Regex("""\d+\.\d+(\.\d+)?""").matches(instalada)) return
        assertEquals(
            majorMinor(instalada),
            majorMinor(releaseNotes.first().version),
            "A tela Novidades precisa de uma linha para a minor ${majorMinor(instalada)} " +
                "(acrescente RN_Vxxxx em i18n/Strings.kt e a linha em ui/ReleaseNotesScreen.kt)."
        )
    }

    @Test
    fun todaChaveDeNovidadesTemPtEnEs() {
        val chaves = releaseNotes.map { it.key }.distinct()
        assertTrue(chaves.isNotEmpty())
        chaves.forEach { key ->
            assertTrue(key.pt.isNotBlank(), "${key.name} sem texto em pt")
            assertTrue(key.en.isNotBlank(), "${key.name} sem texto em en")
            assertTrue(key.es.isNotBlank(), "${key.name} sem texto em es")
        }
    }

    @Test
    fun versoesEmOrdemDecrescenteSemRepeticao() {
        val versoes = releaseNotes.map { it.version }
        assertEquals(versoes.distinct(), versoes, "Versão repetida na lista de Novidades")
        val numeros = versoes.map { v -> v.split(".").map { it.toInt() } }
        for (i in 1 until numeros.size) {
            val anterior = numeros[i - 1]
            val atual = numeros[i]
            val anteriorMaiorOuIgual = anterior[0] > atual[0] ||
                (anterior[0] == atual[0] && anterior[1] > atual[1]) ||
                (anterior[0] == atual[0] && anterior[1] == atual[1] && anterior[2] > atual[2])
            assertTrue(anteriorMaiorOuIgual, "${versoes[i - 1]} deveria vir antes de ${versoes[i]}")
        }
    }

    @Test
    fun chavesDasVersoesNaoSeRepetem() {
        val chaves = releaseNotes.map { it.key }
        assertEquals(chaves.distinct(), chaves, "Mesma chave RN usada em duas versões")
        assertTrue(chaves.all { it.name.startsWith("RN_V") }, "Chave fora do padrão RN_V")
        assertTrue(K.values().count { it.name.startsWith("RN_V") } >= chaves.size)
    }
}
