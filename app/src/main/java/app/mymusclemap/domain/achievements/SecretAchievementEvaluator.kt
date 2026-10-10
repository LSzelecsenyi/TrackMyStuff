package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * One-time secret awards.
 *
 * The calendar date is [app.mymusclemap.domain.workout.WorkoutSession.workoutDate].
 * That value is the local date captured when the workout started, or the calendar
 * date supplied with an import. It is not derived from [startedAt], because that
 * instant has no stored timezone and must not be reinterpreted with the device zone.
 *
 * One More Thing uses the session's prescribed sets (`addedDuringWorkout == false`)
 * and each set's `completedAt`. Positions and set counts are not used as evidence.
 * A workout without those timestamps does not qualify.
 */
data class SecretQualification(
    val achievementId: AchievementId,
    val unlockedAt: Long,
    val clientWorkoutId: String?
)

object SecretAchievementEvaluator {
    fun qualifications(history: List<WorkoutSessionAggregate>): List<SecretQualification> {
        val eligible = history.filter { eligible(it) }
        val records = PerformanceRecordEvaluator.events(history)
        return listOfNotNull(
            earliest(eligible, AchievementId.SILENT_NIGHT) {
                it.month == Month.DECEMBER && it.dayOfMonth in 24..26
            },
            earliest(eligible, AchievementId.TRICK_OR_LIFT) { it.month == Month.OCTOBER && it.dayOfMonth == 31 },
            earliest(eligible, AchievementId.NEW_YEAR_SAME_ME) { it.month == Month.JANUARY && it.dayOfMonth == 1 },
            earliest(eligible, AchievementId.LEAP_DAY_LIFTER) { it.month == Month.FEBRUARY && it.dayOfMonth == 29 },
            earliest(eligible, AchievementId.FRIDAY_THE_STRONGTEENTH) {
                it.dayOfWeek == DayOfWeek.FRIDAY && it.dayOfMonth == 13
            },
            earliestAggregate(eligible, AchievementId.ONE_MORE_THING) { oneMoreThing(it) },
            tripleCrown(records)
        )
    }

    /**
     * A completed workout with at least one set that was actually performed.
     * Skipped, pending, empty, abandoned, and in-progress sessions do not qualify.
     */
    fun eligible(aggregate: WorkoutSessionAggregate): Boolean {
        if (aggregate.session.status != SessionStatus.COMPLETED) return false
        return aggregate.exercises.any { item ->
            item.sets.any { genuinelyPerformed(item.exercise.measurementType, it) }
        }
    }

    fun genuinelyPerformed(measurement: MeasurementType, set: SessionSet): Boolean {
        if (set.status != SessionSetStatus.COMPLETED) return false
        return when (measurement) {
            MeasurementType.REPETITIONS,
            MeasurementType.REPETITIONS_AND_WEIGHT -> (set.actualReps ?: 0) > 0
            MeasurementType.DURATION,
            MeasurementType.DURATION_AND_WEIGHT -> (set.actualDurationSeconds ?: 0) > 0
            MeasurementType.DISTANCE_AND_DURATION ->
                (set.actualDistanceMeters ?: 0.0) > 0.0 || (set.actualDurationSeconds ?: 0) > 0
            MeasurementType.COMPLETION_ONLY -> true
        }
    }

    /**
     * Every original prescribed set is completed, and a later completed extra set
     * belongs to an exercise that already had a prescribed set.
     */
    fun oneMoreThing(aggregate: WorkoutSessionAggregate): Boolean {
        if (aggregate.session.templateId == null) return false
        val prescribed = aggregate.exercises.flatMap { it.sets }.filter { !it.addedDuringWorkout }
        if (prescribed.isEmpty()) return false
        if (prescribed.any { it.status != SessionSetStatus.COMPLETED || it.completedAt == null }) {
            return false
        }
        val prescribedFinishedAt = prescribed.maxOf { it.completedAt!! }
        return aggregate.exercises.any { item ->
            if (item.sets.none { !it.addedDuringWorkout }) return@any false
            item.sets.any { set ->
                set.addedDuringWorkout &&
                    set.completedAt != null &&
                    set.completedAt > prescribedFinishedAt &&
                    genuinelyPerformed(item.exercise.measurementType, set)
            }
        }
    }

    private fun earliest(
        eligible: List<WorkoutSessionAggregate>,
        id: AchievementId,
        matches: (LocalDate) -> Boolean
    ): SecretQualification? {
        return earliestAggregate(eligible, id) { matches(it.session.workoutDate) }
    }

    private fun earliestAggregate(
        eligible: List<WorkoutSessionAggregate>,
        id: AchievementId,
        matches: (WorkoutSessionAggregate) -> Boolean
    ): SecretQualification? {
        val match = eligible
            .filter(matches)
            .minWithOrNull(compareBy({ PerformanceRecordEvaluator.completedAt(it) }, { it.session.id }))
            ?: return null
        return SecretQualification(
            achievementId = id,
            unlockedAt = PerformanceRecordEvaluator.completedAt(match),
            clientWorkoutId = match.session.clientWorkoutId.takeIf { it.isNotBlank() }
        )
    }

    private fun tripleCrown(events: List<PerformanceRecordEvent>): SecretQualification? {
        val bySession = events.groupBy { it.sessionId }
        val match = events
            .map { it.sessionId }
            .distinct()
            .firstOrNull { sessionId ->
                val kinds = bySession.getValue(sessionId).map { it.kind }.toSet()
                PerformanceRecordKind.WEIGHT in kinds &&
                    PerformanceRecordKind.REPS in kinds &&
                    PerformanceRecordKind.VOLUME in kinds
            } ?: return null
        val event = bySession.getValue(match).first()
        return SecretQualification(
            achievementId = AchievementId.TRIPLE_CROWN,
            unlockedAt = event.unlockedAt,
            clientWorkoutId = event.clientWorkoutId
        )
    }
}
