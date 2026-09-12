package br.com.navalbattle.design

import androidx.compose.ui.graphics.Color

/**
 * Uma libré repinta a frota inteira com três tokens: casco, convés e detalhe.
 * É o que torna barato produzir frotas nacionais como item de loja.
 */
data class Livery(
    val id: String,
    val name: String,
    val hull: Color,
    val deck: Color,
    val trim: Color,
    val dark: Color,
    val priceLabel: String,
    val owned: Boolean
) {
    companion object {
        val STANDARD = Livery(
            "std", "Padrão Naval",
            hull = Color(0xFF2C3624), deck = Color(0xFF46583A),
            trim = Color(0xFF8ED17A), dark = Color(0xFF1A2115),
            priceLabel = "INCLUSA", owned = true
        )
        val BRAZIL = Livery(
            "br", "Frota Brasil",
            hull = Color(0xFF123A1D), deck = Color(0xFF1E7038),
            trim = Color(0xFFFFD83D), dark = Color(0xFF0B2412),
            priceLabel = "◆ 650", owned = true
        )
        val JAPAN = Livery(
            "jp", "Frota Japão",
            hull = Color(0xFF393F44), deck = Color(0xFF575F66),
            trim = Color(0xFFE0392B), dark = Color(0xFF23272B),
            priceLabel = "◆ 650", owned = true
        )
        val USA = Livery(
            "us", "Frota EUA",
            hull = Color(0xFF2B3740), deck = Color(0xFF4A5C67),
            trim = Color(0xFFCFD9E0), dark = Color(0xFF1A232A),
            priceLabel = "◆ 650", owned = true
        )
        val UK = Livery(
            "uk", "Frota Reino Unido",
            hull = Color(0xFF243040), deck = Color(0xFF3D5069),
            trim = Color(0xFFE6EBEF), dark = Color(0xFF161E29),
            priceLabel = "◆ 650", owned = false
        )
        val ARCTIC = Livery(
            "arc", "Camuflagem Ártica",
            hull = Color(0xFF59626B), deck = Color(0xFFA9B3BB),
            trim = Color(0xFFEEF3F7), dark = Color(0xFF3D444B),
            priceLabel = "◆ 480", owned = false
        )
        val STEALTH = Livery(
            "slt", "Furtiva",
            hull = Color(0xFF121412), deck = Color(0xFF1F231F),
            trim = Color(0xFF5F8F4A), dark = Color(0xFF0A0C0A),
            priceLabel = "◆ 800", owned = false
        )

        val all = listOf(STANDARD, BRAZIL, JAPAN, USA, UK, ARCTIC, STEALTH)
    }
}
