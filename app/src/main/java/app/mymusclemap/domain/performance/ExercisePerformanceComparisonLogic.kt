package app.mymusclemap.domain.performance

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedLoadLogic
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.round

/**
 * Compares recorded exercise performance under matching conditions.
 *
 * The latest eligible occurrence is compared with the nearest earlier occurrence,
 * within [REFERENCE_WINDOW_DAYS] calendar days, that shares at least one direct
 * match. The window is a product limit, not a physiological threshold. Search
 * stops at the first match, so an older and more favorable session cannot replace
 * a nearer result.
 *
 * Completion order uses [WorkoutSession.finishedAt] when it is positive, and
 * otherwise [WorkoutSession.startedAt] when that is positive. A session with
 * neither is excluded. Equal timestamps are ordered by session id, highest id
 * last. Future workout dates are excluded. Pending sets, skipped sets, and
 * abandoned sessions never contribute evidence.
 *
 * Each set is read with the measurement, resistance, load kind, and weight
 * interpretation snapshotted on that session exercise. Catalog edits are not
 * consulted. Bodyweight-only sets are matched with [WeightInterpretation.NOT_APPLICABLE]
 * because that basis has no per-side or total external load. Added weight keeps
 * the snapshot interpretation and uses [WorkoutSetVolume.effectiveLoadKg], which
 * does not double per-side added weight and never includes body mass.
 *
 * Duplicate evidence: a same-repetition load comparison is dropped when both
 * sides already share that load and that repetition count on a same-load
 * repetition comparison. The remaining repetition comparison is the single
 * record of that unchanged set pair. Duration exercises emit duration
 * comparisons only, so they do not also emit a kilogram result.
 *
 * Complexity: occurrences are grouped and sorted once. The latest occurrence is
 * paired only with earlier ones until the first match, so a single exercise is
 * O(k) pair checks rather than a comparison of every historical pair.
 */
object ExercisePerformanceComparisonLogic {
    const val REFERENCE_WINDOW_DAYS = 90L
    private const val KILOGRAM_MILLIS = 1000.0

    fun compareAll(
        aggregates: List<WorkoutSessionAggregate>,
        today: LocalDate
    ): List<ExercisePerformanceComparison> {
        return occurrencesByExercise(aggregates, today, muscle = null)
            .map { (exerciseId, occurrences) -> compareOccurrences(exerciseId, occurrences) }
            .sortedWith(outputOrder)
    }

    fun compareExercise(
        exerciseId: Long,
        aggregates: List<WorkoutSessionAggregate>,
        today: LocalDate
    ): ExercisePerformanceComparison? {
        val occurrences = occurrencesByExercise(aggregates, today, muscle = null)[exerciseId]
            ?: return null
        return compareOccurrences(exerciseId, occurrences)
    }

    /**
     * Lists exercises that have an eligible primary-muscle occurrence inside
     * [period], ending at [periodEnd] or [today], whichever is earlier.
     *
     * The current occurrence is the latest one inside that window. A newer
     * occurrence outside the window is ignored. The reference may be older
     * than the period and stays inside the 90-day window. Secondary muscles
     * are ignored. [compareExercise] and [compareAll] still use the global
     * latest occurrence.
     */
    fun summarizeMuscle(
        aggregates: List<WorkoutSessionAggregate>,
        muscle: MuscleGroup,
        period: MuscleInsightsPeriod,
        today: LocalDate,
        periodEnd: LocalDate = today
    ): MuscleExercisePerformance {
        val end = if (periodEnd.isAfter(today)) today else periodEnd
        val exercises = occurrencesByExercise(aggregates, today, muscle)
            .mapNotNull { (exerciseId, occurrences) ->
                val ordered = occurrences.sortedWith(newestFirst)
                val currentIndex = ordered.indexOfFirst {
                    dateInListedPeriod(it.date, period, today, end)
                }
                if (currentIndex < 0) {
                    null
                } else {
                    compareOccurrences(exerciseId, ordered.drop(currentIndex))
                }
            }
            .sortedWith(outputOrder)
        return MuscleExercisePerformance(
            muscle = muscle,
            period = period,
            exercises = exercises,
            improved = exercises.count { it.outcome == RecordedPerformanceOutcome.IMPROVED },
            lowerRecordedPerformance = exercises.count {
                it.outcome == RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE
            },
            stable = exercises.count { it.outcome == RecordedPerformanceOutcome.STABLE },
            mixed = exercises.count { it.outcome == RecordedPerformanceOutcome.MIXED },
            insufficientOrNotComparable = exercises.count { it.outcome.isUncompared }
        )
    }

    private val outputOrder = compareBy<ExercisePerformanceComparison> {
        it.displayName.lowercase(Locale.ROOT)
    }.thenBy { it.exerciseId }

    private fun compareOccurrences(
        exerciseId: Long,
        occurrences: List<Occurrence>
    ): ExercisePerformanceComparison {
        val ordered = occurrences.sortedWith(newestFirst)
        val current = ordered.first()
        if (current.evidence.isEmpty()) {
            return unmatched(
                current = current,
                exerciseId = exerciseId,
                outcome = RecordedPerformanceOutcome.UNSUPPORTED,
                limitation = unsupportedLimitation(current)
            )
        }
        val earlier = ordered.drop(1)
        val inWindow = earlier.filter { withinWindow(current.date, it.date) }
        val reference = inWindow.firstOrNull { candidate ->
            matchedComparisons(current, candidate).isNotEmpty()
        }
        if (reference == null) {
            val limitation = when {
                earlier.isEmpty() -> ComparisonLimitation.ONLY_ONE_OCCURRENCE
                inWindow.isEmpty() -> ComparisonLimitation.NO_EARLIER_OCCURRENCE_IN_WINDOW
                else -> ComparisonLimitation.NO_MATCHING_CONDITION
            }
            val outcome = if (limitation == ComparisonLimitation.NO_MATCHING_CONDITION) {
                RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE
            } else {
                RecordedPerformanceOutcome.INSUFFICIENT_HISTORY
            }
            return unmatched(current, exerciseId, outcome, limitation)
        }
        val comparisons = matchedComparisons(current, reference)
        val outcome = classify(comparisons)
        return ExercisePerformanceComparison(
            exerciseId = exerciseId,
            displayName = current.name,
            currentSessionId = current.sessionId,
            currentDate = current.date,
            currentCompletedAt = current.completedAt,
            referenceSessionId = reference.sessionId,
            referenceDate = reference.date,
            referenceCompletedAt = reference.completedAt,
            outcome = outcome,
            limitation = ComparisonLimitation.NONE,
            comparisons = comparisons,
            includesZeroDelta = comparisons.any { it.zero },
            matchedConditionCount = comparisons.size
        )
    }

    private fun unmatched(
        current: Occurrence,
        exerciseId: Long,
        outcome: RecordedPerformanceOutcome,
        limitation: ComparisonLimitation
    ): ExercisePerformanceComparison {
        return ExercisePerformanceComparison(
            exerciseId = exerciseId,
            displayName = current.name,
            currentSessionId = current.sessionId,
            currentDate = current.date,
            currentCompletedAt = current.completedAt,
            referenceSessionId = null,
            referenceDate = null,
            referenceCompletedAt = null,
            outcome = outcome,
            limitation = limitation,
            comparisons = emptyList(),
            includesZeroDelta = false,
            matchedConditionCount = 0
        )
    }

    /**
     * Same-load repetition and duration comparisons, plus same-repetition load
     * comparisons that are not already represented by an equal repetition match.
     */
    private fun matchedComparisons(current: Occurrence, reference: Occurrence): List<MatchedPerformance> {
        val repetitionMatches = buildList {
            current.maxReps.forEach { (condition, currentReps) ->
                val referenceReps = reference.maxReps[condition] ?: return@forEach
                add(repetitionMatch(condition, currentReps, referenceReps))
            }
        }
        val loadMatches = buildList {
            current.maxLoad.forEach { (repsCondition, currentLoad) ->
                val referenceLoad = reference.maxLoad[repsCondition] ?: return@forEach
                if (isDuplicateLoadMatch(repsCondition, currentLoad, referenceLoad, repetitionMatches)) {
                    return@forEach
                }
                add(loadMatch(repsCondition, currentLoad, referenceLoad))
            }
        }
        val durationMatches = buildList {
            current.maxDuration.forEach { (condition, currentSeconds) ->
                val referenceSeconds = reference.maxDuration[condition] ?: return@forEach
                add(durationMatch(condition, currentSeconds, referenceSeconds))
            }
        }
        return (repetitionMatches + loadMatches + durationMatches).sortedWith(comparisonOrder)
    }

    private fun isDuplicateLoadMatch(
        repsCondition: RepsCondition,
        currentLoad: Long,
        referenceLoad: Long,
        repetitionMatches: List<MatchedPerformance>
    ): Boolean {
        if (currentLoad != referenceLoad) return false
        val sharedKilograms = kilograms(currentLoad)
        return repetitionMatches.any { match ->
            match.dimension == ComparisonDimension.REPETITIONS &&
                match.zero &&
                match.matchedEffectiveKilograms == sharedKilograms &&
                match.current == repsCondition.repetitions.toDouble() &&
                match.reference == repsCondition.repetitions.toDouble() &&
                match.measurementType == repsCondition.measurementType &&
                match.resistanceBasis == repsCondition.resistanceBasis &&
                match.loadKind == repsCondition.loadKind &&
                match.weightInterpretation == repsCondition.weightInterpretation
        }
    }

    private fun repetitionMatch(condition: LoadCondition, currentReps: Int, referenceReps: Int): MatchedPerformance {
        return MatchedPerformance(
            dimension = ComparisonDimension.REPETITIONS,
            delta = (currentReps - referenceReps).toDouble(),
            current = currentReps.toDouble(),
            reference = referenceReps.toDouble(),
            measurementType = condition.measurementType,
            resistanceBasis = condition.resistanceBasis,
            loadKind = condition.loadKind,
            weightInterpretation = condition.weightInterpretation,
            matchedEffectiveKilograms = condition.canonicalKilograms?.let(::kilograms),
            matchedRepetitions = null
        )
    }

    private fun loadMatch(condition: RepsCondition, currentLoad: Long, referenceLoad: Long): MatchedPerformance {
        val currentKg = kilograms(currentLoad)
        val referenceKg = kilograms(referenceLoad)
        return MatchedPerformance(
            dimension = ComparisonDimension.KILOGRAMS,
            delta = currentKg - referenceKg,
            current = currentKg,
            reference = referenceKg,
            measurementType = condition.measurementType,
            resistanceBasis = condition.resistanceBasis,
            loadKind = condition.loadKind,
            weightInterpretation = condition.weightInterpretation,
            matchedEffectiveKilograms = null,
            matchedRepetitions = condition.repetitions
        )
    }

    private fun durationMatch(
        condition: LoadCondition,
        currentSeconds: Int,
        referenceSeconds: Int
    ): MatchedPerformance {
        return MatchedPerformance(
            dimension = ComparisonDimension.DURATION_SECONDS,
            delta = (currentSeconds - referenceSeconds).toDouble(),
            current = currentSeconds.toDouble(),
            reference = referenceSeconds.toDouble(),
            measurementType = condition.measurementType,
            resistanceBasis = condition.resistanceBasis,
            loadKind = condition.loadKind,
            weightInterpretation = condition.weightInterpretation,
            matchedEffectiveKilograms = condition.canonicalKilograms?.let(::kilograms),
            matchedRepetitions = null
        )
    }

    private fun classify(comparisons: List<MatchedPerformance>): RecordedPerformanceOutcome {
        val nonzero = comparisons.filterNot { it.zero }
        return when {
            nonzero.isEmpty() -> RecordedPerformanceOutcome.STABLE
            nonzero.all { it.positive } -> RecordedPerformanceOutcome.IMPROVED
            nonzero.all { it.negative } -> RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE
            else -> RecordedPerformanceOutcome.MIXED
        }
    }

    private fun unsupportedLimitation(occurrence: Occurrence): ComparisonLimitation {
        return when {
            occurrence.sawInvalid -> ComparisonLimitation.INVALID_OR_MISSING_LOAD
            occurrence.sawAssistance && !occurrence.sawUnsupportedType ->
                ComparisonLimitation.ASSISTANCE_EXCLUDED
            else -> ComparisonLimitation.UNSUPPORTED_TYPE
        }
    }

    private fun dateInListedPeriod(
        date: LocalDate,
        period: MuscleInsightsPeriod,
        today: LocalDate,
        periodEnd: LocalDate
    ): Boolean {
        if (date.isAfter(periodEnd)) return false
        return period.contains(date, today)
    }

    private fun withinWindow(latest: LocalDate, earlier: LocalDate): Boolean {
        if (earlier.isAfter(latest)) return false
        return ChronoUnit.DAYS.between(earlier, latest) <= REFERENCE_WINDOW_DAYS
    }

    private fun occurrencesByExercise(
        aggregates: List<WorkoutSessionAggregate>,
        today: LocalDate,
        muscle: MuscleGroup?
    ): Map<Long, List<Occurrence>> {
        val grouped = linkedMapOf<Long, MutableList<Occurrence>>()
        aggregates.forEach { aggregate ->
            val session = aggregate.session
            if (session.status != SessionStatus.COMPLETED) return@forEach
            if (session.workoutDate.isAfter(today)) return@forEach
            val completedAt = completionMillis(session) ?: return@forEach
            val rows = aggregate.exercises.filter { item ->
                muscle == null || item.exercise.primaryMuscle == muscle
            }
            val byExercise = rows.groupBy { it.exercise.exerciseId }
            byExercise.forEach { (exerciseId, items) ->
                val occurrence = occurrence(exerciseId, session.id, session.workoutDate, completedAt, items)
                    ?: return@forEach
                grouped.getOrPut(exerciseId) { mutableListOf() }.add(occurrence)
            }
        }
        return grouped
    }

    private fun occurrence(
        exerciseId: Long,
        sessionId: Long,
        date: LocalDate,
        completedAt: Long,
        items: List<app.mymusclemap.domain.workout.SessionExerciseItem>
    ): Occurrence? {
        val evidence = mutableListOf<Evidence>()
        var sawAssistance = false
        var sawInvalid = false
        var sawUnsupportedType = false
        var sawCompleted = false
        var name = items.first().exercise.name
        var namePosition = Int.MIN_VALUE
        var nameRowId = Long.MIN_VALUE
        items.forEach { item ->
            val exercise = item.exercise
            if (exercise.position > namePosition ||
                (exercise.position == namePosition && exercise.id > nameRowId)
            ) {
                namePosition = exercise.position
                nameRowId = exercise.id
                name = exercise.name
            }
            item.sets.forEach { set ->
                when (val reading = readSet(exercise, set)) {
                    Reading.Ineligible -> Unit
                    Reading.Assistance -> {
                        sawCompleted = true
                        sawAssistance = true
                    }
                    Reading.Invalid -> {
                        sawCompleted = true
                        sawInvalid = true
                    }
                    Reading.UnsupportedType -> {
                        sawCompleted = true
                        sawUnsupportedType = true
                    }
                    is Reading.Comparable -> {
                        sawCompleted = true
                        evidence += reading.evidence
                    }
                }
            }
        }
        if (!sawCompleted) return null
        return Occurrence(
            exerciseId = exerciseId,
            sessionId = sessionId,
            name = name,
            date = date,
            completedAt = completedAt,
            evidence = evidence,
            sawAssistance = sawAssistance,
            sawInvalid = sawInvalid,
            sawUnsupportedType = sawUnsupportedType
        )
    }

    private fun readSet(exercise: SessionExercise, set: SessionSet): Reading {
        if (set.status != SessionSetStatus.COMPLETED) return Reading.Ineligible
        val measurement = exercise.measurementType
        val resistance = exercise.resistanceBasis
        val kind = set.actualLoadKind ?: return Reading.Invalid
        if (measurement == MeasurementType.DISTANCE_AND_DURATION ||
            measurement == MeasurementType.COMPLETION_ONLY
        ) {
            return Reading.UnsupportedType
        }
        if (kind == PlannedLoadKind.ASSISTANCE) return Reading.Assistance
        if (kind !in PlannedLoadLogic.compatibleKinds(resistance, measurement) ||
            kind == PlannedLoadKind.NONE
        ) {
            return Reading.UnsupportedType
        }
        return when (measurement) {
            MeasurementType.REPETITIONS,
            MeasurementType.REPETITIONS_AND_WEIGHT -> readRepSet(exercise, set, kind)
            MeasurementType.DURATION,
            MeasurementType.DURATION_AND_WEIGHT -> readDurationSet(exercise, set, kind)
            MeasurementType.DISTANCE_AND_DURATION,
            MeasurementType.COMPLETION_ONLY -> Reading.UnsupportedType
        }
    }

    private fun readRepSet(
        exercise: SessionExercise,
        set: SessionSet,
        kind: PlannedLoadKind
    ): Reading {
        val reps = set.actualReps ?: return Reading.Invalid
        if (reps <= 0) return Reading.Invalid
        val measurement = exercise.measurementType
        val resistance = exercise.resistanceBasis
        return when (kind) {
            PlannedLoadKind.BODYWEIGHT_ONLY -> {
                if (resistance != ResistanceBasis.BODYWEIGHT) return Reading.UnsupportedType
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    canonicalKilograms = null,
                    reps = reps,
                    durationSeconds = null
                )
            }
            PlannedLoadKind.ADDED_WEIGHT -> {
                if (resistance != ResistanceBasis.BODYWEIGHT) return Reading.UnsupportedType
                val kilograms = canonicalLoad(set, exercise.weightInterpretation) ?: return Reading.Invalid
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = exercise.weightInterpretation,
                    canonicalKilograms = kilograms,
                    reps = reps,
                    durationSeconds = null
                )
            }
            PlannedLoadKind.EXTERNAL_WEIGHT -> {
                if (resistance != ResistanceBasis.EXTERNAL ||
                    measurement != MeasurementType.REPETITIONS_AND_WEIGHT
                ) {
                    return Reading.UnsupportedType
                }
                val interpretation = exercise.weightInterpretation
                if (interpretation != WeightInterpretation.TOTAL &&
                    interpretation != WeightInterpretation.PER_SIDE
                ) {
                    return Reading.Invalid
                }
                val kilograms = canonicalLoad(set, interpretation) ?: return Reading.Invalid
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = interpretation,
                    canonicalKilograms = kilograms,
                    reps = reps,
                    durationSeconds = null
                )
            }
            else -> Reading.UnsupportedType
        }
    }

    private fun readDurationSet(
        exercise: SessionExercise,
        set: SessionSet,
        kind: PlannedLoadKind
    ): Reading {
        val seconds = set.actualDurationSeconds ?: return Reading.Invalid
        if (seconds <= 0) return Reading.Invalid
        val measurement = exercise.measurementType
        val resistance = exercise.resistanceBasis
        return when (kind) {
            PlannedLoadKind.BODYWEIGHT_ONLY -> {
                if (resistance != ResistanceBasis.BODYWEIGHT) return Reading.UnsupportedType
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    canonicalKilograms = null,
                    reps = null,
                    durationSeconds = seconds
                )
            }
            PlannedLoadKind.ADDED_WEIGHT -> {
                if (resistance != ResistanceBasis.BODYWEIGHT) return Reading.UnsupportedType
                val kilograms = canonicalLoad(set, exercise.weightInterpretation) ?: return Reading.Invalid
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = exercise.weightInterpretation,
                    canonicalKilograms = kilograms,
                    reps = null,
                    durationSeconds = seconds
                )
            }
            PlannedLoadKind.EXTERNAL_WEIGHT -> {
                if (resistance != ResistanceBasis.EXTERNAL ||
                    measurement != MeasurementType.DURATION_AND_WEIGHT
                ) {
                    return Reading.UnsupportedType
                }
                val interpretation = exercise.weightInterpretation
                if (interpretation != WeightInterpretation.TOTAL &&
                    interpretation != WeightInterpretation.PER_SIDE
                ) {
                    return Reading.Invalid
                }
                val kilograms = canonicalLoad(set, interpretation) ?: return Reading.Invalid
                comparable(
                    measurement = measurement,
                    resistance = resistance,
                    kind = kind,
                    interpretation = interpretation,
                    canonicalKilograms = kilograms,
                    reps = null,
                    durationSeconds = seconds
                )
            }
            else -> Reading.UnsupportedType
        }
    }

    private fun comparable(
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        kind: PlannedLoadKind,
        interpretation: WeightInterpretation,
        canonicalKilograms: Long?,
        reps: Int?,
        durationSeconds: Int?
    ): Reading {
        return Reading.Comparable(
            Evidence(
                measurementType = measurement,
                resistanceBasis = resistance,
                loadKind = kind,
                weightInterpretation = interpretation,
                canonicalKilograms = canonicalKilograms,
                reps = reps,
                durationSeconds = durationSeconds
            )
        )
    }

    /** Positive finite effective kilograms, rounded to the nearest milligram. */
    private fun canonicalLoad(set: SessionSet, interpretation: WeightInterpretation): Long? {
        val effective = WorkoutSetVolume.effectiveLoadKg(set, interpretation) ?: return null
        if (!effective.isFinite() || effective <= 0.0) return null
        val scaled = effective * KILOGRAM_MILLIS
        if (!scaled.isFinite()) return null
        val rounded = round(scaled)
        if (!rounded.isFinite() || rounded <= 0.0) return null
        return rounded.toLong()
    }

    private fun kilograms(canonical: Long): Double = canonical / KILOGRAM_MILLIS

    /**
     * Positive [WorkoutSession.finishedAt], otherwise positive [WorkoutSession.startedAt].
     * Null when neither instant is usable.
     */
    private fun completionMillis(session: WorkoutSession): Long? {
        val finished = session.finishedAt
        if (finished != null && finished > 0L) return finished
        if (session.startedAt > 0L) return session.startedAt
        return null
    }

    private val newestFirst = compareByDescending<Occurrence> { it.completedAt }
        .thenByDescending { it.sessionId }

    private val comparisonOrder = compareBy<MatchedPerformance> { it.dimension.ordinal }
        .thenBy { it.loadKind.ordinal }
        .thenBy { it.weightInterpretation.ordinal }
        .thenBy { it.matchedEffectiveKilograms ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.matchedRepetitions ?: Int.MIN_VALUE }
        .thenBy { it.measurementType.ordinal }

    private val RecordedPerformanceOutcome.isUncompared: Boolean
        get() = this == RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE ||
            this == RecordedPerformanceOutcome.INSUFFICIENT_HISTORY ||
            this == RecordedPerformanceOutcome.UNSUPPORTED
}

private data class Occurrence(
    val exerciseId: Long,
    val sessionId: Long,
    val name: String,
    val date: LocalDate,
    val completedAt: Long,
    val evidence: List<Evidence>,
    val sawAssistance: Boolean,
    val sawInvalid: Boolean,
    val sawUnsupportedType: Boolean
) {
    val maxReps: Map<LoadCondition, Int> by lazy {
        buildMap {
            evidence.forEach { item ->
                val reps = item.reps ?: return@forEach
                val condition = item.loadCondition()
                val previous = get(condition)
                if (previous == null || reps > previous) {
                    put(condition, reps)
                }
            }
        }
    }

    val maxDuration: Map<LoadCondition, Int> by lazy {
        buildMap {
            evidence.forEach { item ->
                val seconds = item.durationSeconds ?: return@forEach
                val condition = item.loadCondition()
                val previous = get(condition)
                if (previous == null || seconds > previous) {
                    put(condition, seconds)
                }
            }
        }
    }

    val maxLoad: Map<RepsCondition, Long> by lazy {
        buildMap {
            evidence.forEach { item ->
                val reps = item.reps ?: return@forEach
                val kilograms = item.canonicalKilograms ?: return@forEach
                if (!item.measurementType.recordsRepetitions()) return@forEach
                val condition = RepsCondition(
                    measurementType = item.measurementType,
                    resistanceBasis = item.resistanceBasis,
                    loadKind = item.loadKind,
                    weightInterpretation = item.weightInterpretation,
                    repetitions = reps
                )
                val previous = get(condition)
                if (previous == null || kilograms > previous) {
                    put(condition, kilograms)
                }
            }
        }
    }
}

private data class Evidence(
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val loadKind: PlannedLoadKind,
    val weightInterpretation: WeightInterpretation,
    val canonicalKilograms: Long?,
    val reps: Int?,
    val durationSeconds: Int?
) {
    fun loadCondition(): LoadCondition {
        return LoadCondition(
            measurementType = measurementType,
            resistanceBasis = resistanceBasis,
            loadKind = loadKind,
            weightInterpretation = weightInterpretation,
            canonicalKilograms = canonicalKilograms
        )
    }
}

private data class LoadCondition(
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val loadKind: PlannedLoadKind,
    val weightInterpretation: WeightInterpretation,
    val canonicalKilograms: Long?
)

private data class RepsCondition(
    val measurementType: MeasurementType,
    val resistanceBasis: ResistanceBasis,
    val loadKind: PlannedLoadKind,
    val weightInterpretation: WeightInterpretation,
    val repetitions: Int
)

private sealed interface Reading {
    data object Ineligible : Reading
    data object Assistance : Reading
    data object Invalid : Reading
    data object UnsupportedType : Reading
    data class Comparable(val evidence: Evidence) : Reading
}

private fun MeasurementType.recordsRepetitions(): Boolean {
    return this == MeasurementType.REPETITIONS || this == MeasurementType.REPETITIONS_AND_WEIGHT
}
