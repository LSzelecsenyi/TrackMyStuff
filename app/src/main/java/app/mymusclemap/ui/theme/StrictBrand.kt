package app.mymusclemap.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.mymusclemap.domain.theme.StrictBrandTokens

/**
 * Compose view of [StrictBrandTokens]. Material primary stays the readable ink.
 * Use [actionButtonColors] where a filled Strict action should be a lime field.
 */
object StrictBrand {
    val blue = Color(StrictBrandTokens.BLUE)
    val lime = Color(StrictBrandTokens.LIME)
    val dark = Color(StrictBrandTokens.DARK)
    val light = Color(StrictBrandTokens.LIGHT)

    fun ink(darkTheme: Boolean): Color = Color(StrictBrandTokens.ink(darkTheme))

    fun field(darkTheme: Boolean): Color = Color(StrictBrandTokens.field(darkTheme))

    fun onField(darkTheme: Boolean): Color = Color(StrictBrandTokens.onField(darkTheme))

    @Composable
    fun actionContainer(): Color {
        val scheme = MaterialTheme.colorScheme
        return Color(StrictBrandTokens.actionContainer(scheme.background.toArgb(), scheme.primary.toArgb()))
    }

    @Composable
    fun onAction(): Color {
        val scheme = MaterialTheme.colorScheme
        return Color(StrictBrandTokens.onAction(scheme.background.toArgb(), scheme.primary.toArgb()))
    }

    @Composable
    fun result(): Color {
        val scheme = MaterialTheme.colorScheme
        return Color(StrictBrandTokens.result(scheme.background.toArgb(), scheme.primary.toArgb()))
    }

    @Composable
    fun actionButtonColors(): ButtonColors {
        return ButtonDefaults.buttonColors(
            containerColor = actionContainer(),
            contentColor = onAction()
        )
    }
}

/**
 * Status colors that are not Strict brand accents and not error red.
 */
object StrictStatus {
    val abandonedLight = Color(0xFF7A4E12)
    val abandonedDark = Color(0xFFE2A23A)

    fun abandoned(darkTheme: Boolean): Color = if (darkTheme) abandonedDark else abandonedLight
}
