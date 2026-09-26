package app.mymusclemap.ui.statistics

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.model.ChartPoint
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.WeightedLoadVolume
import app.mymusclemap.domain.statistics.WeeklyVolumePoint
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.components.WeightChart
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme
import java.time.LocalDate

internal const val STATISTICS_ROOT = "statistics-root"
internal const val STATISTICS_EMPTY = "statistics-empty"
internal const val STATISTICS_CONSISTENCY = "statistics-consistency"
internal const val STATISTICS_VOLUME = "statistics-volume"
internal const val STATISTICS_PROGRESS = "statistics-progress"
internal const val STATISTICS_MUSCLES = "statistics-muscles"

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onBack: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(STATISTICS_ROOT),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            StatisticsHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(
                        top = AppDimens.headerStackGap,
                        bottom = AppDimens.scrollEndPadding
                    )
            ) {
                if (!state.loading && !state.dashboard.hasCompletedWorkouts) {
                    Text(
                        text = stringResource(R.string.statistics_empty_title),
                        style = AppTypeTokens.sectionTitle,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag(STATISTICS_EMPTY)
                    )
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = stringResource(R.string.statistics_empty_body),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!state.loading) {
                    ConsistencySection(state.dashboard)
                    CompactEditorDivider()
                    VolumeSection(state.dashboard)
                    CompactEditorDivider()
                    ProgressSection(state.dashboard)
                    CompactEditorDivider()
                    MuscleSection(state.dashboard)
                }
            }
        }
    }
}

@Composable
private fun StatisticsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(AppDimens.minTouch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = stringResource(R.string.statistics_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConsistencySection(dashboard: TrainingStatistics) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_consistency_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_CONSISTENCY)
    ) {
        StatisticsCard {
            StatRow(
                label = stringResource(R.string.statistics_last_7_days),
                value = pluralStringResource(
                    R.plurals.statistics_workouts,
                    dashboard.workoutsLast7Days,
                    dashboard.workoutsLast7Days
                )
            )
            StatRow(
                label = stringResource(R.string.statistics_training_days_7),
                value = pluralStringResource(
                    R.plurals.statistics_training_days,
                    dashboard.trainingDaysLast7Days,
                    dashboard.trainingDaysLast7Days
                )
            )
            StatRow(
                label = stringResource(R.string.statistics_last_30_days),
                value = pluralStringResource(
                    R.plurals.statistics_workouts,
                    dashboard.workoutsLast30Days,
                    dashboard.workoutsLast30Days
                )
            )
            dashboard.recentWeeklyFrequency?.let { frequency ->
                StatRow(
                    label = stringResource(R.string.statistics_weekly_frequency),
                    value = stringResource(R.string.statistics_frequency_value, frequency)
                )
            }
        }
    }
}

@Composable
private fun VolumeSection(dashboard: TrainingStatistics) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_volume_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_VOLUME)
    ) {
        val load = dashboard.weightedLoad
        if (load == null) {
            Text(
                text = stringResource(R.string.statistics_volume_unavailable),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                Text(
                    text = stringResource(R.string.statistics_volume_scope),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppDimens.itemGap))
                StatRow(
                    label = stringResource(R.string.statistics_volume_7_days),
                    value = UiFormatters.weightKg(load.last7DaysKg)
                )
                StatRow(
                    label = stringResource(R.string.statistics_volume_30_days),
                    value = UiFormatters.weightKg(load.last30DaysKg)
                )
                if (dashboard.hasVolumeTrend) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = stringResource(R.string.statistics_volume_trend),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    WeightChart(
                        points = dashboard.weeklyVolume.map { ChartPoint(it.weekStart, it.volumeKg) },
                        contentDescription = volumeChartDescription(dashboard.weeklyVolume),
                        subdued = true,
                        chartHeight = 180.dp
                    )
                } else {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = stringResource(R.string.statistics_trend_insufficient),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressSection(dashboard: TrainingStatistics) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_progress_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_PROGRESS)
    ) {
        if (dashboard.exerciseProgress.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_progress_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            dashboard.exerciseProgress.forEachIndexed { index, summary ->
                if (index > 0) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                }
                ExerciseProgressCard(summary)
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(summary: ExerciseProgressSummary) {
    StatisticsCard {
        Text(
            text = summary.name,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(summary.measurementType.labelRes()),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        summary.best?.let { best ->
            StatRow(
                label = stringResource(R.string.statistics_best),
                value = bestLabel(best)
            )
        }
        summary.recent?.let { recent ->
            StatRow(
                label = stringResource(R.string.statistics_latest),
                value = bestLabel(recent)
            )
        }
        if (summary.hasProgression) {
            Spacer(Modifier.height(AppDimens.itemGap))
            WeightChart(
                points = summary.history.map { ChartPoint(it.date, it.value) },
                contentDescription = stringResource(
                    R.string.statistics_progress_chart_description,
                    summary.name,
                    summary.history.size
                ),
                subdued = true,
                chartHeight = 140.dp
            )
        } else {
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = stringResource(R.string.statistics_progress_insufficient),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MuscleSection(dashboard: TrainingStatistics) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_muscles_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_MUSCLES)
    ) {
        Text(
            text = stringResource(R.string.statistics_muscles_scope),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        if (dashboard.recentlyTrainedMuscles.isEmpty() && dashboard.mostTrainedMuscles.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_muscles_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                if (dashboard.recentlyTrainedMuscles.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.statistics_muscles_recent),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    dashboard.recentlyTrainedMuscles.forEach { count ->
                        MuscleRow(count)
                    }
                }
                if (dashboard.mostTrainedMuscles.isNotEmpty()) {
                    if (dashboard.recentlyTrainedMuscles.isNotEmpty()) {
                        Spacer(Modifier.height(AppDimens.itemGap))
                    }
                    Text(
                        text = stringResource(R.string.statistics_muscles_frequent),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    dashboard.mostTrainedMuscles.forEach { count ->
                        MuscleRow(count)
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleRow(count: MuscleTrainingCount) {
    StatRow(
        label = stringResource(count.muscle.labelRes()),
        value = pluralStringResource(
            R.plurals.statistics_muscle_sets,
            count.completedSetCount,
            count.completedSetCount
        )
    )
}

@Composable
private fun StatisticsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapeTokens.surface,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.heroPadding),
            content = content
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = AppTypeTokens.statValue,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = AppDimens.itemGap)
        )
    }
}

@Composable
private fun bestLabel(best: ExerciseBest): String {
    return when (best) {
        is ExerciseBest.Reps -> stringResource(R.string.statistics_best_reps, best.reps)
        is ExerciseBest.WeightedSet -> if (best.perSide) {
            stringResource(
                R.string.statistics_best_weight_per_side,
                UiFormatters.weightValue(best.recordedKg),
                best.reps,
                UiFormatters.weightKg(best.effectiveKg)
            )
        } else {
            stringResource(
                R.string.statistics_best_weight,
                UiFormatters.weightKg(best.effectiveKg),
                best.reps
            )
        }
        is ExerciseBest.Duration -> ElapsedTime.formatMillis(best.seconds * 1000L)
        is ExerciseBest.Distance -> distanceLabel(best)
        is ExerciseBest.Completions -> pluralStringResource(
            R.plurals.statistics_completions,
            best.count,
            best.count
        )
    }
}

@Composable
private fun distanceLabel(best: ExerciseBest.Distance): String {
    val distance = if (best.meters >= 1000.0) {
        stringResource(
            R.string.set_copy_distance_km,
            QuantityParser.formatDisplay(QuantityParser.fromMeters(best.meters, DistanceUnit.KILOMETERS))
        )
    } else {
        stringResource(
            R.string.set_copy_distance_m,
            QuantityParser.formatDisplay(best.meters)
        )
    }
    val duration = best.durationSeconds?.let { ElapsedTime.formatMillis(it * 1000L) }
    return if (duration == null) {
        distance
    } else {
        stringResource(R.string.statistics_best_distance_duration, distance, duration)
    }
}

@Composable
private fun volumeChartDescription(points: List<WeeklyVolumePoint>): String {
    val values = points.map { it.volumeKg }
    return stringResource(
        R.string.statistics_volume_chart_description,
        points.size,
        UiFormatters.weightKg(values.minOrNull() ?: 0.0),
        UiFormatters.weightKg(values.maxOrNull() ?: 0.0)
    )
}

@Preview(showBackground = true, name = "Statistics light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Statistics dark")
@Composable
private fun StatisticsPreview() {
    val today = LocalDate.of(2026, 9, 16)
    WeightTrackerTheme {
        StatisticsScreen(
            state = StatisticsUiState(
                loading = false,
                dashboard = TrainingStatistics(
                    completedWorkoutCount = 4,
                    workoutsLast7Days = 2,
                    trainingDaysLast7Days = 2,
                    workoutsLast30Days = 4,
                    trainingDaysLast30Days = 4,
                    recentWeeklyFrequency = 1.0,
                    weightedLoad = WeightedLoadVolume(800.0, 2400.0, 6, 18),
                    weeklyVolume = listOf(
                        WeeklyVolumePoint(today.minusWeeks(2), 400.0),
                        WeeklyVolumePoint(today.minusWeeks(1), 800.0),
                        WeeklyVolumePoint(today.minusWeeks(0), 800.0)
                    ),
                    exerciseProgress = listOf(
                        ExerciseProgressSummary(
                            exerciseId = 1L,
                            name = "Bench press",
                            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                            lastTrained = today,
                            best = ExerciseBest.WeightedSet(80.0, 80.0, 5, false),
                            recent = ExerciseBest.WeightedSet(80.0, 80.0, 5, false),
                            history = listOf(
                                app.mymusclemap.domain.statistics.ExerciseHistoryPoint(today.minusWeeks(2), 70.0),
                                app.mymusclemap.domain.statistics.ExerciseHistoryPoint(today, 80.0)
                            ),
                            historyKind = ExerciseHistoryKind.EFFECTIVE_KG
                        )
                    ),
                    recentlyTrainedMuscles = listOf(
                        MuscleTrainingCount(MuscleGroup.CHEST, 6, 2, today)
                    ),
                    mostTrainedMuscles = listOf(
                        MuscleTrainingCount(MuscleGroup.CHEST, 18, 4, today)
                    )
                )
            ),
            onBack = {}
        )
    }
}
