package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate

/**
 * Requirement facts for Pro achievements.
 *
 * Meeting a requirement does not by itself earn the badge. Reconciliation awards it
 * only while the canonical entitlement grants Pro. Progress itself is derived again
 * from completed workout history and is not stored.
 */
data class ProQualification(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val clientWorkoutId: String?
)

object ProAchievementEvaluator {
    const val VOLUME_MASTER_KG = 100_000.0
    const val IRON_DISCIPLINE_WORKOUTS = 250

    fun qualifications(history: List<WorkoutSessionAggregate>): List<ProQualification> {
        return listOfNotNull(ironDiscipline(history), volumeMaster(history))
    }

    fun lifetimeVolumeKg(history: List<WorkoutSessionAggregate>): Double {
        return completed(history).sumOf { aggregate ->
            PerformanceRecordEvaluator.workoutVolumeKg(aggregate) ?: 0.0
        }
    }

    fun ironDiscipline(history: List<WorkoutSessionAggregate>): ProQualification? {
        val ordered = completed(history)
        if (ordered.size < IRON_DISCIPLINE_WORKOUTS) return null
        val crossing = ordered[IRON_DISCIPLINE_WORKOUTS - 1]
        return qualification(AchievementId.IRON_DISCIPLINE, crossing)
    }

    /**
     * The first completed workout whose eligible volume pushes the lifetime total
     * to [VOLUME_MASTER_KG]. Earlier workouts only build the baseline.
     */
    fun volumeMaster(history: List<WorkoutSessionAggregate>): ProQualification? {
        var total = 0.0
        completed(history).forEach { aggregate ->
            val added = PerformanceRecordEvaluator.workoutVolumeKg(aggregate) ?: return@forEach
            total += added
            if (total >= VOLUME_MASTER_KG) {
                return qualification(AchievementId.VOLUME_MASTER, aggregate)
            }
        }
        return null
    }

    private fun qualification(
        id: AchievementId,
        aggregate: WorkoutSessionAggregate
    ): ProQualification {
        return ProQualification(
            achievementId = id,
            unlockedAt = PerformanceRecordEvaluator.completedAt(aggregate),
            clientWorkoutId = aggregate.session.clientWorkoutId.takeIf { it.isNotBlank() }
        )
    }

    private fun completed(history: List<WorkoutSessionAggregate>): List<WorkoutSessionAggregate> {
        return history
            .filter { it.session.status == SessionStatus.COMPLETED }
            .sortedWith(compareBy({ PerformanceRecordEvaluator.completedAt(it) }, { it.session.id }))
    }
}
