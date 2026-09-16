package hu.laca.weighttracker.domain.workout

import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.model.WeightMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SessionLogicTest {
    @Test
    fun sameDayWeightSnapshotIsPreferred() {
        val today = LocalDate.parse("2026-09-15")
        val same = measurement(today, 88.3)
        val previous = measurement(today.minusDays(1), 88.4)
        val proposal = BodyWeightSnapshotLogic.propose(today, same, previous)
        assertEquals(88.3, proposal.kilograms!!, 0.0)
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, proposal.source)
        assertEquals(today, proposal.sourceDate)
    }

    @Test
    fun nearestPreviousWeightSnapshotIsUsed() {
        val today = LocalDate.parse("2026-09-15")
        val previous = measurement(LocalDate.parse("2026-09-14"), 88.4)
        val proposal = BodyWeightSnapshotLogic.propose(today, null, previous)
        assertEquals(88.4, proposal.kilograms!!, 0.0)
        assertEquals(BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT, proposal.source)
        assertEquals(previous.date, proposal.sourceDate)
    }

    @Test
    fun missingWeightProducesUnknownSnapshot() {
        val proposal = BodyWeightSnapshotLogic.propose(LocalDate.parse("2026-09-15"), null, null)
        assertNull(proposal.kilograms)
        assertEquals(BodyWeightSource.UNKNOWN, proposal.source)
    }

    @Test
    fun manualEditChangesSourceToManual() {
        val proposal = BodyWeightSnapshotLogic.afterManualEdit(87.9)
        assertEquals(BodyWeightSource.MANUAL, proposal.source)
        assertEquals(87.9, proposal.kilograms!!, 0.0)
        assertNull(proposal.sourceDate)
    }

    @Test
    fun skippedSetsAreNotCompleted() {
        val progress = SessionProgressLogic.from(
            listOf(
                set(SessionSetStatus.COMPLETED),
                set(SessionSetStatus.SKIPPED),
                set(SessionSetStatus.PENDING)
            )
        )
        assertEquals(1, progress.completed)
        assertEquals(1, progress.skipped)
        assertEquals(1, progress.pending)
        assertEquals(3, progress.total)
        assertEquals(2, progress.resolved)
    }

    @Test
    fun extraSetIncreasesTotal() {
        val before = SessionProgressLogic.from(listOf(set(SessionSetStatus.PENDING), set(SessionSetStatus.PENDING)))
        val after = SessionProgressLogic.from(
            listOf(
                set(SessionSetStatus.PENDING),
                set(SessionSetStatus.PENDING),
                set(SessionSetStatus.PENDING, extra = true)
            )
        )
        assertEquals(2, before.total)
        assertEquals(3, after.total)
    }

    @Test
    fun elapsedTimeNeverGoesNegative() {
        assertEquals("0:00", ElapsedTime.format(startedAt = 5_000L, now = 1_000L))
        assertEquals("0:42", ElapsedTime.format(startedAt = 0L, now = 42_000L))
        assertEquals("1:02:05", ElapsedTime.format(startedAt = 0L, now = 3_725_000L))
        assertEquals("0:00", ElapsedTime.formatMillis(-5_000L))
    }

    @Test
    fun completedDurationUsesFinishedMinusStarted() {
        val session = session(SessionStatus.COMPLETED, startedAt = 1_000L, finishedAt = 2_538_000L)
        assertEquals(2_537_000L, ElapsedTime.forSession(session))
        assertEquals("42:17", ElapsedTime.formatSession(session))
    }

    @Test
    fun abandonedDurationUsesAbandonedTimestamp() {
        val session = session(
            status = SessionStatus.ABANDONED,
            startedAt = 1_000L,
            abandonedAt = 4_326_000L
        )
        assertEquals("1:12:05", ElapsedTime.formatSession(session))
    }

    @Test
    fun malformedSessionDurationDoesNotGoNegative() {
        val completed = session(SessionStatus.COMPLETED, startedAt = 9_000L, finishedAt = 1_000L)
        val abandoned = session(SessionStatus.ABANDONED, startedAt = 9_000L, abandonedAt = 1_000L)
        assertEquals(0L, ElapsedTime.forSession(completed))
        assertEquals("0:00", ElapsedTime.formatSession(abandoned))
    }

    @Test
    fun plannedRangeProducesSingleActualRepetition() {
        val set = set(SessionSetStatus.PENDING).copy(plannedMinReps = 8, plannedMaxReps = 10, actualReps = 8)
        val draft = ActualSetLogic.draftFromSet(set)
        assertEquals("8", draft.repsText)
        val parsed = ActualSetLogic.parse(draft.copy(repsText = "10"), MeasurementType.REPETITIONS, ResistanceBasis.BODYWEIGHT)
        assertEquals(10, parsed.first!!.reps)
    }

    @Test
    fun bodyweightPlanCanBecomeAddedWeight() {
        val draft = ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "10")
        val values = ActualSetLogic.parse(draft, MeasurementType.REPETITIONS, ResistanceBasis.BODYWEIGHT).first!!
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, values.loadKind)
        assertEquals(10.0, values.weightKg!!, 0.0)
    }

    @Test
    fun addedWeightCanBecomeAssistance() {
        val draft = ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.ASSISTANCE, weightText = "5")
        val values = ActualSetLogic.parse(draft, MeasurementType.REPETITIONS, ResistanceBasis.BODYWEIGHT).first!!
        assertEquals(PlannedLoadKind.ASSISTANCE, values.loadKind)
        assertEquals(5.0, values.weightKg!!, 0.0)
    }

    @Test
    fun durationAndDistanceAreCanonical() {
        val duration = ActualSetLogic.parse(
            ActualSetDraft(loadKind = PlannedLoadKind.NONE, minutesText = "1", secondsText = "5"),
            MeasurementType.DURATION,
            ResistanceBasis.NONE
        ).first!!
        assertEquals(65, duration.durationSeconds)
        val distance = ActualSetLogic.parse(
            ActualSetDraft(
                loadKind = PlannedLoadKind.NONE,
                minutesText = "30",
                secondsText = "0",
                distanceText = "5,2",
                distanceUnit = DistanceUnit.KILOMETERS
            ),
            MeasurementType.DISTANCE_AND_DURATION,
            ResistanceBasis.NONE
        ).first!!
        assertEquals(5200.0, distance.distanceMeters!!, 0.0)
        assertEquals(1800, distance.durationSeconds)
    }

    @Test
    fun completionOnlyNeedsNoNumericValues() {
        val parsed = ActualSetLogic.parse(
            ActualSetDraft(loadKind = PlannedLoadKind.NONE),
            MeasurementType.COMPLETION_ONLY,
            ResistanceBasis.NONE
        )
        assertTrue(parsed.second.isEmpty())
        assertNull(parsed.first!!.reps)
    }

    @Test
    fun originalSetCannotBeRemoved() {
        assertFalse(ActualSetLogic.canRemove(set(SessionSetStatus.PENDING, extra = false)))
        assertTrue(ActualSetLogic.canRemove(set(SessionSetStatus.PENDING, extra = true)))
        assertFalse(ActualSetLogic.canRemove(set(SessionSetStatus.COMPLETED, extra = true)))
    }

    @Test
    fun currentPendingExerciseIgnoresCompletedAndSkipped() {
        val first = item(1L, "A", listOf(set(1L, SessionSetStatus.COMPLETED), set(2L, SessionSetStatus.SKIPPED)))
        val second = item(2L, "B", listOf(set(3L, SessionSetStatus.PENDING)))
        val aggregate = WorkoutSessionAggregate(session(), listOf(first, second))
        assertEquals(2L, SessionFocusLogic.currentPendingExercise(aggregate)!!.exercise.id)
        assertEquals(2L, SessionProgressLogic.currentExercise(aggregate)!!.exercise.id)
    }

    @Test
    fun completedWorkoutHasNoPendingExercise() {
        val only = item(1L, "A", listOf(set(1L, SessionSetStatus.COMPLETED), set(2L, SessionSetStatus.SKIPPED)))
        val aggregate = WorkoutSessionAggregate(session(), listOf(only))
        assertNull(SessionFocusLogic.currentPendingExercise(aggregate))
    }

    @Test
    fun focusStaysOnSameExerciseWhenAnotherSetIsPending() {
        val first = item(
            1L,
            "A",
            listOf(set(1L, SessionSetStatus.COMPLETED), set(2L, SessionSetStatus.PENDING))
        )
        val second = item(2L, "B", listOf(set(3L, SessionSetStatus.PENDING)))
        val target = SessionFocusLogic.focusAfterResolving(listOf(first, second), resolvedSetId = 1L)
        assertEquals(WorkoutFocusTarget.Set(2L, 1L), target)
    }

    @Test
    fun focusMovesToNextPendingExerciseAfterLastSet() {
        val first = item(1L, "A", listOf(set(1L, SessionSetStatus.COMPLETED), set(2L, SessionSetStatus.COMPLETED)))
        val skipped = item(2L, "B", listOf(set(3L, SessionSetStatus.SKIPPED)))
        val third = item(3L, "C", listOf(set(4L, SessionSetStatus.PENDING)))
        val target = SessionFocusLogic.focusAfterResolving(listOf(first, skipped, third), resolvedSetId = 2L)
        assertEquals(WorkoutFocusTarget.Set(4L, 3L), target)
    }

    @Test
    fun finalSetFocusesFinishSection() {
        val only = item(1L, "A", listOf(set(1L, SessionSetStatus.COMPLETED)))
        val target = SessionFocusLogic.focusAfterResolving(listOf(only), resolvedSetId = 1L)
        assertEquals(WorkoutFocusTarget.Finish, target)
    }

    @Test
    fun schemaHasNoRirOrRpe() {
        val names = WorkoutSession::class.java.declaredFields.map { it.name.lowercase() } +
            SessionSet::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { it.contains("rir") || it.contains("rpe") })
    }

    private fun measurement(date: LocalDate, kg: Double): WeightMeasurement {
        return WeightMeasurement(1L, date, kg, 1L, 1L)
    }

    private fun session(
        status: SessionStatus,
        startedAt: Long,
        finishedAt: Long? = null,
        abandonedAt: Long? = null
    ): WorkoutSession {
        return WorkoutSession(
            id = 1L,
            templateId = 1L,
            templateName = "Push A",
            status = status,
            workoutDate = LocalDate.parse("2026-09-15"),
            startedAt = startedAt,
            finishedAt = finishedAt,
            abandonedAt = abandonedAt,
            notes = null,
            bodyWeightKg = 88.3,
            bodyWeightSource = BodyWeightSource.MEASURED_SAME_DAY,
            bodyWeightSourceDate = LocalDate.parse("2026-09-15"),
            createdAt = startedAt,
            updatedAt = startedAt
        )
    }

    private fun session(): WorkoutSession = session(SessionStatus.IN_PROGRESS, 1_000L)

    private fun item(id: Long, name: String, sets: List<SessionSet>): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = id,
                sessionId = 1L,
                exerciseId = id,
                position = id.toInt() - 1,
                name = name,
                category = hu.laca.weighttracker.domain.exercise.ExerciseCategory.STRENGTH,
                movementPattern = hu.laca.weighttracker.domain.exercise.MovementPattern.VERTICAL_PULL,
                measurementType = hu.laca.weighttracker.domain.exercise.MeasurementType.REPETITIONS,
                resistanceBasis = hu.laca.weighttracker.domain.exercise.ResistanceBasis.BODYWEIGHT,
                weightInterpretation = hu.laca.weighttracker.domain.exercise.WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = hu.laca.weighttracker.domain.exercise.MuscleGroup.LATS,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(id: Long, status: SessionSetStatus): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1L,
            position = 0,
            plannedMinReps = 8,
            plannedMaxReps = 8,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = 8,
            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = null,
            addedDuringWorkout = false
        )
    }

    private fun set(status: SessionSetStatus, extra: Boolean = false): SessionSet {
        return SessionSet(
            id = 1L,
            sessionExerciseId = 1L,
            position = 0,
            plannedMinReps = 8,
            plannedMaxReps = 8,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = 8,
            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = null,
            addedDuringWorkout = extra
        )
    }
}
