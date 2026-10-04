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
 * Bandeira pintada na popa das frotas nacionais — a marca que separa pinturas lisas de
 * tons parecidos sem depender só da cor do casco (ver `drawEnsign` em ShipArt.kt).
 */
enum class Ensign { BRAZIL, JAPAN, USA, UK, PORTUGAL }

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
    val camo: Camo = Camo.LISA,
    val ensign: Ensign? = null
) {
    val name: String get() = t(key)
    val priceLabel: String get() = if (price == 0) t(K.STORE_INCLUDED) else t(K.PRICE_DOUBLOONS, price)


    companion object {
        val STANDARD = Paint(
            "std", K.PAINT_STD,
            hull = Color(0xFF39432D), deck = Color(0xFF6B6A43),
            trim = Color(0xFF8ED17A), dark = Color(0xFF1A2115),
            price = 0
        )
        val BRAZIL = Paint(
            "br", K.PAINT_BR,
            hull = Color(0xFF0E4A22), deck = Color(0xFF1E7038),
            trim = Color(0xFFFFD83D), dark = Color(0xFF0B2412),
            price = 0, ensign = Ensign.BRAZIL
        )
        val JAPAN = Paint(
            "jp", K.PAINT_JP,
            hull = Color(0xFF55595D), deck = Color(0xFF73787C),
            trim = Color(0xFFE0392B), dark = Color(0xFF2E3134),
            price = 450, ensign = Ensign.JAPAN
        )
        val USA = Paint(
            "us", K.PAINT_US,
            hull = Color(0xFF3A5163), deck = Color(0xFF5A7385),
            trim = Color(0xFFCFD9E0), dark = Color(0xFF1F2C36),
            price = 450, ensign = Ensign.USA
        )
        val UK = Paint(
            "uk", K.PAINT_UK,
            hull = Color(0xFF172A4E), deck = Color(0xFF2C4675),
            trim = Color(0xFFE6EBEF), dark = Color(0xFF0E1A31),
            price = 600, ensign = Ensign.UK
        )
        val PORTUGAL = Paint(
            "pt", K.PAINT_PT,
            hull = Color(0xFF123B2A), deck = Color(0xFF7A2621),
            trim = Color(0xFFE8C24A), dark = Color(0xFF0A2219),
            price = 600, ensign = Ensign.PORTUGAL
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
