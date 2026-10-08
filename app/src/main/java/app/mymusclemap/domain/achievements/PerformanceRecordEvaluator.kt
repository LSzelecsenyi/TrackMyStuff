package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate

/**
 * Lifetime Performance records reconstructed from completed workout history.
 *
 * A record is a later qualifying performance that exceeds a comparable earlier one.
 * The first performance of an exercise, or the first workout that has volume, is only
 * a baseline. Abandoned, in-progress, and skipped sets never qualify.
 *
 * Weight and volume use [WorkoutSetVolume]. Bodyweight-only and assistance loads are
 * not converted into kilograms. Added weight is the external load only.
 * Rep records are the plain maximum completed repetitions for that exercise.
 */
data class PerformanceQualification(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val clientWorkoutId: String?
)

/** One strict improvement over the previous comparable record. The first performance is not an event. */
enum class PerformanceRecordKind {
    WEIGHT,
    REPS,
    VOLUME
}

data class PerformanceRecordEvent(
    val kind: PerformanceRecordKind,
    val unlockedAt: Long,
    val clientWorkoutId: String?,
    val sessionId: Long
)

/** How a highlight value should be shown. The stored numbers are the comparable values. */
enum class HighlightMeasure {
    ADDED_KG,
    EXTERNAL_KG,
    /** Effective load, which is the per-side weight doubled. */
    PER_SIDE_TOTAL_KG,
    REPS,
    VOLUME_KG
}

/**
 * One genuine record in a single completed workout.
 * Previous is the best comparable value before the first improvement in that workout.
 * Current is the best comparable value after the workout. The first performance is omitted.
 */
data class PerformanceHighlight(
    val kind: PerformanceRecordKind,
    val exerciseName: String?,
    val previous: Double,
    val current: Double,
    val measure: HighlightMeasure
)

private data class OrderedSet(
    val exerciseId: Long,
    val exerciseName: String,
    val measurement: MeasurementType,
    val interpretation: WeightInterpretation,
    val set: SessionSet
)

object PerformanceRecordEvaluator {
    fun qualifications(history: List<WorkoutSessionAggregate>): List<PerformanceQualification> {
        return qualificationsFrom(events(history))
    }

    /**
     * Chronological record events. Lifetime Performance badges use the first event of each kind.
     * Later events stay in this list for PR Hunter and are not stored.
     */
    fun events(history: List<WorkoutSessionAggregate>): List<PerformanceRecordEvent> {
        val bestWeight = HashMap<Long, Double>()
        val bestReps = HashMap<Long, Int>()
        var bestVolume: Double? = null
        val found = ArrayList<PerformanceRecordEvent>()
        completedInOrder(history).forEach { aggregate ->
            val at = completedAt(aggregate)
            val client = aggregate.session.clientWorkoutId.takeIf { it.isNotBlank() }
            val sessionId = aggregate.session.id
            setsInOrder(aggregate).forEach { item ->
                comparableWeightKg(item)?.let { load ->
                    val previous = bestWeight[item.exerciseId]
                    if (previous == null) {
                        bestWeight[item.exerciseId] = load
                    } else if (load > previous) {
                        bestWeight[item.exerciseId] = load
                        found += PerformanceRecordEvent(PerformanceRecordKind.WEIGHT, at, client, sessionId)
                    }
                }
                comparableReps(item)?.let { count ->
                    val previous = bestReps[item.exerciseId]
                    if (previous == null) {
                        bestReps[item.exerciseId] = count
                    } else if (count > previous) {
                        bestReps[item.exerciseId] = count
                        found += PerformanceRecordEvent(PerformanceRecordKind.REPS, at, client, sessionId)
                    }
                }
            }
            val workoutVolume = workoutVolumeKg(aggregate)
            if (workoutVolume != null) {
                val previous = bestVolume
                if (previous == null) {
                    bestVolume = workoutVolume
                } else if (workoutVolume > previous) {
                    bestVolume = workoutVolume
                    found += PerformanceRecordEvent(PerformanceRecordKind.VOLUME, at, client, sessionId)
                }
            }
        }
        return found
    }

    /**
     * Records achieved inside [clientWorkoutId], collapsed to one line per exercise and kind.
     * Uses the same comparisons as [events]. Later records are included after a badge already exists.
     * This does not create or count achievement unlocks.
     */
    fun highlightsFor(
        history: List<WorkoutSessionAggregate>,
        clientWorkoutId: String
    ): List<PerformanceHighlight> {
        if (clientWorkoutId.isBlank()) return emptyList()
        val bestWeight = HashMap<Long, Double>()
        val bestReps = HashMap<Long, Int>()
        var bestVolume: Double? = null
        val found = ArrayList<PerformanceHighlight>()
        completedInOrder(history).forEach { aggregate ->
            val client = aggregate.session.clientWorkoutId.takeIf { it.isNotBlank() }
            val volumeBefore = bestVolume
            val weightBeforeFirstBeat = HashMap<Long, Double>()
            val repsBeforeFirstBeat = HashMap<Long, Int>()
            val weightRecord = HashMap<Long, OrderedSet>()
            val repsRecord = HashMap<Long, OrderedSet>()
            setsInOrder(aggregate).forEach { item ->
                comparableWeightKg(item)?.let { load ->
                    val previous = bestWeight[item.exerciseId]
                    if (previous == null) {
                        bestWeight[item.exerciseId] = load
                    } else if (load > previous) {
                        if (client == clientWorkoutId && weightBeforeFirstBeat[item.exerciseId] == null) {
                            weightBeforeFirstBeat[item.exerciseId] = previous
                        }
                        bestWeight[item.exerciseId] = load
                        if (client == clientWorkoutId) {
                            weightRecord[item.exerciseId] = item
                        }
                    }
                }
                comparableReps(item)?.let { count ->
                    val previous = bestReps[item.exerciseId]
                    if (previous == null) {
                        bestReps[item.exerciseId] = count
                    } else if (count > previous) {
                        if (client == clientWorkoutId && repsBeforeFirstBeat[item.exerciseId] == null) {
                            repsBeforeFirstBeat[item.exerciseId] = previous
                        }
                        bestReps[item.exerciseId] = count
                        if (client == clientWorkoutId) {
                            repsRecord[item.exerciseId] = item
                        }
                    }
                }
            }
            val workoutVolume = workoutVolumeKg(aggregate)
            if (workoutVolume != null) {
                val previous = bestVolume
                if (previous == null) {
                    bestVolume = workoutVolume
                } else if (workoutVolume > previous) {
                    bestVolume = workoutVolume
                }
            }
            if (client != clientWorkoutId) return@forEach
            val seen = HashSet<Long>()
            aggregate.exercises
                .sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
                .forEach { item ->
                    val exerciseId = item.exercise.exerciseId
                    if (!seen.add(exerciseId)) return@forEach
                    weightBeforeFirstBeat[exerciseId]?.let { previous ->
                        val record = weightRecord[exerciseId] ?: return@let
                        found += PerformanceHighlight(
                            kind = PerformanceRecordKind.WEIGHT,
                            exerciseName = record.exerciseName,
                            previous = previous,
                            current = bestWeight.getValue(exerciseId),
                            measure = weightMeasure(record)
                        )
                    }
                    repsBeforeFirstBeat[exerciseId]?.let { previous ->
                        val record = repsRecord[exerciseId] ?: return@let
                        found += PerformanceHighlight(
                            kind = PerformanceRecordKind.REPS,
                            exerciseName = record.exerciseName,
                            previous = previous.toDouble(),
                            current = bestReps.getValue(exerciseId).toDouble(),
                            measure = HighlightMeasure.REPS
                        )
                    }
                }
            if (workoutVolume != null && volumeBefore != null && workoutVolume > volumeBefore) {
                found += PerformanceHighlight(
                    kind = PerformanceRecordKind.VOLUME,
                    exerciseName = null,
                    previous = volumeBefore,
                    current = workoutVolume,
                    measure = HighlightMeasure.VOLUME_KG
                )
            }
        }
        return found
    }

    fun qualificationsFrom(events: List<PerformanceRecordEvent>): List<PerformanceQualification> {
        val weight = events.firstOrNull { it.kind == PerformanceRecordKind.WEIGHT }
            ?.toQualification(AchievementId.WEIGHT_PR)
        val reps = events.firstOrNull { it.kind == PerformanceRecordKind.REPS }
            ?.toQualification(AchievementId.REP_RECORD)
        val volume = events.firstOrNull { it.kind == PerformanceRecordKind.VOLUME }
            ?.toQualification(AchievementId.VOLUME_RECORD)
        val records = listOfNotNull(weight, reps, volume)
        val first = records.minWithOrNull(
            compareBy<PerformanceQualification> { it.unlockedAt }.thenBy { recordRank(it.achievementId) }
        )
        return buildList {
            if (first != null) {
                add(
                    PerformanceQualification(
                        achievementId = AchievementId.FIRST_PR,
                        unlockedAt = first.unlockedAt,
                        clientWorkoutId = first.clientWorkoutId
                    )
                )
            }
            addAll(records)
        }
    }

    fun completedInOrder(history: List<WorkoutSessionAggregate>): List<WorkoutSessionAggregate> {
        return history
            .filter { it.session.status == SessionStatus.COMPLETED }
            .sortedWith(compareBy({ completedAt(it) }, { it.session.id }))
    }

    /** Session completion instant. [app.mymusclemap.domain.workout.WorkoutSession.finishedAt] is that instant. */
    fun completedAt(aggregate: WorkoutSessionAggregate): Long {
        return aggregate.session.finishedAt ?: aggregate.session.startedAt
    }

    /**
     * Kilogram volume of one completed workout, using the Statistics definition.
     * Returns null when the workout has no volume-eligible set.
     */
    fun workoutVolumeKg(aggregate: WorkoutSessionAggregate): Double? {
        if (aggregate.session.status != SessionStatus.COMPLETED) return null
        val volumes = aggregate.exercises.flatMap { item ->
            item.sets.mapNotNull { set -> WorkoutSetVolume.volumeKg(item.exercise, set) }
        }
        if (volumes.isEmpty()) return null
        return volumes.sum()
    }

    private fun comparableWeightKg(item: OrderedSet): Double? {
        if (item.measurement != MeasurementType.REPETITIONS_AND_WEIGHT) return null
        if (!positiveCompletedReps(item.set)) return null
        return WorkoutSetVolume.effectiveLoadKg(item.set, item.interpretation)
    }

    private fun comparableReps(item: OrderedSet): Int? {
        if (item.measurement != MeasurementType.REPETITIONS &&
            item.measurement != MeasurementType.REPETITIONS_AND_WEIGHT
        ) {
            return null
        }
        if (!positiveCompletedReps(item.set)) return null
        return item.set.actualReps
    }

    private fun positiveCompletedReps(set: SessionSet): Boolean {
        if (!WorkoutSetVolume.isCompleted(set)) return false
        val reps = set.actualReps ?: return false
        return reps > 0
    }

    private fun setsInOrder(aggregate: WorkoutSessionAggregate): List<OrderedSet> {
        return aggregate.exercises
            .sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
            .flatMap { item ->
                item.sets
                    .sortedWith(compareBy({ it.position }, { it.id }))
                    .map { set ->
                        OrderedSet(
                            exerciseId = item.exercise.exerciseId,
                            exerciseName = item.exercise.name,
                            measurement = item.exercise.measurementType,
                            interpretation = item.exercise.weightInterpretation,
                            set = set
                        )
                    }
            }
    }

    private fun weightMeasure(item: OrderedSet): HighlightMeasure {
        return when (item.set.actualLoadKind) {
            PlannedLoadKind.ADDED_WEIGHT -> HighlightMeasure.ADDED_KG
            PlannedLoadKind.EXTERNAL_WEIGHT -> if (item.interpretation == WeightInterpretation.PER_SIDE) {
                HighlightMeasure.PER_SIDE_TOTAL_KG
            } else {
                HighlightMeasure.EXTERNAL_KG
            }
            else -> HighlightMeasure.EXTERNAL_KG
        }
    }

    private fun PerformanceRecordEvent.toQualification(id: AchievementId): PerformanceQualification {
        return PerformanceQualification(id, unlockedAt, clientWorkoutId)
    }

    /** Earlier rank wins a timestamp tie so First PR follows a stable record. */
    private fun recordRank(id: AchievementId): Int {
        return when (id) {
            AchievementId.WEIGHT_PR -> 0
            AchievementId.REP_RECORD -> 1
            AchievementId.VOLUME_RECORD -> 2
            else -> 3
        }
    }
}
