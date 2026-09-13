package br.com.navalbattle.design

/** Formato da proa — o traço que mais muda a silhueta vista de cima. */
enum class Prow { PADRAO, CLIPPER, BULBOSA, FACETADA }

/** Superestrutura: o volume no meio do navio. */
enum class Tower { PADRAO, PAGODE, BLOCO, FACETADA }

/**
 * Linha de construção da frota: muda a **silhueta** das cinco embarcações — proa,
 * boca (largura) e superestrutura. É o segundo eixo de personalização, independente
 * da camuflagem, que só muda a pintura.
 */
data class FleetLine(
    val id: String,
    val name: String,
    val description: String,
    val price: Int,
    /** Multiplicador da boca: abaixo de 1 afina o casco, acima alarga. */
    val beam: Float,
    val prow: Prow,
    val tower: Tower,
    /** Chaminés inclinadas no convés, marca das frotas clássicas. */
    val funnels: Int
) {
    val priceLabel: String get() = if (price == 0) "INCLUSA" else "◆ $price"

    companion object {
        val STANDARD = FleetLine(
            "std", "Linha Padrão",
            "Cascos de série, equilibrados em boca e proa.",
            price = 0, beam = 1f, prow = Prow.PADRAO, tower = Tower.PADRAO, funnels = 0
        )
        val IMPERIAL = FleetLine(
            "imp", "Linha Imperial",
            "Cascos estreitos, proa clipper e mastro em pagode.",
            price = 900, beam = 0.88f, prow = Prow.CLIPPER, tower = Tower.PAGODE, funnels = 2
        )
        val ATLANTIC = FleetLine(
            "atl", "Linha Atlântica",
            "Cascos largos, proa bulbosa e superestrutura em bloco.",
            price = 900, beam = 1.12f, prow = Prow.BULBOSA, tower = Tower.BLOCO, funnels = 1
        )
        val GHOST = FleetLine(
            "gho", "Linha Fantasma",
            "Cascos facetados de baixa assinatura, sem chaminés.",
            price = 1800, beam = 0.94f, prow = Prow.FACETADA, tower = Tower.FACETADA, funnels = 0
        )

        val all = listOf(STANDARD, IMPERIAL, ATLANTIC, GHOST)

        fun of(id: String): FleetLine = all.firstOrNull { it.id == id } ?: STANDARD
    }
}

/**
 * O visual completo da frota: a linha de casco mais a camuflagem. Anda junto por
 * toda a interface porque as duas coisas são desenhadas na mesma passada.
 */
data class Skin(val livery: Livery, val fleet: FleetLine) {
    companion object {
        val DEFAULT = Skin(Livery.BRAZIL, FleetLine.STANDARD)
    }
}
