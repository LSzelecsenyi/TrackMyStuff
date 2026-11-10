package app.mymusclemap.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import app.mymusclemap.ui.theme.AppDimens

/**
 * Centers a column that uses the available width on phones and stops widening past
 * [maxWidth] on larger windows. Horizontal padding stays at least [horizontalPadding].
 */
@Composable
fun ReadableContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = AppDimens.readableMaxWidth,
    horizontalPadding: Dp = AppDimens.screenPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = horizontalPadding)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}
