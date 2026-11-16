package br.com.navalbattle.game

import kotlin.random.Random

/**
 * Pool de personas do adversario virtual (#105). Nomes FICTICIOS no estilo dos nomes de guerra do jogo: nenhum
 * nome de pessoa conhecida (atleta, artista, politico, streamer), nenhum jogador real do banco, sem foto de perfil.
 * Todos passam pelo [NameFilter] e cabem em 16 letras (teste `BotPersonasTest`). A lista mora no repositorio
 * (sem dado pessoal), decisao registrada na issue #105.
 */
object BotPersonas {
    internal val POOL: List<String> = listOf(
        "MarujoLeo", "Capitao_Sul", "VentoNorte", "Kaique77", "BiaTorpedo", "LoboDoMar_X", "Duda Mares",
        "Timoneiro91", "CaboRocha", "Nico_Lemos", "Ancora22", "SeaFox_Jr", "Rafa_Proa", "Lara Convés",
        "Tonho_Naval", "CapBrisa", "Joca Farol", "Pedro_Bombordo", "Sereia_Lu", "Helmo_88", "Gabi Tribordo",
        "RedWake", "Cabo_Vitor", "IlhaGrande7", "Zeca_Popa", "Bru Marujo", "NavioFantasma9", "Mestre_Onda",
        "Dani_Radar", "Tiago Cais", "SubZero_Ivan", "Caio_Escuna", "Lia_Maresia", "Gui_Fragata",
        "Bento_Corveta", "Nina_Proa", "Ruy_Vela", "Fabi Salgado", "Otto_Casco", "Vivi_Leme"
    )

    /**
     * Sorteia uma persona diferente da [last] (nunca a mesma em partidas seguidas do mesmo jogador) e do
     * [playerName] (nunca com o proprio nome do jogador na tela).
     */
    fun pick(
        random: Random = Random.Default,
        last: String? = null,
        playerName: String? = null,
        blocked: Set<String> = emptySet()
    ): String {
        val pool = POOL.filter { it != last && it !in blocked && !it.equals(playerName, ignoreCase = true) }
            .ifEmpty { POOL.filter { it !in blocked } }.ifEmpty { POOL }
        return pool[random.nextInt(pool.size)]
    }

    /** Retrato, experiencia e pontos que o adversario mostra na tela "Adversario encontrado" (#112). */
    data class Look(val avatarId: String, val xp: Int, val rating: Int)

    /**
     * Ficha plausivel da persona, igual a de um jogador comum e sem rotulo: o mesmo nome sempre da a mesma ficha
     * (semente pelo nome), perto do nivel do jogador. [level] segue a forca real do adversario: o facil fica um
     * pouco abaixo, o dificil um pouco acima.
     */
    fun look(name: String, level: BotLevel, playerXp: Int, playerRating: Int, exact: Boolean = false): Look {
        val r = Random(name.hashCode().toLong() * 31L + level.ordinal)
        val avatar = Avatar.entries[r.nextInt(Avatar.entries.size)]
        val (xpLo, xpHi) = when (level) {
            BotLevel.EASY -> 0.55 to 1.0
            BotLevel.MEDIUM -> 0.85 to 1.3
            BotLevel.HARD -> 1.15 to 1.8
        }
        val factor = xpLo + r.nextDouble() * (xpHi - xpLo)
        val xp = (maxOf(playerXp, 120) * factor).toInt().coerceIn(60, Rank.ALMIRANTE.xp + 3000)
        val (rLo, rHi) = when (level) {
            BotLevel.EASY -> -90 to -15
            BotLevel.MEDIUM -> -40 to 40
            BotLevel.HARD -> 15 to 90
        }
        // exact = true: [playerRating] ja e o rating do adversario sorteado pelo servidor (bot_rating)
        val rating = if (exact) playerRating.coerceAtLeast(0) else (playerRating + rLo + r.nextInt(rHi - rLo + 1)).coerceAtLeast(0)
        return Look(avatar.id, xp, rating)
    }
}
