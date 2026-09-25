package app.mymusclemap.domain.musclemap

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.workout.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MuscleHeatmapAssemblerTest {
    private val today = LocalDate.parse("2026-09-15")

    @Test
    fun completedSessionContributes() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today, MuscleGroup.CHEST, completedSets = 1)),
            today
        )
        assertEquals(0, state.entry(MuscleGroup.CHEST).daysAgo)
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.CHEST).band)
        assertTrue(state.hasCompletedWorkouts)
    }

    @Test
    fun abandonedSessionDoesNotContribute() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.ABANDONED, today, MuscleGroup.CHEST, completedSets = 3)),
            today
        )
        assertNever(state, MuscleGroup.CHEST)
        assertFalse(state.hasCompletedWorkouts)
    }

    @Test
    fun inProgressSessionDoesNotContribute() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.IN_PROGRESS, today, MuscleGroup.CHEST, completedSets = 3)),
            today
        )
        assertNever(state, MuscleGroup.CHEST)
        assertFalse(state.hasCompletedWorkouts)
    }

    @Test
    fun exerciseWithZeroCompletedSetsDoesNotContribute() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today, MuscleGroup.BICEPS, completedSets = 0)),
            today
        )
        assertNever(state, MuscleGroup.BICEPS)
        assertTrue(state.hasCompletedWorkouts)
    }

    @Test
    fun exerciseWithAtLeastOneCompletedSetContributes() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today.minusDays(2), MuscleGroup.BICEPS, completedSets = 1)),
            today
        )
        assertEquals(2, state.entry(MuscleGroup.BICEPS).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, state.entry(MuscleGroup.BICEPS).band)
    }

    @Test
    fun skippedSetsDoNotCount() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today, MuscleGroup.TRICEPS, completedSets = 0)),
            today
        )
        assertNever(state, MuscleGroup.TRICEPS)
    }

    @Test
    fun latestCompletedDateWins() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(SessionStatus.COMPLETED, today.minusDays(10), MuscleGroup.LATS, completedSets = 2),
                exercise(SessionStatus.COMPLETED, today.minusDays(1), MuscleGroup.LATS, completedSets = 1)
            ),
            today
        )
        assertEquals(today.minusDays(1), state.entry(MuscleGroup.LATS).lastTrained)
        assertEquals(1, state.entry(MuscleGroup.LATS).daysAgo)
    }

    @Test
    fun primaryMusclesAreIncluded() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today.minusDays(3), MuscleGroup.QUADRICEPS, completedSets = 1)),
            today
        )
        assertEquals(MuscleRecencyBand.DAYS_3_4, state.entry(MuscleGroup.QUADRICEPS).band)
    }

    @Test
    fun secondaryMusclesAreIncluded() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(
                    status = SessionStatus.COMPLETED,
                    date = today.minusDays(8),
                    primary = MuscleGroup.LATS,
                    secondary = listOf(MuscleGroup.BICEPS),
                    completedSets = 2
                )
            ),
            today
        )
        assertEquals(today.minusDays(8), state.entry(MuscleGroup.LATS).lastTrained)
        assertEquals(today.minusDays(8), state.entry(MuscleGroup.BICEPS).lastTrained)
        assertEquals(MuscleRecencyBand.DAYS_7_13, state.entry(MuscleGroup.BICEPS).band)
    }

    @Test
    fun neckIsAnAnatomicalGroupAndAcceptsPrimaryOrSecondaryCompletedSets() {
        assertTrue(MuscleGroup.NECK in MuscleHeatmapAssembler.anatomicalGroups)
        val asPrimary = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today.minusDays(2), MuscleGroup.NECK, completedSets = 1)),
            today
        )
        val asSecondary = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(
                    status = SessionStatus.COMPLETED,
                    date = today.minusDays(2),
                    primary = MuscleGroup.UPPER_BACK,
                    secondary = listOf(MuscleGroup.NECK),
                    completedSets = 1
                )
            ),
            today
        )
        assertEquals(2, asPrimary.entry(MuscleGroup.NECK).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, asPrimary.entry(MuscleGroup.NECK).band)
        assertEquals(2, asSecondary.entry(MuscleGroup.NECK).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_1_2, asSecondary.entry(MuscleGroup.NECK).band)
        assertEquals(MuscleRecencyBand.NEVER, asPrimary.entry(MuscleGroup.UPPER_BACK).band)
    }

    @Test
    fun duplicatedMuscleGroupsCollapseToLatestDate() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(SessionStatus.COMPLETED, today.minusDays(4), MuscleGroup.CHEST, completedSets = 1),
                exercise(
                    status = SessionStatus.COMPLETED,
                    date = today.minusDays(4),
                    primary = MuscleGroup.FRONT_DELTOID,
                    secondary = listOf(MuscleGroup.CHEST),
                    completedSets = 1
                )
            ),
            today
        )
        assertEquals(today.minusDays(4), state.entry(MuscleGroup.CHEST).lastTrained)
        assertEquals(1, state.entries.values.count { it.group == MuscleGroup.CHEST })
    }

    @Test
    fun todayGivesZeroDays() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today, MuscleGroup.ABS, completedSets = 1)),
            today
        )
        assertEquals(0, state.entry(MuscleGroup.ABS).daysAgo)
        assertEquals(MuscleRecencyBand.TODAY, MuscleHeatmapAssembler.bandFor(0))
    }

    @Test
    fun dateProviderDeterminismDoesNotUseSystemDate() {
        val otherToday = LocalDate.parse("2026-01-01")
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, otherToday.minusDays(14), MuscleGroup.CALVES, completedSets = 1)),
            otherToday
        )
        assertEquals(14, state.entry(MuscleGroup.CALVES).daysAgo)
        assertEquals(MuscleRecencyBand.DAYS_14_PLUS, state.entry(MuscleGroup.CALVES).band)
    }

    @Test
    fun neverTrainedState() {
        val state = MuscleHeatmapAssembler.assemble(emptyList(), today)
        MuscleHeatmapAssembler.anatomicalGroups.forEach { group ->
            assertNever(state, group)
        }
        assertFalse(state.hasCompletedWorkouts)
        assertNull(state.fullBody)
        assertNull(state.cardiovascular)
    }

    @Test
    fun recencyBandBoundaries() {
        assertEquals(MuscleRecencyBand.TODAY, MuscleHeatmapAssembler.bandFor(0))
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(1))
        assertEquals(MuscleRecencyBand.DAYS_1_2, MuscleHeatmapAssembler.bandFor(2))
        assertEquals(MuscleRecencyBand.DAYS_3_4, MuscleHeatmapAssembler.bandFor(3))
        assertEquals(MuscleRecencyBand.DAYS_3_4, MuscleHeatmapAssembler.bandFor(4))
        assertEquals(MuscleRecencyBand.DAYS_5_6, MuscleHeatmapAssembler.bandFor(5))
        assertEquals(MuscleRecencyBand.DAYS_5_6, MuscleHeatmapAssembler.bandFor(6))
        assertEquals(MuscleRecencyBand.DAYS_7_13, MuscleHeatmapAssembler.bandFor(7))
        assertEquals(MuscleRecencyBand.DAYS_7_13, MuscleHeatmapAssembler.bandFor(13))
        assertEquals(MuscleRecencyBand.DAYS_14_PLUS, MuscleHeatmapAssembler.bandFor(14))
        assertEquals(MuscleRecencyBand.DAYS_14_PLUS, MuscleHeatmapAssembler.bandFor(40))
        assertEquals(MuscleRecencyBand.NEVER, MuscleHeatmapAssembler.bandFor(null))
    }

    @Test
    fun fourteenDayBoundaryIsFourteenPlus() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today.minusDays(14), MuscleGroup.GLUTES, completedSets = 1)),
            today
        )
        assertEquals(MuscleRecencyBand.DAYS_14_PLUS, state.entry(MuscleGroup.GLUTES).band)
    }

    @Test
    fun eachDeltoidGroupHasIndependentState() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(SessionStatus.COMPLETED, today, MuscleGroup.FRONT_DELTOID, completedSets = 1),
                exercise(SessionStatus.COMPLETED, today.minusDays(5), MuscleGroup.SIDE_DELTOID, completedSets = 1),
                exercise(SessionStatus.COMPLETED, today.minusDays(10), MuscleGroup.REAR_DELTOID, completedSets = 1)
            ),
            today
        )
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.FRONT_DELTOID).band)
        assertEquals(MuscleRecencyBand.DAYS_5_6, state.entry(MuscleGroup.SIDE_DELTOID).band)
        assertEquals(MuscleRecencyBand.DAYS_7_13, state.entry(MuscleGroup.REAR_DELTOID).band)
    }

    @Test
    fun trainingOneDeltoidDoesNotActivateTheOthers() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(exercise(SessionStatus.COMPLETED, today, MuscleGroup.FRONT_DELTOID, completedSets = 1)),
            today
        )
        assertEquals(MuscleRecencyBand.TODAY, state.entry(MuscleGroup.FRONT_DELTOID).band)
        assertNever(state, MuscleGroup.SIDE_DELTOID)
        assertNever(state, MuscleGroup.REAR_DELTOID)
    }

    @Test
    fun fullBodyAndCardiovascularAreNotPainted() {
        val state = MuscleHeatmapAssembler.assemble(
            listOf(
                exercise(SessionStatus.COMPLETED, today.minusDays(1), MuscleGroup.FULL_BODY, completedSets = 1),
                exercise(
                    status = SessionStatus.COMPLETED,
                    date = today.minusDays(2),
                    primary = MuscleGroup.CARDIOVASCULAR,
                    completedSets = 1
                )
            ),
            today
        )
        MuscleHeatmapAssembler.anatomicalGroups.forEach { group ->
            assertNever(state, group)
        }
        assertEquals(today.minusDays(1), state.fullBody?.lastTrained)
        assertEquals(today.minusDays(2), state.cardiovascular?.lastTrained)
    }

    private fun assertNever(state: MuscleHeatmapState, group: MuscleGroup) {
        val entry = state.entry(group)
        assertNull(entry.lastTrained)
        assertNull(entry.daysAgo)
        assertEquals(MuscleRecencyBand.NEVER, entry.band)
    }

    private fun exercise(
        status: SessionStatus,
        date: LocalDate,
        primary: MuscleGroup,
        secondary: List<MuscleGroup> = emptyList(),
        completedSets: Int
    ): MuscleTrainingExercise {
        return MuscleTrainingExercise(
            status = status,
            workoutDate = date,
            primaryMuscle = primary,
            secondaryMuscles = secondary,
            completedSetCount = completedSets
        )
    }
}
