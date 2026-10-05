package app.mymusclemap.data.founder

import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FounderWorkoutObservationsTest {
    @Test
    fun derivesCoarseFactsFromTheCompletedSession() {
        val observation = FounderWorkoutObservations.derive(
            templateName = "  Push day  ",
            templateId = 44L,
            startedAt = 1_000L,
            finishedAt = 91_500L,
            exerciseCount = 3,
            sets = listOf(
                set(SessionSetStatus.COMPLETED, PlannedLoadKind.BODYWEIGHT_ONLY),
                set(SessionSetStatus.SKIPPED, PlannedLoadKind.EXTERNAL_WEIGHT),
                set(SessionSetStatus.PENDING, PlannedLoadKind.ADDED_WEIGHT),
                set(SessionSetStatus.COMPLETED, PlannedLoadKind.ASSISTANCE),
                set(SessionSetStatus.COMPLETED, PlannedLoadKind.ADDED_WEIGHT)
            )
        )
        assertEquals("Push day", observation.displayName)
        assertEquals(90, observation.durationSeconds)
        assertEquals(3, observation.exerciseCount)
        assertEquals(3, observation.completedSetCount)
        assertEquals(true, observation.fromTemplate)
        assertEquals(true, observation.usedExternalLoad)
        assertFalse(observation.toString().contains("44"))
    }

    @Test
    fun assistanceAndSkippedExternalLoadDoNotCountAsExternalLoad() {
        val observation = FounderWorkoutObservations.derive(
            templateName = "Core",
            templateId = null,
            startedAt = 0L,
            finishedAt = 1_000L,
            exerciseCount = 1,
            sets = listOf(
                set(SessionSetStatus.COMPLETED, PlannedLoadKind.ASSISTANCE),
                set(SessionSetStatus.SKIPPED, PlannedLoadKind.EXTERNAL_WEIGHT)
            )
        )
        assertEquals(false, observation.fromTemplate)
        assertEquals(false, observation.usedExternalLoad)
        assertEquals(1, observation.completedSetCount)
        assertEquals(1, observation.durationSeconds)
    }

    @Test
    fun capsTheDisplayNameAndTreatsABlankNameAsAbsent() {
        val longName = "x".repeat(90)
        val capped = FounderWorkoutObservations.derive(
            templateName = longName,
            templateId = null,
            startedAt = 5_000L,
            finishedAt = 5_000L,
            exerciseCount = 0,
            sets = emptyList()
        )
        assertEquals(FounderWorkoutObservations.DISPLAY_NAME_MAX, capped.displayName?.length)
        assertEquals(0, capped.durationSeconds)
        assertEquals(0, capped.completedSetCount)

        val blank = FounderWorkoutObservations.derive(
            templateName = "   ",
            templateId = null,
            startedAt = 0L,
            finishedAt = 0L,
            exerciseCount = 2,
            sets = emptyList()
        )
        assertNull(blank.displayName)
        assertEquals(2, blank.exerciseCount)
    }

    private fun set(status: SessionSetStatus, kind: PlannedLoadKind): FounderSetObservation {
        return FounderSetObservation(status.name, kind.name)
    }
}
