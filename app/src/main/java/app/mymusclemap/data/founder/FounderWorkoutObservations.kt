package app.mymusclemap.data.founder

import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus

/**
 * Coarse facts copied from a completed session when it is queued.
 * [FounderWorkoutObservation.durationSeconds] is elapsed wall-clock time from start to finish.
 */
data class FounderWorkoutObservation(
    val displayName: String?,
    val durationSeconds: Int?,
    val exerciseCount: Int?,
    val completedSetCount: Int?,
    val fromTemplate: Boolean?,
    val usedExternalLoad: Boolean?
)

data class FounderSetObservation(
    val status: String,
    val loadKind: String?
)

object FounderWorkoutObservations {
    const val DISPLAY_NAME_MAX = 80

    fun derive(
        templateName: String,
        templateId: Long?,
        startedAt: Long,
        finishedAt: Long,
        exerciseCount: Int,
        sets: List<FounderSetObservation>
    ): FounderWorkoutObservation {
        val completed = sets.filter { it.status == SessionSetStatus.COMPLETED.name }
        val name = templateName.trim().take(DISPLAY_NAME_MAX).ifBlank { null }
        val elapsedMillis = (finishedAt - startedAt).coerceAtLeast(0L)
        val seconds = (elapsedMillis / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return FounderWorkoutObservation(
            displayName = name,
            durationSeconds = seconds,
            exerciseCount = exerciseCount.coerceAtLeast(0),
            completedSetCount = completed.size,
            fromTemplate = templateId != null,
            usedExternalLoad = completed.any { set ->
                set.loadKind == PlannedLoadKind.EXTERNAL_WEIGHT.name ||
                    set.loadKind == PlannedLoadKind.ADDED_WEIGHT.name
            }
        )
    }
}
