package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.WorkoutSetVolume
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

private data class OrderedSet(
    val exerciseId: Long,
    val measurement: MeasurementType,
    val interpretation: WeightInterpretation,
    val set: SessionSet
)

object PerformanceRecordEvaluator {
    fun qualifications(history: List<WorkoutSessionAggregate>): List<PerformanceQualification> {
        val ordered = history
            .filter { it.session.status == SessionStatus.COMPLETED }
            .sortedWith(compareBy({ completedAt(it) }, { it.session.id }))
        val bestWeight = HashMap<Long, Double>()
        val bestReps = HashMap<Long, Int>()
        var bestVolume: Double? = null
        var weight: PerformanceQualification? = null
        var reps: PerformanceQualification? = null
        var volume: PerformanceQualification? = null
        ordered.forEach { aggregate ->
            val at = completedAt(aggregate)
            val client = aggregate.session.clientWorkoutId.takeIf { it.isNotBlank() }
            setsInOrder(aggregate).forEach { item ->
                comparableWeightKg(item)?.let { load ->
                    val previous = bestWeight[item.exerciseId]
                    if (previous == null) {
                        bestWeight[item.exerciseId] = load
                    } else if (load > previous) {
                        bestWeight[item.exerciseId] = load
                        if (weight == null) {
                            weight = PerformanceQualification(AchievementId.WEIGHT_PR, at, client)
                        }
                    }
                }
                comparableReps(item)?.let { count ->
                    val previous = bestReps[item.exerciseId]
                    if (previous == null) {
                        bestReps[item.exerciseId] = count
                    } else if (count > previous) {
                        bestReps[item.exerciseId] = count
                        if (reps == null) {
                            reps = PerformanceQualification(AchievementId.REP_RECORD, at, client)
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
                    if (volume == null) {
                        volume = PerformanceQualification(AchievementId.VOLUME_RECORD, at, client)
                    }
                }
            }
        }
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
                            measurement = item.exercise.measurementType,
                            interpretation = item.exercise.weightInterpretation,
                            set = set
                        )
                    }
            }
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
