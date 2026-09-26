package app.mymusclemap.ui.statistics

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.statistics.ExerciseBest
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseHistoryPoint
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleRestSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
import app.mymusclemap.domain.statistics.PlanAdherence
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.statistics.TrainingActivity
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.TrainingStatisticsLogic
import app.mymusclemap.domain.statistics.TrainingVolume
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.SeriesChart
import app.mymusclemap.ui.components.UiFormatters
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.WeightTrackerTheme
import java.time.LocalDate

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onBack: () -> Unit,
    onRangeSelected: (StatisticsRange) -> Unit = {},
    onDismissLocked: () -> Unit = {},
    onOpenMuscleDistribution: () -> Unit = {},
    onOpenRest: () -> Unit = {},
    onOpenExercises: () -> Unit = {},
    onOpenExercise: (Long) -> Unit = {}
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
            StatisticsHeader(title = stringResource(R.string.statistics_title), onBack = onBack)
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
                StatisticsRangeSelector(selected = state.range, onSelected = onRangeSelected)
                Spacer(Modifier.height(AppDimens.sectionGap))
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
                    CompactEditorDivider()
                    AdherenceSection(state.dashboard.adherence)
                } else if (!state.loading) {
                    ActivitySection(state.dashboard.activity)
                    CompactEditorDivider()
                    AdherenceSection(state.dashboard.adherence)
                    CompactEditorDivider()
                    VolumeSection(state.dashboard.volume)
                    CompactEditorDivider()
                    MuscleSection(
                        muscles = state.dashboard.muscleDistribution,
                        onSeeDetails = onOpenMuscleDistribution
                    )
                    CompactEditorDivider()
                    RestSection(
                        rest = state.dashboard.restBetweenSessions,
                        onSeeDetails = onOpenRest
                    )
                    CompactEditorDivider()
                    ExerciseSection(
                        exercises = state.dashboard.exercises,
                        onSeeAll = onOpenExercises,
                        onOpenExercise = onOpenExercise
                    )
                }
            }
        }
    }
    state.lockedFeature?.let { feature ->
        ProInfoSheet(feature = feature, onDismiss = onDismissLocked)
    }
}

@Composable
private fun ActivitySection(activity: TrainingActivity) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_activity_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_ACTIVITY)
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
                label = stringResource(R.string.statistics_activity_sets),
                value = pluralStringResource(
                    R.plurals.weekly_overview_sets,
                    activity.completedSetCount,
                    activity.completedSetCount
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
            activity.durationMillis?.let { duration ->
                StatRow(
                    label = stringResource(R.string.statistics_activity_duration),
                    value = ElapsedTime.formatMillis(duration)
                )
            }
        }
    }
}

@Composable
private fun AdherenceSection(adherence: PlanAdherence) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_adherence_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_ADHERENCE)
    ) {
        val percent = adherence.percent
        if (percent == null) {
            Text(
                text = stringResource(R.string.statistics_adherence_empty),
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
private fun VolumeSection(volume: TrainingVolume) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_volume_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_VOLUME)
    ) {
        if (!volume.hasTotal) {
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
                    label = stringResource(R.string.statistics_volume_total),
                    value = UiFormatters.weightKg(volume.totalKg ?: 0.0)
                )
                if (volume.hasTrend) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                    Text(
                        text = stringResource(R.string.statistics_volume_trend),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    SeriesChart(
                        points = volume.trend,
                        contentDescription = volumeChartDescription(volume.trend),
                        subdued = true,
                        chartHeight = 180.dp,
                        valueDomain = ChartValueDomain.NonNegative
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
private fun MuscleSection(
    muscles: List<MuscleTrainingCount>,
    onSeeDetails: () -> Unit
) {
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
        if (muscles.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_muscles_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                TrainingStatisticsLogic.summaryMuscles(muscles).forEach { count ->
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
            if (muscles.size > TrainingStatisticsLogic.SUMMARY_MUSCLES) {
                SeeMoreRow(
                    label = stringResource(R.string.statistics_see_details),
                    onClick = onSeeDetails,
                    testTag = STATISTICS_SEE_MUSCLES
                )
            }
        }
    }
}

@Composable
private fun RestSection(
    rest: List<MuscleRestSummary>,
    onSeeDetails: () -> Unit
) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_rest_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_REST)
    ) {
        Text(
            text = stringResource(R.string.statistics_rest_scope),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        if (rest.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_rest_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                TrainingStatisticsLogic.summaryRest(rest).forEach { summary ->
                    StatRow(
                        label = stringResource(summary.muscle.labelRes()),
                        value = stringResource(R.string.statistics_rest_days_value, summary.averageDays)
                    )
                }
            }
            if (rest.size > TrainingStatisticsLogic.SUMMARY_REST) {
                SeeMoreRow(
                    label = stringResource(R.string.statistics_see_details),
                    onClick = onSeeDetails,
                    testTag = STATISTICS_SEE_REST
                )
            }
        }
    }
}

@Composable
private fun ExerciseSection(
    exercises: List<ExerciseProgressSummary>,
    onSeeAll: () -> Unit,
    onOpenExercise: (Long) -> Unit
) {
    CompactEditorSection(
        title = stringResource(R.string.statistics_exercises_title).uppercase(AppLocale.UI),
        modifier = Modifier.testTag(STATISTICS_EXERCISES)
    ) {
        if (exercises.isEmpty()) {
            Text(
                text = stringResource(R.string.statistics_exercises_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            StatisticsCard {
                TrainingStatisticsLogic.summaryExercises(exercises).forEach { summary ->
                    DestinationRow(
                        title = summary.name,
                        subtitle = latestSubtitle(summary),
                        onClick = { onOpenExercise(summary.exerciseId) },
                        testTag = "statistics-exercise-${summary.exerciseId}"
                    )
                }
            }
            if (exercises.size > TrainingStatisticsLogic.SUMMARY_EXERCISES) {
                SeeMoreRow(
                    label = stringResource(R.string.statistics_see_all),
                    onClick = onSeeAll,
                    testTag = STATISTICS_SEE_EXERCISES
                )
            }
        }
    }
}

@Composable
private fun latestSubtitle(summary: ExerciseProgressSummary): String? {
    val recent = summary.recent ?: return null
    return if (summary.historyKind == ExerciseHistoryKind.COMPLETIONS) {
        bestLabel(recent)
    } else {
        bestLabel(recent)
    }
}

@Composable
private fun volumeChartDescription(points: List<SeriesPoint>): String {
    val values = points.map { it.value }
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
                range = StatisticsRange.Days30,
                dashboard = TrainingStatistics(
                    range = StatisticsRange.Days30,
                    activity = TrainingActivity(4, 18, 4, 3_600_000L),
                    adherence = PlanAdherence(plannedCount = 12, completedCount = 10, missedCount = 2),
                    volume = TrainingVolume(
                        totalKg = 2400.0,
                        completedSetCount = 12,
                        trend = listOf(
                            SeriesPoint(today.minusWeeks(2), 400.0),
                            SeriesPoint(today.minusWeeks(1), 800.0)
                        )
                    ),
                    muscleDistribution = listOf(
                        MuscleTrainingCount(MuscleGroup.CHEST, 18, 4, today)
                    ),
                    restBetweenSessions = listOf(
                        MuscleRestSummary(MuscleGroup.CHEST, 4, 2.8, 1, 6, today)
                    ),
                    exercises = listOf(
                        ExerciseProgressSummary(
                            exerciseId = 1L,
                            name = "Bench press",
                            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                            lastTrained = today,
                            best = ExerciseBest.WeightedSet(80.0, 80.0, 5, false),
                            recent = ExerciseBest.WeightedSet(80.0, 80.0, 5, false),
                            history = listOf(
                                ExerciseHistoryPoint(today.minusWeeks(2), 70.0),
                                ExerciseHistoryPoint(today, 80.0)
                            ),
                            historyKind = ExerciseHistoryKind.EFFECTIVE_KG
                        )
                    )
                )
            ),
            onBack = {}
        )
    }
}
