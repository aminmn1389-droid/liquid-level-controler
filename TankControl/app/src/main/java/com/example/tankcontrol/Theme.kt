package com.example.tankcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// "Classic gray" tech dashboard palette -- everything here is chosen to contrast
// cleanly against the dark gray background + faint circuit-board overlay.
val PageBackground = Color(0xFF181B1E)
val SurfaceDark = Color(0xFF2E3338)
val SurfaceDarkAlt = Color(0xFF383F45)
val BorderGray = Color(0xFF565D64)
val TextPrimary = Color(0xFFEFF2F4)
val TextSecondary = Color(0xFFAEB6BD)

val AccentBlue = Color(0xFF4DA3FF)
val WaterBlue = Color(0xFF2E8FE0)
val PumpGreen = Color(0xFF2ECC71)
val PumpRed = Color(0xFFE0453D)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    background = PageBackground,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = Color.White
)

@Composable
fun TankControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
