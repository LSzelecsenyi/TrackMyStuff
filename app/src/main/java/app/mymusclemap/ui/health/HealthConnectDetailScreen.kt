package app.mymusclemap.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.health.ExerciseHistoryEntry
import app.mymusclemap.domain.health.ExerciseHistorySnapshot
import app.mymusclemap.domain.health.HealthDetailState
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthExerciseLine
import app.mymusclemap.domain.health.HealthHistoryAccess
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
internal const val HEALTH_DETAIL_HISTORY = "health-detail-history"
internal const val HEALTH_DETAIL_HISTORY_MORE = "health-detail-history-more"
internal const val HEALTH_DETAIL_HISTORY_ALLOW = "health-detail-history-allow"
internal const val HEALTH_DETAIL_HISTORY_RETRY = "health-detail-history-retry"

@Composable
fun HealthConnectDetailScreen(
    state: HealthDetailState,
    onBack: () -> Unit,
    exerciseHistory: ExerciseHistorySnapshot = ExerciseHistorySnapshot(),
    onLoadMore: () -> Unit = {},
    onRetryHistory: () -> Unit = {},
    onRequestHistory: () -> Unit = {},
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
            is HealthDetailState.Connected -> ConnectedDetail(
                state = state,
                exerciseHistory = exerciseHistory,
                onLoadMore = onLoadMore,
                onRetryHistory = onRetryHistory,
                onRequestHistory = onRequestHistory
            )
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
private fun ConnectedDetail(
    state: HealthDetailState.Connected,
    exerciseHistory: ExerciseHistorySnapshot,
    onLoadMore: () -> Unit,
    onRetryHistory: () -> Unit,
    onRequestHistory: () -> Unit
) {
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
        if (exerciseHistory.visible) {
            Spacer(Modifier.height(AppDimens.itemGap))
            ExerciseHistorySection(
                history = exerciseHistory,
                onLoadMore = onLoadMore,
                onRetry = onRetryHistory,
                onRequestHistory = onRequestHistory
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
private fun ExerciseHistorySection(
    history: ExerciseHistorySnapshot,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onRequestHistory: () -> Unit
) {
    SectionLabel(stringResource(R.string.health_connect_history_title), HEALTH_DETAIL_HISTORY)
    Text(
        text = stringResource(R.string.health_connect_history_subtitle),
        style = AppTypeTokens.statCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = AppDimens.statSecondaryGap)
    )
    val note = when {
        history.limitedToRecentWindow -> stringResource(R.string.health_connect_history_limited)
        history.access == HealthHistoryAccess.Unsupported ->
            stringResource(R.string.health_connect_history_unsupported)
        history.access == HealthHistoryAccess.NotGranted ->
            stringResource(R.string.health_connect_history_not_granted)
        else -> null
    }
    if (note != null) {
        Text(
            text = note,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = AppDimens.statSecondaryGap)
        )
    }
    if (history.access == HealthHistoryAccess.NotGranted) {
        Text(
            text = stringResource(R.string.health_connect_history_allow),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable(onClick = onRequestHistory)
                .testTag(HEALTH_DETAIL_HISTORY_ALLOW)
                .padding(vertical = AppDimens.statSecondaryGap)
        )
    }
    when {
        history.loading && history.entries.isEmpty() -> {
            Text(
                text = stringResource(R.string.health_connect_history_loading),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        history.entries.isEmpty() && history.failed -> {
            HistoryProblem(stringResource(R.string.health_connect_history_failed), onRetry)
        }
        history.entries.isEmpty() && !history.loading -> {
            Text(
                text = stringResource(R.string.health_connect_history_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        else -> {
            MetricSurface {
                var shownMonth: YearMonth? = null
                history.entries.forEach { entry ->
                    val month = YearMonth.from(entry.date)
                    if (month != shownMonth) {
                        if (shownMonth != null) MetricDivider()
                        Text(
                            text = UiFormatters.monthTitle(month),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(AppDimens.statSecondaryGap))
                        shownMonth = month
                    } else {
                        Spacer(Modifier.height(AppDimens.itemGap))
                    }
                    ExerciseHistoryRow(entry)
                }
            }
            if (history.failed) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                HistoryProblem(stringResource(R.string.health_connect_history_failed), onRetry)
            }
            if (history.canLoadMore) {
                Text(
                    text = if (history.loading) {
                        stringResource(R.string.health_connect_history_loading)
                    } else {
                        stringResource(R.string.health_connect_history_more)
                    },
                    style = AppTypeTokens.sectionTitle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .clickable(enabled = !history.loading, onClick = onLoadMore)
                        .testTag(HEALTH_DETAIL_HISTORY_MORE)
                        .padding(vertical = AppDimens.statSecondaryGap)
                )
            }
        }
    }
}

@Composable
private fun HistoryProblem(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = stringResource(R.string.health_connect_history_retry),
        style = AppTypeTokens.sectionTitle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onRetry)
            .testTag(HEALTH_DETAIL_HISTORY_RETRY)
            .padding(vertical = AppDimens.statSecondaryGap)
    )
}

@Composable
private fun ExerciseHistoryRow(entry: ExerciseHistoryEntry) {
    val kind = stringResource(
        when (entry.kind) {
            HealthExerciseKind.STRENGTH -> R.string.health_connect_strength
            HealthExerciseKind.CYCLING -> R.string.health_connect_cycling
            HealthExerciseKind.RUNNING -> R.string.health_connect_running
            HealthExerciseKind.OTHER -> R.string.health_connect_exercise_sessions
        }
    )
    val whenLabel = stringResource(
        R.string.health_connect_dated_value,
        UiFormatters.compactDate(entry.date),
        entry.startTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(AppLocale.UI))
    )
    val duration = historyDuration(entry.duration)
    val source = sourceLabel(entry.sourcePackage)
    Column(modifier = Modifier.testTag(HEALTH_DETAIL_HISTORY + "-" + entry.id)) {
        Text(text = kind, style = AppTypeTokens.sectionTitle, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = stringResource(R.string.health_connect_dated_value, whenLabel, duration),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (source != null) {
            Text(
                text = source,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun sourceLabel(packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    val context = LocalContext.current
    return remember(packageName) {
        try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            null
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
    val total = minutes.toLong()
    val hours = total / 60
    val remainder = total % 60
    return if (hours > 0) {
        stringResource(R.string.health_connect_duration_hm, hours, remainder)
    } else {
        stringResource(R.string.health_connect_duration_m, remainder)
    }
}

@Composable
private fun formatDuration(duration: Duration): String {
    return historyDuration(duration)
}

@Composable
private fun historyDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0)
    if (totalSeconds < 60) {
        val shown = if (totalSeconds == 0L && duration > Duration.ZERO) 1L else totalSeconds
        return stringResource(R.string.health_connect_duration_s, shown)
    }
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val minutePart = minutes % 60
    return when {
        hours > 0 -> stringResource(R.string.health_connect_duration_hm, hours, minutePart)
        seconds == 0L -> stringResource(R.string.health_connect_duration_m, minutes)
        else -> stringResource(R.string.health_connect_duration_ms, minutes, seconds)
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
