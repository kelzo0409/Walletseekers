package com.hackerai.walletseeker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E676),
    onPrimary = Color(0xFF003300),
    primaryContainer = Color(0xFF003300),
    onPrimaryContainer = Color(0xFF00E676),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color(0xFF00332E),
    secondaryContainer = Color(0xFF00332E),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color(0xFF331A00),
    background = Color(0xFF0F0F23),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A3E),
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFFF1744),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF330000),
    outline = Color(0xFF444455),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF1A1A2E)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B3F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF00E676),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F)
)

@Composable
fun WalletSeekerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}