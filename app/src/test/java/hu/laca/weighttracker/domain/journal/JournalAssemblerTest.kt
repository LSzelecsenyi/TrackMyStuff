package hu.laca.weighttracker.domain.journal

import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.SessionProgress
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalAssemblerTest {
    private val day = LocalDate.parse("2026-09-15")
    private val earlier = LocalDate.parse("2026-09-14")

    @Test
    fun combinedJournalContainsWeightAndCompletedWorkout() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, day, 88.3)),
            summaries = listOf(summary(10, day, SessionStatus.COMPLETED, startedAt = 2_000L)),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        assertEquals(1, timeline.groups.size)
        assertEquals(2, timeline.groups.single().entries.size)
        assertTrue(timeline.entries[0] is WeightJournalEntry)
        assertTrue(timeline.entries[1] is WorkoutJournalEntry)
        assertNull(timeline.emptyKind)
    }

    @Test
    fun groupsNewestDateFirst() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, earlier, 88.4), weight(2, day, 88.3)),
            summaries = emptyList(),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        assertEquals(listOf(day, earlier), timeline.groups.map { it.date })
    }

    @Test
    fun withinDayWeightComesBeforeWorkoutsNewestStartFirst() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(5, day, 88.3)),
            summaries = listOf(
                summary(1, day, SessionStatus.COMPLETED, startedAt = 1_000L),
                summary(2, day, SessionStatus.COMPLETED, startedAt = 9_000L)
            ),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        val entries = timeline.groups.single().entries
        assertTrue(entries[0] is WeightJournalEntry)
        assertEquals(2L, (entries[1] as WorkoutJournalEntry).summary.session.id)
        assertEquals(1L, (entries[2] as WorkoutJournalEntry).summary.session.id)
    }

    @Test
    fun allFilterKeepsWeightsAndCompletedWorkouts() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, day, 88.3)),
            summaries = listOf(summary(2, day, SessionStatus.COMPLETED)),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        assertEquals(2, timeline.entries.size)
    }

    @Test
    fun weightFilterHidesWorkouts() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, day, 88.3)),
            summaries = listOf(summary(2, day, SessionStatus.COMPLETED)),
            filter = JournalFilter.WEIGHT,
            includeAbandoned = false
        )
        assertEquals(1, timeline.entries.size)
        assertTrue(timeline.entries.single() is WeightJournalEntry)
    }

    @Test
    fun workoutFilterHidesWeights() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, day, 88.3)),
            summaries = listOf(summary(2, day, SessionStatus.COMPLETED)),
            filter = JournalFilter.WORKOUT,
            includeAbandoned = false
        )
        assertEquals(1, timeline.entries.size)
        assertTrue(timeline.entries.single() is WorkoutJournalEntry)
    }

    @Test
    fun abandonedWorkoutsAreHiddenByDefault() {
        val timeline = JournalAssembler.assemble(
            measurements = emptyList(),
            summaries = listOf(summary(3, day, SessionStatus.ABANDONED)),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        assertTrue(timeline.entries.isEmpty())
        assertEquals(JournalEmptyKind.NoEntries, timeline.emptyKind)
    }

    @Test
    fun abandonedWorkoutsAppearWhenEnabled() {
        val timeline = JournalAssembler.assemble(
            measurements = emptyList(),
            summaries = listOf(summary(3, day, SessionStatus.ABANDONED)),
            filter = JournalFilter.WORKOUT,
            includeAbandoned = true
        )
        assertEquals(1, timeline.entries.size)
        assertTrue((timeline.entries.single() as WorkoutJournalEntry).abandoned)
    }

    @Test
    fun inProgressSessionsAreNeverListed() {
        val timeline = JournalAssembler.assemble(
            measurements = emptyList(),
            summaries = listOf(summary(4, day, SessionStatus.IN_PROGRESS)),
            filter = JournalFilter.ALL,
            includeAbandoned = true
        )
        assertTrue(timeline.entries.none { it is WorkoutJournalEntry })
    }

    @Test
    fun filterEmptyStateWhenWeightsExistButWorkoutFilterIsEmpty() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, day, 88.3)),
            summaries = emptyList(),
            filter = JournalFilter.WORKOUT,
            includeAbandoned = false
        )
        assertEquals(JournalEmptyKind.FilterEmpty, timeline.emptyKind)
    }

    @Test
    fun weightDifferenceStaysChronologicalInNewestFirstList() {
        val timeline = JournalAssembler.assemble(
            measurements = listOf(weight(1, earlier, 88.0), weight(2, day, 88.4)),
            summaries = emptyList(),
            filter = JournalFilter.ALL,
            includeAbandoned = false
        )
        val newest = timeline.entries.filterIsInstance<WeightJournalEntry>().first()
        assertEquals(day, newest.date)
        assertEquals(0.4, newest.item.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun skippedSetsAreNotCountedAsCompleted() {
        val summary = summary(
            id = 8,
            date = day,
            status = SessionStatus.COMPLETED,
            progress = SessionProgress(completed = 2, skipped = 1, pending = 0, total = 3)
        )
        val timeline = JournalAssembler.assemble(emptyList(), listOf(summary), JournalFilter.WORKOUT, false)
        val item = timeline.entries.single() as WorkoutJournalEntry
        assertEquals(2, item.summary.progress.completed)
        assertEquals(1, item.summary.progress.skipped)
    }

    @Test
    fun extraSetsIncreaseTotal() {
        val summary = summary(
            id = 9,
            date = day,
            status = SessionStatus.COMPLETED,
            progress = SessionProgress(completed = 5, skipped = 0, pending = 0, total = 5)
        )
        assertEquals(5, summary.progress.total)
    }

    private fun weight(id: Long, date: LocalDate, kg: Double): WeightMeasurement {
        return WeightMeasurement(id, date, kg, 1L, 1L)
    }

    private fun summary(
        id: Long,
        date: LocalDate,
        status: SessionStatus,
        startedAt: Long = 1_000L,
        progress: SessionProgress = SessionProgress(3, 0, 0, 3)
    ): WorkoutSessionSummary {
        return WorkoutSessionSummary(
            session = WorkoutSession(
                id = id,
                templateId = 1L,
                templateName = "Push A",
                status = status,
                workoutDate = date,
                startedAt = startedAt,
                finishedAt = if (status == SessionStatus.COMPLETED) startedAt + 1_000L else null,
                abandonedAt = if (status == SessionStatus.ABANDONED) startedAt + 500L else null,
                notes = null,
                bodyWeightKg = 88.3,
                bodyWeightSource = BodyWeightSource.MEASURED_SAME_DAY,
                bodyWeightSourceDate = date,
                createdAt = startedAt,
                updatedAt = startedAt
            ),
            progress = progress,
            exerciseCount = 1,
            primaryMuscles = listOf(MuscleGroup.CHEST),
            durationMillis = 1_000L
        )
    }
}
