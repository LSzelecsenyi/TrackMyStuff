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
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
