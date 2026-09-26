package app.mymusclemap.ui.statistics

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
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.model.ChartValueDomain
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.statistics.ExerciseHistoryKind
import app.mymusclemap.domain.statistics.ExerciseProgressSummary
import app.mymusclemap.domain.statistics.MuscleRestSummary
import app.mymusclemap.domain.statistics.MuscleTrainingCount
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.SeriesChart
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens
import androidx.compose.ui.unit.dp

internal const val STATISTICS_MUSCLE_DETAILS = "statistics-muscle-details"
internal const val STATISTICS_REST_DETAILS = "statistics-rest-details"
internal const val STATISTICS_EXERCISE_LIST = "statistics-exercise-list"
internal const val STATISTICS_EXERCISE_DETAIL = "statistics-exercise-detail"

@Composable
fun StatisticsMuscleDistributionScreen(
    muscles: List<MuscleTrainingCount>,
    onBack: () -> Unit
) {
    StatisticsDetailScaffold(
        title = stringResource(R.string.statistics_muscles_details_title),
        testTag = STATISTICS_MUSCLE_DETAILS,
        onBack = onBack
    ) {
        Text(
            text = stringResource(R.string.statistics_muscles_scope),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        StatisticsCard {
            muscles.forEach { count ->
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

@Composable
fun StatisticsRestScreen(
    rest: List<MuscleRestSummary>,
    onBack: () -> Unit
) {
    StatisticsDetailScaffold(
        title = stringResource(R.string.statistics_rest_details_title),
        testTag = STATISTICS_REST_DETAILS,
        onBack = onBack
    ) {
        Text(
            text = stringResource(R.string.statistics_rest_scope),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        rest.forEach { summary ->
            CompactEditorSection(
                title = stringResource(summary.muscle.labelRes()).uppercase(AppLocale.UI)
            ) {
                StatisticsCard {
                    StatRow(
                        label = stringResource(R.string.statistics_rest_average),
                        value = stringResource(R.string.statistics_rest_days_value, summary.averageDays)
                    )
                    StatRow(
                        label = stringResource(R.string.statistics_rest_shortest),
                        value = pluralStringResource(
                            R.plurals.statistics_rest_whole_days,
                            summary.shortestDays,
                            summary.shortestDays
                        )
                    )
                    StatRow(
                        label = stringResource(R.string.statistics_rest_longest),
                        value = pluralStringResource(
                            R.plurals.statistics_rest_whole_days,
                            summary.longestDays,
                            summary.longestDays
                        )
                    )
                    StatRow(
                        label = stringResource(R.string.statistics_rest_sessions),
                        value = summary.sessionDates.toString()
                    )
                }
            }
            Spacer(Modifier.height(AppDimens.itemGap))
        }
    }
}

@Composable
fun StatisticsExerciseListScreen(
    exercises: List<ExerciseProgressSummary>,
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit
) {
    StatisticsDetailScaffold(
        title = stringResource(R.string.statistics_exercises_all_title),
        testTag = STATISTICS_EXERCISE_LIST,
        onBack = onBack
    ) {
        StatisticsCard {
            exercises.forEach { summary ->
                DestinationRow(
                    title = summary.name,
                    subtitle = summary.recent?.let { bestLabel(it) },
                    onClick = { onOpenExercise(summary.exerciseId) },
                    testTag = "statistics-exercise-all-${summary.exerciseId}"
                )
            }
        }
    }
}

@Composable
fun StatisticsExerciseDetailScreen(
    summary: ExerciseProgressSummary?,
    onBack: () -> Unit
) {
    StatisticsDetailScaffold(
        title = summary?.name ?: stringResource(R.string.statistics_exercises_title),
        testTag = STATISTICS_EXERCISE_DETAIL,
        onBack = onBack
    ) {
        if (summary == null) {
            Text(
                text = stringResource(R.string.statistics_exercise_missing),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@StatisticsDetailScaffold
        }
        Text(
            text = stringResource(summary.measurementType.labelRes()),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        StatisticsCard {
            if (summary.measurementType == MeasurementType.COMPLETION_ONLY) {
                summary.best?.let { best ->
                    StatRow(
                        label = stringResource(R.string.statistics_rest_sessions),
                        value = bestLabel(best)
                    )
                }
            } else {
                if (summary.showsBestSeparately) {
                    summary.best?.let { best ->
                        StatRow(label = stringResource(R.string.statistics_best), value = bestLabel(best))
                    }
                }
                summary.recent?.let { recent ->
                    StatRow(label = stringResource(R.string.statistics_latest), value = bestLabel(recent))
                }
            }
            if (summary.hasProgression && summary.historyKind != ExerciseHistoryKind.COMPLETIONS) {
                Spacer(Modifier.height(AppDimens.itemGap))
                SeriesChart(
                    points = summary.history.map { SeriesPoint(it.date, it.value) },
                    contentDescription = stringResource(
                        R.string.statistics_progress_chart_description,
                        summary.name,
                        summary.history.size
                    ),
                    subdued = true,
                    chartHeight = 160.dp,
                    valueDomain = ChartValueDomain.NonNegative
                )
            } else if (!summary.hasProgression) {
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = stringResource(R.string.statistics_progress_insufficient),
                    style = AppTypeTokens.statCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatisticsDetailScaffold(
    title: String,
    testTag: String,
    onBack: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            StatisticsHeader(title = title, onBack = onBack)
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
                    ),
                content = content
            )
        }
    }
}
