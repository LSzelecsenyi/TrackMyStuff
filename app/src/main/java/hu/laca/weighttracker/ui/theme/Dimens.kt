package hu.laca.weighttracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AppDimens {
    val screenPadding = 20.dp
    val sectionGap = 20.dp
    val itemGap = 12.dp
    val heroPadding = 20.dp
    val minTouch = 48.dp
    val calendarCell = 44.dp
    val swatchSize = 36.dp
    val scrollEndPadding = 96.dp

    val cornerSurface = 16.dp
    val cornerCompact = 12.dp
    val cornerButton = 14.dp
    val strokeThin = 1.dp
    val headerStackGap = 6.dp
    val statSecondaryGap = 2.dp
    val sectionDividerSpace = 16.dp
    val navBarHeight = 56.dp
    val navIndicatorThickness = 2.dp
    val navIndicatorWidth = 28.dp
}

object AppShapeTokens {
    val surface = RoundedCornerShape(AppDimens.cornerSurface)
    val compact = RoundedCornerShape(AppDimens.cornerCompact)
    val button = RoundedCornerShape(AppDimens.cornerButton)
    val chip = RoundedCornerShape(percent = 50)
}

val AppShapes = Shapes(
    extraSmall = AppShapeTokens.compact,
    small = AppShapeTokens.button,
    medium = AppShapeTokens.surface,
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
