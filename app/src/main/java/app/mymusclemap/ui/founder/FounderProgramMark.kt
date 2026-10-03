package app.mymusclemap.ui.founder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppDimens

internal const val FOUNDER_MARK = "founder-program-mark"

/**
 * Temporary Founding Tester hero.
 *
 * TODO: Replace this placeholder with the final Strict logo during the planned branding/visual refactor.
 * This is not a final logo.
 */
@Composable
fun FounderProgramMark(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    decorative: Boolean = true
) {
    val description = stringResource(R.string.founder_mark_content_description)
    val container = if (prominent) 96.dp else 72.dp
    val iconSize = if (prominent) AppDimens.minTouch else AppDimens.swatchSize
    Box(modifier = modifier.testTag(FOUNDER_MARK)) {
        Box(
            modifier = Modifier
                .size(container)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .then(
                    if (decorative) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier.semantics { contentDescription = description }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
