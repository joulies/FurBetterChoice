package com.example.furbetterchoice.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkPrimary,
    onSecondary = DarkOnPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainerLowest = DarkSurface,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = AccentMediumGreen,
    secondary = AccentLightGreen,
    tertiary = TextLightGreen,
    background = Color(0xFFE4EAE8),
    surface = Color(0xFFE4EAE8),
    surfaceContainer = Color(0xFFE4EAE8),
    surfaceContainerHigh = Color(0xFFE4EAE8),
    surfaceContainerHighest = Color(0xFFE4EAE8),
    surfaceContainerLow = Color(0xFFE4EAE8),
    surfaceContainerLowest = Color(0xFFE4EAE8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextDarkGreen,
    onSurface = TextDarkGreen,
    surfaceVariant = Color(0xFFE4EAE8),
    onSurfaceVariant = GrayMedium,
    outline = GrayLight
)

@Composable
fun FurBetterChoiceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    // Animate colors for smooth theme transitions
    val animatedColorScheme = colorScheme.copy(
        primary = animateColorAsState(colorScheme.primary, tween(300), label = "primary").value,
        onPrimary = animateColorAsState(colorScheme.onPrimary, tween(300), label = "onPrimary").value,
        primaryContainer = animateColorAsState(colorScheme.primaryContainer, tween(300), label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(colorScheme.onPrimaryContainer, tween(300), label = "onPrimaryContainer").value,
        inversePrimary = animateColorAsState(colorScheme.inversePrimary, tween(300), label = "inversePrimary").value,
        secondary = animateColorAsState(colorScheme.secondary, tween(300), label = "secondary").value,
        onSecondary = animateColorAsState(colorScheme.onSecondary, tween(300), label = "onSecondary").value,
        secondaryContainer = animateColorAsState(colorScheme.secondaryContainer, tween(300), label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(colorScheme.onSecondaryContainer, tween(300), label = "onSecondaryContainer").value,
        tertiary = animateColorAsState(colorScheme.tertiary, tween(300), label = "tertiary").value,
        onTertiary = animateColorAsState(colorScheme.onTertiary, tween(300), label = "onTertiary").value,
        tertiaryContainer = animateColorAsState(colorScheme.tertiaryContainer, tween(300), label = "tertiaryContainer").value,
        onTertiaryContainer = animateColorAsState(colorScheme.onTertiaryContainer, tween(300), label = "onTertiaryContainer").value,
        background = animateColorAsState(colorScheme.background, tween(300), label = "background").value,
        onBackground = animateColorAsState(colorScheme.onBackground, tween(300), label = "onBackground").value,
        surface = animateColorAsState(colorScheme.surface, tween(300), label = "surface").value,
        onSurface = animateColorAsState(colorScheme.onSurface, tween(300), label = "onSurface").value,
        surfaceVariant = animateColorAsState(colorScheme.surfaceVariant, tween(300), label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(colorScheme.onSurfaceVariant, tween(300), label = "onSurfaceVariant").value,
        surfaceTint = animateColorAsState(colorScheme.surfaceTint, tween(300), label = "surfaceTint").value,
        inverseSurface = animateColorAsState(colorScheme.inverseSurface, tween(300), label = "inverseSurface").value,
        inverseOnSurface = animateColorAsState(colorScheme.inverseOnSurface, tween(300), label = "inverseOnSurface").value,
        error = animateColorAsState(colorScheme.error, tween(300), label = "error").value,
        onError = animateColorAsState(colorScheme.onError, tween(300), label = "onError").value,
        errorContainer = animateColorAsState(colorScheme.errorContainer, tween(300), label = "errorContainer").value,
        onErrorContainer = animateColorAsState(colorScheme.onErrorContainer, tween(300), label = "onErrorContainer").value,
        outline = animateColorAsState(colorScheme.outline, tween(300), label = "outline").value,
        outlineVariant = animateColorAsState(colorScheme.outlineVariant, tween(300), label = "outlineVariant").value,
        scrim = animateColorAsState(colorScheme.scrim, tween(300), label = "scrim").value,
        surfaceContainer = animateColorAsState(colorScheme.surfaceContainer, tween(300), label = "surfaceContainer").value,
        surfaceContainerHigh = animateColorAsState(colorScheme.surfaceContainerHigh, tween(300), label = "surfaceContainerHigh").value,
        surfaceContainerHighest = animateColorAsState(colorScheme.surfaceContainerHighest, tween(300), label = "surfaceContainerHighest").value,
        surfaceContainerLow = animateColorAsState(colorScheme.surfaceContainerLow, tween(300), label = "surfaceContainerLow").value,
        surfaceContainerLowest = animateColorAsState(colorScheme.surfaceContainerLowest, tween(300), label = "surfaceContainerLowest").value
    )

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}
