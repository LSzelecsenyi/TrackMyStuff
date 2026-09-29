package app.mymusclemap.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthDetailState
import app.mymusclemap.domain.health.HealthExerciseLine
import app.mymusclemap.domain.health.HealthLine
import app.mymusclemap.domain.health.HealthLineStatus
import app.mymusclemap.domain.health.HealthQuietStatus
import app.mymusclemap.domain.health.HealthTrendSlot
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.text.NumberFormat
import java.time.Duration

internal const val HEALTH_DETAIL = "health-detail"
internal const val HEALTH_DETAIL_ACTIVITY = "health-detail-activity"
internal const val HEALTH_DETAIL_RECOVERY = "health-detail-recovery"
internal const val HEALTH_DETAIL_STEPS = "health-detail-steps"
internal const val HEALTH_DETAIL_STRENGTH = "health-detail-strength"
internal const val HEALTH_DETAIL_CYCLING = "health-detail-cycling"
internal const val HEALTH_DETAIL_RUNNING = "health-detail-running"
internal const val HEALTH_DETAIL_HEART = "health-detail-heart"
internal const val HEALTH_DETAIL_HRV = "health-detail-hrv"
internal const val HEALTH_DETAIL_SLEEP = "health-detail-sleep"
internal const val HEALTH_DETAIL_STEPS_CHART = "health-detail-steps-chart-"
internal const val HEALTH_DETAIL_HEART_CHART = "health-detail-heart-chart-"
internal const val HEALTH_DETAIL_HRV_CHART = "health-detail-hrv-chart-"
internal const val HEALTH_DETAIL_SLEEP_CHART = "health-detail-sleep-chart-"

@Composable
fun HealthConnectDetailScreen(
    state: HealthDetailState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(HEALTH_DETAIL)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenPadding)
                .defaultMinSize(minHeight = AppDimens.minTouch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(AppDimens.minTouch)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back)
                )
            }
            Text(
                text = stringResource(R.string.health_connect_title),
                style = AppTypeTokens.sectionTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (state) {
            HealthDetailState.Checking -> Unit
            is HealthDetailState.Quiet -> QuietDetail(state.status)
            is HealthDetailState.Connected -> ConnectedDetail(state)
        }
    }
}

@Composable
private fun QuietDetail(status: HealthQuietStatus) {
    Text(
        text = stringResource(
            when (status) {
                HealthQuietStatus.Unavailable -> R.string.health_connect_overview_unavailable
                HealthQuietStatus.UpdateRequired -> R.string.health_connect_overview_update
                HealthQuietStatus.NotConnected -> R.string.health_connect_overview_not_connected
            }
        ),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppDimens.screenPadding)
    )
}

@Composable
private fun ConnectedDetail(state: HealthDetailState.Connected) {
    Column(modifier = Modifier.padding(horizontal = AppDimens.screenPadding)) {
        SectionLabel(stringResource(R.string.health_connect_activity), HEALTH_DETAIL_ACTIVITY)
        MetricSurface {
            ScalarBlock(
                tag = HEALTH_DETAIL_STEPS,
                label = stringResource(R.string.health_connect_steps),
                line = state.steps,
                value = { formatSteps(it.toLong()) },
                empty = stringResource(R.string.health_connect_no_steps),
                off = stringResource(R.string.health_connect_steps_off),
                chartPrefix = HEALTH_DETAIL_STEPS_CHART,
                slotDescription = { slot ->
                    slot.amount?.let {
                        stringResource(
                            R.string.health_connect_steps_day,
                            UiFormatters.compactDate(slot.date),
                            formatSteps(it.toLong())
                        )
                    }
                },
                datedHeadline = false
            )
            MetricDivider()
            ExerciseBlock(
                tag = HEALTH_DETAIL_STRENGTH,
                label = stringResource(R.string.health_connect_strength),
                line = state.strength,
                empty = stringResource(R.string.health_connect_no_strength)
            )
            MetricDivider()
            ExerciseBlock(
                tag = HEALTH_DETAIL_CYCLING,
                label = stringResource(R.string.health_connect_cycling),
                line = state.cycling,
                empty = stringResource(R.string.health_connect_no_cycling)
            )
            MetricDivider()
            ExerciseBlock(
                tag = HEALTH_DETAIL_RUNNING,
                label = stringResource(R.string.health_connect_running),
                line = state.running,
                empty = stringResource(R.string.health_connect_no_running)
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        SectionLabel(stringResource(R.string.health_connect_recovery), HEALTH_DETAIL_RECOVERY)
        MetricSurface {
            ScalarBlock(
                tag = HEALTH_DETAIL_HEART,
                label = stringResource(R.string.health_connect_resting_heart_rate),
                line = state.heart,
                value = { stringResource(R.string.health_connect_bpm, it.toLong().toInt()) },
                empty = stringResource(R.string.health_connect_no_heart),
                off = stringResource(R.string.health_connect_heart_off),
                chartPrefix = HEALTH_DETAIL_HEART_CHART,
                slotDescription = { slot ->
                    slot.amount?.let {
                        stringResource(
                            R.string.health_connect_heart_day,
                            UiFormatters.compactDate(slot.date),
                            it.toLong().toInt()
                        )
                    }
                },
                datedHeadline = false
            )
            MetricDivider()
            ScalarBlock(
                tag = HEALTH_DETAIL_HRV,
                label = stringResource(R.string.health_connect_hrv),
                line = state.hrv,
                value = { stringResource(R.string.health_connect_hrv_ms, formatHrv(it)) },
                empty = stringResource(R.string.health_connect_no_hrv),
                off = stringResource(R.string.health_connect_hrv_off),
                chartPrefix = HEALTH_DETAIL_HRV_CHART,
                slotDescription = { slot ->
                    slot.amount?.let {
                        stringResource(
                            R.string.health_connect_hrv_day,
                            UiFormatters.compactDate(slot.date),
                            formatHrv(it)
                        )
                    }
                },
                datedHeadline = true
            )
            MetricDivider()
            ScalarBlock(
                tag = HEALTH_DETAIL_SLEEP,
                label = stringResource(R.string.health_connect_sleep),
                line = state.sleep,
                value = { formatMinutes(it) },
                empty = stringResource(R.string.health_connect_no_sleep),
                off = stringResource(R.string.health_connect_sleep_off),
                chartPrefix = HEALTH_DETAIL_SLEEP_CHART,
                slotDescription = { slot ->
                    slot.amount?.let {
                        stringResource(
                            R.string.health_connect_sleep_day,
                            UiFormatters.compactDate(slot.date),
                            formatMinutes(it)
                        )
                    }
                },
                datedHeadline = true,
                headlineCaption = stringResource(R.string.health_connect_last_sleep)
            )
            Text(
                text = stringResource(R.string.health_connect_sleep_note),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.headerStackGap)
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
    }
}

@Composable
private fun SectionLabel(title: String, tag: String) {
    Text(
        text = title,
        style = AppTypeTokens.statCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(bottom = AppDimens.statSecondaryGap)
            .testTag(tag)
    )
}

@Composable
private fun MetricSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(AppDimens.itemGap), content = { content() })
    }
}

@Composable
private fun MetricDivider() {
    Spacer(Modifier.height(AppDimens.itemGap))
    BoxDivider()
    Spacer(Modifier.height(AppDimens.itemGap))
}

@Composable
private fun BoxDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppDimens.strokeThin)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun ScalarBlock(
    tag: String,
    label: String,
    line: HealthLine,
    value: @Composable (Double) -> String,
    empty: String,
    off: String,
    chartPrefix: String,
    slotDescription: @Composable (HealthTrendSlot) -> String?,
    datedHeadline: Boolean,
    headlineCaption: String? = null
) {
    val headline = when (line.status) {
        HealthLineStatus.Off -> off
        HealthLineStatus.Failed -> stringResource(R.string.health_connect_metric_failed)
        HealthLineStatus.Shown -> {
            val amount = line.headline
            if (amount == null) {
                empty
            } else if (datedHeadline && line.headlineDate != null && (headlineCaption != null || !line.headlineIsToday)) {
                stringResource(
                    R.string.health_connect_dated_value,
                    UiFormatters.compactDate(line.headlineDate),
                    value(amount)
                )
            } else {
                value(amount)
            }
        }
    }
    val prominent = line.status == HealthLineStatus.Shown && line.headline != null
    Column(modifier = Modifier.testTag(tag)) {
        Text(
            text = headline,
            style = if (prominent) AppTypeTokens.statValue else AppTypeTokens.statSecondary,
            color = if (prominent) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (prominent && headlineCaption != null) headlineCaption else label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (prominent && headlineCaption != null) {
            Text(
                text = label,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (line.status == HealthLineStatus.Shown && line.slots.any { it.amount != null }) {
            val descriptions = line.slots.map { slotDescription(it) }
            Text(
                text = stringResource(R.string.health_connect_last_7_days),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppDimens.headerStackGap)
            )
            HealthFixedBarChart(
                slots = line.slots,
                tagPrefix = chartPrefix,
                descriptionFor = { slot ->
                    descriptions.getOrNull(line.slots.indexOf(slot))
                },
                modifier = Modifier.padding(top = AppDimens.statSecondaryGap)
            )
        }
    }
}

@Composable
private fun ExerciseBlock(
    tag: String,
    label: String,
    line: HealthExerciseLine,
    empty: String
) {
    val headline = when (line.status) {
        HealthLineStatus.Off -> stringResource(R.string.health_connect_exercise_off)
        HealthLineStatus.Failed -> stringResource(R.string.health_connect_metric_failed)
        HealthLineStatus.Shown -> {
            if (line.recentSessions == 0) {
                empty
            } else {
                sessionSummary(line.recentSessions, line.recentDuration)
            }
        }
    }
    val prominent = line.status == HealthLineStatus.Shown && line.recentSessions > 0
    Column(modifier = Modifier.testTag(tag)) {
        Text(
            text = headline,
            style = if (prominent) AppTypeTokens.statValue else AppTypeTokens.statSecondary,
            color = if (prominent) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (prominent) {
            Text(
                text = stringResource(R.string.health_connect_last_7_days),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun sessionSummary(sessions: Int, duration: Duration): String {
    val formatted = formatDuration(duration)
    return if (sessions == 1) {
        stringResource(R.string.health_connect_one_session, formatted)
    } else {
        stringResource(R.string.health_connect_sessions, sessions, formatted)
    }
}

@Composable
private fun formatMinutes(minutes: Double): String {
    return formatDuration(Duration.ofMinutes(minutes.toLong()))
}

@Composable
private fun formatDuration(duration: Duration): String {
    val total = duration.toMinutes()
    val hours = total / 60
    val minutes = total % 60
    return if (hours > 0) {
        stringResource(R.string.health_connect_duration_hm, hours, minutes)
    } else {
        stringResource(R.string.health_connect_duration_m, minutes)
    }
}

private fun formatSteps(steps: Long): String {
    return NumberFormat.getIntegerInstance(AppLocale.UI).format(steps)
}

private fun formatHrv(millis: Double): String {
    val format = NumberFormat.getNumberInstance(AppLocale.UI)
    format.maximumFractionDigits = 1
    format.minimumFractionDigits = 0
    return format.format(millis)
}
