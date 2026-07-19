package com.jeremysu0818.igthreadsdownloader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = AppPrimary,
    secondary = AppPrimary,
    tertiary = AppPrimary,
    background = AppBackground,
    surface = AppSurface,
    onPrimary = AppText,
    onSecondary = AppText,
    onBackground = AppText,
    onSurface = AppText,
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
