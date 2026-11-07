package br.com.navalbattle.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tokens de cor do Command HUD. O jogo é dark-first por identidade. */
object Naval {
    val bg = Color(0xFF080B07)
    val surface = Color(0xFF101509)
    val surface2 = Color(0xFF161C10)
    val surface3 = Color(0xFF1F2717)
    val line = Color(0xFF2B3520)
    val lineSoft = Color(0xFF1B2214)

    val abyss = Color(0xFF08120E)
    val abyss2 = Color(0xFF0C1A14)
    val gridLine = Color(0x298ED17A)

    val ink = Color(0xFFEFF2E4)
    val inkSoft = Color(0xFFB4C0A4)
    val muted = Color(0xFF939F88)

    val green = Color(0xFF5F8F4A)
    val greenBright = Color(0xFF8ED17A)
    val amber = Color(0xFFE6AC3F)
    val amberStrong = Color(0xFFFFC95C)
    val amberInk = Color(0xFF1E1402)
    val danger = Color(0xFFE05A35)

    /** Identidade de cada comandante no modo local — legível sobre o verde do radar. */
    val commanderOne = Color(0xFF48C9F0)
    val commanderTwo = Color(0xFF3ED598)

    /**
     * Largura máxima do conteúdo e dos popups em tela grande (tablet, dobrável, Chromebook): o
     * celular (< 600 dp) não muda. Só a batalha em paisagem passa disso (ver BoardLayout.kt).
     */
    val contentMaxWidth = 600.dp
}

/**
 * Caixa alta com espaçamento largo imita o painel condensado do design system. As famílias vêm
 * de [LocalNavalFonts]: Roboto e Roboto Mono embarcadas, iguais no Android e no iPhone (#80).
 */
object NavalType {
    private val displayBase = TextStyle(fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = 0.5.sp)
    private val titleBase = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = 1.sp)
    private val buttonBase = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.2.sp)
    private val bodyBase = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp)
    private val monoBase = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.8.sp)
    private val monoSmallBase = TextStyle(fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 1.sp)
    private val timerBase = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 2.sp)

    val display: TextStyle @Composable @ReadOnlyComposable get() = displayBase.copy(fontFamily = LocalNavalFonts.current.sans)
    val title: TextStyle @Composable @ReadOnlyComposable get() = titleBase.copy(fontFamily = LocalNavalFonts.current.sans)
    val button: TextStyle @Composable @ReadOnlyComposable get() = buttonBase.copy(fontFamily = LocalNavalFonts.current.sans)
    val body: TextStyle @Composable @ReadOnlyComposable get() = bodyBase.copy(fontFamily = LocalNavalFonts.current.sans)
    val mono: TextStyle @Composable @ReadOnlyComposable get() = monoBase.copy(fontFamily = LocalNavalFonts.current.mono)
    val monoSmall: TextStyle @Composable @ReadOnlyComposable get() = monoSmallBase.copy(fontFamily = LocalNavalFonts.current.mono)
    val timer: TextStyle @Composable @ReadOnlyComposable get() = timerBase.copy(fontFamily = LocalNavalFonts.current.mono)
}

/** O jogo mantém um único mundo visual, noturno — não segue o tema do sistema. */
@Composable
fun NavalTheme(content: @Composable () -> Unit) {
    val fonts = rememberNavalFonts()
    CompositionLocalProvider(LocalNavalFonts provides fonts) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Naval.amber,
                onPrimary = Naval.amberInk,
                secondary = Naval.greenBright,
                background = Naval.bg,
                onBackground = Naval.ink,
                surface = Naval.surface,
                onSurface = Naval.ink,
                error = Naval.danger
            ),
            typography = Typography(
                bodyLarge = NavalType.body,
                bodyMedium = NavalType.body,
                labelLarge = NavalType.button
            ),
            content = content
        )
    }
}
