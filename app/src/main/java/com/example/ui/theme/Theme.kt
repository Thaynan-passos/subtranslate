package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SubTranslateDarkColorScheme = darkColorScheme(
    primary = PrimaryViolet,
    onPrimary = OnPrimaryViolet,
    primaryContainer = PrimaryContainerViolet,
    onPrimaryContainer = OnPrimaryContainerViolet,
    secondary = SecondaryBlue,
    onSecondary = OnSecondaryBlue,
    secondaryContainer = SecondaryContainerBlue,
    onSecondaryContainer = OnSecondaryContainerBlue,
    tertiary = TertiaryIndigo,
    onTertiary = OnTertiaryIndigo,
    tertiaryContainer = TertiaryContainerIndigo,
    onTertiaryContainer = OnTertiaryContainerIndigo,
    background = SurfaceCanvas,
    onBackground = OnSurfaceText,
    surface = SurfaceCanvas,
    onSurface = OnSurfaceText,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariantText,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = OutlineBorder,
    outlineVariant = OutlineVariantBorder,
    error = ErrorCrimson,
    onError = OnPrimaryViolet,
    errorContainer = ErrorContainerCrimson,
    onErrorContainer = OnErrorContainerCrimson
)

private val SubTranslateLightColorScheme = lightColorScheme(
    primary = PrimaryViolet,
    onPrimary = OnPrimaryViolet,
    primaryContainer = PrimaryContainerViolet,
    onPrimaryContainer = OnPrimaryContainerViolet,
    secondary = SecondaryBlue,
    onSecondary = OnSecondaryBlue,
    secondaryContainer = SecondaryContainerBlue,
    onSecondaryContainer = OnSecondaryContainerBlue,
    tertiary = TertiaryIndigo,
    onTertiary = OnTertiaryIndigo,
    background = SurfaceCanvas,
    onBackground = OnSurfaceText,
    surface = SurfaceContainer,
    onSurface = OnSurfaceText,
    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = OnSurfaceVariantText,
    outline = OutlineBorder,
    outlineVariant = OutlineVariantBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to SubTranslate M3 Dark OLED theme
    dynamicColor: Boolean = false, // Keep signature Gemini / Video Dark theme consistent
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SubTranslateDarkColorScheme
        else -> SubTranslateDarkColorScheme // Ensure signature dark experience
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
