package app.mymusclemap.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthCardState
import app.mymusclemap.domain.health.HealthQuietStatus
import app.mymusclemap.domain.health.StepSlot
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.text.NumberFormat

internal const val OVERVIEW_HEALTH = "overview-health"
internal const val OVERVIEW_HEALTH_STEPS = "overview-health-steps"
internal const val OVERVIEW_HEALTH_HEART = "overview-health-heart"
internal const val OVERVIEW_HEALTH_CHART = "overview-health-steps-chart"
internal const val OVERVIEW_HEALTH_CHART_SLOT = "overview-health-steps-slot-"

@Composable
fun HealthConnectOverviewCard(
    state: HealthCardState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        HealthCardState.Checking -> Unit
        is HealthCardState.Quiet -> QuietHealthCard(
            status = state.status,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        is HealthCardState.Readings -> ReadingsHealthCard(state = state, modifier = modifier)
    }
}

@Composable
private fun QuietHealthCard(
    status: HealthQuietStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier
) {
    val subtitle = stringResource(
        when (status) {
            HealthQuietStatus.Unavailable -> R.string.health_connect_overview_unavailable
            HealthQuietStatus.UpdateRequired -> R.string.health_connect_overview_update
            HealthQuietStatus.NotConnected -> R.string.health_connect_overview_not_connected
        }
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings)
            .testTag(OVERVIEW_HEALTH)
    ) {
        Text(
            text = stringResource(R.string.health_connect_title),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReadingsHealthCard(
    state: HealthCardState.Readings,
    modifier: Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(OVERVIEW_HEALTH),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(AppDimens.itemGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.health_connect_title),
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.health_connect_from),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.readFailed) {
                Text(
                    text = stringResource(R.string.health_connect_read_failed),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppDimens.statSecondaryGap)
                )
            } else {
                val showStepsChart = state.stepsGranted && state.recentSteps.any { it.steps != null }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(top = AppDimens.itemGap),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(OVERVIEW_HEALTH_STEPS)
                    ) {
                        MetricColumn(
                            label = stringResource(R.string.health_connect_steps),
                            value = stepsValue(state),
                            prominent = state.stepsGranted && state.todaySteps != null
                        )
                        if (showStepsChart) {
                            Text(
                                text = stringResource(R.string.health_connect_last_7_days),
                                style = AppTypeTokens.statCaption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = AppDimens.headerStackGap)
                            )
                            RecentStepsChart(
                                slots = state.recentSteps,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = AppDimens.statSecondaryGap)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = AppDimens.itemGap)
                            .fillMaxHeight()
                            .width(AppDimens.strokeThin)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    MetricColumn(
                        label = stringResource(R.string.health_connect_resting_heart_rate),
                        value = heartValue(state),
                        prominent = state.heartRateGranted && state.todayHeartRate != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(OVERVIEW_HEALTH_HEART)
                    )
                }
            }
        }
    }
}

@Composable
private fun stepsValue(state: HealthCardState.Readings): String {
    if (!state.stepsGranted) return stringResource(R.string.health_connect_steps_off)
    val steps = state.todaySteps ?: return stringResource(R.string.health_connect_no_steps)
    return formatSteps(steps)
}

@Composable
private fun heartValue(state: HealthCardState.Readings): String {
    if (!state.heartRateGranted) return stringResource(R.string.health_connect_heart_off)
    val bpm = state.todayHeartRate ?: return stringResource(R.string.health_connect_no_heart)
    return stringResource(R.string.health_connect_bpm, bpm.toInt())
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    prominent: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = if (prominent) AppTypeTokens.statValue else AppTypeTokens.statSecondary,
            color = if (prominent) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = if (prominent) 1 else 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentStepsChart(
    slots: List<StepSlot>,
    modifier: Modifier = Modifier
) {
    val maxSteps = slots.mapNotNull { it.steps }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag(OVERVIEW_HEALTH_CHART),
        content = {
            slots.forEachIndexed { index, slot ->
                val steps = slot.steps
                val description = steps?.let {
                    stringResource(
                        R.string.health_connect_steps_day,
                        UiFormatters.compactDate(slot.date),
                        formatSteps(it)
                    )
                }
                Box(
                    modifier = Modifier
                        .testTag(OVERVIEW_HEALTH_CHART_SLOT + index)
                        .then(
                            if (description != null) {
                                Modifier.semantics { contentDescription = description }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (steps != null) {
                        val fraction = (steps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f)
                        val barHeight = if (steps == 0L) 2.dp else (32.dp * fraction).coerceAtLeast(2.dp)
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val count = measurables.size.coerceAtLeast(1)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val base = width / count
        val remainder = width - base * count
        val placeables = measurables.mapIndexed { index, measurable ->
            val slotWidth = base + if (index == count - 1) remainder else 0
            measurable.measure(Constraints.fixed(slotWidth, height))
        }
        layout(width, height) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width
            }
        }
    }
}

private fun formatSteps(steps: Long): String {
    return NumberFormat.getIntegerInstance(AppLocale.UI).format(steps)
}
