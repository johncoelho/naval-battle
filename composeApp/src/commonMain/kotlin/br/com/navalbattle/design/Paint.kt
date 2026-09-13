package br.com.navalbattle.design

import androidx.compose.ui.graphics.Color
import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

/** Desenho aplicado sobre o casco, recortado no contorno da embarcação. */
enum class Camo {
    /** Pintura lisa, só as três cores da libré. */
    LISA,

    /** Faixas diagonais de alto contraste, o dazzle da Primeira Guerra. */
    DAZZLE,

    /** Manchas angulares, camuflagem de estilhaço. */
    ESTILHACO,

    /** Faixas horizontais claras e escuras, disfarce de linha d'água. */
    LISTRAS,

    /** Retículo fino, padrão digital moderno. */
    DIGITAL
}

/**
 * Uma libré repinta a frota inteira com três tokens de cor mais um padrão de
 * camuflagem. É o que torna barato produzir pinturas novas como item de loja.
 */
data class Paint(
    val id: String,
    val key: K,
    val hull: Color,
    val deck: Color,
    val trim: Color,
    val dark: Color,
    /** Preço em créditos ganhos em combate. Zero para as que já vêm com o jogo. */
    val price: Int,
    val camo: Camo = Camo.LISA
) {
    val name: String get() = t(key)
    val priceLabel: String get() = if (price == 0) t(K.STORE_INCLUDED) else "◆ $price"


    companion object {
        val STANDARD = Paint(
            "std", K.PAINT_STD,
            hull = Color(0xFF2C3624), deck = Color(0xFF46583A),
            trim = Color(0xFF8ED17A), dark = Color(0xFF1A2115),
            price = 0
        )
        val BRAZIL = Paint(
            "br", K.PAINT_BR,
            hull = Color(0xFF123A1D), deck = Color(0xFF1E7038),
            trim = Color(0xFFFFD83D), dark = Color(0xFF0B2412),
            price = 0
        )
        val JAPAN = Paint(
            "jp", K.PAINT_JP,
            hull = Color(0xFF393F44), deck = Color(0xFF575F66),
            trim = Color(0xFFE0392B), dark = Color(0xFF23272B),
            price = 450
        )
        val USA = Paint(
            "us", K.PAINT_US,
            hull = Color(0xFF2B3740), deck = Color(0xFF4A5C67),
            trim = Color(0xFFCFD9E0), dark = Color(0xFF1A232A),
            price = 450
        )
        val UK = Paint(
            "uk", K.PAINT_UK,
            hull = Color(0xFF243040), deck = Color(0xFF3D5069),
            trim = Color(0xFFE6EBEF), dark = Color(0xFF161E29),
            price = 600
        )
        val PORTUGAL = Paint(
            "pt", K.PAINT_PT,
            hull = Color(0xFF1E4436), deck = Color(0xFF2F6B52),
            trim = Color(0xFFD8362F), dark = Color(0xFF122A21),
            price = 600
        )
        val ARCTIC = Paint(
            "arc", K.PAINT_ARC,
            hull = Color(0xFF59626B), deck = Color(0xFFA9B3BB),
            trim = Color(0xFFEEF3F7), dark = Color(0xFF3D444B),
            price = 800
        )
        val DAZZLE = Paint(
            "dzl", K.PAINT_DZL,
            hull = Color(0xFF2A3138), deck = Color(0xFFB9C3CB),
            trim = Color(0xFF10151A), dark = Color(0xFF141A1F),
            price = 1000, camo = Camo.DAZZLE
        )
        val SPLINTER = Paint(
            "spl", K.PAINT_SPL,
            hull = Color(0xFF3B4A52), deck = Color(0xFF6E828C),
            trim = Color(0xFFDCE6EC), dark = Color(0xFF20292E),
            price = 1100, camo = Camo.ESTILHACO
        )
        val CORSAIR = Paint(
            "cor", K.PAINT_COR,
            hull = Color(0xFF2A1F2E), deck = Color(0xFF4A3654),
            trim = Color(0xFFE8C25B), dark = Color(0xFF17111A),
            price = 1200, camo = Camo.LISTRAS
        )
        val STEALTH = Paint(
            "slt", K.PAINT_SLT,
            hull = Color(0xFF121412), deck = Color(0xFF1F231F),
            trim = Color(0xFF5F8F4A), dark = Color(0xFF0A0C0A),
            price = 1400, camo = Camo.DIGITAL
        )

        val all = listOf(
            STANDARD, BRAZIL, JAPAN, USA, UK, PORTUGAL,
            ARCTIC, DAZZLE, SPLINTER, CORSAIR, STEALTH
        )

        fun of(id: String): Paint = all.firstOrNull { it.id == id } ?: BRAZIL
    }
}
