package com.jeremysu0818.igthreadsdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppColors(
    val bg: Color,
    val card: Color,
    val cardBorder: Color,
    val cardHover: Color,
    val primary: Color,
    val emerald: Color,
    val amber: Color,
    val rose: Color,
    val roseDark: Color,
    val roseLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean,
)

val DarkColors = AppColors(
    bg = DarkMatteBg,
    card = DarkMatteCard,
    cardBorder = DarkMatteCardBorder,
    cardHover = DarkMatteCardHover,
    primary = DefaultMattePrimary,
    emerald = DefaultMatteEmerald,
    amber = DefaultMatteAmber,
    rose = DefaultMatteRose,
    roseDark = DefaultMatteRoseDark,
    roseLight = DefaultMatteRoseLight,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    isDark = true,
)

val LightColors = AppColors(
    bg = LightMatteBg,
    card = LightMatteCard,
    cardBorder = LightMatteCardBorder,
    cardHover = LightMatteCardHover,
    primary = Color(0xFFB86646),
    emerald = Color(0xFF396AB8),
    amber = Color(0xFFD97706),
    rose = Color(0xFFDC2626),
    roseDark = Color(0xFFFEE2E2),
    roseLight = Color(0xFF991B1B),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { DarkColors }

val MatteBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.bg

val MatteCard: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.card

val MatteCardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.cardBorder

val MatteCardHover: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.cardHover

val MattePrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.primary

val MatteEmerald: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.emerald

val MatteAmber: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.amber

val MatteRose: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.rose

val MatteRoseDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.roseDark

val MatteRoseLight: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.roseLight

val MatteTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textPrimary

val MatteTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textSecondary

val MatteTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textMuted

@Composable
fun IGThreadsDownloaderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val appColors = if (isDark) DarkColors else LightColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = appColors.primary,
            secondary = appColors.emerald,
            tertiary = appColors.primary,
            background = appColors.bg,
            surface = appColors.card,
            onPrimary = appColors.textPrimary,
            onSecondary = appColors.textPrimary,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
            surfaceContainer = appColors.cardHover,
        )
    } else {
        lightColorScheme(
            primary = appColors.primary,
            secondary = appColors.emerald,
            tertiary = appColors.primary,
            background = appColors.bg,
            surface = appColors.card,
            onPrimary = appColors.textPrimary,
            onSecondary = appColors.textPrimary,
            onBackground = appColors.textPrimary,
            onSurface = appColors.textPrimary,
            surfaceContainer = appColors.cardHover,
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
