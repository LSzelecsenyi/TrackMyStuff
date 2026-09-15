package hu.laca.weighttracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import hu.laca.weighttracker.domain.theme.AppearanceSettings
import hu.laca.weighttracker.domain.theme.ColorSchemeFactory
import hu.laca.weighttracker.domain.theme.ThemeMode
import hu.laca.weighttracker.domain.theme.ThemeSeeds

@Composable
fun WeightTrackerTheme(
    appearance: AppearanceSettings = AppearanceSettings.Default,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearance.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
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
