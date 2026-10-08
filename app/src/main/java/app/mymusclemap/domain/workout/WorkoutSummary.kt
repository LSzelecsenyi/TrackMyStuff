package app.mymusclemap.domain.workout

import app.mymusclemap.domain.achievements.PerformanceHighlight
import app.mymusclemap.domain.achievements.PerformanceRecordEvaluator
import app.mymusclemap.domain.exercise.WeightInterpretation

data class WorkoutSummaryExercise(
    val name: String,
    val completedSets: List<SessionSet>,
    val interpretation: WeightInterpretation
)

data class WorkoutSummary(
    val workoutName: String?,
    val durationMillis: Long,
    val exercises: List<WorkoutSummaryExercise>,
    val volumeKg: Double?,
    val highlights: List<PerformanceHighlight>
) {
    val exerciseCount: Int get() = exercises.size
    val completedSetCount: Int get() = exercises.sumOf { it.completedSets.size }
    val durationLabel: String get() = ElapsedTime.formatMillis(durationMillis)
}

object WorkoutSummaryLogic {
    /**
     * Presentation of one already-completed session.
     * Skipped and pending sets are omitted. Exercise and set order follow stored position, then id.
     * Volume is null when the canonical volume rules have nothing to add.
     */
    fun from(
        aggregate: WorkoutSessionAggregate,
        highlights: List<PerformanceHighlight> = emptyList()
    ): WorkoutSummary? {
        if (aggregate.session.status != SessionStatus.COMPLETED) return null
        val exercises = aggregate.exercises
            .sortedWith(compareBy({ it.exercise.position }, { it.exercise.id }))
            .mapNotNull { item ->
                val completed = item.sets
                    .filter { it.status == SessionSetStatus.COMPLETED }
                    .sortedWith(compareBy({ it.position }, { it.id }))
                if (completed.isEmpty()) {
                    null
                } else {
                    WorkoutSummaryExercise(
                        name = item.exercise.name,
                        completedSets = completed,
                        interpretation = item.exercise.weightInterpretation
                    )
                }
            }
        return WorkoutSummary(
            workoutName = aggregate.session.templateName.trim().takeIf { it.isNotEmpty() },
            durationMillis = ElapsedTime.forSession(aggregate.session),
            exercises = exercises,
            volumeKg = PerformanceRecordEvaluator.workoutVolumeKg(aggregate),
            highlights = highlights
        )
    }
}
