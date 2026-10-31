package app.mymusclemap.domain.journal

import android.content.res.Resources
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.units.DistanceWord
import app.mymusclemap.domain.units.MeasurementText
import app.mymusclemap.domain.units.UnitSystem
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
    fun display(
        resources: Resources,
        set: SessionSet,
        exercise: SessionExercise,
        system: UnitSystem = UnitSystem.METRIC
    ): WorkoutSetDisplay {
        val planned = plannedValue(resources, set, exercise.weightInterpretation, system)
        val performed = when (set.status) {
            SessionSetStatus.SKIPPED, SessionSetStatus.PENDING -> ""
            SessionSetStatus.COMPLETED -> performedValue(resources, set, exercise.weightInterpretation, system)
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

    fun plannedValue(
        resources: Resources,
        set: SessionSet,
        interpretation: WeightInterpretation,
        system: UnitSystem = UnitSystem.METRIC
    ): String {
        return formatValue(
            resources = resources,
            repsText = plannedReps(set.plannedMinReps, set.plannedMaxReps),
            loadKind = set.plannedLoadKind,
            weightKg = set.plannedWeightKg,
            durationSeconds = set.plannedDurationSeconds,
            distanceMeters = set.plannedDistanceMeters,
            interpretation = interpretation,
            system = system
        )
    }

    fun performedValue(
        resources: Resources,
        set: SessionSet,
        interpretation: WeightInterpretation,
        system: UnitSystem = UnitSystem.METRIC
    ): String {
        return formatValue(
            resources = resources,
            repsText = set.actualReps?.toString(),
            loadKind = set.actualLoadKind,
            weightKg = set.actualWeightKg,
            durationSeconds = set.actualDurationSeconds,
            distanceMeters = set.actualDistanceMeters,
            interpretation = interpretation,
            system = system
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
        markTotalWeight: Boolean = false,
        system: UnitSystem = UnitSystem.METRIC
    ): String {
        val parts = mutableListOf<String>()
        if (!repsText.isNullOrBlank()) {
            parts += resources.getString(R.string.set_copy_reps, repsText)
        }
        loadLabel(resources, loadKind, weightKg, interpretation, markTotalWeight, system)?.let { parts += it }
        if (distanceMeters != null) {
            parts += distanceLabel(resources, distanceMeters, system)
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

    fun loadText(
        resources: Resources,
        kind: PlannedLoadKind?,
        weightKg: Double?,
        interpretation: WeightInterpretation,
        markTotalWeight: Boolean = false,
        system: UnitSystem = UnitSystem.METRIC
    ): String? = loadLabel(resources, kind, weightKg, interpretation, markTotalWeight, system)

    fun durationText(resources: Resources, seconds: Int): String = durationLabel(resources, seconds)

    fun distanceText(
        resources: Resources,
        meters: Double,
        system: UnitSystem = UnitSystem.METRIC
    ): String = distanceLabel(resources, meters, system)

    private fun loadLabel(
        resources: Resources,
        kind: PlannedLoadKind?,
        weightKg: Double?,
        interpretation: WeightInterpretation,
        markTotalWeight: Boolean,
        system: UnitSystem
    ): String? {
        val weight = weightKg?.let { MeasurementText.workoutWeightNumber(it, system) }
        val imperial = system == UnitSystem.IMPERIAL
        return when (kind) {
            null, PlannedLoadKind.NONE -> null
            PlannedLoadKind.BODYWEIGHT_ONLY -> resources.getString(R.string.set_copy_bodyweight)
            PlannedLoadKind.ADDED_WEIGHT -> if (weight != null) {
                resources.getString(
                    if (imperial) R.string.set_copy_added_weight_lb else R.string.set_copy_added_weight,
                    weight
                )
            } else {
                resources.getString(R.string.set_copy_added_weight, "").trim()
            }
            PlannedLoadKind.ASSISTANCE -> if (weight != null) {
                resources.getString(
                    if (imperial) R.string.set_copy_assistance_lb else R.string.set_copy_assistance,
                    weight
                )
            } else {
                resources.getString(R.string.set_copy_assistance_plain)
            }
            PlannedLoadKind.EXTERNAL_WEIGHT -> when {
                weight == null -> null
                interpretation == WeightInterpretation.PER_SIDE ->
                    resources.getString(
                        if (imperial) R.string.set_copy_per_side_lb else R.string.set_copy_per_side,
                        weight
                    )
                markTotalWeight -> resources.getString(
                    if (imperial) R.string.set_copy_weight_total_lb else R.string.set_copy_weight_total,
                    weight
                )
                else -> resources.getString(
                    if (imperial) R.string.set_copy_weight_lb else R.string.set_copy_weight,
                    weight
                )
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

    private fun distanceLabel(resources: Resources, meters: Double, system: UnitSystem): String {
        if (system == UnitSystem.METRIC && meters >= 1000.0) {
            val kilometers = QuantityParser.fromMeters(meters, app.mymusclemap.domain.workout.DistanceUnit.KILOMETERS)
            return resources.getString(
                R.string.set_copy_distance_km,
                QuantityParser.formatDisplay(kilometers)
            )
        }
        if (system == UnitSystem.METRIC) {
            return resources.getString(
                R.string.set_copy_distance_m,
                QuantityParser.formatDisplay(meters)
            )
        }
        val (number, word) = MeasurementText.distanceNumber(meters, system)
        val res = when (word) {
            DistanceWord.METERS -> R.string.set_copy_distance_m
            DistanceWord.KILOMETERS -> R.string.set_copy_distance_km
            DistanceWord.FEET -> R.string.set_copy_distance_ft
            DistanceWord.MILES -> R.string.set_copy_distance_mi
        }
        return resources.getString(res, number)
    }
}
