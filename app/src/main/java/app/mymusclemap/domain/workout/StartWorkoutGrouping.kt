package app.mymusclemap.domain.workout

import app.mymusclemap.domain.entitlement.FreeAccessDecision

data class GroupedStartWorkouts(
    val todayPlanned: List<ScheduledWorkout>,
    val todayInProgress: List<ScheduledWorkout>,
    val available: List<TemplateListItem>,
    val locked: List<TemplateListItem>
)

object StartWorkoutGrouping {
    /**
     * Splits the already ordered active plans using [FreeAccessDecision.planAccessible].
     * Archived plans are omitted: they are not startable for a reason other than FREE access.
     * Order inside each section stays the order of [orderedTemplates].
     */
    fun group(
        orderedTemplates: List<TemplateListItem>,
        todaySchedules: List<ScheduledWorkout>,
        decision: FreeAccessDecision
    ): GroupedStartWorkouts {
        val active = orderedTemplates.filter { !it.template.archived }
        val assembled = QuickStartAssembler.assemble(active, todaySchedules)
        return GroupedStartWorkouts(
            todayPlanned = assembled.todayPlanned.filter { item ->
                val templateId = item.templateId ?: return@filter true
                decision.planAccessible(templateId)
            },
            todayInProgress = assembled.todayInProgress,
            available = assembled.remainingTemplates.filter { decision.planAccessible(it.template.id) },
            locked = active.filter { !decision.planAccessible(it.template.id) }
        )
    }
}
