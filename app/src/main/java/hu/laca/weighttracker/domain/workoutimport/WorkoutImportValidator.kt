package hu.laca.weighttracker.domain.workoutimport

object WorkoutImportValidator {
    fun group(rows: List<WorkoutImportRow>): WorkoutImportParseResult {
        val errors = ArrayList<WorkoutImportError>()
        val byWorkout = LinkedHashMap<String, MutableList<WorkoutImportRow>>()
        rows.forEach { row ->
            byWorkout.getOrPut(row.workoutId) { ArrayList() }.add(row)
        }
        if (byWorkout.size > WorkoutImportLimits.MAX_WORKOUTS) {
            errors += WorkoutImportError(
                rowNumber = rows.first().sourceRowNumber,
                field = "workout_id",
                code = WorkoutImportErrorCode.TooManyWorkouts,
                detail = byWorkout.size.toString()
            )
        }
        val workouts = ArrayList<WorkoutImportWorkout>()
        byWorkout.forEach { (_, workoutRows) ->
            val grouped = groupWorkout(workoutRows, errors)
            if (grouped != null) {
                workouts += grouped
            }
        }
        if (errors.isNotEmpty()) {
            return WorkoutImportParseResult.Failure(errors)
        }
        if (workouts.isEmpty()) {
            return WorkoutImportParseResult.Failure(
                listOf(
                    WorkoutImportError(
                        rowNumber = rows.firstOrNull()?.sourceRowNumber,
                        field = null,
                        code = WorkoutImportErrorCode.EmptyWorkout
                    )
                )
            )
        }
        return WorkoutImportParseResult.Success(WorkoutImportDocument(workouts))
    }

    private fun groupWorkout(
        rows: List<WorkoutImportRow>,
        errors: MutableList<WorkoutImportError>
    ): WorkoutImportWorkout? {
        val first = rows.first()
        var consistent = true
        rows.drop(1).forEach { row ->
            if (row.normalizedWorkoutName != first.normalizedWorkoutName) {
                errors += conflict(row, "workout_name")
                consistent = false
            }
            if (row.workoutDate != first.workoutDate) {
                errors += conflict(row, "workout_date")
                consistent = false
            }
            if (row.startedAt != first.startedAt) {
                errors += conflict(row, "started_at")
                consistent = false
            }
            if (row.finishedAt != first.finishedAt) {
                errors += conflict(row, "finished_at")
                consistent = false
            }
            if (row.notes != first.notes) {
                errors += conflict(row, "notes")
                consistent = false
            }
            if (!sameDecimal(row.bodyWeightKg, first.bodyWeightKg)) {
                errors += conflict(row, "body_weight_kg")
                consistent = false
            }
        }
        val byExercise = LinkedHashMap<Int, MutableList<WorkoutImportRow>>()
        rows.forEach { row ->
            byExercise.getOrPut(row.exerciseIndex) { ArrayList() }.add(row)
        }
        val indices = byExercise.keys.sorted()
        if (indices.isEmpty()) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "exercise_index",
                code = WorkoutImportErrorCode.EmptyWorkout
            )
            return null
        }
        if (indices.first() != 1 || indices.last() != indices.size) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "exercise_index",
                code = WorkoutImportErrorCode.NonContiguousExerciseIndex,
                detail = indices.joinToString(",")
            )
            consistent = false
        }
        if (byExercise.size > WorkoutImportLimits.MAX_EXERCISES_PER_WORKOUT) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "exercise_index",
                code = WorkoutImportErrorCode.TooManyExercises,
                detail = byExercise.size.toString()
            )
            consistent = false
        }
        val exercises = ArrayList<WorkoutImportExercise>()
        indices.forEach { index ->
            val exerciseRows = byExercise.getValue(index)
            val names = exerciseRows.map { it.normalizedExerciseName }.distinct()
            if (names.size > 1) {
                errors += WorkoutImportError(
                    rowNumber = exerciseRows.first { it.normalizedExerciseName != exerciseRows.first().normalizedExerciseName }.sourceRowNumber,
                    field = "exercise_name",
                    code = WorkoutImportErrorCode.ConflictingExerciseName,
                    detail = names.joinToString(",")
                )
                consistent = false
            }
            val set = groupSets(exerciseRows, errors)
            if (set != null) {
                exercises += set
            } else {
                consistent = false
            }
        }
        if (!consistent) {
            return null
        }
        val workout = WorkoutImportWorkout(
            sourceRowNumber = first.sourceRowNumber,
            workoutId = first.workoutId,
            name = first.workoutName,
            normalizedName = first.normalizedWorkoutName,
            workoutDate = first.workoutDate,
            startedAt = first.startedAt,
            finishedAt = first.finishedAt,
            notes = first.notes,
            bodyWeightKg = first.bodyWeightKg,
            exercises = exercises
        )
        if (workout.completedSetCount == 0) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "set_status",
                code = WorkoutImportErrorCode.NoCompletedSets,
                detail = first.workoutId
            )
            return null
        }
        return workout
    }

    private fun groupSets(
        rows: List<WorkoutImportRow>,
        errors: MutableList<WorkoutImportError>
    ): WorkoutImportExercise? {
        val first = rows.first()
        val byIndex = LinkedHashMap<Int, WorkoutImportRow>()
        var ok = true
        rows.forEach { row ->
            val previous = byIndex.put(row.setIndex, row)
            if (previous != null) {
                errors += WorkoutImportError(
                    rowNumber = row.sourceRowNumber,
                    field = "set_index",
                    code = WorkoutImportErrorCode.DuplicateSetIndex,
                    detail = row.setIndex.toString()
                )
                ok = false
            }
        }
        val indices = byIndex.keys.sorted()
        if (indices.isEmpty() || indices.first() != 1 || indices.last() != indices.size) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "set_index",
                code = WorkoutImportErrorCode.NonContiguousSetIndex,
                detail = indices.joinToString(",")
            )
            ok = false
        }
        if (indices.size > WorkoutImportLimits.MAX_SETS_PER_EXERCISE) {
            errors += WorkoutImportError(
                rowNumber = first.sourceRowNumber,
                field = "set_index",
                code = WorkoutImportErrorCode.TooManySets,
                detail = indices.size.toString()
            )
            ok = false
        }
        if (!ok) {
            return null
        }
        return WorkoutImportExercise(
            sourceRowNumber = first.sourceRowNumber,
            exerciseIndex = first.exerciseIndex,
            name = first.exerciseName,
            normalizedName = first.normalizedExerciseName,
            sets = indices.map { index ->
                val row = byIndex.getValue(index)
                WorkoutImportSet(
                    sourceRowNumber = row.sourceRowNumber,
                    setIndex = row.setIndex,
                    status = row.status,
                    reps = row.reps,
                    durationSeconds = row.durationSeconds,
                    distanceMeters = row.distanceMeters,
                    loadKind = row.loadKind,
                    weightKg = row.weightKg
                )
            }
        )
    }

    private fun conflict(row: WorkoutImportRow, field: String): WorkoutImportError {
        return WorkoutImportError(
            rowNumber = row.sourceRowNumber,
            field = field,
            code = WorkoutImportErrorCode.InconsistentWorkoutField
        )
    }

    private fun sameDecimal(left: java.math.BigDecimal?, right: java.math.BigDecimal?): Boolean {
        if (left == null || right == null) {
            return left == null && right == null
        }
        return left.compareTo(right) == 0
    }
}
