package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate

/**
 * Lifetime completed sets of one exercise. Counts from different exercises never combine.
 * A qualifying set is a [SessionSetStatus.COMPLETED] set in a completed workout.
 */
object ExerciseMasteryEvaluator {
    fun leadingCount(history: List<WorkoutSessionAggregate>): Int {
        val counts = HashMap<Long, Int>()
        walk(history) { exerciseId, _, _ ->
            counts[exerciseId] = (counts[exerciseId] ?: 0) + 1
        }
        return counts.values.maxOrNull() ?: 0
    }

    fun qualifications(history: List<WorkoutSessionAggregate>): List<ProQualification> {
        val counts = HashMap<Long, Int>()
        val crossed = HashMap<AchievementId, ProQualification>()
        walk(history) { exerciseId, aggregate, _ ->
            val next = (counts[exerciseId] ?: 0) + 1
            counts[exerciseId] = next
            AchievementCatalog.exerciseMastery.forEach { id ->
                val target = id.masterySetTarget ?: return@forEach
                if (next == target && id !in crossed) {
                    crossed[id] = ProQualification(
                        achievementId = id,
                        unlockedAt = PerformanceRecordEvaluator.completedAt(aggregate),
                        clientWorkoutId = aggregate.session.clientWorkoutId.takeIf { it.isNotBlank() }
                    )
                }
            }
        }
        return AchievementCatalog.exerciseMastery.mapNotNull { crossed[it] }
    }

    private fun walk(
        history: List<WorkoutSessionAggregate>,
        onCompletedSet: (exerciseId: Long, aggregate: WorkoutSessionAggregate, set: SessionSet) -> Unit
    ) {
        PerformanceRecordEvaluator.completedInOrder(history).forEach { aggregate ->
            aggregate.exercises
                .sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
                .forEach { item ->
                    item.sets
                        .sortedWith(compareBy({ it.position }, { it.id }))
                        .forEach { set ->
                            if (set.status == SessionSetStatus.COMPLETED) {
                                onCompletedSet(item.exercise.exerciseId, aggregate, set)
                            }
                        }
                }
        }
    }
}
