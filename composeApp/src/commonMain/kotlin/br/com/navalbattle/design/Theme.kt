package br.com.navalbattle.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val muted = Color(0xFF6B7760)

    val green = Color(0xFF5F8F4A)
    val greenBright = Color(0xFF8ED17A)
    val amber = Color(0xFFE6AC3F)
    val amberStrong = Color(0xFFFFC95C)
    val amberInk = Color(0xFF1E1402)
    val danger = Color(0xFFE05A35)
}

/**
 * Caixa alta com espaçamento largo imita o painel condensado do design system
 * sem depender de fonte externa — o APK fica leve e não precisa de rede.
 */
object NavalType {
    val display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = 0.5.sp
    )
    val title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        letterSpacing = 1.sp
    )
    val button = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 1.2.sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp
    )
    val monoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 1.sp
    )
    val timer = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = 2.sp
    )
}

@Composable
fun NavalTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme() // o jogo mantém um único mundo visual, noturno
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
            bodyMedium = NavalType.body,
            labelLarge = NavalType.button
        ),
        content = content
    )
}
