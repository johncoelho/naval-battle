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
    fun pick(random: Random = Random.Default, last: String? = null, playerName: String? = null): String {
        val pool = POOL.filter { it != last && !it.equals(playerName, ignoreCase = true) }.ifEmpty { POOL }
        return pool[random.nextInt(pool.size)]
    }
}
