package app.mymusclemap.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.ProAccess
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val PRO_BADGE = "pro-badge"
internal const val PRO_GATED_ROW = "pro-gated-row"

@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pro_badge)
    val description = stringResource(R.string.pro_badge_description)
    Surface(
        modifier = modifier
            .testTag(PRO_BADGE)
            .semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = AppShapeTokens.compact,
        tonalElevation = 0.dp
    ) {
        Text(
            text = label.uppercase(AppLocale.UI),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GatedFeatureRow(
    title: String,
    subtitle: String,
    feature: AppFeature,
    onUnlockedClick: () -> Unit,
    onLockedClick: (AppFeature) -> Unit,
    modifier: Modifier = Modifier,
    entitlements: FeatureEntitlements = LocalFeatureEntitlements.current,
    icon: ImageVector? = null,
    testTag: String = PRO_GATED_ROW
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable {
                    ProAccess.run(
                        entitlements = entitlements,
                        feature = feature,
                        onLocked = { onLockedClick(feature) },
                        onAllowed = onUnlockedClick
                    )
                }
                .testTag(testTag)
                .padding(AppDimens.heroPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(AppDimens.itemGap))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    ProBadge()
                }
                Text(
                    text = subtitle,
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
