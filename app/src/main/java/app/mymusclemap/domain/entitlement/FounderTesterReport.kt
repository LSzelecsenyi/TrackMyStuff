package app.mymusclemap.domain.entitlement

import java.time.LocalDate

/**
 * One native Strict workout that can qualify for the local Founder path.
 * Identity is the session id. Progress is derived from these rows, not from a counter.
 * The backend Tester Report does not upload this row.
 */
data class FounderWorkoutRecord(
    val id: Long,
    val day: LocalDate,
    val name: String
) {
    fun asCompletedWorkout(): CompletedWorkout {
        return CompletedWorkout(day = day, origin = WorkoutOrigin.NativeStrict)
    }
}
