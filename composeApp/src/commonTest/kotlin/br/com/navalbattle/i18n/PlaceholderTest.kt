package br.com.navalbattle.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #43 (0.43.3): os popups de boas-vindas mostravam "%s" literal porque as chaves usavam `%s`
 * com duas substituições, e `t(key, a, b)` só troca `%1` e `%2`. Este teste renderiza todas as
 * chaves, nos três idiomas, e reprova placeholder sobrando ou mistura de estilos.
 */
class PlaceholderTest {

    private val marcadores = Regex("""%s|%\d""")

    private fun textos(k: K) = listOf("pt" to k.pt, "en" to k.en, "es" to k.es)

    /** Renderiza com argumentos de exemplo conforme o estilo do texto. */
    private fun renderiza(texto: String, lang: Lang, k: K): String {
        val original = I18n.lang
        return try {
            I18n.lang = lang
            when {
                texto.contains("%3") -> t(k, "A", "B", "C")
                texto.contains("%2") || texto.contains("%1") -> t(k, "A", "B")
                texto.contains("%s") -> t(k, "A")
                else -> t(k)
            }
        } finally {
            I18n.lang = original
        }
    }

    @Test
    fun nenhumTextoRenderizadoTemPlaceholderSobrando() {
        K.entries.forEach { k ->
            Lang.entries.forEach { lang ->
                val bruto = when (lang) { Lang.PT -> k.pt; Lang.EN -> k.en; Lang.ES -> k.es }
                val out = renderiza(bruto, lang, k)
                // "%" legitimo (ex.: "A% de precisao") nao e placeholder; so reprova %s ou %digito.
                assertFalse(marcadores.containsMatchIn(out), "${k.name}/$lang sobrou placeholder: $out")
            }
        }
    }

    @Test
    fun placeholderSImplicaUmUnicoValorEnaoMisturaComNumerado() {
        K.entries.forEach { k ->
            textos(k).forEach { (lang, texto) ->
                val s = Regex("%s").findAll(texto).count()
                val numerados = Regex("""%\d""").findAll(texto).count()
                assertTrue(s <= 1, "${k.name}/$lang usa %s ${s}x; com mais de um valor use %1, %2")
                assertTrue(s == 0 || numerados == 0, "${k.name}/$lang mistura %s com %1/%2")
            }
        }
    }

    @Test
    fun textosDeBoasVindasMostramDobroesEMilhas() {
        val original = I18n.lang
        try {
            Lang.entries.forEach { lang ->
                I18n.lang = lang
                listOf(K.GUIDED_OFFER_BODY, K.GIFT_GOT, K.GIFT_GUEST).forEach { k ->
                    val out = t(k, 150, 5)
                    assertTrue(out.contains("150") && out.contains("5"), "${k.name}/$lang sem valores: $out")
                    assertFalse(out.contains("%"), "${k.name}/$lang com %: $out")
                }
            }
            I18n.lang = Lang.PT
            assertEquals("◆150 dobrões + 5 milhas", t(K.GIFT_GOT, 150, 5))
        } finally {
            I18n.lang = original
        }
    }

    @Test
    fun trocaDeLoteNaoMexeEmPorcentagemLegitima() {
        // MEDAL_SHARPSHOOTER_REQ tem "%s%": o segundo % e simbolo, nao placeholder.
        val original = I18n.lang
        try {
            I18n.lang = Lang.PT
            assertEquals("80% de precisão", t(K.MEDAL_SHARPSHOOTER_REQ, 80))
        } finally {
            I18n.lang = original
        }
    }
}
