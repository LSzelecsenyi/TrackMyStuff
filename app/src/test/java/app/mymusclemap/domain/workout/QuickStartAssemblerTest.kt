package app.mymusclemap.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickStartAssemblerTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun givenTodaySchedulesWhenAssembledThenTheySitAboveRemainingTemplates() {
        val push = template(1, "Push A")
        val pull = template(2, "Pull A")
        val legs = template(3, "Láb")
        val content = QuickStartAssembler.assemble(
            templates = listOf(push, pull, legs),
            todaySchedules = listOf(
                scheduled(10, 1, "Push A", ScheduledWorkoutStatus.PLANNED),
                scheduled(11, 2, "Pull A", ScheduledWorkoutStatus.IN_PROGRESS, sessionId = 99L, sessionStatus = SessionStatus.IN_PROGRESS)
            )
        )
        assertTrue(content.hasTodaySection)
        assertEquals(listOf("Push A"), content.todayPlanned.map { it.templateName })
        assertEquals(listOf("Pull A"), content.todayInProgress.map { it.templateName })
        assertEquals(listOf("Láb"), content.remainingTemplates.map { it.template.name })
    }

    @Test
    fun givenCompletedOrArchivedTodayWhenAssembledThenTheyAreNotOffered() {
        val push = template(1, "Push A")
        val old = template(2, "Régi")
        val content = QuickStartAssembler.assemble(
            templates = listOf(push),
            todaySchedules = listOf(
                scheduled(10, 1, "Push A", ScheduledWorkoutStatus.COMPLETED, sessionId = 5L, sessionStatus = SessionStatus.COMPLETED),
                scheduled(11, 2, "Régi", ScheduledWorkoutStatus.PLANNED, archived = true)
            )
        )
        assertFalse(content.hasTodaySection)
        assertEquals(listOf("Push A"), content.remainingTemplates.map { it.template.name })
        assertTrue(old.template.id !in content.todayPlanned.map { it.templateId })
    }

    @Test
    fun givenNoTodaySchedulesWhenAssembledThenPreviousListIsUnchanged() {
        val templates = listOf(template(1, "Alma"), template(2, "Körte"))
        val content = QuickStartAssembler.assemble(templates, emptyList())
        assertFalse(content.hasTodaySection)
        assertEquals(templates, content.remainingTemplates)
        assertTrue(content.todayPlanned.isEmpty())
        assertTrue(content.todayInProgress.isEmpty())
    }

    private fun template(id: Long, name: String): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exerciseCount = 1,
            setCount = 2,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }

    private fun scheduled(
        id: Long,
        templateId: Long,
        name: String,
        status: ScheduledWorkoutStatus,
        archived: Boolean = false,
        sessionId: Long? = null,
        sessionStatus: SessionStatus? = null
    ): ScheduledWorkout {
        val derivedStatus = sessionStatus ?: when (status) {
            ScheduledWorkoutStatus.IN_PROGRESS -> SessionStatus.IN_PROGRESS
            ScheduledWorkoutStatus.COMPLETED -> SessionStatus.COMPLETED
            ScheduledWorkoutStatus.PLANNED -> null
        }
        return ScheduledWorkout(
            id = id,
            scheduledDate = today,
            templateId = templateId,
            templateName = name,
            exerciseCount = 1,
            plannedSetCount = 2,
            templateArchived = archived,
            sessionId = sessionId,
            sessionStatus = derivedStatus,
            createdAt = id
        )
    }
}
