package app.mymusclemap.ui.workoutimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workoutimport.WorkoutImportPlan
import app.mymusclemap.domain.workoutimport.WorkoutImportPreviewCopy
import app.mymusclemap.domain.workoutimport.WorkoutImportResolvedExercise
import app.mymusclemap.domain.workoutimport.WorkoutImportResolvedWorkout
import app.mymusclemap.ui.exercises.labelRes
import app.mymusclemap.ui.theme.AppDimens

@Composable
fun WorkoutImportPreviewSection(
    fileName: String?,
    plan: WorkoutImportPlan,
    warningCount: Int,
    expandedWorkoutIds: Set<String>,
    onToggleWorkout: (String) -> Unit
) {
    val resources = LocalResources.current
    val sources = WorkoutImportPreviewCopy.bodyWeightSources(plan)
        .map { resources.getString(WorkoutImportMessages.bodyWeightSourceRes(it)) }
        .distinct()
        .joinToString(", ")
    Column(modifier = Modifier.testTag("workout_import_preview_summary")) {
        Text(
            text = stringResource(R.string.workout_import_preview_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        if (fileName != null) {
            Text(stringResource(R.string.workout_import_file_name, fileName))
        }
        Text(stringResource(R.string.workout_import_summary_workouts, plan.workoutCount))
        plan.dateRange?.let { range ->
            Text(
                stringResource(
                    R.string.workout_import_summary_range,
                    WorkoutImportPreviewCopy.dateRange(range)
                )
            )
        }
        Text(
            stringResource(
                R.string.workout_import_summary_exercises,
                plan.workouts.flatMap { workout ->
                    workout.exercises.mapNotNull { it.snapshot?.exerciseId }
                }.distinct().size
            )
        )
        Text(stringResource(R.string.workout_import_summary_completed, plan.completedSetCount))
        Text(stringResource(R.string.workout_import_summary_skipped, plan.skippedSetCount))
        Text(stringResource(R.string.workout_import_summary_warnings, warningCount))
        if (sources.isNotEmpty()) {
            Text(stringResource(R.string.workout_import_summary_body_weight, sources))
        }
        plan.workouts.forEach { workout ->
            Spacer(Modifier.height(12.dp))
            WorkoutCard(
                workout = workout,
                expanded = workout.workoutId in expandedWorkoutIds,
                onToggle = { onToggleWorkout(workout.workoutId) }
            )
        }
    }
}

@Composable
private fun WorkoutCard(
    workout: WorkoutImportResolvedWorkout,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val resources = LocalResources.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(workout.name, style = MaterialTheme.typography.titleMedium)
            Text(
                WorkoutImportPreviewCopy.dateLabel(workout.workoutDate),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    R.string.workout_import_times,
                    WorkoutImportPreviewCopy.timeLabel(workout.startedAt),
                    WorkoutImportPreviewCopy.timeLabel(workout.finishedAt)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(
                    R.string.workout_import_duration,
                    WorkoutImportPreviewCopy.duration(workout.durationMillis)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(stringResource(R.string.workout_import_exercise_count, workout.exercises.size))
            Text(
                stringResource(
                    R.string.workout_import_set_counts,
                    workout.completedSetCount,
                    workout.skippedSetCount
                )
            )
            Text(
                stringResource(
                    R.string.workout_import_body_weight,
                    WorkoutImportPreviewCopy.bodyWeightValue(workout.bodyWeight.kilograms)
                )
            )
            Text(
                stringResource(WorkoutImportMessages.bodyWeightSourceRes(workout.bodyWeight.source)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            workout.warnings.forEach { warning ->
                Text(
                    text = WorkoutImportMessages.warning(resources, warning),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = AppDimens.minTouch)
            ) {
                Text(
                    stringResource(
                        if (expanded) R.string.workout_import_collapse else R.string.workout_import_expand
                    )
                )
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                workout.exercises.forEach { exercise ->
                    ExercisePreview(exercise)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ExercisePreview(exercise: WorkoutImportResolvedExercise) {
    val resources = LocalResources.current
    val snapshot = exercise.snapshot
    Text(
        text = snapshot?.catalogName ?: exercise.incomingName,
        style = MaterialTheme.typography.titleSmall
    )
    if (snapshot != null && snapshot.catalogName != exercise.incomingName) {
        Text(
            text = stringResource(R.string.workout_import_incoming_name, exercise.incomingName),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (snapshot != null) {
        val muscles = buildList {
            add(stringResource(snapshot.primaryMuscle.labelRes()))
            snapshot.secondaryMuscles.forEach { add(stringResource(it.labelRes())) }
        }.joinToString()
        if (muscles.isNotBlank()) {
            Text(
                text = muscles,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    val interpretation = snapshot?.weightInterpretation ?: WeightInterpretation.NOT_APPLICABLE
    exercise.sets.forEach { set ->
        Text(
            text = WorkoutImportPreviewCopy.setLine(resources, set, interpretation),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
