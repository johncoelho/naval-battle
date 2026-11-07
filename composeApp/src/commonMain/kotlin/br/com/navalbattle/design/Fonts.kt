package br.com.navalbattle.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import br.com.navalbattle.generated.resources.Res
import br.com.navalbattle.generated.resources.roboto_black
import br.com.navalbattle.generated.resources.roboto_bold
import br.com.navalbattle.generated.resources.roboto_extrabold
import br.com.navalbattle.generated.resources.roboto_regular
import br.com.navalbattle.generated.resources.robotomono_bold
import br.com.navalbattle.generated.resources.robotomono_medium
import br.com.navalbattle.generated.resources.robotomono_regular

/** Famílias de texto do design system: Roboto (sans) e Roboto Mono (mono), embarcadas. */
class NavalFonts(val sans: FontFamily, val mono: FontFamily)

/**
 * O padrão (famílias do sistema) é só reserva, para preview e teste: o [NavalTheme] sempre
 * fornece as fontes embarcadas.
 */
val LocalNavalFonts = staticCompositionLocalOf { NavalFonts(FontFamily.SansSerif, FontFamily.Monospace) }

/**
 * Roboto 400/700/800/900 e Roboto Mono 400/500/700, TTF estáticos em `composeResources/font`
 * (licenças em `files/licenses`). Iguais no Android e no iPhone: antes cada plataforma resolvia
 * `SansSerif`/`Monospace` para uma fonte de métricas diferentes e o HUD saía "achatado" no iPhone.
 *
 * Subset Latin (acentos, pontuação, símbolos usados no app). Setas (U+2190 a U+21FF, como o → de
 * "120 → 140" e o ⇄) **não existem** em Roboto nem em Roboto Mono, nem na fonte de origem: o Compose
 * cai na fonte do sistema só para esse glifo (igual nas duas plataformas, sem ficar em branco).
 */
@Composable
fun rememberNavalFonts(): NavalFonts {
    val sans = FontFamily(
        Font(Res.font.roboto_regular, FontWeight.Normal),
        Font(Res.font.roboto_bold, FontWeight.Bold),
        Font(Res.font.roboto_extrabold, FontWeight.ExtraBold),
        Font(Res.font.roboto_black, FontWeight.Black)
    )
    val mono = FontFamily(
        Font(Res.font.robotomono_regular, FontWeight.Normal),
        Font(Res.font.robotomono_medium, FontWeight.Medium),
        Font(Res.font.robotomono_bold, FontWeight.Bold)
    )
    return remember(sans, mono) { NavalFonts(sans, mono) }
}
