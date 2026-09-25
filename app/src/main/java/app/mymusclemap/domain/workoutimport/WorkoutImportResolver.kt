package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.model.WeightMeasurement
import java.time.Duration

object WorkoutImportResolver {
    fun resolve(
        document: WorkoutImportDocument,
        catalog: List<Exercise>,
        mappings: List<WorkoutImportMapping> = emptyList(),
        measurements: List<WeightMeasurement> = emptyList()
    ): WorkoutImportPlan {
        val errors = ArrayList<WorkoutImportError>()
        val warnings = ArrayList<WorkoutImportWarning>()
        val byNormalized = catalog.groupBy { it.normalizedName }
        val byId = catalog.associateBy { it.id }
        val mappingByIncoming = HashMap<String, WorkoutImportMapping>()
        mappings.forEach { mapping ->
            val key = ExerciseNaming.normalize(mapping.incomingNormalizedName)
            val previous = mappingByIncoming.put(key, mapping.copy(incomingNormalizedName = key))
            if (previous != null && previous.catalogExerciseId != mapping.catalogExerciseId) {
                errors += WorkoutImportError(
                    rowNumber = null,
                    field = "exercise_name",
                    code = WorkoutImportErrorCode.ConflictingManualMapping,
                    detail = key,
                    incomingExerciseName = key
                )
            }
        }

        val incomingNames = LinkedHashSet<String>()
        document.workouts.forEach { workout ->
            workout.exercises.forEach { exercise ->
                incomingNames += exercise.normalizedName
            }
        }

        val resolvedCatalog = LinkedHashMap<String, Exercise>()
        val unresolved = ArrayList<String>()
        val mappedManually = HashSet<String>()
        incomingNames.forEach { incoming ->
            val mapped = mappingByIncoming[incoming]
            val exactMatches = byNormalized[incoming].orEmpty()
            when {
                mapped != null -> {
                    val target = byId[mapped.catalogExerciseId]
                    if (target == null) {
                        errors += WorkoutImportError(
                            rowNumber = null,
                            field = "exercise_name",
                            code = WorkoutImportErrorCode.StaleManualMapping,
                            detail = mapped.catalogExerciseId.toString(),
                            incomingExerciseName = incoming
                        )
                        unresolved += incoming
                    } else if (exactMatches.size == 1 && exactMatches.first().id != target.id) {
                        errors += WorkoutImportError(
                            rowNumber = null,
                            field = "exercise_name",
                            code = WorkoutImportErrorCode.ConflictingManualMapping,
                            detail = incoming,
                            incomingExerciseName = incoming
                        )
                        unresolved += incoming
                    } else {
                        resolvedCatalog[incoming] = target
                        mappedManually += incoming
                        if (exactMatches.isEmpty() || ExerciseNaming.normalize(target.name) != incoming) {
                            warnings += WorkoutImportWarning(
                                rowNumber = null,
                                field = "exercise_name",
                                code = WorkoutImportWarningCode.ManualAliasMapping,
                                detail = target.name,
                                incomingExerciseName = incoming
                            )
                        }
                    }
                }
                exactMatches.size == 1 -> resolvedCatalog[incoming] = exactMatches.first()
                exactMatches.size > 1 -> {
                    errors += WorkoutImportError(
                        rowNumber = null,
                        field = "exercise_name",
                        code = WorkoutImportErrorCode.AmbiguousCatalogMatch,
                        detail = incoming,
                        incomingExerciseName = incoming
                    )
                    unresolved += incoming
                }
                else -> {
                    unresolved += incoming
                }
            }
        }

        val catalogIdsToIncoming = resolvedCatalog.entries.groupBy({ it.value.id }, { it.key })
        catalogIdsToIncoming.forEach { (_, names) ->
            if (names.size > 1) {
                warnings += WorkoutImportWarning(
                    rowNumber = null,
                    field = "exercise_name",
                    code = WorkoutImportWarningCode.MultipleIncomingNamesMapped,
                    detail = names.joinToString(",")
                )
            }
        }

        val workouts = document.workouts.map { workout ->
            resolveWorkout(
                workout = workout,
                resolvedCatalog = resolvedCatalog,
                mappedManually = mappedManually,
                measurements = measurements,
                errors = errors,
                warnings = warnings
            )
        }
        return WorkoutImportPlan(
            formatVersion = WorkoutImportLimits.FORMAT_VERSION,
            workouts = workouts,
            unresolvedNames = unresolved.distinct(),
            errors = errors.toList(),
            warnings = warnings.distinct()
        )
    }

    private fun resolveWorkout(
        workout: WorkoutImportWorkout,
        resolvedCatalog: Map<String, Exercise>,
        mappedManually: Set<String>,
        measurements: List<WeightMeasurement>,
        errors: MutableList<WorkoutImportError>,
        warnings: MutableList<WorkoutImportWarning>
    ): WorkoutImportResolvedWorkout {
        val (proposal, bodyWarning) = WorkoutImportBodyWeight.propose(
            workout.workoutDate,
            workout.bodyWeightKg,
            measurements
        )
        val workoutWarnings = ArrayList<WorkoutImportWarning>()
        if (bodyWarning != null) {
            val warning = WorkoutImportWarning(
                rowNumber = workout.sourceRowNumber,
                field = "body_weight_kg",
                code = bodyWarning,
                workoutId = workout.workoutId
            )
            warnings += warning
            workoutWarnings += warning
        }
        val exercises = workout.exercises.map { exercise ->
            resolveExercise(workout, exercise, resolvedCatalog, mappedManually, errors, warnings)
        }
        workoutWarnings += exercises.flatMap { it.warnings }
        return WorkoutImportResolvedWorkout(
            sourceRowNumber = workout.sourceRowNumber,
            workoutId = workout.workoutId,
            name = workout.name,
            normalizedName = workout.normalizedName,
            workoutDate = workout.workoutDate,
            startedAt = workout.startedAt,
            finishedAt = workout.finishedAt,
            durationMillis = Duration.between(workout.startedAt, workout.finishedAt).toMillis().coerceAtLeast(0L),
            notes = workout.notes,
            bodyWeight = proposal,
            exercises = exercises,
            errors = exercises.flatMap { it.errors },
            warnings = workoutWarnings
        )
    }

    private fun resolveExercise(
        workout: WorkoutImportWorkout,
        exercise: WorkoutImportExercise,
        resolvedCatalog: Map<String, Exercise>,
        mappedManually: Set<String>,
        errors: MutableList<WorkoutImportError>,
        warnings: MutableList<WorkoutImportWarning>
    ): WorkoutImportResolvedExercise {
        val catalog = resolvedCatalog[exercise.normalizedName]
        val localErrors = ArrayList<WorkoutImportError>()
        val localWarnings = ArrayList<WorkoutImportWarning>()
        var snapshot: WorkoutImportExerciseSnapshot? = null
        if (catalog == null) {
            localErrors += WorkoutImportError(
                rowNumber = exercise.sourceRowNumber,
                field = "exercise_name",
                code = WorkoutImportErrorCode.UnresolvedExercise,
                detail = exercise.name,
                workoutId = workout.workoutId,
                incomingExerciseName = exercise.name,
                exerciseIndex = exercise.exerciseIndex
            )
        } else {
            val (built, muscleError) = WorkoutImportCompatibility.snapshot(
                catalog, exercise.name, exercise.sourceRowNumber, workout.workoutId, exercise.exerciseIndex
            )
            if (muscleError != null) {
                localErrors += muscleError
            } else {
                snapshot = built
            }
            if (catalog.archived) {
                localWarnings += WorkoutImportWarning(
                    rowNumber = exercise.sourceRowNumber,
                    field = "exercise_name",
                    code = WorkoutImportWarningCode.ArchivedExercise,
                    detail = catalog.name,
                    workoutId = workout.workoutId,
                    incomingExerciseName = exercise.name,
                    exerciseIndex = exercise.exerciseIndex
                )
            }
            if (snapshot != null) {
                exercise.sets.forEach { set ->
                    localErrors += WorkoutImportCompatibility.validateSet(
                        catalog, set, workout.workoutId, exercise.name, exercise.exerciseIndex
                    )
                }
            }
        }
        errors += localErrors
        warnings += localWarnings
        return WorkoutImportResolvedExercise(
            sourceRowNumber = exercise.sourceRowNumber,
            exerciseIndex = exercise.exerciseIndex,
            incomingName = exercise.name,
            normalizedIncomingName = exercise.normalizedName,
            snapshot = snapshot,
            mappedManually = exercise.normalizedName in mappedManually,
            sets = exercise.sets.map { set ->
                WorkoutImportResolvedSet(
                    sourceRowNumber = set.sourceRowNumber,
                    setIndex = set.setIndex,
                    status = set.status,
                    reps = set.reps,
                    durationSeconds = set.durationSeconds,
                    distanceMeters = set.distanceMeters,
                    loadKind = set.loadKind,
                    weightKg = set.weightKg
                )
            },
            errors = localErrors,
            warnings = localWarnings
        )
    }
}
