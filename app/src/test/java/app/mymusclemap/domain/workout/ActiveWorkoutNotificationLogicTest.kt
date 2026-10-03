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

class ActiveWorkoutNotificationLogicTest {
    @Test
    fun validCurrentSetIsCompletableWithThePersistedDraft() {
        val current = set(id = 3L, position = 2, draft = ActualSetDraft(repsText = "11", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        val aggregate = aggregate(
            item(
                id = 10L,
                name = "Dips",
                sets = listOf(
                    set(id = 1L, position = 0, status = SessionSetStatus.COMPLETED),
                    set(id = 2L, position = 1, status = SessionSetStatus.SKIPPED),
                    current
                )
            )
        )
        val model = ActiveWorkoutNotificationLogic.project(aggregate)!!
        val body = model.body as ActiveWorkoutNotificationBody.CurrentSet
        assertEquals("Push", model.workoutName)
        assertEquals(3L, body.setId)
        assertEquals("Dips", body.exerciseName)
        assertEquals(3, body.setNumber)
        assertEquals(3, body.setCount)
        assertEquals(11, body.reps)
        assertTrue(body.completable)
        assertTrue(ActiveWorkoutNotificationLogic.completes(aggregate, 3L))
        assertFalse(ActiveWorkoutNotificationLogic.completes(aggregate, 1L))
    }

    @Test
    fun reconstructedPlanUsesTheConcreteMinimumNotTheRange() {
        val aggregate = aggregate(
            item(
                id = 10L,
                name = "Dips",
                sets = listOf(
                    set(
                        id = 1L,
                        position = 0,
                        minReps = 8,
                        maxReps = 12,
                        reps = 8,
                        load = PlannedLoadKind.ADDED_WEIGHT,
                        weightKg = 15.0
                    )
                )
            )
        )
        val body = ActiveWorkoutNotificationLogic.project(aggregate)!!.body as ActiveWorkoutNotificationBody.CurrentSet
        assertEquals(8, body.reps)
        assertEquals(15.0, body.weightKg!!, 0.0)
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, body.loadKind)
        assertTrue(body.completable)
        assertEquals(
            "8 reps · +15 kg",
            notificationValueParts("8 reps", notificationLoadLabel(body.loadKind!!, body.weightKg, "bodyweight"), null, null)
        )
    }

    @Test
    fun missingRequiredValuesStayVisibleButAreNotCompletable() {
        val aggregate = aggregate(
            item(
                id = 10L,
                name = "Dip",
                measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistance = ResistanceBasis.BODYWEIGHT,
                sets = listOf(
                    set(
                        id = 4L,
                        position = 2,
                        reps = null,
                        minReps = null,
                        maxReps = null,
                        load = PlannedLoadKind.ADDED_WEIGHT,
                        weightKg = null,
                        draft = ActualSetDraft(repsText = "", loadKind = PlannedLoadKind.ADDED_WEIGHT, weightText = "")
                    )
                )
            )
        )
        val body = ActiveWorkoutNotificationLogic.project(aggregate)!!.body as ActiveWorkoutNotificationBody.CurrentSet
        assertEquals(4L, body.setId)
        assertEquals(3, body.setNumber)
        assertFalse(body.completable)
        assertNull(body.reps)
        assertNull(body.weightKg)
    }

    @Test
    fun noPendingSetsAsksToFinishWithoutACurrentSet() {
        val aggregate = aggregate(
            item(
                id = 10L,
                name = "Dips",
                sets = listOf(set(id = 1L, position = 0, status = SessionSetStatus.COMPLETED))
            )
        )
        assertEquals(
            ActiveWorkoutNotificationBody.ReadyToFinish,
            ActiveWorkoutNotificationLogic.project(aggregate)!!.body
        )
    }

    @Test
    fun absentWhenThereIsNoInProgressSession() {
        assertNull(ActiveWorkoutNotificationLogic.project(null))
        val finished = aggregate(
            item(id = 10L, name = "Dips", sets = listOf(set(id = 1L, position = 0))),
            status = SessionStatus.COMPLETED
        )
        assertNull(ActiveWorkoutNotificationLogic.project(finished))
    }

    @Test
    fun completionOnlyAndDurationSetsUseParsedPersistedValues() {
        val completion = aggregate(
            item(
                id = 1L,
                name = "Hold",
                measurement = MeasurementType.COMPLETION_ONLY,
                resistance = ResistanceBasis.NONE,
                sets = listOf(set(id = 1L, position = 0, reps = null, minReps = null, maxReps = null, load = PlannedLoadKind.NONE))
            )
        )
        val completionBody = ActiveWorkoutNotificationLogic.project(completion)!!.body as ActiveWorkoutNotificationBody.CurrentSet
        assertTrue(completionBody.completable)
        assertNull(completionBody.reps)

        val timed = aggregate(
            item(
                id = 2L,
                name = "Plank",
                measurement = MeasurementType.DURATION,
                resistance = ResistanceBasis.NONE,
                sets = listOf(
                    set(
                        id = 8L,
                        position = 0,
                        reps = null,
                        minReps = null,
                        maxReps = null,
                        load = PlannedLoadKind.NONE,
                        durationSeconds = 45
                    )
                )
            )
        )
        val timedBody = ActiveWorkoutNotificationLogic.project(timed)!!.body as ActiveWorkoutNotificationBody.CurrentSet
        assertTrue(timedBody.completable)
        assertEquals(45, timedBody.durationSeconds)
    }

    private fun aggregate(
        item: SessionExerciseItem,
        status: SessionStatus = SessionStatus.IN_PROGRESS
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = 7L,
                templateId = 1L,
                templateName = "Push",
                status = status,
                workoutDate = LocalDate.parse("2026-09-16"),
                startedAt = 1_000L,
                finishedAt = null,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1_000L,
                updatedAt = 1_000L
            ),
            exercises = listOf(item)
        )
    }

    private fun item(
        id: Long,
        name: String,
        sets: List<SessionSet>,
        measurement: MeasurementType = MeasurementType.REPETITIONS,
        resistance: ResistanceBasis = ResistanceBasis.BODYWEIGHT
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = id,
                sessionId = 7L,
                exerciseId = id,
                position = 0,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun set(
        id: Long,
        position: Int,
        status: SessionSetStatus = SessionSetStatus.PENDING,
        reps: Int? = 8,
        minReps: Int? = reps,
        maxReps: Int? = reps,
        load: PlannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
        weightKg: Double? = null,
        durationSeconds: Int? = null,
        draft: ActualSetDraft? = null
    ): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 10L,
            position = position,
            plannedMinReps = minReps,
            plannedMaxReps = maxReps,
            plannedLoadKind = load,
            plannedWeightKg = weightKg,
            plannedDurationSeconds = durationSeconds,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = load,
            actualWeightKg = weightKg,
            actualDurationSeconds = durationSeconds,
            actualDistanceMeters = null,
            status = status,
            completedAt = null,
            addedDuringWorkout = false,
            draft = draft
        )
    }
}
