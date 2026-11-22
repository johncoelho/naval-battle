package br.com.navalbattle.i18n

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #118: a Apple le o BINARIO, nao so o que aparece na tela (2.3.10 recusou a 0.47.0 por "Google Play"). Todo
 * texto de [K] vai para o framework iOS, entao nenhuma chave, de nenhum idioma, pode casar `tools/ios-forbidden-terms.txt`.
 * Sem excecao e sem filtro de "visivel": o que e so do Android mora em [platformGatedText] (vazio no iPhone).
 * Substitui o IosVisibleTextTest, que isentava as chaves "so Android" e todas as LAUNCH_*.
 * Roda na JVM porque le a lista do repositorio (a mesma que o CI e o ensaio de revisao usam).
 */
class BinaryTextTest {

    private fun termos(): List<Regex> {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "tools/ios-forbidden-terms.txt")
            if (f.exists()) {
                return f.readLines(Charsets.UTF_8)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { Regex(it, RegexOption.IGNORE_CASE) }
            }
            dir = dir.parentFile
        }
        error("tools/ios-forbidden-terms.txt nao encontrado a partir de ${File("").absolutePath}")
    }

    @Test
    fun listaDeTermosPegaOsTermosEsperados() {
        val lista = termos()
        assertTrue(lista.size >= 10, "lista de termos curta demais: ${lista.size}")
        listOf("Google Play", "Android", "Play Store", "beta", "teste fechado", "assinatura").forEach { amostra ->
            assertTrue(lista.any { it.containsMatchIn(amostra) }, "a lista nao pega \"$amostra\"")
        }
    }

    @Test
    fun nenhumaChaveDoCommonMainCitaTermoProibido() {
        val lista = termos()
        val achados = mutableListOf<String>()
        K.entries.forEach { key ->
            listOf(key.pt, key.en, key.es).forEach { texto ->
                lista.forEach { r ->
                    if (r.containsMatchIn(texto)) achados += "${key.name} casa \"${r.pattern}\": $texto"
                }
            }
        }
        assertTrue(achados.isEmpty(), "texto proibido no binario do iPhone:\n" + achados.joinToString("\n"))
    }

    @Test
    fun oTextoDoAndroidNaoVoltouParaOCommonMain() {
        val movidas = listOf(
            "LAUNCH_TITLE", "LAUNCH_BODY_TESTER", "LAUNCH_BODY_NEW", "DOUBLOON_CHARGE_NOTICE", "PREMIO_TITLE", "PREMIO_BODY",
            "PREMIO_TAKE", "PREMIO_TAKEN", "PREMIO_DONE_NOTICE", "PREMIO_UNAVAILABLE", "BADGE_TESTER", "BADGE_TESTER_DESC"
        )
        val nomes = K.entries.map { it.name }.toSet()
        movidas.forEach { assertFalse(it in nomes, "$it voltou para o K") }
    }
}
