package app.mymusclemap.domain.workout

import app.mymusclemap.domain.entitlement.FreeAccessLogic
import app.mymusclemap.domain.entitlement.FreeAccessPhase
import app.mymusclemap.domain.entitlement.FreeAccessState
import app.mymusclemap.domain.entitlement.FreeAccessSubject
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartWorkoutGroupingTest {
    @Test
    fun confirmedFreeKeepsSelectedPlansAvailableAndTheRestLockedInOrder() {
        val plans = listOf(plan(1, "A"), plan(2, "B"), plan(3, "C"), plan(4, "D"), plan(5, "E"))
        val decision = decide(
            phase = FreeAccessPhase.Confirmed,
            selected = setOf(1L, 3L, 5L),
            plans = plans
        )
        val grouped = StartWorkoutGrouping.group(plans, emptyList(), decision)
        assertEquals(listOf(1L, 3L, 5L), grouped.available.map { it.template.id })
        assertEquals(listOf(2L, 4L), grouped.locked.map { it.template.id })
        assertTrue(grouped.available.all { decision.planAccessible(it.template.id) })
        assertTrue(grouped.locked.none { decision.planAccessible(it.template.id) })
    }

    @Test
    fun requiredSelectionDoesNotGrantDraftAccess() {
        val plans = listOf(plan(1, "A"), plan(2, "B"), plan(3, "C"), plan(4, "D"))
        val decision = decide(
            phase = FreeAccessPhase.Required,
            selected = setOf(1L, 2L, 3L),
            plans = plans
        )
        val grouped = StartWorkoutGrouping.group(plans, emptyList(), decision)
        assertTrue(grouped.available.isEmpty())
        assertEquals(plans.map { it.template.id }, grouped.locked.map { it.template.id })
        assertTrue(plans.all { !decision.planAccessible(it.template.id) })
    }

    @Test
    fun proAndWithinLimitPlansHaveNoLockedSection() {
        val plans = listOf(plan(1, "A"), plan(2, "B"), plan(3, "C"))
        val within = decide(phase = FreeAccessPhase.NotRequired, selected = emptySet(), plans = plans)
        val pro = FreeAccessLogic.decide(
            state = FreeAccessState(2L, false, FreeAccessPhase.Confirmed, selectedPlanIds = setOf(1L)),
            grantsPro = true,
            plans = plans.map { FreeAccessSubject(it.template.id, it.template.id) },
            customExercises = emptyList()
        )
        assertTrue(StartWorkoutGrouping.group(plans, emptyList(), within).locked.isEmpty())
        assertEquals(plans.map { it.template.id }, StartWorkoutGrouping.group(plans, emptyList(), pro).available.map { it.template.id })
        assertTrue(StartWorkoutGrouping.group(plans, emptyList(), pro).locked.isEmpty())
    }

    @Test
    fun anArchivedPlanIsOmittedInsteadOfBeingLabeledLocked() {
        val active = plan(1, "Keep")
        val archived = plan(2, "Old").let { item ->
            item.copy(template = item.template.copy(archived = true))
        }
        val decision = decide(
            phase = FreeAccessPhase.Confirmed,
            selected = emptySet(),
            plans = listOf(active, archived)
        )
        val grouped = StartWorkoutGrouping.group(listOf(active, archived), emptyList(), decision)
        assertEquals(listOf(1L), grouped.locked.map { it.template.id })
        assertTrue(grouped.available.isEmpty())
        assertFalse(decision.planAccessible(1L))
    }

    @Test
    fun aLockedPlanScheduledTodayMovesOutOfTheStartableTodaySection() {
        val plans = listOf(plan(1, "Today"), plan(2, "Later"))
        val decision = decide(
            phase = FreeAccessPhase.Confirmed,
            selected = setOf(2L),
            plans = plans
        )
        val grouped = StartWorkoutGrouping.group(
            orderedTemplates = plans,
            todaySchedules = listOf(scheduled(10, templateId = 1, name = "Today")),
            decision = decision
        )
        assertTrue(grouped.todayPlanned.isEmpty())
        assertEquals(listOf(2L), grouped.available.map { it.template.id })
        assertEquals(listOf(1L), grouped.locked.map { it.template.id })
    }

    @Test
    fun anAccessiblePlanScheduledTodayStaysInTodayAndNotInThePlanList() {
        val plans = listOf(plan(1, "Today"), plan(2, "Later"))
        val decision = decide(phase = FreeAccessPhase.NotRequired, selected = emptySet(), plans = plans)
        val grouped = StartWorkoutGrouping.group(
            orderedTemplates = plans,
            todaySchedules = listOf(scheduled(10, templateId = 1, name = "Today")),
            decision = decision
        )
        assertEquals(listOf(1L), grouped.todayPlanned.map { it.templateId })
        assertEquals(listOf(2L), grouped.available.map { it.template.id })
        assertTrue(grouped.locked.isEmpty())
    }

    private fun decide(
        phase: FreeAccessPhase,
        selected: Set<Long>,
        plans: List<TemplateListItem>
    ) = FreeAccessLogic.decide(
        state = FreeAccessState(
            periodId = 2L,
            lastKnownPro = false,
            phase = phase,
            selectedPlanIds = selected
        ),
        grantsPro = false,
        plans = plans.map { FreeAccessSubject(it.template.id, it.template.id) },
        customExercises = emptyList()
    )

    private fun plan(id: Long, name: String): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = name.lowercase(),
                notes = null,
                archived = false,
                createdAt = id,
                updatedAt = id
            ),
            exerciseCount = 2,
            setCount = 6,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }

    private fun scheduled(id: Long, templateId: Long, name: String): ScheduledWorkout {
        return ScheduledWorkout(
            id = id,
            scheduledDate = LocalDate.of(2026, 9, 15),
            templateId = templateId,
            templateName = name,
            exerciseCount = 2,
            plannedSetCount = 6,
            templateArchived = false,
            sessionId = null,
            sessionStatus = null,
            createdAt = id
        )
    }
}
