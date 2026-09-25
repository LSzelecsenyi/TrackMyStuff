package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseDraftLogic
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.PlannedSetLogic
import app.mymusclemap.domain.workout.SessionSetStatus

object WorkoutImportCompatibility {
    fun snapshot(
        exercise: Exercise,
        incomingName: String,
        rowNumber: Int,
        workoutId: String,
        exerciseIndex: Int
    ): Pair<WorkoutImportExerciseSnapshot?, WorkoutImportError?> {
        val secondary = ExerciseDraftLogic.normalizeSecondary(exercise.primaryMuscle, exercise.secondaryMuscles)
        if (exercise.primaryMuscle in exercise.secondaryMuscles) {
            return null to error(
                rowNumber, workoutId, incomingName, exerciseIndex, null,
                WorkoutImportErrorCode.InvalidCatalogMuscle,
                "primary_also_secondary"
            )
        }
        val muscles = buildList {
            add(WorkoutImportMuscleSnapshot(exercise.primaryMuscle, MuscleRole.PRIMARY))
            secondary.forEach { group ->
                add(WorkoutImportMuscleSnapshot(group, MuscleRole.SECONDARY))
            }
        }
        return WorkoutImportExerciseSnapshot(
            exerciseId = exercise.id,
            catalogName = exercise.name,
            incomingName = incomingName,
            category = exercise.category,
            movementPattern = exercise.movementPattern,
            measurementType = exercise.measurementType,
            resistanceBasis = exercise.resistanceBasis,
            weightInterpretation = exercise.weightInterpretation,
            primaryMuscle = exercise.primaryMuscle,
            secondaryMuscles = secondary.toList(),
            muscles = muscles.toList(),
            notes = exercise.notes,
            archived = exercise.archived
        ) to null
    }

    fun validateSet(
        exercise: Exercise,
        set: WorkoutImportSet,
        workoutId: String,
        incomingName: String,
        exerciseIndex: Int
    ): List<WorkoutImportError> {
        if (set.status == SessionSetStatus.SKIPPED) {
            return emptyList()
        }
        val errors = ArrayList<WorkoutImportError>()
        fun add(field: String, code: WorkoutImportErrorCode, detail: String? = null) {
            errors += error(set.sourceRowNumber, workoutId, incomingName, exerciseIndex, set.setIndex, code, detail, field)
        }
        val measurement = exercise.measurementType
        if (PlannedSetLogic.requiresReps(measurement)) {
            if (set.reps == null) {
                add("reps", WorkoutImportErrorCode.MissingRequiredActual, measurement.name)
            }
        } else if (set.reps != null) {
            add("reps", WorkoutImportErrorCode.ForbiddenActual, measurement.name)
        }
        if (PlannedSetLogic.requiresDuration(measurement)) {
            if (set.durationSeconds == null) {
                add("duration_seconds", WorkoutImportErrorCode.MissingRequiredActual, measurement.name)
            }
        } else if (set.durationSeconds != null) {
            add("duration_seconds", WorkoutImportErrorCode.ForbiddenActual, measurement.name)
        }
        if (PlannedSetLogic.requiresDistance(measurement)) {
            if (set.distanceMeters == null) {
                add("distance", WorkoutImportErrorCode.MissingRequiredActual, measurement.name)
            }
        } else if (set.distanceMeters != null) {
            add("distance", WorkoutImportErrorCode.ForbiddenActual, measurement.name)
        }
        val allowed = PlannedLoadLogic.compatibleKinds(exercise.resistanceBasis, measurement)
        val loadKind = set.loadKind
        if (loadKind == null) {
            add("load_kind", WorkoutImportErrorCode.CompletedMissingLoadKind)
            return errors
        }
        if (loadKind !in allowed) {
            add("load_kind", WorkoutImportErrorCode.IncompatibleLoadKind, loadKind.name)
        }
        val needsWeight = PlannedLoadLogic.requiresPositiveWeight(loadKind)
        if (needsWeight && set.weightKg == null) {
            add("weight_kg", WorkoutImportErrorCode.MissingLoadWeight, loadKind.name)
        }
        if (!needsWeight && set.weightKg != null) {
            add("weight_kg", WorkoutImportErrorCode.ForbiddenLoadWeight, loadKind.name)
        }
        return errors
    }

    private fun error(
        rowNumber: Int,
        workoutId: String,
        incomingName: String,
        exerciseIndex: Int,
        setIndex: Int?,
        code: WorkoutImportErrorCode,
        detail: String?,
        field: String? = null
    ): WorkoutImportError {
        return WorkoutImportError(
            rowNumber = rowNumber,
            field = field,
            code = code,
            detail = detail,
            workoutId = workoutId,
            incomingExerciseName = incomingName,
            exerciseIndex = exerciseIndex,
            setIndex = setIndex
        )
    }
}
