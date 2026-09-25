package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.recordsExternalWeight

object TemplateOrdering {
    fun <T> moveUp(items: List<T>, index: Int): List<T> {
        if (!canMoveUp(index)) {
            return items
        }
        return items.toMutableList().also { list ->
            val item = list.removeAt(index)
            list.add(index - 1, item)
        }
    }

    fun <T> moveDown(items: List<T>, index: Int): List<T> {
        if (!canMoveDown(index, items.size)) {
            return items
        }
        return items.toMutableList().also { list ->
            val item = list.removeAt(index)
            list.add(index + 1, item)
        }
    }

    fun canMoveUp(index: Int): Boolean = index > 0

    fun canMoveDown(index: Int, size: Int): Boolean = index >= 0 && index < size - 1

    fun compactPositions(size: Int): List<Int> = List(size) { it }
}

object PlannedLoadLogic {
    fun compatibleKinds(
        resistance: ResistanceBasis,
        measurement: MeasurementType
    ): Set<PlannedLoadKind> {
        if (measurement == MeasurementType.COMPLETION_ONLY) {
            return setOf(PlannedLoadKind.NONE)
        }
        return when (resistance) {
            ResistanceBasis.BODYWEIGHT -> setOf(
                PlannedLoadKind.BODYWEIGHT_ONLY,
                PlannedLoadKind.ADDED_WEIGHT,
                PlannedLoadKind.ASSISTANCE
            )
            ResistanceBasis.EXTERNAL -> if (measurement.recordsExternalWeight()) {
                setOf(PlannedLoadKind.EXTERNAL_WEIGHT)
            } else {
                setOf(PlannedLoadKind.NONE)
            }
            ResistanceBasis.NONE -> setOf(PlannedLoadKind.NONE)
        }
    }

    fun defaultKind(
        resistance: ResistanceBasis,
        measurement: MeasurementType
    ): PlannedLoadKind {
        return compatibleKinds(resistance, measurement).first()
    }

    fun requiresPositiveWeight(kind: PlannedLoadKind): Boolean {
        return kind == PlannedLoadKind.ADDED_WEIGHT ||
            kind == PlannedLoadKind.ASSISTANCE ||
            kind == PlannedLoadKind.EXTERNAL_WEIGHT
    }

    fun showsLoadChooser(resistance: ResistanceBasis, measurement: MeasurementType): Boolean {
        if (measurement == MeasurementType.COMPLETION_ONLY) {
            return false
        }
        return resistance == ResistanceBasis.BODYWEIGHT
    }

    fun showsExternalWeight(resistance: ResistanceBasis, measurement: MeasurementType): Boolean {
        return resistance == ResistanceBasis.EXTERNAL && measurement.recordsExternalWeight()
    }

    fun addedAndAssistanceAreExclusive(): Boolean = true
}

object PlannedSetLogic {
    const val DEFAULT_SET_COUNT = 4
    const val DEFAULT_REPS = 8
    const val DEFAULT_DURATION_SECONDS = 30
    const val DEFAULT_DISTANCE_METERS = 1000.0
    const val DEFAULT_EXTERNAL_KG = 10.0
    const val DEFAULT_ADDED_KG = 5.0

    fun requiresReps(type: MeasurementType): Boolean {
        return type == MeasurementType.REPETITIONS ||
            type == MeasurementType.REPETITIONS_AND_WEIGHT
    }

    fun requiresDuration(type: MeasurementType): Boolean {
        return type == MeasurementType.DURATION ||
            type == MeasurementType.DURATION_AND_WEIGHT ||
            type == MeasurementType.DISTANCE_AND_DURATION
    }

    fun requiresDistance(type: MeasurementType): Boolean {
        return type == MeasurementType.DISTANCE_AND_DURATION
    }

    fun defaultSetCount(type: MeasurementType): Int {
        return if (type == MeasurementType.COMPLETION_ONLY) 1 else DEFAULT_SET_COUNT
    }

    fun maxSetCount(type: MeasurementType): Int {
        return if (type == MeasurementType.COMPLETION_ONLY) 1 else 20
    }

    fun defaultSet(localId: Long, exercise: Exercise): PlannedSetDraft {
        val kind = PlannedLoadLogic.defaultKind(exercise.resistanceBasis, exercise.measurementType)
        val (minutes, seconds) = QuantityParser.fromSeconds(DEFAULT_DURATION_SECONDS)
        return PlannedSetDraft(
            localId = localId,
            minRepsText = if (requiresReps(exercise.measurementType)) DEFAULT_REPS.toString() else "",
            maxRepsText = "",
            loadKind = kind,
            weightText = when (kind) {
                PlannedLoadKind.EXTERNAL_WEIGHT -> formatPlain(DEFAULT_EXTERNAL_KG)
                else -> ""
            },
            minutesText = if (requiresDuration(exercise.measurementType)) minutes.toString() else "0",
            secondsText = if (requiresDuration(exercise.measurementType)) seconds.toString() else "0",
            distanceText = if (requiresDistance(exercise.measurementType)) {
                formatPlain(DEFAULT_DISTANCE_METERS)
            } else {
                ""
            },
            distanceUnit = DistanceUnit.METERS
        )
    }

    fun defaultSets(startLocalId: Long, exercise: Exercise): Pair<List<PlannedSetDraft>, Long> {
        val count = defaultSetCount(exercise.measurementType)
        var next = startLocalId
        val sets = List(count) {
            val set = defaultSet(next, exercise)
            next -= 1
            set
        }
        return sets to next
    }

    fun resizeSets(
        sets: List<PlannedSetDraft>,
        count: Int,
        exercise: Exercise,
        nextLocalId: Long
    ): Pair<List<PlannedSetDraft>, Long> {
        val bounded = count.coerceIn(1, maxSetCount(exercise.measurementType))
        if (bounded == sets.size) {
            return sets to nextLocalId
        }
        if (bounded < sets.size) {
            return sets.take(bounded) to nextLocalId
        }
        var next = nextLocalId
        val extra = (bounded - sets.size)
        val grown = sets.toMutableList()
        repeat(extra) {
            val source = grown.lastOrNull() ?: defaultSet(next, exercise)
            grown += source.copy(localId = next)
            next -= 1
        }
        return grown to next
    }

    fun applyToRemaining(sets: List<PlannedSetDraft>, fromIndex: Int): List<PlannedSetDraft> {
        if (fromIndex !in sets.indices) {
            return sets
        }
        val source = sets[fromIndex]
        return sets.mapIndexed { index, set ->
            if (index <= fromIndex) {
                set
            } else {
                source.copy(localId = set.localId)
            }
        }
    }

    fun applyToAll(sets: List<PlannedSetDraft>, source: PlannedSetDraft): List<PlannedSetDraft> {
        return sets.map { set -> source.copy(localId = set.localId) }
    }

    fun copyPrevious(sets: List<PlannedSetDraft>, newLocalId: Long): List<PlannedSetDraft> {
        val source = sets.lastOrNull() ?: return sets
        return sets + source.copy(localId = newLocalId)
    }

    fun parseValues(
        draft: PlannedSetDraft,
        exercise: Exercise
    ): Pair<PlannedSetValues?, List<TemplateFieldError>> {
        val errors = mutableListOf<TemplateFieldError>()
        val measurement = exercise.measurementType
        val resistance = exercise.resistanceBasis
        var minReps: Int? = null
        var maxReps: Int? = null
        if (requiresReps(measurement)) {
            when (val parsedMin = QuantityParser.parseInt(draft.minRepsText, 1, QuantityParser.MAX_REPS)) {
                is IntParseResult.Invalid -> errors += when (parsedMin.error) {
                    QuantityParseError.Empty -> TemplateFieldError.RepsRequired
                    QuantityParseError.Malformed -> TemplateFieldError.RepsMalformed
                    QuantityParseError.OutOfRange ->
                        if (draft.minRepsText.trim() == "0") {
                            TemplateFieldError.RepsNotPositive
                        } else {
                            TemplateFieldError.RepsTooLarge
                        }
                    else -> TemplateFieldError.RepsMalformed
                }
                is IntParseResult.Valid -> minReps = parsedMin.value
            }
            val maxRaw = draft.maxRepsText.trim()
            if (maxRaw.isEmpty()) {
                maxReps = minReps
            } else {
                when (val parsedMax = QuantityParser.parseInt(maxRaw, 1, QuantityParser.MAX_REPS)) {
                    is IntParseResult.Invalid -> errors += when (parsedMax.error) {
                        QuantityParseError.Malformed -> TemplateFieldError.RepsMalformed
                        QuantityParseError.OutOfRange ->
                            if (maxRaw == "0") {
                                TemplateFieldError.RepsNotPositive
                            } else {
                                TemplateFieldError.RepsTooLarge
                            }
                        else -> TemplateFieldError.RepsNotPositive
                    }
                    is IntParseResult.Valid -> maxReps = parsedMax.value
                }
            }
            if (minReps != null && maxReps != null && maxReps < minReps) {
                errors += TemplateFieldError.RepsMaxLessThanMin
            }
        }
        val allowed = PlannedLoadLogic.compatibleKinds(resistance, measurement)
        if (draft.loadKind !in allowed) {
            errors += TemplateFieldError.LoadIncompatible
        }
        var weightKg: Double? = null
        val needsWeight = draft.loadKind in allowed &&
            PlannedLoadLogic.requiresPositiveWeight(draft.loadKind)
        if (needsWeight && draft.loadKind in allowed) {
            when (
                val parsed = QuantityParser.parseDecimal(
                    raw = draft.weightText,
                    maxDecimals = QuantityParser.WEIGHT_MAX_DECIMALS,
                    minExclusiveZero = true,
                    max = QuantityParser.MAX_WEIGHT_KG
                )
            ) {
                is DecimalParseResult.Invalid -> errors += when (parsed.error) {
                    QuantityParseError.Empty -> TemplateFieldError.WeightRequired
                    QuantityParseError.Malformed,
                    QuantityParseError.NotFinite,
                    QuantityParseError.TooManyDecimals -> TemplateFieldError.WeightMalformed
                    QuantityParseError.Negative,
                    QuantityParseError.OutOfRange ->
                        if (draft.weightText.trim() == "0" || draft.weightText.trim() == "0,0") {
                            TemplateFieldError.WeightNotPositive
                        } else {
                            TemplateFieldError.WeightTooLarge
                        }
                }
                is DecimalParseResult.Valid -> weightKg = parsed.value
            }
        } else if (!PlannedLoadLogic.requiresPositiveWeight(draft.loadKind)) {
            weightKg = null
        }
        var durationSeconds: Int? = null
        if (requiresDuration(measurement)) {
            val minutes = QuantityParser.parseInt(draft.minutesText.ifBlank { "0" }, 0, QuantityParser.MAX_MINUTES)
            val seconds = QuantityParser.parseInt(draft.secondsText.ifBlank { "0" }, 0, QuantityParser.MAX_SECONDS_COMPONENT)
            if (minutes is IntParseResult.Invalid || seconds is IntParseResult.Invalid) {
                errors += TemplateFieldError.DurationMalformed
            } else {
                val total = QuantityParser.toSeconds(
                    (minutes as IntParseResult.Valid).value,
                    (seconds as IntParseResult.Valid).value
                )
                if (total <= 0) {
                    errors += TemplateFieldError.DurationNotPositive
                } else {
                    durationSeconds = total
                }
            }
        }
        var distanceMeters: Double? = null
        if (requiresDistance(measurement)) {
            when (
                val parsed = QuantityParser.parseDecimal(
                    raw = draft.distanceText,
                    maxDecimals = QuantityParser.DISTANCE_MAX_DECIMALS,
                    minExclusiveZero = true,
                    max = if (draft.distanceUnit == DistanceUnit.KILOMETERS) {
                        QuantityParser.MAX_DISTANCE_METERS / 1000.0
                    } else {
                        QuantityParser.MAX_DISTANCE_METERS
                    }
                )
            ) {
                is DecimalParseResult.Invalid -> errors += when (parsed.error) {
                    QuantityParseError.Empty -> TemplateFieldError.DistanceRequired
                    QuantityParseError.Malformed,
                    QuantityParseError.NotFinite,
                    QuantityParseError.TooManyDecimals -> TemplateFieldError.DistanceMalformed
                    else -> TemplateFieldError.DistanceNotPositive
                }
                is DecimalParseResult.Valid -> {
                    distanceMeters = QuantityParser.toMeters(parsed.value, draft.distanceUnit)
                }
            }
        }
        if (errors.isNotEmpty()) {
            return null to errors
        }
        return PlannedSetValues(
            minReps = minReps,
            maxReps = maxReps,
            loadKind = draft.loadKind,
            weightKg = weightKg,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters
        ) to emptyList()
    }

    fun toDraft(localId: Long, values: PlannedSetValues): PlannedSetDraft {
        val (minutes, seconds) = QuantityParser.fromSeconds(values.durationSeconds ?: 0)
        val exact = values.minReps != null && values.minReps == values.maxReps
        return PlannedSetDraft(
            localId = localId,
            minRepsText = values.minReps?.toString().orEmpty(),
            maxRepsText = if (exact) "" else values.maxReps?.toString().orEmpty(),
            loadKind = values.loadKind,
            weightText = values.weightKg?.let { formatPlain(it) }.orEmpty(),
            minutesText = minutes.toString(),
            secondsText = seconds.toString(),
            distanceText = values.distanceMeters?.let { formatPlain(it) }.orEmpty(),
            distanceUnit = DistanceUnit.METERS
        )
    }

    private fun formatPlain(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }
}

object TemplateDraftLogic {
    fun validate(draft: TemplateDraft, catalog: Map<Long, Exercise>): List<TemplateValidationIssue> {
        val issues = mutableListOf<TemplateValidationIssue>()
        val name = TemplateNaming.displayName(draft.name)
        if (name.isEmpty()) {
            issues += TemplateValidationIssue(TemplateFieldError.NameBlank)
        } else if (name.length > TemplateNaming.NAME_MAX_LENGTH) {
            issues += TemplateValidationIssue(TemplateFieldError.NameTooLong)
        }
        if (draft.notes.trim().length > TemplateNaming.NOTES_MAX_LENGTH) {
            issues += TemplateValidationIssue(TemplateFieldError.NotesTooLong)
        }
        draft.exercises.forEachIndexed { exerciseIndex, item ->
            val exercise = catalog[item.exerciseId]
            if (exercise == null) {
                return@forEachIndexed
            }
            if (exercise.measurementType == MeasurementType.COMPLETION_ONLY && item.sets.size != 1) {
                issues += TemplateValidationIssue(
                    TemplateFieldError.CompletionSingleSet,
                    exerciseIndex = exerciseIndex
                )
            }
            item.sets.forEachIndexed { setIndex, set ->
                val (_, errors) = PlannedSetLogic.parseValues(set, exercise)
                errors.forEach { error ->
                    issues += TemplateValidationIssue(error, exerciseIndex, setIndex)
                }
            }
        }
        return issues
    }

    fun addExercise(
        draft: TemplateDraft,
        exercise: Exercise,
        nextLocalId: Long,
        allowDuplicate: Boolean
    ): AddExerciseResult {
        val exists = draft.exercises.any { it.exerciseId == exercise.id }
        if (exists && !allowDuplicate) {
            return AddExerciseResult.NeedsConfirmation(exercise)
        }
        if (exercise.archived) {
            return AddExerciseResult.ArchivedRejected
        }
        var next = nextLocalId
        val exerciseLocalId = next
        next -= 1
        val (sets, afterSets) = PlannedSetLogic.defaultSets(next, exercise)
        val item = TemplateExerciseDraft(
            localId = exerciseLocalId,
            exerciseId = exercise.id,
            sets = sets
        )
        return AddExerciseResult.Added(
            draft = draft.copy(exercises = draft.exercises + item),
            nextLocalId = afterSets
        )
    }

    fun muscleSummary(items: List<WorkoutTemplateExerciseItem>): TemplateMuscleSummary {
        return muscleSummaryFromExercises(items.map { it.exercise })
    }

    fun muscleSummaryFromExercises(exercises: List<Exercise>): TemplateMuscleSummary {
        val primary = linkedMapOf<MuscleGroup, Int>()
        val secondary = linkedMapOf<MuscleGroup, Int>()
        exercises.forEach { exercise ->
            primary[exercise.primaryMuscle] = (primary[exercise.primaryMuscle] ?: 0) + 1
            exercise.secondaryMuscles.forEach { group ->
                secondary[group] = (secondary[group] ?: 0) + 1
            }
        }
        return TemplateMuscleSummary(
            primary = primary.map { TemplateMuscleCount(it.key, it.value) },
            secondary = secondary.map { TemplateMuscleCount(it.key, it.value) }
        )
    }
}

sealed class AddExerciseResult {
    data class Added(val draft: TemplateDraft, val nextLocalId: Long) : AddExerciseResult()
    data class NeedsConfirmation(val exercise: Exercise) : AddExerciseResult()
    data object ArchivedRejected : AddExerciseResult()
}

fun WorkoutTemplateSet.toSetDraft(localId: Long): PlannedSetDraft {
    return PlannedSetLogic.toDraft(
        localId = localId,
        values = PlannedSetValues(
            minReps = minReps,
            maxReps = maxReps,
            loadKind = loadKind,
            weightKg = weightKg,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters
        )
    )
}
