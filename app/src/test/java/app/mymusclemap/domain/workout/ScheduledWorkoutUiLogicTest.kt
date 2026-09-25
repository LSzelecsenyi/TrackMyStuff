package app.mymusclemap.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduledWorkoutUiLogicTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun givenTodayPlannedWhenPresentedThenItCanStartAndBeMoved() {
        val actions = ScheduledWorkoutUiLogic.actions(item(today), today)
        assertTrue(actions.canStart)
        assertTrue(actions.canReschedule)
        assertTrue(actions.canUnschedule)
        assertFalse(actions.canContinue)
        assertEquals(ScheduledStatusLabel.PLANNED, actions.status)
    }

    @Test
    fun givenFuturePlannedWhenPresentedThenItCannotStart() {
        val actions = ScheduledWorkoutUiLogic.actions(item(today.plusDays(2)), today)
        assertFalse(actions.canStart)
        assertEquals(ScheduledStatusLabel.PLANNED, actions.status)
        assertTrue(actions.canReschedule)
    }

    @Test
    fun givenPastPlannedWhenPresentedThenItIsMissedAndCannotStart() {
        val actions = ScheduledWorkoutUiLogic.actions(item(today.minusDays(1)), today)
        assertFalse(actions.canStart)
        assertEquals(ScheduledStatusLabel.MISSED, actions.status)
        assertTrue(actions.canUnschedule)
    }

    @Test
    fun givenInProgressWhenPresentedThenItCanContinueButNotMove() {
        val actions = ScheduledWorkoutUiLogic.actions(
            item(today, sessionId = 9L, sessionStatus = SessionStatus.IN_PROGRESS),
            today
        )
        assertTrue(actions.canContinue)
        assertFalse(actions.canStart)
        assertFalse(actions.canReschedule)
        assertFalse(actions.canUnschedule)
        assertEquals(ScheduledStatusLabel.IN_PROGRESS, actions.status)
    }

    @Test
    fun givenCompletedWhenPresentedThenItOpensJournalAndCannotRestart() {
        val actions = ScheduledWorkoutUiLogic.actions(
            item(today, sessionId = 9L, sessionStatus = SessionStatus.COMPLETED),
            today
        )
        assertTrue(actions.canOpenJournal)
        assertFalse(actions.canStart)
        assertFalse(actions.canReschedule)
        assertEquals(ScheduledStatusLabel.COMPLETED, actions.status)
    }

    @Test
    fun givenArchivedPlannedWhenPresentedThenItCannotStartButCanBeRemoved() {
        val actions = ScheduledWorkoutUiLogic.actions(item(today, archived = true), today)
        assertFalse(actions.canStart)
        assertTrue(actions.canUnschedule)
        assertFalse(actions.canReschedule)
        assertEquals(ScheduledStatusLabel.ARCHIVED, actions.status)
    }

    @Test
    fun givenCompletedSchedulesWhenCountingMarkersThenOnlyOpenOnesRemain() {
        val counts = ScheduledWorkoutUiLogic.plannedMarkerCounts(
            listOf(
                item(today, id = 1),
                item(today, id = 2, sessionId = 8L, sessionStatus = SessionStatus.IN_PROGRESS),
                item(today, id = 3, sessionId = 9L, sessionStatus = SessionStatus.COMPLETED),
                item(today.plusDays(1), id = 4)
            )
        )
        assertEquals(2, counts[today])
        assertEquals(1, counts[today.plusDays(1)])
    }

    @Test
    fun givenScheduledTemplateWhenFilteringPickerThenItIsExcluded() {
        val push = template(1, "Push A")
        val pull = template(2, "Pull A")
        val available = ScheduledWorkoutUiLogic.availableTemplates(
            active = listOf(push, pull),
            scheduledThatDay = listOf(item(today, templateId = 1, name = "Push A"))
        )
        assertEquals(listOf("Pull A"), available.map { it.template.name })
    }

    @Test
    fun givenPastDateWhenSelectingRescheduleThenItIsRejected() {
        assertFalse(ScheduledWorkoutUiLogic.canSelectRescheduleDate(today.minusDays(1), today))
        assertTrue(ScheduledWorkoutUiLogic.canSelectRescheduleDate(today, today))
        assertTrue(ScheduledWorkoutUiLogic.canSelectRescheduleDate(today.plusDays(1), today))
    }

    private fun item(
        date: LocalDate,
        id: Long = 1L,
        templateId: Long = 10L,
        name: String = "Push A",
        archived: Boolean = false,
        sessionId: Long? = null,
        sessionStatus: SessionStatus? = null
    ): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = date,
            templateId = templateId,
            templateName = name,
            exerciseCount = 2,
            plannedSetCount = 6,
            templateArchived = archived,
            sessionId = sessionId,
            sessionStatus = sessionStatus,
            createdAt = id
        )
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
}
