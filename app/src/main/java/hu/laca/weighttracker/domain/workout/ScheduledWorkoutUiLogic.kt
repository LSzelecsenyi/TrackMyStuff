package hu.laca.weighttracker.domain.workout

import hu.laca.weighttracker.domain.locale.LocalizedLabelOrder
import java.time.LocalDate

enum class ScheduledStatusLabel {
    PLANNED,
    MISSED,
    IN_PROGRESS,
    COMPLETED,
    ARCHIVED
}

data class ScheduledWorkoutActions(
    val status: ScheduledStatusLabel,
    val canStart: Boolean,
    val canContinue: Boolean,
    val canOpenJournal: Boolean,
    val canReschedule: Boolean,
    val canUnschedule: Boolean
)

object ScheduledWorkoutUiLogic {
    fun actions(item: ScheduledWorkout, today: LocalDate): ScheduledWorkoutActions {
        return when (item.status) {
            ScheduledWorkoutStatus.IN_PROGRESS -> ScheduledWorkoutActions(
                status = ScheduledStatusLabel.IN_PROGRESS,
                canStart = false,
                canContinue = true,
                canOpenJournal = false,
                canReschedule = false,
                canUnschedule = false
            )
            ScheduledWorkoutStatus.COMPLETED -> ScheduledWorkoutActions(
                status = ScheduledStatusLabel.COMPLETED,
                canStart = false,
                canContinue = false,
                canOpenJournal = item.sessionId != null,
                canReschedule = false,
                canUnschedule = false
            )
            ScheduledWorkoutStatus.PLANNED -> {
                if (item.templateArchived) {
                    ScheduledWorkoutActions(
                        status = ScheduledStatusLabel.ARCHIVED,
                        canStart = false,
                        canContinue = false,
                        canOpenJournal = false,
                        canReschedule = false,
                        canUnschedule = true
                    )
                } else {
                    when {
                        item.scheduledDate == today -> ScheduledWorkoutActions(
                            status = ScheduledStatusLabel.PLANNED,
                            canStart = true,
                            canContinue = false,
                            canOpenJournal = false,
                            canReschedule = true,
                            canUnschedule = true
                        )
                        item.scheduledDate.isAfter(today) -> ScheduledWorkoutActions(
                            status = ScheduledStatusLabel.PLANNED,
                            canStart = false,
                            canContinue = false,
                            canOpenJournal = false,
                            canReschedule = true,
                            canUnschedule = true
                        )
                        else -> ScheduledWorkoutActions(
                            status = ScheduledStatusLabel.MISSED,
                            canStart = false,
                            canContinue = false,
                            canOpenJournal = false,
                            canReschedule = true,
                            canUnschedule = true
                        )
                    }
                }
            }
        }
    }

    fun plannedMarkerCounts(items: List<ScheduledWorkout>): Map<LocalDate, Int> {
        return items
            .filter { it.status != ScheduledWorkoutStatus.COMPLETED }
            .groupingBy { it.scheduledDate }
            .eachCount()
    }

    fun availableTemplates(
        active: List<TemplateListItem>,
        scheduledThatDay: List<ScheduledWorkout>
    ): List<TemplateListItem> {
        val takenIds = scheduledThatDay.map { it.templateId }.toSet()
        return LocalizedLabelOrder.sorted(
            items = active.filter { !it.template.archived && it.template.id !in takenIds },
            label = { it.template.name },
            key = { it.template.id.toString() }
        )
    }

    fun canSelectRescheduleDate(date: LocalDate, today: LocalDate): Boolean {
        return !date.isBefore(today)
    }
}
