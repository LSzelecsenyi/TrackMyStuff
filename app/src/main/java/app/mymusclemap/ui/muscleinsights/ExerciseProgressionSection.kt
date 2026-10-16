package app.mymusclemap.ui.muscleinsights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.muscleinsights.MuscleInsightTopic
import app.mymusclemap.domain.performance.ExercisePerformanceComparison
import app.mymusclemap.domain.performance.MuscleExercisePerformance
import app.mymusclemap.domain.performance.RecordedPerformanceOutcome
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppTypeTokens

internal const val MUSCLE_INSIGHTS_PROGRESSION_CARD = "muscle-insights-progression-card"
internal const val MUSCLE_INSIGHTS_PROGRESSION_INFO = "muscle-insights-progression-info"
internal const val MUSCLE_INSIGHTS_PROGRESSION_EMPTY = "muscle-insights-progression-empty"
internal const val MUSCLE_INSIGHTS_PROGRESSION_DETAIL = "muscle-insights-progression-detail"
internal const val MUSCLE_INSIGHTS_PROGRESSION_CLOSE = "muscle-insights-progression-close"
internal const val MUSCLE_INSIGHTS_PROGRESSION_PREVIOUS = "muscle-insights-progression-previous"
internal const val MUSCLE_INSIGHTS_PROGRESSION_CURRENT = "muscle-insights-progression-current"

internal fun progressionRowTag(exerciseId: Long): String = "muscle-insights-progression-row-$exerciseId"

internal fun progressionSummaryTag(exerciseId: Long): String = "muscle-insights-progression-summary-$exerciseId"

@Composable
internal fun ProgressionSection(
    progression: MuscleExercisePerformance,
    onExplain: (MuscleInsightTopic) -> Unit,
    onOpen: (Long) -> Unit
) {
    InsightSection(
        title = stringResource(R.string.exercise_progression_title),
        onExplain = { onExplain(MuscleInsightTopic.EXERCISE_PROGRESSION) },
        infoTag = MUSCLE_INSIGHTS_PROGRESSION_INFO,
        infoDescription = stringResource(R.string.exercise_progression_info),
        cardTag = MUSCLE_INSIGHTS_PROGRESSION_CARD
    ) {
        if (progression.exercises.isEmpty()) {
            Text(
                text = stringResource(R.string.exercise_progression_empty),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(MUSCLE_INSIGHTS_PROGRESSION_EMPTY)
            )
        } else {
            progression.exercises.forEachIndexed { index, exercise ->
                if (index > 0) {
                    Spacer(Modifier.height(AppDimens.itemGap))
                }
                ProgressionRow(exercise = exercise, onOpen = { onOpen(exercise.exerciseId) })
            }
        }
    }
}

@Composable
private fun ProgressionRow(
    exercise: ExercisePerformanceComparison,
    onOpen: () -> Unit
) {
    val description = progressionRowDescription(exercise)
    val summary = progressionSummary(exercise)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(progressionRowTag(exercise.exerciseId))
            .semantics { contentDescription = description }
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.displayName,
                style = AppTypeTokens.statValue,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = listSummary(exercise, summary),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(progressionSummaryTag(exercise.exerciseId))
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.exercise_progression_open_details),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun listSummary(exercise: ExercisePerformanceComparison, summary: String?): String {
    val classification = outcomeLabel(exercise.outcome)
    return if (summary == null) {
        classification
    } else {
        "$classification · $summary"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseProgressionDetailSheet(
    exercise: ExercisePerformanceComparison,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    val classification = outcomeLabel(exercise.outcome)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.itemGap)
                .testTag(MUSCLE_INSIGHTS_PROGRESSION_DETAIL)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = AppDimens.itemGap)
                ) {
                    Text(
                        text = exercise.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(AppDimens.headerStackGap))
                    Text(
                        text = classification,
                        style = AppTypeTokens.statValue,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(AppDimens.minTouch)
                        .testTag(MUSCLE_INSIGHTS_PROGRESSION_CLOSE)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.muscle_insights_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(AppDimens.itemGap))
                ComparisonDates(exercise)
                Spacer(Modifier.height(AppDimens.itemGap))
                if (exercise.comparisons.isEmpty()) {
                    Text(
                        text = limitationText(exercise.limitation),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    exercise.comparisons.forEach { match ->
                        val condition = comparisonCondition(match)
                        if (condition != null) {
                            Text(
                                text = condition,
                                style = AppTypeTokens.statValue,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = comparisonTransition(match),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = comparisonDelta(match),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(AppDimens.itemGap))
                    }
                    if (exercise.comparisons.size > 1 &&
                        exercise.outcome == RecordedPerformanceOutcome.MIXED
                    ) {
                        Text(
                            text = stringResource(R.string.exercise_progression_overall, classification),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonDates(exercise: ExercisePerformanceComparison) {
    val currentDate = progressionDate(exercise.currentDate)
    val referenceDate = exercise.referenceDate?.let(::progressionDate)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
    ) {
        if (referenceDate != null) {
            DateColumn(
                label = stringResource(R.string.exercise_progression_previous),
                date = referenceDate,
                description = stringResource(R.string.exercise_progression_reference_date, referenceDate),
                tag = MUSCLE_INSIGHTS_PROGRESSION_PREVIOUS,
                modifier = Modifier.weight(1f)
            )
        }
        DateColumn(
            label = stringResource(R.string.exercise_progression_current_label),
            date = currentDate,
            description = stringResource(R.string.exercise_progression_current_date, currentDate),
            tag = MUSCLE_INSIGHTS_PROGRESSION_CURRENT,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DateColumn(
    label: String,
    date: String,
    description: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description }
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = date,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
