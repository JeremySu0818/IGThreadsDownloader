package com.jeremysu0818.igthreadsdownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = MattePrimary,
    secondary = MatteEmerald,
    tertiary = MattePrimary,
    background = MatteBg,
    surface = MatteCard,
    onPrimary = MatteTextPrimary,
    onSecondary = MatteTextPrimary,
    onBackground = MatteTextPrimary,
    onSurface = MatteTextPrimary,
    surfaceContainer = MatteCardHover,
)

@Composable
fun IGThreadsDownloaderTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
