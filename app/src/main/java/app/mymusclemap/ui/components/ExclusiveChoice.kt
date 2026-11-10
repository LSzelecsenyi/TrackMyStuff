package app.mymusclemap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand

/**
 * Full-width exclusive option used by onboarding and settings.
 * Padding sits inside the border so the label cannot touch the outline,
 * and the control keeps a 48dp minimum touch height.
 */
@Composable
fun ExclusiveChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    testTag: String? = null
) {
    val shape = AppShapeTokens.surface
    val container = if (selected) {
        StrictBrand.actionContainer()
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (selected) {
        StrictBrand.actionContainer()
    } else {
        MaterialTheme.colorScheme.outline
    }
    val labelColor = if (selected) {
        StrictBrand.onAction()
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (selected) {
        StrictBrand.onAction()
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clip(shape)
            .border(AppDimens.strokeThin, borderColor, shape)
            .background(container, shape)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics { this.selected = selected }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(
                horizontal = AppDimens.choicePaddingHorizontal,
                vertical = AppDimens.choicePaddingVertical
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            textAlign = TextAlign.Center
        )
        if (supporting != null) {
            Text(
                text = supporting,
                style = AppTypeTokens.statCaption,
                color = supportingColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
