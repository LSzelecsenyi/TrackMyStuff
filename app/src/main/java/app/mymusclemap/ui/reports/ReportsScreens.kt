package app.mymusclemap.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.reports.ReportAdherence
import app.mymusclemap.domain.reports.ReportBodyWeight
import app.mymusclemap.domain.reports.ReportExerciseHighlight
import app.mymusclemap.domain.reports.ReportHistoryCoverage
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportPerformanceKind
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.reports.ReportPeriodComparison
import app.mymusclemap.domain.reports.ReportSummary
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.components.SeriesChart
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.ui.statistics.DestinationRow
import app.mymusclemap.ui.statistics.StatRow
import app.mymusclemap.ui.statistics.StatisticsCard
import app.mymusclemap.ui.statistics.StatisticsHeader
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.round

internal const val REPORTS_ROOT = "reports-root"
internal const val REPORTS_EMPTY = "reports-empty"
internal const val REPORTS_KIND = "reports-kind"
internal const val REPORT_DETAIL_ROOT = "report-detail-root"
internal const val REPORT_PARTIAL = "report-partial"
internal const val REPORT_ACTIVITY = "report-activity"
internal const val REPORT_ADHERENCE = "report-adherence"
internal const val REPORT_VOLUME = "report-volume"
internal const val REPORT_COMPARISON = "report-comparison"
internal const val REPORT_HIGHLIGHTS = "report-highlights"
internal const val REPORT_BODY_WEIGHT = "report-body-weight"
internal const val REPORT_VOLUME_CHART = "report-volume-chart"
internal const val REPORT_MUSCLES = "report-muscles"
internal const val REPORT_UNAVAILABLE = "report-unavailable"

internal val reportKindTags = listOf(
    "reports-kind-monthly",
    "reports-kind-quarterly",
    "reports-kind-half-year",
    "reports-kind-yearly"
)

internal fun reportPeriodTag(kind: ReportKind, start: LocalDate): String {
    return "report-period-${kind.name}-$start"
}

@Composable
fun ReportsScreen(
    state: ReportsUiState,
    onBack: () -> Unit,
    onKindSelected: (ReportKind) -> Unit,
    onOpenReport: (ReportPeriod) -> Unit,
    onLockedReport: () -> Unit = {},
    onDismissLocked: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(REPORTS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            StatisticsHeader(title = stringResource(R.string.reports_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(top = AppDimens.headerStackGap, bottom = AppDimens.scrollEndPadding)
            ) {
                ReportKindSelector(
                    selected = state.kind,
                    lockedKinds = state.lockedKinds,
                    onSelected = onKindSelected
                )
                Spacer(Modifier.height(AppDimens.sectionGap))
                if (!state.loading && state.reports.isEmpty()) {
                    Text(
                        text = stringResource(R.string.reports_empty_title),
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag(REPORTS_EMPTY)
                    )
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = stringResource(R.string.reports_empty_body),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!state.loading) {
                    state.reports.forEach { available ->
                        val period = available.period
                        val locked = state.kindLocked
                        DestinationRow(
                            title = period.displayTitle(),
                            subtitle = periodSubtitle(available.coverage, period),
                            onClick = {
                                if (locked) onLockedReport() else onOpenReport(period)
                            },
                            testTag = reportPeriodTag(period.kind, period.startInclusive),
                            showProBadge = locked
                        )
                    }
                }
            }
        }
    }
    state.lockedFeature?.let { feature ->
        ProInfoSheet(feature = feature, onDismiss = onDismissLocked)
    }
}

@Composable
fun ReportDetailScreen(
    loading: Boolean,
    summary: ReportSummary?,
    onBack: () -> Unit,
    lockedFeature: AppFeature? = null,
    onDismissLocked: () -> Unit = {}
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(REPORT_DETAIL_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            val title = summary?.period?.displayTitle() ?: stringResource(R.string.reports_title)
            StatisticsHeader(title = title, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(top = AppDimens.headerStackGap, bottom = AppDimens.scrollEndPadding)
            ) {
                when {
                    summary != null && lockedFeature == null -> ReportDetailContent(summary)
                    !loading && lockedFeature == null -> Text(
                        text = stringResource(R.string.reports_unavailable),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(REPORT_UNAVAILABLE)
                    )
                }
            }
        }
    }
    lockedFeature?.let { feature ->
        ProInfoSheet(feature = feature, onDismiss = onDismissLocked)
    }
}

@Composable
private fun ReportDetailContent(summary: ReportSummary) {
    Text(
        text = summary.period.displayRange(),
        style = AppTypeTokens.statSecondary,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (summary.coverage == ReportHistoryCoverage.Partial) {
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = stringResource(R.string.reports_partial_notice),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(REPORT_PARTIAL)
        )
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    ActivitySection(summary)
    CompactEditorDivider()
    AdherenceSection(summary.adherence)
    CompactEditorDivider()
    VolumeTotalSection(summary)
    summary.previous?.let { comparison ->
        CompactEditorDivider()
        ComparisonSection(comparison)
    }
    CompactEditorDivider()
    HighlightsSection(summary.exerciseHighlights)
    summary.bodyWeight?.let { weight ->
        CompactEditorDivider()
        BodyWeightSection(weight)
    }
    CompactEditorDivider()
    VolumeChartSection(summary)
    CompactEditorDivider()
    MuscleSection(summary)
}

@Composable
private fun ReportKindSelector(
    selected: ReportKind,
    lockedKinds: Set<ReportKind>,
    onSelected: (ReportKind) -> Unit
) {
    val options = ReportKind.entries.map { stringResource(it.labelRes()) }
    val lockedIndices = ReportKind.entries.mapIndexedNotNull { index, kind ->
        if (kind in lockedKinds) index else null
    }.toSet()
    SegmentedControl(
        options = options,
        selectedIndex = ReportKind.entries.indexOf(selected),
        onSelected = { index -> onSelected(ReportKind.entries[index]) },
        compact = true,
        optionTestTags = reportKindTags,
        lockedIndices = lockedIndices,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(REPORTS_KIND)
    )
}

@Composable
private fun periodSubtitle(coverage: ReportHistoryCoverage, period: ReportPeriod): String {
    val range = period.displayRange()
    return if (coverage == ReportHistoryCoverage.Partial) {
        stringResource(R.string.reports_period_partial, range)
    } else {
        range
    }
}

@Composable
private fun ActivitySection(summary: ReportSummary) {
    val activity = summary.activity
    CompactEditorSection(
        title = stringResource(R.string.reports_activity_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_ACTIVITY)
    ) {
        StatisticsCard {
            StatRow(
                label = stringResource(R.string.statistics_activity_workouts),
                value = pluralStringResource(
                    R.plurals.statistics_workouts,
                    activity.workoutCount,
                    activity.workoutCount
                )
            )
            StatRow(
                label = stringResource(R.string.statistics_activity_days),
                value = pluralStringResource(
                    R.plurals.statistics_training_days,
                    activity.trainingDayCount,
                    activity.trainingDayCount
                )
            )
            StatRow(
                label = stringResource(R.string.statistics_activity_sets),
                value = pluralStringResource(
                    R.plurals.weekly_overview_sets,
                    activity.completedSetCount,
                    activity.completedSetCount
                )
            )
            activity.durationMillis?.let { duration ->
                StatRow(
                    label = stringResource(R.string.statistics_activity_duration),
                    value = reportDuration(duration)
                )
            }
        }
    }
}

@Composable
private fun AdherenceSection(adherence: ReportAdherence) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_adherence_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_ADHERENCE)
    ) {
        val percent = adherence.percent
        if (percent == null) {
            Text(
                text = stringResource(R.string.reports_adherence_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                Text(
                    text = stringResource(R.string.statistics_adherence_percent, percent),
                    style = AppTypeTokens.statHero,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = stringResource(
                        R.string.statistics_adherence_summary,
                        adherence.completedCount,
                        adherence.plannedCount
                    ),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VolumeTotalSection(summary: ReportSummary) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_volume_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_VOLUME)
    ) {
        Text(
            text = stringResource(R.string.reports_volume_scope),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        if (summary.volume.totalKg == null) {
            Text(
                text = stringResource(R.string.reports_volume_unavailable),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                StatRow(
                    label = stringResource(R.string.reports_volume_total),
                    value = ReportVolumeFormat.kilograms(summary.volume.totalKg)
                )
            }
        }
    }
}

@Composable
private fun ComparisonSection(comparison: ReportPeriodComparison) {
    CompactEditorSection(
        title = stringResource(R.string.reports_comparison_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_COMPARISON)
    ) {
        Text(
            text = stringResource(R.string.reports_comparison_with, comparison.period.displayTitle()),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        ComparisonRow(
            label = stringResource(R.string.statistics_activity_workouts),
            value = comparisonValue(comparison.workoutCountDelta, comparison.workoutCountPercent)
        )
        ComparisonRow(
            label = stringResource(R.string.reports_volume_total),
            value = volumeComparisonValue(comparison)
        )
        comparison.adherencePointDelta?.let { points ->
            ComparisonRow(
                label = stringResource(R.string.statistics_adherence_title),
                value = adherenceComparison(points)
            )
        }
    }
}

@Composable
private fun ComparisonRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun adherenceComparison(points: Int): String {
    return if (points == 0) {
        stringResource(R.string.reports_comparison_no_change)
    } else {
        stringResource(R.string.reports_comparison_points, signedCount(points))
    }
}

@Composable
private fun comparisonValue(delta: Int, percent: Double?): String {
    val count = signedCount(delta)
    val change = percent?.let(::signedPercent) ?: return count
    return stringResource(R.string.reports_comparison_with_percent, count, change)
}

@Composable
private fun volumeComparisonValue(comparison: ReportPeriodComparison): String {
    val amount = ReportVolumeFormat.signedKilograms(comparison.volumeDeltaKg)
    val change = comparison.volumePercent?.let(::signedPercent) ?: return amount
    return stringResource(R.string.reports_comparison_with_percent, amount, change)
}

@Composable
private fun HighlightsSection(highlights: List<ReportExerciseHighlight>) {
    CompactEditorSection(
        title = stringResource(R.string.reports_highlights_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_HIGHLIGHTS)
    ) {
        if (highlights.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_highlights_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                highlights.forEachIndexed { index, highlight ->
                    if (index > 0) {
                        Spacer(Modifier.height(AppDimens.itemGap))
                    }
                    Text(
                        text = highlight.name,
                        style = AppTypeTokens.statValue,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(
                            R.string.reports_highlight_dates,
                            UiFormatters.compactDate(highlight.baselineDate),
                            UiFormatters.compactDate(highlight.latestDate)
                        ),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = highlightTransition(highlight),
                        style = AppTypeTokens.statValue,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = highlightDelta(highlight),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun highlightTransition(highlight: ReportExerciseHighlight): String {
    return stringResource(
        R.string.reports_highlight_change,
        performanceValue(highlight.kind, highlight.baseline),
        performanceValue(highlight.kind, highlight.latest)
    )
}

@Composable
private fun highlightDelta(highlight: ReportExerciseHighlight): String {
    return when (highlight.kind) {
        ReportPerformanceKind.Reps -> stringResource(
            R.string.reports_highlight_reps_delta,
            signedCount(round(highlight.delta).toInt())
        )
        ReportPerformanceKind.EffectiveKg -> ReportVolumeFormat.signedKilograms(highlight.delta)
        ReportPerformanceKind.DurationSeconds -> stringResource(
            R.string.reports_highlight_duration_delta,
            signedCount(round(highlight.delta).toInt())
        )
        ReportPerformanceKind.DistanceMeters -> stringResource(
            R.string.reports_highlight_distance_delta,
            signedDistance(highlight.delta)
        )
    }
}

@Composable
private fun performanceValue(kind: ReportPerformanceKind, value: Double): String {
    return when (kind) {
        ReportPerformanceKind.Reps -> stringResource(R.string.statistics_best_reps, round(value).toInt())
        ReportPerformanceKind.EffectiveKg -> ReportVolumeFormat.kilograms(value)
        ReportPerformanceKind.DurationSeconds -> ElapsedTime.formatMillis(round(value).toLong() * 1000L)
        ReportPerformanceKind.DistanceMeters -> distanceText(value)
    }
}

@Composable
private fun signedDistance(deltaMeters: Double): String {
    val formatted = distanceText(abs(deltaMeters))
    return when {
        deltaMeters > 0 -> "+$formatted"
        deltaMeters < 0 -> "−$formatted"
        else -> formatted
    }
}

@Composable
private fun distanceText(meters: Double): String {
    return if (abs(meters) >= 1000.0) {
        stringResource(
            R.string.set_copy_distance_km,
            QuantityParser.formatDisplay(QuantityParser.fromMeters(abs(meters), DistanceUnit.KILOMETERS))
        )
    } else {
        stringResource(
            R.string.set_copy_distance_m,
            QuantityParser.formatDisplay(abs(meters))
        )
    }
}

@Composable
private fun BodyWeightSection(weight: ReportBodyWeight) {
    CompactEditorSection(
        title = stringResource(R.string.reports_body_weight_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_BODY_WEIGHT)
    ) {
        StatisticsCard {
            if (weight.lastKg == null) {
                StatRow(
                    label = UiFormatters.longDate(weight.firstDate),
                    value = UiFormatters.weightKg(weight.firstKg)
                )
            } else {
                StatRow(
                    label = stringResource(R.string.reports_body_weight_first),
                    value = stringResource(
                        R.string.reports_body_weight_sample,
                        UiFormatters.weightKg(weight.firstKg),
                        UiFormatters.compactDate(weight.firstDate)
                    )
                )
                StatRow(
                    label = stringResource(R.string.reports_body_weight_last),
                    value = stringResource(
                        R.string.reports_body_weight_sample,
                        UiFormatters.weightKg(weight.lastKg),
                        UiFormatters.compactDate(weight.lastDate ?: weight.firstDate)
                    )
                )
                weight.changeKg?.let { change ->
                    StatRow(
                        label = stringResource(R.string.reports_body_weight_change),
                        value = UiFormatters.signedWeightKg(change)
                    )
                }
                weight.averageKg?.let { average ->
                    StatRow(
                        label = stringResource(R.string.reports_body_weight_average),
                        value = UiFormatters.weightKg(average)
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeChartSection(summary: ReportSummary) {
    val trend = ReportChartSeries.plotted(
        trend = summary.volume.trend,
        resolution = summary.volume.resolution,
        coverage = summary.coverage,
        historyStart = summary.historyStart
    )
    CompactEditorSection(
        title = stringResource(volumeTrendTitle(summary.volume.resolution)).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_VOLUME_CHART)
    ) {
        if (trend.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_volume_unavailable),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SeriesChart(
                points = trend,
                contentDescription = volumeChartDescription(trend),
                subdued = true,
                chartHeight = 180.dp,
                valueDomain = ChartValueDomain.NonNegative,
                snapXAxisToPoints = true,
                yAxisLabel = ReportVolumeFormat::axis,
                xAxisLabel = { date ->
                    ReportChartSeries.axisLabel(
                        date = date,
                        resolution = summary.volume.resolution,
                        period = summary.period,
                        plotted = trend
                    )
                }
            )
        }
    }
}

@Composable
private fun MuscleSection(summary: ReportSummary) {
    val top = summary.topMuscles()
    CompactEditorSection(
        title = stringResource(R.string.reports_muscles_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(REPORT_MUSCLES)
    ) {
        if (top.isEmpty()) {
            Text(
                text = stringResource(R.string.reports_muscles_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                top.forEach { count ->
                    StatRow(
                        label = stringResource(count.muscle.labelRes()),
                        value = pluralStringResource(
                            R.plurals.statistics_muscle_sets,
                            count.completedSetCount,
                            count.completedSetCount
                        )
                    )
                }
            }
        }
    }
}

private fun volumeTrendTitle(resolution: VolumeTrendResolution): Int {
    return when (resolution) {
        VolumeTrendResolution.Daily -> R.string.statistics_volume_trend_daily
        VolumeTrendResolution.Weekly -> R.string.statistics_volume_trend
        VolumeTrendResolution.Monthly -> R.string.statistics_volume_trend_monthly
    }
}

@Composable
private fun volumeChartDescription(points: List<SeriesPoint>): String {
    val values = points.map { it.value }
    return stringResource(
        R.string.statistics_volume_chart_description,
        points.size,
        ReportVolumeFormat.kilograms(values.minOrNull() ?: 0.0),
        ReportVolumeFormat.kilograms(values.maxOrNull() ?: 0.0)
    )
}

private fun ReportKind.labelRes(): Int {
    return when (this) {
        ReportKind.Monthly -> R.string.reports_kind_monthly
        ReportKind.Quarterly -> R.string.reports_kind_quarterly
        ReportKind.HalfYear -> R.string.reports_kind_half_year
        ReportKind.Yearly -> R.string.reports_kind_yearly
    }
}
