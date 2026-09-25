package app.mymusclemap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.mymusclemap.domain.theme.DerivedColorScheme

fun DerivedColorScheme.toComposeColorScheme(isDark: Boolean): ColorScheme {
    fun color(rgb: Int) = Color(rgb)
    return if (isDark) {
        darkColorScheme(
            primary = color(primary),
            onPrimary = color(onPrimary),
            primaryContainer = color(primaryContainer),
            onPrimaryContainer = color(onPrimaryContainer),
            secondary = color(secondary),
            onSecondary = color(onSecondary),
            secondaryContainer = color(secondaryContainer),
            onSecondaryContainer = color(onSecondaryContainer),
            tertiary = color(tertiary),
            onTertiary = color(onTertiary),
            tertiaryContainer = color(tertiaryContainer),
            onTertiaryContainer = color(onTertiaryContainer),
            background = color(background),
            onBackground = color(onBackground),
            surface = color(surface),
            onSurface = color(onSurface),
            surfaceVariant = color(surfaceVariant),
            onSurfaceVariant = color(onSurfaceVariant),
            surfaceContainer = color(surfaceContainer),
            surfaceContainerHigh = color(surfaceContainerHigh),
            outline = color(outline),
            outlineVariant = color(outlineVariant),
            error = color(error),
            onError = color(onError),
            errorContainer = color(errorContainer),
            onErrorContainer = color(onErrorContainer),
            inverseSurface = color(onSurface),
            inverseOnSurface = color(surface),
            inversePrimary = color(primaryContainer),
            scrim = Color.Black,
            surfaceTint = color(primary)
        )
    } else {
        lightColorScheme(
            primary = color(primary),
            onPrimary = color(onPrimary),
            primaryContainer = color(primaryContainer),
            onPrimaryContainer = color(onPrimaryContainer),
            secondary = color(secondary),
            onSecondary = color(onSecondary),
            secondaryContainer = color(secondaryContainer),
            onSecondaryContainer = color(onSecondaryContainer),
            tertiary = color(tertiary),
            onTertiary = color(onTertiary),
            tertiaryContainer = color(tertiaryContainer),
            onTertiaryContainer = color(onTertiaryContainer),
            background = color(background),
            onBackground = color(onBackground),
            surface = color(surface),
            onSurface = color(onSurface),
            surfaceVariant = color(surfaceVariant),
            onSurfaceVariant = color(onSurfaceVariant),
            surfaceContainer = color(surfaceContainer),
            surfaceContainerHigh = color(surfaceContainerHigh),
            outline = color(outline),
            outlineVariant = color(outlineVariant),
            error = color(error),
            onError = color(onError),
            errorContainer = color(errorContainer),
            onErrorContainer = color(onErrorContainer),
            inverseSurface = color(onSurface),
            inverseOnSurface = color(surface),
            inversePrimary = color(primaryContainer),
            scrim = Color.Black,
            surfaceTint = color(primary)
        )
    }
}
