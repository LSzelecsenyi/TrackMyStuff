package app.mymusclemap.ui.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import app.mymusclemap.domain.theme.ColorSchemeFactory
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.components.musclemap.MuscleMapColors
import app.mymusclemap.ui.theme.toComposeColorScheme

data class HeatmapWidgetChrome(
    val bitmap: MuscleMapBitmapChrome,
    val isDark: Boolean
)

object HeatmapWidgetChromeFactory {
    fun fromContext(context: Context): HeatmapWidgetChrome {
        return forSystemDark(isSystemDark(context))
    }

    fun forSystemDark(isDark: Boolean): HeatmapWidgetChrome {
        val seeds = if (isDark) ThemeSeeds.DefaultDark else ThemeSeeds.DefaultLight
        val scheme = ColorSchemeFactory.derive(seeds, isDark).toComposeColorScheme(isDark)
        // Mid-tone head/face fills. Theme surfaces can be nearly white or black, which
        // disappear on a transparent widget. These stay readable on either wallpaper.
        val unmapped = if (isDark) Color(0xFFD5DDE4) else Color(0xFF7E8B99)
        return HeatmapWidgetChrome(
            bitmap = MuscleMapBitmapChrome(
                unmappedFill = unmapped,
                outline = unmapped,
                mappedOutline = MuscleMapColors.mappedOutline(scheme)
            ),
            isDark = isDark
        )
    }

    fun isSystemDark(context: Context): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }
}
