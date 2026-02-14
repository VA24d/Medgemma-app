package com.google.mediapipe.examples.llminference.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════
// App Theme Enum
// ═══════════════════════════════════════
enum class AppTheme(val displayName: String) {
    WHITE("Light"),
    BLACK("Dark"),
    PURPLISH_BLUE("MedGemma Purple")
}

// ═══════════════════════════════════════
// Color Schemes
// ═══════════════════════════════════════
private val WhiteColorScheme = lightColorScheme(
    primary = WhitePrimary,
    onPrimary = WhiteOnPrimary,
    primaryContainer = WhitePrimaryContainer,
    onPrimaryContainer = WhiteOnPrimaryContainer,
    secondary = WhiteSecondary,
    onSecondary = WhiteOnSecondary,
    secondaryContainer = WhiteSecondaryContainer,
    onSecondaryContainer = WhiteOnSecondaryContainer,
    tertiary = WhiteTertiary,
    onTertiary = WhiteOnTertiary,
    tertiaryContainer = WhiteTertiaryContainer,
    onTertiaryContainer = WhiteOnTertiaryContainer,
    background = WhiteBackground,
    onBackground = WhiteOnBackground,
    surface = WhiteSurface,
    onSurface = WhiteOnSurface,
    surfaceVariant = WhiteSurfaceVariant,
    onSurfaceVariant = WhiteOnSurfaceVariant,
    error = WhiteError,
    onError = WhiteOnError,
    outline = WhiteOutline
)

private val BlackColorScheme = darkColorScheme(
    primary = BlackPrimary,
    onPrimary = BlackOnPrimary,
    primaryContainer = BlackPrimaryContainer,
    onPrimaryContainer = BlackOnPrimaryContainer,
    secondary = BlackSecondary,
    onSecondary = BlackOnSecondary,
    secondaryContainer = BlackSecondaryContainer,
    onSecondaryContainer = BlackOnSecondaryContainer,
    tertiary = BlackTertiary,
    onTertiary = BlackOnTertiary,
    tertiaryContainer = BlackTertiaryContainer,
    onTertiaryContainer = BlackOnTertiaryContainer,
    background = BlackBackground,
    onBackground = BlackOnBackground,
    surface = BlackSurface,
    onSurface = BlackOnSurface,
    surfaceVariant = BlackSurfaceVariant,
    onSurfaceVariant = BlackOnSurfaceVariant,
    error = BlackError,
    onError = BlackOnError,
    outline = BlackOutline
)

private val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurplePrimaryContainer,
    onPrimaryContainer = PurpleOnPrimaryContainer,
    secondary = PurpleSecondary,
    onSecondary = PurpleOnSecondary,
    secondaryContainer = PurpleSecondaryContainer,
    onSecondaryContainer = PurpleOnSecondaryContainer,
    tertiary = PurpleTertiary,
    onTertiary = PurpleOnTertiary,
    tertiaryContainer = PurpleTertiaryContainer,
    onTertiaryContainer = PurpleOnTertiaryContainer,
    background = PurpleBackground,
    onBackground = PurpleOnBackground,
    surface = PurpleSurface,
    onSurface = PurpleOnSurface,
    surfaceVariant = PurpleSurfaceVariant,
    onSurfaceVariant = PurpleOnSurfaceVariant,
    error = PurpleError,
    onError = PurpleOnError,
    outline = PurpleOutline
)

private val PurpleDarkColorScheme = darkColorScheme(
    primary = PurpleDarkPrimary,
    onPrimary = PurpleDarkOnPrimary,
    primaryContainer = PurpleDarkPrimaryContainer,
    onPrimaryContainer = PurpleDarkOnPrimaryContainer,
    secondary = PurpleDarkSecondary,
    onSecondary = PurpleDarkOnSecondary,
    secondaryContainer = PurpleDarkSecondaryContainer,
    onSecondaryContainer = PurpleDarkOnSecondaryContainer,
    tertiary = PurpleDarkTertiary,
    onTertiary = PurpleDarkOnTertiary,
    background = PurpleDarkBackground,
    onBackground = PurpleDarkOnBackground,
    surface = PurpleDarkSurface,
    onSurface = PurpleDarkOnSurface,
    surfaceVariant = PurpleDarkSurfaceVariant,
    onSurfaceVariant = PurpleDarkOnSurfaceVariant,
    error = PurpleDarkError,
    onError = PurpleDarkOnError,
    outline = PurpleDarkOutline
)

// ═══════════════════════════════════════
// Global Theme State
// ═══════════════════════════════════════
object ThemeManager {
    var currentTheme by mutableStateOf(AppTheme.PURPLISH_BLUE)
}

// ═══════════════════════════════════════
// Theme Composable
// ═══════════════════════════════════════
@Composable
fun LLMInferenceTheme(
    appTheme: AppTheme = ThemeManager.currentTheme,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.WHITE -> WhiteColorScheme
        AppTheme.BLACK -> BlackColorScheme
        AppTheme.PURPLISH_BLUE -> PurpleLightColorScheme
    }

    val isDark = appTheme == AppTheme.BLACK

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
