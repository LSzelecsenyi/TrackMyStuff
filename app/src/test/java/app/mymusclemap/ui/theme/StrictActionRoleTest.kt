package app.mymusclemap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.domain.theme.ColorSchemeFactory
import app.mymusclemap.domain.theme.StrictBrandTokens
import app.mymusclemap.domain.theme.ThemeSeeds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StrictActionRoleTest {
    @Test
    fun composedDefaultThemesKeepLimeWorkoutActionAndAsymmetricResults() {
        val dark = ColorSchemeFactory.derive(ThemeSeeds.DefaultDark, isDark = true).toComposeColorScheme(isDark = true)
        assertEquals(StrictBrandTokens.LIME, action(dark))
        assertEquals(StrictBrandTokens.DARK, onAction(dark))
        assertEquals(StrictBrandTokens.LIME, result(dark))

        val light = ColorSchemeFactory.derive(ThemeSeeds.DefaultLight, isDark = false).toComposeColorScheme(isDark = false)
        assertEquals(StrictBrandTokens.LIME, action(light))
        assertEquals(StrictBrandTokens.DARK, onAction(light))
        assertEquals(StrictBrandTokens.BLUE, result(light))
        assertNotEquals(StrictBrandTokens.LIME, result(light))
    }

    @Test
    fun composedCustomThemesDoNotForceStrictLime() {
        val customDark = ColorSchemeFactory.derive(
            ThemeSeeds.DefaultDark.copy(primary = 0xFF8FB2FF.toInt()),
            isDark = true
        ).toComposeColorScheme(isDark = true)
        assertEquals(0xFF8FB2FF.toInt(), action(customDark))
        assertEquals(0xFF8FB2FF.toInt(), result(customDark))
        assertNotEquals(StrictBrandTokens.LIME, result(customDark))
    }

    private fun action(scheme: ColorScheme): Int {
        return StrictBrandTokens.actionContainer(scheme.background.toArgb(), scheme.primary.toArgb())
    }

    private fun onAction(scheme: ColorScheme): Int {
        return StrictBrandTokens.onAction(scheme.background.toArgb(), scheme.primary.toArgb())
    }

    private fun result(scheme: ColorScheme): Int {
        return StrictBrandTokens.result(scheme.background.toArgb(), scheme.primary.toArgb())
    }
}
