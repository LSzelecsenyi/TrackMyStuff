package hu.laca.weighttracker.domain.journal

import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.DistanceUnit
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.QuantityParser
import hu.laca.weighttracker.domain.workout.RepetitionTarget
import hu.laca.weighttracker.domain.workout.SessionExercise
import hu.laca.weighttracker.domain.workout.SessionSet
import hu.laca.weighttracker.domain.workout.SessionSetStatus

data class WorkoutSetDisplay(
    val position: Int,
    val status: SessionSetStatus,
    val addedDuringWorkout: Boolean,
    val planned: String,
    val performed: String,
    val valuesDiffer: Boolean
)

object WorkoutSetCopy {
    fun display(set: SessionSet, exercise: SessionExercise): WorkoutSetDisplay {
        val planned = plannedValue(set, exercise.weightInterpretation)
        val performed = when (set.status) {
            SessionSetStatus.SKIPPED, SessionSetStatus.PENDING -> ""
            SessionSetStatus.COMPLETED -> performedValue(set, exercise.weightInterpretation)
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

    fun plannedValue(set: SessionSet, interpretation: WeightInterpretation): String {
        return formatValue(
            repsText = plannedReps(set.plannedMinReps, set.plannedMaxReps),
            loadKind = set.plannedLoadKind,
            weightKg = set.plannedWeightKg,
            durationSeconds = set.plannedDurationSeconds,
            distanceMeters = set.plannedDistanceMeters,
            interpretation = interpretation
        )
    }

    fun performedValue(set: SessionSet, interpretation: WeightInterpretation): String {
        return formatValue(
            repsText = set.actualReps?.let { "$it ism." },
            loadKind = set.actualLoadKind,
            weightKg = set.actualWeightKg,
            durationSeconds = set.actualDurationSeconds,
            distanceMeters = set.actualDistanceMeters,
            interpretation = interpretation
        )
    }

    fun formatValue(
        repsText: String?,
        loadKind: PlannedLoadKind?,
        weightKg: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        interpretation: WeightInterpretation
    ): String {
        val parts = mutableListOf<String>()
        if (!repsText.isNullOrBlank()) {
            parts += if (repsText.endsWith("ism.")) repsText else "$repsText ism."
        }
        loadLabel(loadKind, weightKg, interpretation)?.let { parts += it }
        if (distanceMeters != null) {
            parts += distanceLabel(distanceMeters)
            durationSeconds?.let { parts += clock(it) }
        } else {
            durationSeconds?.let { parts += durationLabel(it) }
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
        kind: PlannedLoadKind?,
        weightKg: Double?,
        interpretation: WeightInterpretation
    ): String? {
        val weight = weightKg?.let { QuantityParser.formatDisplay(it) }
        return when (kind) {
            null, PlannedLoadKind.NONE -> null
            PlannedLoadKind.BODYWEIGHT_ONLY -> "saját testsúly"
            PlannedLoadKind.ADDED_WEIGHT -> if (weight != null) "+$weight kg" else "+ kg"
            PlannedLoadKind.ASSISTANCE -> if (weight != null) "$weight kg rásegítés" else "rásegítés"
            PlannedLoadKind.EXTERNAL_WEIGHT -> when {
                weight == null -> null
                interpretation == WeightInterpretation.PER_SIDE -> "$weight kg kézenként"
                else -> "$weight kg"
            }
        }
    }

    private fun durationLabel(seconds: Int): String {
        return if (seconds < 60) {
            "$seconds mp"
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

    private fun distanceLabel(meters: Double): String {
        return if (meters >= 1000.0) {
            val kilometers = QuantityParser.fromMeters(meters, DistanceUnit.KILOMETERS)
            "${QuantityParser.formatDisplay(kilometers)} km"
        } else {
            "${QuantityParser.formatDisplay(meters)} m"
        }
    }
}
