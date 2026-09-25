package app.mymusclemap.domain.workout

data class QuickStartContent(
    val todayPlanned: List<ScheduledWorkout>,
    val todayInProgress: List<ScheduledWorkout>,
    val remainingTemplates: List<TemplateListItem>
) {
    val hasTodaySection: Boolean get() = todayPlanned.isNotEmpty() || todayInProgress.isNotEmpty()
}

object QuickStartAssembler {
    fun assemble(
        templates: List<TemplateListItem>,
        todaySchedules: List<ScheduledWorkout>
    ): QuickStartContent {
        val todayPlanned = todaySchedules.filter { item ->
            item.status == ScheduledWorkoutStatus.PLANNED && !item.templateArchived
        }
        val todayInProgress = todaySchedules.filter { item ->
            item.status == ScheduledWorkoutStatus.IN_PROGRESS
        }
        val hiddenTemplateIds = (todayPlanned + todayInProgress).map { it.templateId }.toSet()
        return QuickStartContent(
            todayPlanned = todayPlanned,
            todayInProgress = todayInProgress,
            remainingTemplates = templates.filter { it.template.id !in hiddenTemplateIds }
        )
    }
}
