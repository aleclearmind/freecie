package org.freecie.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.freecie.app.R

// Tokens from "FreeCIE Mockups" (Material 3, seed #0B5394).

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5394),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD4E3FF),
    onPrimaryContainer = Color(0xFF001C3A),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainer = Color(0xFFECEEF4),
    surfaceContainerHigh = Color(0xFFE6E8EE),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5C8FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF0F4A86),
    onPrimaryContainer = Color(0xFFD4E3FF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E8),
    onSurfaceVariant = Color(0xFFC3C6CF),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Colours the mockups use that Material 3 has no role for. */
@Immutable
data class ExtraColors(
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningIcon: Color,
    val progressTrack: Color,
    val codeInk: Color,
)

private val LightExtra = ExtraColors(
    warningContainer = Color(0xFFFFDDB8),
    onWarningContainer = Color(0xFF2A1700),
    warningIcon = Color(0xFF7C5800),
    progressTrack = Color(0xFFA5C8FF),
    codeInk = Color(0xFF0F1114),
)

private val DarkExtra = ExtraColors(
    warningContainer = Color(0xFF5E4200),
    onWarningContainer = Color(0xFFFFDDB8),
    warningIcon = Color(0xFFFFBA4A),
    progressTrack = Color(0xFF3A6EA8),
    codeInk = Color(0xFFFFFFFF),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

val RobotoMono = FontFamily(Font(R.font.roboto_mono_medium, FontWeight.Medium))

@Composable
fun FreeCieTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}
