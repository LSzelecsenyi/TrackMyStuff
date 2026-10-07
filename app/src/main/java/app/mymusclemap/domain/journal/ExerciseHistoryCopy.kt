package app.mymusclemap.domain.journal

import android.content.res.Resources
import app.mymusclemap.R
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.workout.ExerciseHistorySelection
import app.mymusclemap.domain.workout.ExerciseHistorySet
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.PlannedSetLogic

enum class HistoryColumn {
    SET,
    REPS,
    WEIGHT,
    DURATION,
    DISTANCE,
    RESULT
}

data class HistoryTableRow(
    val position: Int,
    val cells: List<String>,
    val comparison: Boolean
)

data class HistoryTable(
    val columns: List<HistoryColumn>,
    val rows: List<HistoryTableRow>
)

object ExerciseHistoryCopy {
    fun columns(measurement: MeasurementType, resistance: ResistanceBasis): List<HistoryColumn> {
        val columns = mutableListOf(HistoryColumn.SET)
        if (PlannedSetLogic.requiresReps(measurement)) columns += HistoryColumn.REPS
        if (PlannedSetLogic.requiresDistance(measurement)) columns += HistoryColumn.DISTANCE
        if (PlannedSetLogic.requiresDuration(measurement)) columns += HistoryColumn.DURATION
        if (measurement == MeasurementType.COMPLETION_ONLY) columns += HistoryColumn.RESULT
        val loads = PlannedLoadLogic.compatibleKinds(resistance, measurement)
        if (loads.any { it != PlannedLoadKind.NONE }) columns += HistoryColumn.WEIGHT
        return columns
    }

    fun inlineValue(resources: Resources, selection: ExerciseHistorySelection, set: ExerciseHistorySet): String {
        val parts = recordedParts(resources, selection, set)
        return parts.joinToString(" · ").ifBlank {
            resources.getString(R.string.active_set_history_empty_value)
        }
    }

    fun table(
        resources: Resources,
        selection: ExerciseHistorySelection,
        comparisonPosition: Int
    ): HistoryTable {
        val columns = columns(selection.measurementType, selection.resistanceBasis)
        val empty = resources.getString(R.string.active_set_history_empty_value)
        val rows = selection.sets.map { set ->
            val parts = recordedParts(resources, selection, set)
            HistoryTableRow(
                position = set.position,
                cells = columns.map { column -> cell(resources, column, set, parts, empty) },
                comparison = set.position == comparisonPosition
            )
        }
        return HistoryTable(columns, rows)
    }

    private fun cell(
        resources: Resources,
        column: HistoryColumn,
        set: ExerciseHistorySet,
        parts: RecordedParts,
        empty: String
    ): String {
        return when (column) {
            HistoryColumn.SET -> (set.position + 1).toString()
            HistoryColumn.REPS -> set.reps?.toString() ?: empty
            HistoryColumn.WEIGHT -> parts.load ?: empty
            HistoryColumn.DURATION -> parts.duration ?: empty
            HistoryColumn.DISTANCE -> parts.distance ?: empty
            HistoryColumn.RESULT -> resources.getString(R.string.set_status_completed)
        }
    }

    private fun recordedParts(
        resources: Resources,
        selection: ExerciseHistorySelection,
        set: ExerciseHistorySet
    ): RecordedParts {
        val measurement = selection.measurementType
        val reps = set.reps?.toString()?.takeIf { PlannedSetLogic.requiresReps(measurement) }
        val duration = set.durationSeconds?.takeIf { PlannedSetLogic.requiresDuration(measurement) }
            ?.let { WorkoutSetCopy.durationText(resources, it) }
        val distance = set.distanceMeters?.takeIf { PlannedSetLogic.requiresDistance(measurement) }
            ?.let { WorkoutSetCopy.distanceText(resources, it) }
        val load = compatibleLoad(selection, set)?.let { (kind, weight) ->
            WorkoutSetCopy.loadText(
                resources = resources,
                kind = kind,
                weightKg = weight,
                interpretation = selection.weightInterpretation,
                markTotalWeight = false
            )
        }
        val completion = if (measurement == MeasurementType.COMPLETION_ONLY) {
            resources.getString(R.string.set_status_completed)
        } else {
            null
        }
        return RecordedParts(
            reps = reps,
            load = load,
            distance = distance,
            duration = duration,
            completion = completion
        )
    }

    private fun compatibleLoad(
        selection: ExerciseHistorySelection,
        set: ExerciseHistorySet
    ): Pair<PlannedLoadKind, Double?>? {
        val kind = set.loadKind ?: return null
        val allowed = PlannedLoadLogic.compatibleKinds(selection.resistanceBasis, selection.measurementType)
        if (kind !in allowed) return null
        val weight = if (PlannedLoadLogic.requiresPositiveWeight(kind)) set.weightKg else null
        if (PlannedLoadLogic.requiresPositiveWeight(kind) && weight == null) return null
        return kind to weight
    }

    private data class RecordedParts(
        val reps: String?,
        val load: String?,
        val distance: String?,
        val duration: String?,
        val completion: String?
    ) {
        fun joinToString(separator: String): String {
            return listOfNotNull(reps, load, distance, duration, completion).joinToString(separator)
        }
    }
}
