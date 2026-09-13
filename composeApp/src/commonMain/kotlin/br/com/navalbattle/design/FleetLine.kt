package br.com.navalbattle.design

import br.com.navalbattle.i18n.K
import br.com.navalbattle.i18n.t

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
    val key: K,
    val descriptionKey: K,
    val price: Int,
    /** Multiplicador da boca: abaixo de 1 afina o casco, acima alarga. */
    val beam: Float,
    val prow: Prow,
    val tower: Tower,
    /** Chaminés inclinadas no convés, marca das frotas clássicas. */
    val funnels: Int
) {
    val name: String get() = t(key)
    val description: String get() = t(descriptionKey)
    val priceLabel: String get() = if (price == 0) t(K.STORE_INCLUDED) else "◆ $price"

    companion object {
        val STANDARD = FleetLine(
            "std", K.FLEET_STD, K.FLEET_STD_SUB,
            price = 0, beam = 1f, prow = Prow.PADRAO, tower = Tower.PADRAO, funnels = 0
        )
        val IMPERIAL = FleetLine(
            "imp", K.FLEET_IMP, K.FLEET_IMP_SUB,
            price = 900, beam = 0.88f, prow = Prow.CLIPPER, tower = Tower.PAGODE, funnels = 2
        )
        val ATLANTIC = FleetLine(
            "atl", K.FLEET_ATL, K.FLEET_ATL_SUB,
            price = 900, beam = 1.12f, prow = Prow.BULBOSA, tower = Tower.BLOCO, funnels = 1
        )
        val GHOST = FleetLine(
            "gho", K.FLEET_GHO, K.FLEET_GHO_SUB,
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
data class Skin(val paint: Paint, val fleet: FleetLine) {
    companion object {
        val DEFAULT = Skin(Paint.BRAZIL, FleetLine.STANDARD)
    }
}
