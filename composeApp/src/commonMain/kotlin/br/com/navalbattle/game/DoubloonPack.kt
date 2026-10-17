package br.com.navalbattle.game

import br.com.navalbattle.design.DoubloonPackArt
import br.com.navalbattle.i18n.K

/**
 * Pacotes da loja de dobrões (#15). O [code] é o id do produto no Play Console; o
 * preço que o jogador vê é o que a Play devolve (`formattedPrice`). Preço e quantidade
 * aqui são vitrine e reserva: quem decide quanto entra é o servidor
 * (`dobroes_products` em `supabase/dobroes.sql`), pelo mesmo id.
 */
enum class DoubloonPack(
    val code: String,
    val key: K,
    val priceCents: Int,
    val doubloons: Int,
    val bonusPercent: Int,
    val art: DoubloonPackArt
) {
    POUCH("dobroes_bolsa", K.DOUBLOON_PACK_POUCH, 490, 500, 0, DoubloonPackArt.POUCH),
    CHEST("dobroes_bau", K.DOUBLOON_PACK_CHEST, 990, 1100, 10, DoubloonPackArt.CHEST),
    STRONGBOX("dobroes_cofre", K.DOUBLOON_PACK_STRONGBOX, 2490, 3000, 20, DoubloonPackArt.STRONGBOX),
    /** Também é o Prêmio Beta (#15, parte c): o testador ganha este pacote de graça, uma vez por dia. */
    TREASURE("dobroes_tesouro", K.DOUBLOON_PACK_TREASURE, 4990, 6500, 30, DoubloonPackArt.TREASURE);

    /** "R$ 9,90" — formatado à mão: não há NumberFormat no código comum. */
    val priceLabel: String get() = brl(priceCents)
}

fun brl(cents: Int): String = "R$ ${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
