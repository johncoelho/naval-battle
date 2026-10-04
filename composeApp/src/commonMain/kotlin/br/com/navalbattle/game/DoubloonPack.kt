package br.com.navalbattle.game

import br.com.navalbattle.design.DoubloonPackArt
import br.com.navalbattle.i18n.K

/**
 * Pacotes da loja de dobrões. Preço e quantidade aqui são só vitrine — quem decide
 * quanto entra é o servidor (`beta_pack` em `supabase/economy.sql`), pelo [code].
 * Hoje a compra é simulada para beta testers; com a Google Play, o mesmo [code] vira
 * o id do produto.
 */
enum class DoubloonPack(
    val code: String,
    val key: K,
    val priceCents: Int,
    val doubloons: Int,
    val bonusPercent: Int,
    val art: DoubloonPackArt
) {
    POUCH("pouch", K.DOUBLOON_PACK_POUCH, 490, 500, 0, DoubloonPackArt.POUCH),
    CHEST("chest", K.DOUBLOON_PACK_CHEST, 990, 1100, 10, DoubloonPackArt.CHEST),
    STRONGBOX("strongbox", K.DOUBLOON_PACK_STRONGBOX, 2490, 3000, 20, DoubloonPackArt.STRONGBOX),
    TREASURE("treasure", K.DOUBLOON_PACK_TREASURE, 4990, 6500, 30, DoubloonPackArt.TREASURE);

    /** "R$ 9,90" — formatado à mão: não há NumberFormat no código comum. */
    val priceLabel: String get() = brl(priceCents)
}

fun brl(cents: Int): String = "R$ ${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
