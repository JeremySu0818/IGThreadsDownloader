package com.jeremysu0818.igthreadsdownloader.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Dark Mode Palette (Background: #20201E)
val DarkMatteBg = Color(0xFF20201E)
val DarkMatteCard = Color(0xFF2A2A27)
val DarkMatteCardBorder = Color(0xFF383834)
val DarkMatteCardHover = Color(0xFF353531)

// Light Mode Palette (Background: #F9F9F7)
val LightMatteBg = Color(0xFFF9F9F7)
val LightMatteCard = Color(0xFFFFFFFF)
val LightMatteCardBorder = Color(0xFFE5E7EB)
val LightMatteCardHover = Color(0xFFEFEFEA)

// Accent Colors
val DefaultMattePrimary = Color(0xFFB86646)
val DefaultMatteEmerald = Color(0xFF396AB8)
val DefaultMatteAmber = Color(0xFFF59E0B)
val DefaultMatteRose = Color(0xFFEF4444)
val DefaultMatteRoseDark = Color(0xFF3D161A)
val DefaultMatteRoseLight = Color(0xFFFCA5A5)

val DarkTextPrimary = Color(0xFFF3F4F6)
val DarkTextSecondary = Color(0xFF9CA3AF)
val DarkTextMuted = Color(0xFF6B7280)

val LightTextPrimary = Color(0xFF1F2937)
val LightTextSecondary = Color(0xFF4B5563)
val LightTextMuted = Color(0xFF9CA3AF)

// View / Overlay ARGB Int Values
val MatteBgInt = DarkMatteBg.toArgb()
val MatteCardInt = DarkMatteCard.toArgb()
val MatteCardBorderInt = DarkMatteCardBorder.toArgb()
val MatteCardHoverInt = DarkMatteCardHover.toArgb()

val MattePrimaryInt = DefaultMattePrimary.toArgb()
val MatteEmeraldInt = DefaultMatteEmerald.toArgb()
val MatteAmberInt = DefaultMatteAmber.toArgb()
val MatteRoseInt = DefaultMatteRose.toArgb()
val MatteRoseDarkInt = DefaultMatteRoseDark.toArgb()
val MatteRoseLightInt = DefaultMatteRoseLight.toArgb()

val MatteTextPrimaryInt = DarkTextPrimary.toArgb()
val MatteTextSecondaryInt = DarkTextSecondary.toArgb()
val MatteTextMutedInt = DarkTextMuted.toArgb()
