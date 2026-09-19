package br.com.navalbattle.game

import kotlin.math.ceil

/**
 * Mesmo corte de percentual usado no fim de temporada (ver `season_trophies` em
 * `supabase/online.sql`): top 10% ouro, próximos 20% prata, próximos 30% bronze,
 * o resto sem medalha. Aqui serve só pra agrupar visualmente o placar ao vivo —
 * nada é gravado, e a temporada de verdade só fecha (e vira troféu de fato)
 * quando a próxima começa.
 */
fun liveTierFor(position: Int, total: Int): String? {
    if (total <= 0) return null
    return when {
        position <= maxOf(1, ceil(total * 0.10).toInt()) -> "ouro"
        position <= maxOf(1, ceil(total * 0.30).toInt()) -> "prata"
        position <= maxOf(1, ceil(total * 0.60).toInt()) -> "bronze"
        else -> null
    }
}
