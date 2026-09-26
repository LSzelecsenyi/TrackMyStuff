package app.mymusclemap.domain.workout

import java.time.LocalDate

enum class ScheduledWorkoutStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED
}

data class ScheduledWorkout(
    val id: Long,
    val scheduledDate: LocalDate,
    val templateId: Long?,
    val templateName: String,
    val exerciseCount: Int,
    val plannedSetCount: Int,
    val templateArchived: Boolean,
    val sessionId: Long?,
    val sessionStatus: SessionStatus?,
    val createdAt: Long,
    val originalScheduledDate: LocalDate = scheduledDate,
    val cancelledAt: Long? = null
) {
    val status: ScheduledWorkoutStatus
        get() = statusFromSession(sessionStatus)

    val isCancelled: Boolean get() = cancelledAt != null
}

fun statusFromSession(sessionStatus: SessionStatus?): ScheduledWorkoutStatus {
    return when (sessionStatus) {
        SessionStatus.IN_PROGRESS -> ScheduledWorkoutStatus.IN_PROGRESS
        SessionStatus.COMPLETED -> ScheduledWorkoutStatus.COMPLETED
        SessionStatus.ABANDONED, null -> ScheduledWorkoutStatus.PLANNED
    }
}

sealed class ScheduleWorkoutResult {
    data class Scheduled(val id: Long) : ScheduleWorkoutResult()
    data object TemplateNotFound : ScheduleWorkoutResult()
    data object TemplateArchived : ScheduleWorkoutResult()
    data object Duplicate : ScheduleWorkoutResult()
}

sealed class RescheduleWorkoutResult {
    data object Moved : RescheduleWorkoutResult()
    data object NotFound : RescheduleWorkoutResult()
    data object Duplicate : RescheduleWorkoutResult()
    data object LinkedToSession : RescheduleWorkoutResult()
}

sealed class UnscheduleWorkoutResult {
    data object Removed : UnscheduleWorkoutResult()
    data object NotFound : UnscheduleWorkoutResult()
    data object LinkedToSession : UnscheduleWorkoutResult()
}
