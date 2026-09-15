package hu.laca.weighttracker.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.theme.ThemeSeeds
import hu.laca.weighttracker.ui.theme.WeightTrackerThemeForPreview

@Composable
fun ThemePreviewCard(
    seeds: ThemeSeeds,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.theme_preview_description)
    WeightTrackerThemeForPreview(seeds = seeds, darkTheme = darkTheme) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.theme_preview_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.theme_preview_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.theme_preview_surface),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = stringResource(R.string.theme_preview_primary_action),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = stringResource(R.string.theme_preview_secondary),
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        val lineColor = MaterialTheme.colorScheme.primary
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
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
                                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawCircle(
                                color = lineColor,
                                radius = 4.dp.toPx(),
                                center = Offset(size.width, size.height * 0.22f)
                            )
                        }
                    }
                }
            }
        }
    }
}
