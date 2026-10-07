package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InWorkoutExerciseHistoryLogicTest {
    @Test
    fun samePlanBeatsANewerWorkoutFromAnotherPlan() {
        val olderSame = session(
            id = 2L,
            templateId = 7L,
            name = "Push A",
            startedAt = 100L,
            sets = listOf(done(0, reps = 8, weight = 12.0))
        )
        val newerOther = session(
            id = 3L,
            templateId = 8L,
            name = "Other",
            startedAt = 500L,
            sets = listOf(done(0, reps = 15, weight = 40.0))
        )
        val selected = ExerciseHistoryLogic.select(request(templateId = 7L), listOf(newerOther, olderSame))
        assertEquals(2L, selected!!.sessionId)
        assertEquals(8, selected.sets.single().reps)
        assertFalse(selected.fromOtherPlan)
        assertEquals("Push A", selected.templateName)
    }

    @Test
    fun fallsBackToTheLatestOtherPlanWhenTheSamePlanHasNoHistory() {
        val older = session(id = 2L, templateId = 9L, name = "Legs", startedAt = 100L, sets = listOf(done(0, reps = 6)))
        val newer = session(id = 4L, templateId = null, name = "Imported", startedAt = 400L, sets = listOf(done(0, reps = 11, weight = 20.0)))
        val selected = ExerciseHistoryLogic.select(request(templateId = 7L), listOf(older, newer))
        assertEquals(4L, selected!!.sessionId)
        assertEquals(11, selected.sets.single().reps)
        assertTrue(selected.fromOtherPlan)
        assertEquals("Imported", selected.templateName)
    }

    @Test
    fun standaloneWorkoutUsesTheLatestEligibleSessionFromAnyPlan() {
        val planned = session(id = 2L, templateId = 3L, name = "Push", startedAt = 100L, sets = listOf(done(0, reps = 5)))
        val standalone = session(id = 3L, templateId = null, name = "Park", startedAt = 300L, sets = listOf(done(0, reps = 9)))
        val selected = ExerciseHistoryLogic.select(request(templateId = null), listOf(planned, standalone))
        assertEquals(3L, selected!!.sessionId)
        assertFalse(selected.fromOtherPlan)
    }

    @Test
    fun renamedPlanStillMatchesOnTemplateId() {
        val renamed = session(
            id = 2L,
            templateId = 7L,
            name = "Old Push",
            startedAt = 100L,
            sets = listOf(done(0, reps = 8))
        )
        val sameDisplayNameDifferentPlan = session(
            id = 3L,
            templateId = 99L,
            name = "Push A",
            startedAt = 400L,
            sets = listOf(done(0, reps = 20))
        )
        val selected = ExerciseHistoryLogic.select(request(templateId = 7L), listOf(renamed, sameDisplayNameDifferentPlan))
        assertEquals(2L, selected!!.sessionId)
        assertEquals("Old Push", selected.templateName)
        assertFalse(selected.fromOtherPlan)
    }

    @Test
    fun excludesActiveAbandonedUnfinishedAndLaterSessions() {
        val eligible = session(id = 2L, templateId = 7L, startedAt = 100L, sets = listOf(done(0, reps = 8)))
        val abandoned = session(
            id = 3L,
            templateId = 7L,
            startedAt = 800L,
            status = SessionStatus.ABANDONED,
            sets = listOf(done(0, reps = 30))
        )
        val unfinished = session(
            id = 4L,
            templateId = 7L,
            startedAt = 850L,
            status = SessionStatus.IN_PROGRESS,
            sets = listOf(done(0, reps = 40))
        )
        val current = session(id = 10L, templateId = 7L, startedAt = 50L, sets = listOf(done(0, reps = 1)))
        val laterSameTime = session(id = 11L, templateId = 7L, startedAt = 1_000L, sets = listOf(done(0, reps = 50)))
        val selected = ExerciseHistoryLogic.select(
            request(sessionId = 10L, startedAt = 1_000L, templateId = 7L),
            listOf(abandoned, unfinished, current, laterSameTime, eligible)
        )
        assertEquals(2L, selected!!.sessionId)
        assertEquals(8, selected.sets.single().reps)
    }

    @Test
    fun sameStartTimeUsesTheHigherEarlierIdAsTheTieBreaker() {
        val lower = session(id = 2L, templateId = 7L, startedAt = 500L, sets = listOf(done(0, reps = 4)))
        val higher = session(id = 6L, templateId = 7L, startedAt = 500L, sets = listOf(done(0, reps = 9)))
        val selected = ExerciseHistoryLogic.select(
            request(sessionId = 8L, startedAt = 500L),
            listOf(lower, higher)
        )
        assertEquals(6L, selected!!.sessionId)
    }

    @Test
    fun pendingDraftsAndSkippedSetsAreNotCompletedHistory() {
        val onlyDrafts = session(
            id = 2L,
            templateId = 7L,
            startedAt = 700L,
            sets = listOf(
                ExerciseHistorySet(
                    position = 0,
                    status = SessionSetStatus.PENDING,
                    reps = 99,
                    loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                    weightKg = 99.0
                )
            )
        )
        val recorded = session(
            id = 3L,
            templateId = 7L,
            startedAt = 200L,
            sets = listOf(
                done(0, reps = 10),
                ExerciseHistorySet(position = 1, status = SessionSetStatus.SKIPPED, reps = 3),
                done(2, reps = 7, weight = 12.0)
            )
        )
        val selected = ExerciseHistoryLogic.select(request(), listOf(onlyDrafts, recorded))
        assertEquals(3L, selected!!.sessionId)
        assertEquals(listOf(0, 2), selected.sets.map { it.position })
        assertEquals(listOf(10, 7), selected.sets.map { it.reps })
        assertNull(ExerciseHistoryLogic.matchingSet(selected, 1))
        assertEquals(7, ExerciseHistoryLogic.matchingSet(selected, 2)!!.reps)
    }

    @Test
    fun missingHistoricalSetStaysANoMatchAndDoesNotBorrowAnotherSet() {
        val selected = ExerciseHistoryLogic.select(
            request(),
            listOf(session(id = 2L, templateId = 7L, startedAt = 100L, sets = listOf(done(0, reps = 10), done(3, reps = 4))))
        )!!
        val history = InWorkoutExerciseHistory.Found(sessionExerciseId = 50L, selection = selected)
        val line = ExerciseHistoryLogic.currentLine(history, sessionExerciseId = 50L, setPosition = 1)
        assertTrue(line is CurrentSetHistoryLine.NoMatchingSet)
        assertEquals(listOf(0, 3), selected.sets.map { it.position })
    }

    @Test
    fun noEligibleHistoryIsAnEmptyLine() {
        assertNull(ExerciseHistoryLogic.select(request(), emptyList()))
        assertEquals(
            CurrentSetHistoryLine.NoPreviousData,
            ExerciseHistoryLogic.currentLine(InWorkoutExerciseHistory.None, 1L, 0)
        )
        assertEquals(
            CurrentSetHistoryLine.Hidden,
            ExerciseHistoryLogic.currentLine(InWorkoutExerciseHistory.Loading, 1L, 0)
        )
    }

    @Test
    fun repeatedOccurrencesStaySeparate() {
        val history = session(
            id = 2L,
            templateId = 7L,
            startedAt = 100L,
            occurrences = listOf(
                occurrence(id = 10L, position = 0, sets = listOf(done(0, reps = 5), done(1, reps = 5))),
                occurrence(id = 11L, position = 3, sets = listOf(done(0, reps = 12), done(1, reps = 9)))
            )
        )
        val first = ExerciseHistoryLogic.select(request(ordinal = 0), listOf(history))!!
        val second = ExerciseHistoryLogic.select(request(ordinal = 1), listOf(history))!!
        assertEquals(listOf(5, 5), first.sets.map { it.reps })
        assertEquals(listOf(12, 9), second.sets.map { it.reps })
        assertNull(ExerciseHistoryLogic.select(request(ordinal = 2), listOf(history)))
    }

    @Test
    fun occurrenceOrdinalFollowsPositionThenRowIdForTheSameCatalogExercise() {
        val exercises = listOf(
            exercise(id = 30L, exerciseId = 7L, position = 2),
            exercise(id = 10L, exerciseId = 7L, position = 0),
            exercise(id = 20L, exerciseId = 8L, position = 1)
        )
        assertEquals(0, ExerciseHistoryLogic.occurrenceOrdinal(exercises, 10L))
        assertEquals(1, ExerciseHistoryLogic.occurrenceOrdinal(exercises, 30L))
        assertEquals(0, ExerciseHistoryLogic.occurrenceOrdinal(exercises, 20L))
    }

    @Test
    fun incompatibleMeasurementIsSkippedForAnOlderCompatibleOccurrence() {
        val newerDuration = session(
            id = 4L,
            templateId = 7L,
            startedAt = 600L,
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.NONE,
            sets = listOf(
                ExerciseHistorySet(position = 0, status = SessionSetStatus.COMPLETED, durationSeconds = 90)
            )
        )
        val olderReps = session(
            id = 2L,
            templateId = 7L,
            startedAt = 100L,
            sets = listOf(done(0, reps = 8, weight = 12.0))
        )
        val selected = ExerciseHistoryLogic.select(request(), listOf(newerDuration, olderReps))
        assertEquals(2L, selected!!.sessionId)
        assertEquals(MeasurementType.REPETITIONS_AND_WEIGHT, selected.measurementType)
        assertEquals(WeightInterpretation.TOTAL, selected.weightInterpretation)
    }

    @Test
    fun weightInterpretationIsTakenFromTheHistoricalOccurrence() {
        val selected = ExerciseHistoryLogic.select(
            request(),
            listOf(
                session(
                    id = 2L,
                    templateId = 7L,
                    startedAt = 100L,
                    interpretation = WeightInterpretation.PER_SIDE,
                    sets = listOf(done(0, reps = 8, weight = 12.0))
                )
            )
        )
        assertEquals(WeightInterpretation.PER_SIDE, selected!!.weightInterpretation)
        assertEquals(12.0, selected.sets.single().weightKg!!, 0.0)
    }

    @Test
    fun staleFoundHistoryForAnotherExerciseIsHidden() {
        val selected = session(id = 2L, templateId = 7L, startedAt = 100L, sets = listOf(done(0, reps = 8))).let {
            ExerciseHistoryLogic.select(request(), listOf(it))!!
        }
        val history = InWorkoutExerciseHistory.Found(sessionExerciseId = 10L, selection = selected)
        assertTrue(ExerciseHistoryLogic.currentLine(history, 10L, 0) is CurrentSetHistoryLine.Recorded)
        assertEquals(
            CurrentSetHistoryLine.Hidden,
            ExerciseHistoryLogic.currentLine(history, sessionExerciseId = 11L, setPosition = 0)
        )
    }

    @Test
    fun gateDropsAResultAfterTheActiveExerciseChanges() {
        val gate = ExerciseHistoryGate()
        val first = gate.begin(10L)
        val second = gate.begin(11L)
        assertFalse(gate.accepts(first, 10L))
        assertFalse(gate.accepts(second, 10L))
        assertTrue(gate.accepts(second, 11L))
    }

    private fun request(
        sessionId: Long = 10L,
        startedAt: Long = 1_000L,
        templateId: Long? = 7L,
        ordinal: Int = 0,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT
    ): ExerciseHistoryRequest {
        return ExerciseHistoryRequest(
            currentSessionId = sessionId,
            currentStartedAt = startedAt,
            currentTemplateId = templateId,
            currentMeasurement = measurement,
            occurrenceOrdinal = ordinal
        )
    }

    private fun session(
        id: Long,
        templateId: Long?,
        startedAt: Long,
        name: String = "Plan $templateId",
        status: SessionStatus = SessionStatus.COMPLETED,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        sets: List<ExerciseHistorySet> = emptyList(),
        occurrences: List<ExerciseHistoryOccurrence>? = null
    ): ExerciseHistoryCandidate {
        return ExerciseHistoryCandidate(
            sessionId = id,
            templateId = templateId,
            templateName = name,
            workoutDate = LocalDate.parse("2026-09-01"),
            startedAt = startedAt,
            status = status,
            occurrences = occurrences ?: listOf(
                occurrence(
                    id = id * 100,
                    position = 0,
                    measurement = measurement,
                    resistance = resistance,
                    interpretation = interpretation,
                    sets = sets
                )
            )
        )
    }

    private fun occurrence(
        id: Long,
        position: Int,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        sets: List<ExerciseHistorySet>
    ): ExerciseHistoryOccurrence {
        return ExerciseHistoryOccurrence(
            sessionExerciseId = id,
            position = position,
            measurementType = measurement,
            resistanceBasis = resistance,
            weightInterpretation = interpretation,
            sets = sets
        )
    }

    private fun done(
        position: Int,
        reps: Int? = 8,
        weight: Double? = 12.0
    ): ExerciseHistorySet {
        return ExerciseHistorySet(
            position = position,
            status = SessionSetStatus.COMPLETED,
            reps = reps,
            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            weightKg = weight
        )
    }

    private fun exercise(id: Long, exerciseId: Long, position: Int): SessionExercise {
        return SessionExercise(
            id = id,
            sessionId = 1L,
            exerciseId = exerciseId,
            position = position,
            name = "Bench",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistanceBasis = ResistanceBasis.EXTERNAL,
            weightInterpretation = WeightInterpretation.TOTAL,
            primaryMuscle = MuscleGroup.CHEST,
            secondaryMuscles = emptyList(),
            notes = null
        )
    }
}
