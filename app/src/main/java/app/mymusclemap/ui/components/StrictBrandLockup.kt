package app.mymusclemap.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val STRICT_WORDMARK = "strict-wordmark"
internal const val STRICT_LOCKUP = "strict-lockup"
internal const val STRICT_SYMBOL = "strict-symbol"

enum class StrictBrandArtwork {
    /** Wordmark only. The localized tagline can be drawn as text underneath. */
    Wordmark,

    /** Wordmark plus the metallic tagline already inside the image. */
    Lockup
}

internal fun strictLockupContentDescription(appName: String, tagline: String): String {
    return "$appName. $tagline"
}

@Composable
internal fun strictSurfaceIsDark(): Boolean {
    return MaterialTheme.colorScheme.background.luminance() < 0.5f
}

/**
 * Metallic Strict mark. There is one approved colorway, so light and dark
 * themes use the same artwork. On a light surface the mark sits on a dark plate.
 *
 * [StrictBrandArtwork.Lockup] already contains the tagline. [showTagline] adds
 * the localized string only under the wordmark.
 */
@Composable
fun StrictBrandLockup(
    modifier: Modifier = Modifier,
    artwork: StrictBrandArtwork = StrictBrandArtwork.Wordmark,
    showTagline: Boolean = false,
    centered: Boolean = false,
    markWidth: Dp = 220.dp,
    taglineIsHeading: Boolean = false,
    containerOnLight: Boolean = true
) {
    val drawable = when (artwork) {
        StrictBrandArtwork.Wordmark -> R.drawable.strict_wordmark
        StrictBrandArtwork.Lockup -> R.drawable.strict_lockup
    }
    val painter = painterResource(drawable)
    val size = painter.intrinsicSize
    val ratio = if (
        size.width.isFinite() &&
        size.height.isFinite() &&
        size.height > 0f
    ) {
        size.width / size.height
    } else {
        when (artwork) {
            StrictBrandArtwork.Wordmark -> 1.8693f
            StrictBrandArtwork.Lockup -> 1.5f
        }
    }
    val textTagline = showTagline && artwork == StrictBrandArtwork.Wordmark
    val description = if (artwork == StrictBrandArtwork.Lockup) {
        strictLockupContentDescription(
            stringResource(R.string.app_name),
            stringResource(R.string.brand_tagline)
        )
    } else {
        stringResource(R.string.app_name)
    }
    val tag = when (artwork) {
        StrictBrandArtwork.Wordmark -> STRICT_WORDMARK
        StrictBrandArtwork.Lockup -> STRICT_LOCKUP
    }
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        MetallicBrandPlate(enabled = containerOnLight && !strictSurfaceIsDark()) {
            Image(
                painter = painter,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(markWidth)
                    .aspectRatio(ratio)
                    .testTag(tag)
                    .then(
                        if (taglineIsHeading && artwork == StrictBrandArtwork.Lockup) {
                            Modifier.semantics { heading() }
                        } else {
                            Modifier
                        }
                    )
            )
        }
        if (textTagline) {
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
    contentDescription: String? = null,
    containerOnLight: Boolean = true
) {
    val painter = painterResource(R.drawable.strict_symbol)
    val size = painter.intrinsicSize
    val ratio = if (
        size.width.isFinite() &&
        size.height.isFinite() &&
        size.height > 0f
    ) {
        size.width / size.height
    } else {
        0.5491f
    }
    MetallicBrandPlate(
        enabled = containerOnLight && !strictSurfaceIsDark(),
        modifier = modifier
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(height)
                .aspectRatio(ratio, matchHeightConstraintsFirst = true)
                .testTag(STRICT_SYMBOL)
        )
    }
}

@Composable
private fun MetallicBrandPlate(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.then(
            if (enabled) {
                Modifier
                    .background(StrictBrand.dark, AppShapeTokens.surface)
                    .padding(12.dp)
            } else {
                Modifier
            }
        )
    ) {
        content()
    }
}
