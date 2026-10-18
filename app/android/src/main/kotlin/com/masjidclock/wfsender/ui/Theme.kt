package com.masjidclock.wfsender.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A fixed Material 3 color scheme from a deep green seed. All roles are set, so no default
// (purple) role colors show on cards or bars.
private val LightColors = lightColorScheme(
    primary = Color(0xFF386A20), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB8F397), onPrimaryContainer = Color(0xFF042100),
    secondary = Color(0xFF55624C), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB), onSecondaryContainer = Color(0xFF131F0D),
    tertiary = Color(0xFF386667), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBBEBEC), onTertiaryContainer = Color(0xFF002020),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFAF2), onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFFAFAF2), onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4D6), onSurfaceVariant = Color(0xFF43483E),
    surfaceTint = Color(0xFF386A20),
    inverseSurface = Color(0xFF2F312C), inverseOnSurface = Color(0xFFF1F1EA), inversePrimary = Color(0xFF9DD67D),
    outline = Color(0xFF74796D), outlineVariant = Color(0xFFC4C8BA), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAF2), surfaceDim = Color(0xFFDADAD2),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F4EC),
    surfaceContainer = Color(0xFFEEEFE6), surfaceContainerHigh = Color(0xFFE9E9E1),
    surfaceContainerHighest = Color(0xFFE3E3DB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD67D), onPrimary = Color(0xFF0B3900),
    primaryContainer = Color(0xFF205107), onPrimaryContainer = Color(0xFFB8F397),
    secondary = Color(0xFFBDCBB0), onSecondary = Color(0xFF283420),
    secondaryContainer = Color(0xFF3E4A35), onSecondaryContainer = Color(0xFFD9E7CB),
    tertiary = Color(0xFFA0CFD0), onTertiary = Color(0xFF003738),
    tertiaryContainer = Color(0xFF1E4E4E), onTertiaryContainer = Color(0xFFBBEBEC),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF12140F), onBackground = Color(0xFFE3E3DC),
    surface = Color(0xFF12140F), onSurface = Color(0xFFE3E3DC),
    surfaceVariant = Color(0xFF43483E), onSurfaceVariant = Color(0xFFC4C8BA),
    surfaceTint = Color(0xFF9DD67D),
    inverseSurface = Color(0xFFE3E3DC), inverseOnSurface = Color(0xFF2F312C), inversePrimary = Color(0xFF386A20),
    outline = Color(0xFF8E9286), outlineVariant = Color(0xFF43483E), scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A34), surfaceDim = Color(0xFF12140F),
    surfaceContainerLowest = Color(0xFF0D0F0A), surfaceContainerLow = Color(0xFF1A1C17),
    surfaceContainer = Color(0xFF1E201B), surfaceContainerHigh = Color(0xFF292B25),
    surfaceContainerHighest = Color(0xFF33362F),
)

/** The HD Sender theme. Light or dark follows the phone setting. */
@Composable
fun HdSenderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
