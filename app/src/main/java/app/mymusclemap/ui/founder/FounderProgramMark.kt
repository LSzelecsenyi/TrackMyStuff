package app.mymusclemap.ui.founder

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.components.StrictSymbol

internal const val FOUNDER_MARK = "founder-program-mark"

/**
 * Product symbol for Founding Tester surfaces.
 * The earned Founder crown badge is separate artwork and is not drawn here.
 */
@Composable
fun FounderProgramMark(
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    decorative: Boolean = true
) {
    val description = stringResource(R.string.founder_mark_content_description)
    val height = if (prominent) 96.dp else 72.dp
    Box(modifier = modifier.testTag(FOUNDER_MARK)) {
        StrictSymbol(
            height = height,
            contentDescription = if (decorative) null else description
        )
    }
}
