package app.mymusclemap.domain.journal

import android.content.res.Resources
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.QuantityParser
import app.mymusclemap.domain.workout.RepetitionTarget
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus

data class WorkoutSetDisplay(
    val position: Int,
    val status: SessionSetStatus,
    val addedDuringWorkout: Boolean,
    val planned: String,
    val performed: String,
    val valuesDiffer: Boolean
)

object WorkoutSetCopy {
    fun display(resources: Resources, set: SessionSet, exercise: SessionExercise): WorkoutSetDisplay {
        val planned = plannedValue(resources, set, exercise.weightInterpretation)
        val performed = when (set.status) {
            SessionSetStatus.SKIPPED, SessionSetStatus.PENDING -> ""
            SessionSetStatus.COMPLETED -> performedValue(resources, set, exercise.weightInterpretation)
        }
        return WorkoutSetDisplay(
            position = set.position + 1,
            status = set.status,
            addedDuringWorkout = set.addedDuringWorkout,
            planned = planned,
            performed = performed,
            valuesDiffer = set.status == SessionSetStatus.COMPLETED &&
                performed.isNotBlank() &&
                planned.isNotBlank() &&
                performed != planned
        )
    }

    fun plannedValue(resources: Resources, set: SessionSet, interpretation: WeightInterpretation): String {
        return formatValue(
            resources = resources,
            repsText = plannedReps(set.plannedMinReps, set.plannedMaxReps),
            loadKind = set.plannedLoadKind,
            weightKg = set.plannedWeightKg,
            durationSeconds = set.plannedDurationSeconds,
            distanceMeters = set.plannedDistanceMeters,
            interpretation = interpretation
        )
    }

    fun performedValue(resources: Resources, set: SessionSet, interpretation: WeightInterpretation): String {
        return formatValue(
            resources = resources,
            repsText = set.actualReps?.toString(),
            loadKind = set.actualLoadKind,
            weightKg = set.actualWeightKg,
            durationSeconds = set.actualDurationSeconds,
            distanceMeters = set.actualDistanceMeters,
            interpretation = interpretation
        )
    }

    fun formatValue(
        resources: Resources,
        repsText: String?,
        loadKind: PlannedLoadKind?,
        weightKg: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        interpretation: WeightInterpretation,
        markTotalWeight: Boolean = false
    ): String {
        val parts = mutableListOf<String>()
        if (!repsText.isNullOrBlank()) {
            parts += resources.getString(R.string.set_copy_reps, repsText)
        }
        loadLabel(resources, loadKind, weightKg, interpretation, markTotalWeight)?.let { parts += it }
        if (distanceMeters != null) {
            parts += distanceLabel(resources, distanceMeters)
            durationSeconds?.let { parts += clock(it) }
        } else {
            durationSeconds?.let { parts += durationLabel(resources, it) }
        }
        return parts.joinToString(" · ")
    }

    private fun plannedReps(minReps: Int?, maxReps: Int?): String? {
        if (minReps == null || maxReps == null) {
            return null
        }
        return RepetitionTarget.display(minReps, maxReps)
    }

    private fun loadLabel(
        resources: Resources,
        kind: PlannedLoadKind?,
        weightKg: Double?,
        interpretation: WeightInterpretation,
        markTotalWeight: Boolean
    ): String? {
        val weight = weightKg?.let { QuantityParser.formatDisplay(it) }
        return when (kind) {
            null, PlannedLoadKind.NONE -> null
            PlannedLoadKind.BODYWEIGHT_ONLY -> resources.getString(R.string.set_copy_bodyweight)
            PlannedLoadKind.ADDED_WEIGHT -> if (weight != null) {
                resources.getString(R.string.set_copy_added_weight, weight)
            } else {
                resources.getString(R.string.set_copy_added_weight, "").trim()
            }
            PlannedLoadKind.ASSISTANCE -> if (weight != null) {
                resources.getString(R.string.set_copy_assistance, weight)
            } else {
                resources.getString(R.string.set_copy_assistance_plain)
            }
            PlannedLoadKind.EXTERNAL_WEIGHT -> when {
                weight == null -> null
                interpretation == WeightInterpretation.PER_SIDE ->
                    resources.getString(R.string.set_copy_per_side, weight)
                markTotalWeight -> resources.getString(R.string.set_copy_weight_total, weight)
                else -> resources.getString(R.string.set_copy_weight, weight)
            }
        }
    }

    private fun durationLabel(resources: Resources, seconds: Int): String {
        return if (seconds < 60) {
            resources.getString(R.string.set_copy_seconds, seconds)
        } else {
            clock(seconds)
        }
    }

    private fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val remainder = safe % 60
        return "$minutes:${remainder.toString().padStart(2, '0')}"
    }

    private fun distanceLabel(resources: Resources, meters: Double): String {
        return if (meters >= 1000.0) {
            val kilometers = QuantityParser.fromMeters(meters, DistanceUnit.KILOMETERS)
            resources.getString(
                R.string.set_copy_distance_km,
                QuantityParser.formatDisplay(kilometers)
            )
        } else {
            resources.getString(
                R.string.set_copy_distance_m,
                QuantityParser.formatDisplay(meters)
            )
        }
    }
}
