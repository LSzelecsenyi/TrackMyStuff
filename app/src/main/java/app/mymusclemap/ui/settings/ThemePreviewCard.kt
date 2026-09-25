package app.mymusclemap.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.theme.HexColor
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview

@Composable
fun ThemePreviewCard(
    seeds: ThemeSeeds,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.theme_preview_description)
    WeightTrackerThemeForPreview(seeds = seeds, darkTheme = darkTheme) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(SETTINGS_PREVIEW)
                .semantics { contentDescription = description }
                .border(
                    width = AppDimens.strokeThin,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_preview_surface),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .testTag(settingsPreviewPrimaryTag(HexColor.format(seeds.primary)))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = AppShapeTokens.compact
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.theme_preview_primary_action),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = AppTypeTokens.statCaption
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = AppShapeTokens.compact
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.theme_preview_secondary),
                        color = MaterialTheme.colorScheme.onSecondary,
                        style = AppTypeTokens.statCaption
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.tertiary, AppShapeTokens.compact)
                )
            }
            Spacer(Modifier.height(8.dp))
            val lineColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.7f)
                    lineTo(size.width * 0.35f, size.height * 0.4f)
                    lineTo(size.width * 0.65f, size.height * 0.55f)
                    lineTo(size.width, size.height * 0.22f)
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color = lineColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(size.width, size.height * 0.22f)
                )
            }
        }
    }
}
