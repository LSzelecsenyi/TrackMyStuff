package app.mymusclemap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.ui.theme.AppDimens

@Composable
fun PaletteSwatch(
    color: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(AppDimens.minTouch)
            .clip(CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                this.contentDescription = contentDescription
                if (onClick != null) role = Role.Button
            }
    ) {
        Box(
            modifier = Modifier
                .size(AppDimens.swatchSize)
                .align(androidx.compose.ui.Alignment.Center)
                .clip(CircleShape)
                .background(color)
                .border(1.5.dp, borderColor, CircleShape)
        )
    }
}
