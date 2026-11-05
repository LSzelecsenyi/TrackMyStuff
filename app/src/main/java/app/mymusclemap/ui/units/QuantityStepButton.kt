package app.mymusclemap.ui.units

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

val QuantityStepCircleSize = 32.dp

/**
 * Circular outlined step control shared by repetition and weight adjustments.
 * The accent distinguishes the two; the shape, size, stroke, glyph, and hit area match.
 */
@Composable
fun OutlinedQuantityStepButton(
    symbol: String,
    enabled: Boolean,
    accent: Color,
    contentDescription: String,
    testTag: String,
    circleTag: String,
    traversalIndex: Float? = null,
    onClick: () -> Unit
) {
    val stroke = if (enabled) accent else MaterialTheme.colorScheme.outline
    val glyph = if (enabled) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val fill = if (enabled) accent.copy(alpha = 0.08f) else Color.Transparent
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
            .testTag(testTag)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (traversalIndex != null) {
                    this.traversalIndex = traversalIndex
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(QuantityStepCircleSize)
                .background(fill, CircleShape)
                .border(AppDimens.strokeThin, stroke, CircleShape)
                .testTag(circleTag),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = AppTypeTokens.statValue,
                color = glyph
            )
        }
    }
}
