package app.mymusclemap.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val STRICT_WORDMARK_LIGHT = "strict-wordmark-light"
internal const val STRICT_WORDMARK_DARK = "strict-wordmark-dark"
internal const val STRICT_SYMBOL_LIGHT = "strict-symbol-light"
internal const val STRICT_SYMBOL_DARK = "strict-symbol-dark"

@Composable
internal fun strictSurfaceIsDark(): Boolean {
    return MaterialTheme.colorScheme.background.luminance() < 0.5f
}

/**
 * Theme wordmark. Light surfaces use the blue body; dark surfaces use the lime body.
 * The tagline is text, so it follows theme color and typography.
 */
@Composable
fun StrictBrandLockup(
    modifier: Modifier = Modifier,
    showTagline: Boolean = false,
    centered: Boolean = false,
    wordmarkWidth: Dp = 220.dp,
    taglineIsHeading: Boolean = false
) {
    val dark = strictSurfaceIsDark()
    val painter = painterResource(
        if (dark) R.drawable.strict_wordmark_dark else R.drawable.strict_wordmark_light
    )
    val wordmarkSize = painter.intrinsicSize
    val wordmarkRatio = if (
        wordmarkSize.width.isFinite() &&
        wordmarkSize.height.isFinite() &&
        wordmarkSize.height > 0f
    ) {
        wordmarkSize.width / wordmarkSize.height
    } else {
        1.88f
    }
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Image(
            painter = painter,
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(wordmarkWidth)
                .aspectRatio(wordmarkRatio)
                .testTag(if (dark) STRICT_WORDMARK_DARK else STRICT_WORDMARK_LIGHT)
        )
        if (showTagline) {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = stringResource(R.string.brand_tagline),
                style = AppTypeTokens.sectionKicker,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                modifier = if (taglineIsHeading) {
                    Modifier.semantics { heading() }
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
internal fun StrictSymbol(
    height: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val dark = strictSurfaceIsDark()
    val painter = painterResource(
        if (dark) R.drawable.strict_symbol_dark else R.drawable.strict_symbol_light
    )
    val symbolSize = painter.intrinsicSize
    val symbolRatio = if (
        symbolSize.width.isFinite() &&
        symbolSize.height.isFinite() &&
        symbolSize.height > 0f
    ) {
        symbolSize.width / symbolSize.height
    } else {
        0.53f
    }
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .height(height)
            .aspectRatio(symbolRatio, matchHeightConstraintsFirst = true)
            .testTag(if (dark) STRICT_SYMBOL_DARK else STRICT_SYMBOL_LIGHT)
    )
}
