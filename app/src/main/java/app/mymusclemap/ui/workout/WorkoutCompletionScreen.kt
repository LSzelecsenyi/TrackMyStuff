package app.mymusclemap.ui.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.achievements.HighlightMeasure
import app.mymusclemap.domain.achievements.PerformanceHighlight
import app.mymusclemap.domain.achievements.PerformanceRecordKind
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.journal.WorkoutSetCopy
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.WorkoutSummaryExercise
import app.mymusclemap.ui.achievements.BadgeArtworkResolver
import app.mymusclemap.ui.achievements.celebrationDetail
import app.mymusclemap.ui.achievements.celebrationName
import app.mymusclemap.ui.achievements.artworkAchievementId
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand

internal const val WORKOUT_COMPLETE_SCREEN = "workout-complete-screen"
internal const val WORKOUT_COMPLETE_TITLE = "workout-complete-title"
internal const val WORKOUT_COMPLETE_SUMMARY = "workout-complete-summary"
internal const val WORKOUT_COMPLETE_BACK = "workout-complete-back"
internal const val WORKOUT_COMPLETE_HEATMAP = "workout-complete-heatmap"
internal const val WORKOUT_COMPLETE_CELEBRATIONS = "workout-complete-celebrations"
internal const val WORKOUT_COMPLETE_LOADING = "workout-complete-loading"
internal const val WORKOUT_COMPLETE_HIGHLIGHTS = "workout-complete-highlights"
internal const val WORKOUT_COMPLETE_EXERCISES = "workout-complete-exercises"

@Composable
fun WorkoutCompletionScreen(
    state: WorkoutSummaryUiState,
    onLeave: () -> Unit,
    playAnimation: Boolean = true,
    celebrations: List<PendingCelebration> = emptyList()
) {
    var acceptLeave by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        if (acceptLeave && !leaving) {
            leaving = true
            onLeave()
        }
    }
    LaunchedEffect(Unit) {
        // A finish-dialog dismissal can arrive as Back on the first frames of this screen.
        withFrameNanos { }
        withFrameNanos { }
        acceptLeave = true
    }
    BackHandler {
        if (acceptLeave && !leaving) {
            leaving = true
            onLeave()
        }
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WORKOUT_COMPLETE_SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding, vertical = AppDimens.sectionGap)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WorkoutCompletionMark(
                        contentDescription = stringResource(R.string.workout_complete_mark_a11y),
                        playAnimation = playAnimation
                    )
                    Spacer(Modifier.height(AppDimens.sectionGap))
                    Text(
                        text = stringResource(R.string.workout_complete_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .testTag(WORKOUT_COMPLETE_TITLE)
                            .semantics { heading() }
                    )
                }
                when (state) {
                    WorkoutSummaryUiState.Loading -> {
                        Spacer(Modifier.height(AppDimens.sectionGap))
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag(WORKOUT_COMPLETE_LOADING),
                            color = StrictBrand.result()
                        )
                    }
                    WorkoutSummaryUiState.Unavailable -> {
                        Spacer(Modifier.height(AppDimens.sectionGap))
                        Text(
                            text = stringResource(R.string.workout_summary_unavailable),
                            style = AppTypeTokens.statSecondary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(WORKOUT_COMPLETE_SUMMARY)
                        )
                    }
                    is WorkoutSummaryUiState.Ready -> {
                        ReadySummary(state, celebrations)
                    }
                }
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            Button(
                onClick = leave,
                enabled = !leaving,
                shape = AppShapeTokens.button,
                colors = StrictBrand.actionButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
                    .testTag(WORKOUT_COMPLETE_BACK)
            ) {
                Text(stringResource(R.string.workout_complete_back))
            }
        }
    }
}

@Composable
private fun ReadySummary(
    state: WorkoutSummaryUiState.Ready,
    celebrations: List<PendingCelebration>
) {
    val summary = state.summary
    summary.workoutName?.let { name ->
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout-complete-name")
        )
    }
    Spacer(Modifier.height(AppDimens.sectionGap))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WORKOUT_COMPLETE_SUMMARY),
        verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
    ) {
        MetricRow(
            stringResource(R.string.workout_summary_duration),
            summary.durationLabel,
            "workout-complete-duration"
        )
        MetricRow(
            stringResource(R.string.workout_summary_exercises),
            summary.exerciseCount.toString(),
            "workout-complete-exercise-count"
        )
        MetricRow(
            stringResource(R.string.workout_summary_sets),
            summary.completedSetCount.toString(),
            "workout-complete-set-count"
        )
        summary.volumeKg?.let { volume ->
            MetricRow(
                stringResource(R.string.workout_summary_volume),
                stringResource(R.string.set_copy_weight, QuantityParser.formatDisplay(volume)),
                "workout-complete-volume"
            )
        }
    }
    if (summary.highlights.isNotEmpty()) {
        SectionTitle(stringResource(R.string.workout_summary_highlights))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WORKOUT_COMPLETE_HIGHLIGHTS),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            summary.highlights.forEach { highlight ->
                HighlightCard(highlight)
            }
        }
    }
    if (celebrations.isNotEmpty()) {
        SectionTitle(stringResource(R.string.workout_summary_achievements))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WORKOUT_COMPLETE_CELEBRATIONS),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            celebrations.forEach { celebration ->
                AchievementRow(celebration)
            }
        }
    }
    if (summary.exercises.isNotEmpty()) {
        SectionTitle(stringResource(R.string.workout_summary_exercises_section))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WORKOUT_COMPLETE_EXERCISES),
            verticalArrangement = Arrangement.spacedBy(AppDimens.sectionGap)
        ) {
            summary.exercises.forEach { exercise ->
                ExerciseBlock(exercise)
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, tag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(AppDimens.sectionGap))
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
    )
    Spacer(Modifier.height(AppDimens.itemGap))
}

@Composable
private fun HighlightCard(highlight: PerformanceHighlight) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = highlightLabel(highlight.kind),
            style = MaterialTheme.typography.titleSmall,
            color = StrictBrand.result()
        )
        highlight.exerciseName?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = stringResource(R.string.workout_summary_previous, formatMeasure(highlight.previous, highlight.measure)),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(R.string.workout_summary_new, formatMeasure(highlight.current, highlight.measure)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun highlightLabel(kind: PerformanceRecordKind): String {
    return when (kind) {
        PerformanceRecordKind.WEIGHT -> stringResource(R.string.workout_summary_weight_pr)
        PerformanceRecordKind.REPS -> stringResource(R.string.workout_summary_rep_record)
        PerformanceRecordKind.VOLUME -> stringResource(R.string.workout_summary_volume_record)
    }
}

@Composable
private fun formatMeasure(value: Double, measure: HighlightMeasure): String {
    val number = if (measure == HighlightMeasure.REPS) {
        value.toInt().toString()
    } else {
        QuantityParser.formatDisplay(value)
    }
    return when (measure) {
        HighlightMeasure.ADDED_KG -> stringResource(R.string.set_copy_added_weight, number)
        HighlightMeasure.EXTERNAL_KG -> stringResource(R.string.set_copy_weight, number)
        HighlightMeasure.PER_SIDE_TOTAL_KG -> stringResource(R.string.set_copy_weight_total, number)
        HighlightMeasure.REPS -> stringResource(R.string.set_copy_reps, number)
        HighlightMeasure.VOLUME_KG -> stringResource(R.string.set_copy_weight, number)
    }
}

@Composable
private fun AchievementRow(celebration: PendingCelebration) {
    val artworkId = celebration.artworkAchievementId()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artworkId != null) {
            Image(
                painter = painterResource(BadgeArtworkResolver.drawableFor(artworkId.name)),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(72.dp)
                    .testTag("workout-complete-badge-${artworkId.name}")
            )
            Spacer(Modifier.width(AppDimens.itemGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = celebrationName(celebration),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = celebrationDetail(celebration),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseBlock(exercise: WorkoutSummaryExercise) {
    val resources = LocalResources.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = pluralStringResource(
                R.plurals.workout_summary_set_count,
                exercise.completedSets.size,
                exercise.completedSets.size
            ),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        exercise.completedSets.forEach { set ->
            val performed = WorkoutSetCopy.performedValue(resources, set, exercise.interpretation)
            if (performed.isNotBlank()) {
                Text(
                    text = performed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
