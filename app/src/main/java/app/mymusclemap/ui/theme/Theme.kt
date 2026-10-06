package app.mymusclemap.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.ColorSchemeFactory
import app.mymusclemap.domain.theme.ThemeSeeds

@Composable
fun WeightTrackerTheme(
    appearance: AppearanceSettings = AppearanceSettings.Default,
    content: @Composable () -> Unit
) {
    val darkTheme = appearance.mode.isDark(isSystemInDarkTheme())
    val seeds = appearance.activeSeeds(darkTheme)
    val colorScheme = remember(seeds, darkTheme) {
        ColorSchemeFactory.derive(seeds, darkTheme).toComposeColorScheme(darkTheme)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun WeightTrackerThemeForPreview(
    seeds: ThemeSeeds = ThemeSeeds.DefaultLight,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(seeds, darkTheme) {
        ColorSchemeFactory.derive(seeds, darkTheme).toComposeColorScheme(darkTheme)
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

/**
 * Light sheets sit on the near-white working surface. Dark sheets keep the
 * approved canvas so the dark theme does not shift.
 */
@Composable
fun sheetContainerColor(): Color {
    return MaterialTheme.colorScheme.sheetContainerColor()
}

fun ColorScheme.sheetContainerColor(): Color {
    return if (background.luminance() < 0.5f) background else surface
}
