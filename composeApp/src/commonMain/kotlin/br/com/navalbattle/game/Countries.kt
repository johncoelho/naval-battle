package br.com.navalbattle.game

import br.com.navalbattle.i18n.Lang

/** Um país da lista fechada da bandeira (#124): código ISO 3166-1 alfa-2 e o nome nos três idiomas. */
data class Country(val code: String, val pt: String, val en: String, val es: String) {
    fun name(lang: Lang): String = when (lang) {
        Lang.PT -> pt
        Lang.EN -> en
        Lang.ES -> es
    }

    /** Bandeira em emoji do sistema: os dois indicadores regionais do código (sem imagem, sem fonte nova). */
    val flag: String get() = Countries.flagOf(code)
}

/**
 * Bandeira de nacionalidade no perfil (#124): lista **fechada** e neutra, igual à do servidor
 * (`supabase/country.sql`, `public.country_codes()`; o `CountriesTest` confere as duas). Só membros da ONU e os dois
 * observadores (Palestina e Santa Sé); fora territórios disputados e dependências. Sem bandeira é estado válido e é o
 * padrão ("Prefiro não mostrar"). Extensível por issue: código novo entra aqui e no SQL ao mesmo tempo.
 */
object Countries {
    val all: List<Country> = listOf(
        // Américas
        Country("AR", "Argentina", "Argentina", "Argentina"),
        Country("BO", "Bolívia", "Bolivia", "Bolivia"),
        Country("BR", "Brasil", "Brazil", "Brasil"),
        Country("CA", "Canadá", "Canada", "Canadá"),
        Country("CL", "Chile", "Chile", "Chile"),
        Country("CO", "Colômbia", "Colombia", "Colombia"),
        Country("CR", "Costa Rica", "Costa Rica", "Costa Rica"),
        Country("CU", "Cuba", "Cuba", "Cuba"),
        Country("DO", "República Dominicana", "Dominican Republic", "República Dominicana"),
        Country("EC", "Equador", "Ecuador", "Ecuador"),
        Country("SV", "El Salvador", "El Salvador", "El Salvador"),
        Country("GT", "Guatemala", "Guatemala", "Guatemala"),
        Country("HN", "Honduras", "Honduras", "Honduras"),
        Country("JM", "Jamaica", "Jamaica", "Jamaica"),
        Country("MX", "México", "Mexico", "México"),
        Country("NI", "Nicarágua", "Nicaragua", "Nicaragua"),
        Country("PA", "Panamá", "Panama", "Panamá"),
        Country("PY", "Paraguai", "Paraguay", "Paraguay"),
        Country("PE", "Peru", "Peru", "Perú"),
        Country("US", "Estados Unidos", "United States", "Estados Unidos"),
        Country("UY", "Uruguai", "Uruguay", "Uruguay"),
        Country("VE", "Venezuela", "Venezuela", "Venezuela"),
        // Europa
        Country("DE", "Alemanha", "Germany", "Alemania"),
        Country("AT", "Áustria", "Austria", "Austria"),
        Country("BE", "Bélgica", "Belgium", "Bélgica"),
        Country("HR", "Croácia", "Croatia", "Croacia"),
        Country("DK", "Dinamarca", "Denmark", "Dinamarca"),
        Country("ES", "Espanha", "Spain", "España"),
        Country("FI", "Finlândia", "Finland", "Finlandia"),
        Country("FR", "França", "France", "Francia"),
        Country("GR", "Grécia", "Greece", "Grecia"),
        Country("NL", "Países Baixos", "Netherlands", "Países Bajos"),
        Country("HU", "Hungria", "Hungary", "Hungría"),
        Country("IE", "Irlanda", "Ireland", "Irlanda"),
        Country("IT", "Itália", "Italy", "Italia"),
        Country("NO", "Noruega", "Norway", "Noruega"),
        Country("PL", "Polônia", "Poland", "Polonia"),
        Country("PT", "Portugal", "Portugal", "Portugal"),
        Country("GB", "Reino Unido", "United Kingdom", "Reino Unido"),
        Country("CZ", "Tchéquia", "Czechia", "Chequia"),
        Country("RO", "Romênia", "Romania", "Rumania"),
        Country("RU", "Rússia", "Russia", "Rusia"),
        Country("RS", "Sérvia", "Serbia", "Serbia"),
        Country("SE", "Suécia", "Sweden", "Suecia"),
        Country("CH", "Suíça", "Switzerland", "Suiza"),
        Country("TR", "Turquia", "Türkiye", "Turquía"),
        Country("UA", "Ucrânia", "Ukraine", "Ucrania"),
        Country("VA", "Santa Sé", "Holy See", "Santa Sede"),
        // Ásia e Oceania
        Country("SA", "Arábia Saudita", "Saudi Arabia", "Arabia Saudita"),
        Country("AU", "Austrália", "Australia", "Australia"),
        Country("CN", "China", "China", "China"),
        Country("KR", "Coreia do Sul", "South Korea", "Corea del Sur"),
        Country("AE", "Emirados Árabes Unidos", "United Arab Emirates", "Emiratos Árabes Unidos"),
        Country("PH", "Filipinas", "Philippines", "Filipinas"),
        Country("IN", "Índia", "India", "India"),
        Country("ID", "Indonésia", "Indonesia", "Indonesia"),
        Country("IL", "Israel", "Israel", "Israel"),
        Country("JP", "Japão", "Japan", "Japón"),
        Country("MY", "Malásia", "Malaysia", "Malasia"),
        Country("NZ", "Nova Zelândia", "New Zealand", "Nueva Zelanda"),
        Country("PK", "Paquistão", "Pakistan", "Pakistán"),
        Country("PS", "Palestina", "Palestine", "Palestina"),
        Country("SG", "Singapura", "Singapore", "Singapur"),
        Country("TH", "Tailândia", "Thailand", "Tailandia"),
        Country("VN", "Vietnã", "Vietnam", "Vietnam"),
        // África
        Country("ZA", "África do Sul", "South Africa", "Sudáfrica"),
        Country("AO", "Angola", "Angola", "Angola"),
        Country("CV", "Cabo Verde", "Cabo Verde", "Cabo Verde"),
        Country("EG", "Egito", "Egypt", "Egipto"),
        Country("GH", "Gana", "Ghana", "Ghana"),
        Country("GW", "Guiné-Bissau", "Guinea-Bissau", "Guinea-Bisáu"),
        Country("KE", "Quênia", "Kenya", "Kenia"),
        Country("MA", "Marrocos", "Morocco", "Marruecos"),
        Country("MZ", "Moçambique", "Mozambique", "Mozambique"),
        Country("NG", "Nigéria", "Nigeria", "Nigeria"),
        Country("ST", "São Tomé e Príncipe", "Sao Tome and Principe", "Santo Tomé y Príncipe"),
        Country("SN", "Senegal", "Senegal", "Senegal"),
    )

    private val byCode: Map<String, Country> = all.associateBy { it.code }

    /** O país da lista com este código (maiúsculo, duas letras); fora da lista = nulo. */
    fun of(code: String?): Country? = code?.let { byCode[it] }

    fun isValid(code: String?): Boolean = of(code) != null

    /** Emoji de bandeira: cada letra vira o indicador regional correspondente (U+1F1E6 = "A"). */
    fun flagOf(code: String): String {
        if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
        val sb = StringBuilder()
        code.forEach { c ->
            val cp = 0x1F1E6 + (c - 'A')
            // par substituto UTF-16 (o emoji fica fora do plano básico)
            val v = cp - 0x10000
            sb.append((0xD800 + (v shr 10)).toChar())
            sb.append((0xDC00 + (v and 0x3FF)).toChar())
        }
        return sb.toString()
    }

    /** Lista na ordem alfabética do idioma (sem acento pesando), filtrada pela busca em nome ou código. */
    fun search(query: String, lang: Lang): List<Country> {
        val q = fold(query.trim())
        return all
            .filter { q.isEmpty() || fold(it.name(lang)).contains(q) || it.code.equals(q, ignoreCase = true) }
            .sortedBy { fold(it.name(lang)) }
    }

    /**
     * Sugestão pela região do aparelho (Android `Locale.country`, iPhone `NSLocale.regionCode`), nunca pelo IP. Região
     * fora da lista ou vazia = sem sugestão. A sugestão nunca é gravada sozinha: o jogador confirma.
     */
    fun suggestion(deviceRegion: String?): Country? = of(deviceRegion?.trim()?.uppercase())

    /**
     * Região do aparelho tirada da etiqueta de idioma (`pt-BR` → BR, `es-419` → nada, `zh-Hans-CN` → CN), a mesma
     * que o app já lê para o idioma (#131): sem código de plataforma novo e nunca pelo IP.
     */
    fun regionOf(languageTag: String?): String? =
        languageTag?.split('-', '_')?.drop(1)?.lastOrNull { it.length == 2 && it.all { c -> c.isLetter() } }?.uppercase()

    private fun fold(s: String): String = buildString {
        s.lowercase().forEach { c ->
            append(
                when (c) {
                    'á', 'à', 'â', 'ã', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    'ñ' -> 'n'
                    else -> c
                }
            )
        }
    }
}

/**
 * Escolha de nacionalidade guardada no aparelho (#124). [UNSET]: nunca escolheu (o Perfil mostra a sugestão);
 * [NONE]: "Prefiro não mostrar" (não sugere de novo); qualquer outro valor é um código da lista.
 */
object CountryChoice {
    const val UNSET = ""
    const val NONE = "-"

    /** O que vai para a nuvem: nulo = não mandar o campo (nunca apaga a bandeira da nuvem); "" = mandar nulo. */
    fun toCloud(local: String): String? = when {
        local == UNSET -> null
        local == NONE -> ""
        Countries.isValid(local) -> local
        else -> null
    }

    /** O que vem da nuvem: código válido substitui; nulo/vazio mantém a escolha do aparelho. */
    fun fromCloud(local: String, remote: String?): String = if (Countries.isValid(remote)) remote!! else local

    /** O código a mostrar (bandeira) ou nulo. */
    fun shown(local: String): String? = local.takeIf { Countries.isValid(it) }
}
