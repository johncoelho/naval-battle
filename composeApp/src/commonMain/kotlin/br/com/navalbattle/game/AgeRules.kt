package br.com.navalbattle.game

/**
 * Faixa etária vinda da loja (Play Age Signals, #49; ECA Digital). Serve só para dar ao jogador
 * a experiência adequada à idade em cumprimento da lei: escolher o tipo de anúncio. A idade
 * nunca sai do aparelho (nem para a rede de anúncios, nem para o servidor, nem para log).
 */
enum class AgeBand { ADULT, TEEN, CHILD, UNKNOWN }

/**
 * Resultado de uma leitura. [answered] falso = erro ou tempo esgotado: a faixa vale (UNKNOWN) só
 * para aquele anúncio e não é guardada, para perguntar de novo no próximo toque. A Play ter
 * respondido "não compartilhou" é resposta ([answered] verdadeiro) e é guardada por 24 h.
 */
data class AgeReading(val band: AgeBand, val answered: Boolean) {
    companion object {
        val FAILED = AgeReading(AgeBand.UNKNOWN, answered = false)
    }
}

/** Classificação máxima de conteúdo do anúncio pedida ao AdMob. [NONE] = sem limite (adulto). */
enum class AdRating { NONE, G, PG }

/** Como o anúncio deve ser pedido para uma faixa. */
data class AdAudience(
    val personalized: Boolean,
    val underAgeOfConsent: Boolean,
    val childDirected: Boolean,
    val maxRating: AdRating
)

object AgeRules {
    /** `AgeSignalsStatus.SHARED` da lib: o jogador deixou a Play compartilhar a faixa. */
    const val STATUS_SHARED = 1

    /** Vale 24 h; depois disso o próximo toque em anúncio consulta a Play de novo. */
    const val CHECK_VALID_MS = 24L * 60 * 60 * 1000

    const val CHILD_MAX = 12
    const val TEEN_MAX = 17
    const val ADULT_MIN = 18

    /**
     * Converte os inteiros crus da API. Só vira adulto, adolescente ou criança com o status
     * SHARED e limites que provem a faixa; todo o resto (não compartilhou, verificação pendente,
     * limites ausentes, erro, tempo esgotado) é [AgeBand.UNKNOWN] e conta como menor no anúncio.
     */
    fun band(status: Int?, lower: Int?, upper: Int?, error: Boolean): AgeBand {
        if (error || status != STATUS_SHARED) return AgeBand.UNKNOWN
        return when {
            upper != null && upper <= CHILD_MAX -> AgeBand.CHILD
            upper != null && upper <= TEEN_MAX -> AgeBand.TEEN
            lower != null && lower >= ADULT_MIN -> AgeBand.ADULT
            else -> AgeBand.UNKNOWN
        }
    }

    /** Leitura completa: erro vira [AgeReading.FAILED]; qualquer resposta da Play é guardada. */
    fun reading(status: Int?, lower: Int?, upper: Int?, error: Boolean): AgeReading =
        if (error) AgeReading.FAILED else AgeReading(band(status, lower, upper, error = false), answered = true)

    /** Só resposta da Play é guardada por 24 h; falha não. */
    fun shouldCache(reading: AgeReading): Boolean = reading.answered

    /** Anúncio por faixa. Desconhecido é tratado como adolescente (decisão registrada na #49). */
    fun audience(band: AgeBand): AdAudience = when (band) {
        AgeBand.ADULT -> AdAudience(personalized = true, underAgeOfConsent = false, childDirected = false, maxRating = AdRating.NONE)
        AgeBand.CHILD -> AdAudience(personalized = false, underAgeOfConsent = true, childDirected = true, maxRating = AdRating.G)
        AgeBand.TEEN, AgeBand.UNKNOWN ->
            AdAudience(personalized = false, underAgeOfConsent = true, childDirected = false, maxRating = AdRating.PG)
    }

    /** A leitura guardada (faixa + hora) já venceu? [lastCheckMillis] = 0 → nunca leu. */
    fun checkDue(lastCheckMillis: Long, now: Long): Boolean =
        lastCheckMillis <= 0L || now - lastCheckMillis >= CHECK_VALID_MS || now < lastCheckMillis

    /** Faixa guardada no Prefs (só o nome; nunca idade nem installId). Desconhecida/inválida → UNKNOWN. */
    fun parse(stored: String): AgeBand = AgeBand.entries.firstOrNull { it.name == stored } ?: AgeBand.UNKNOWN
}
