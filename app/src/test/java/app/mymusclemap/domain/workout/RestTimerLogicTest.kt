package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RestTimerLogicTest {
    @Test
    fun inheritedRestUsesTheExerciseDefault() {
        assertEquals(90, RestDuration.effectiveSeconds(exerciseRestSeconds = 90, setRestSeconds = null))
        val plan = RestTimerLogic.planAfterCompletion(workout(exerciseRest = 90, setRest = null), 1L)
        assertEquals(90_000L, plan?.durationMillis)
        assertEquals(2L, plan?.nextSetId)
    }

    @Test
    fun setOverrideReplacesTheExerciseDefault() {
        assertEquals(30, RestDuration.effectiveSeconds(90, 30))
        val plan = RestTimerLogic.planAfterCompletion(workout(exerciseRest = 90, setRest = 30), 1L)
        assertEquals(30_000L, plan?.durationMillis)
    }

    @Test
    fun explicitZeroDisablesRest() {
        assertEquals(0, RestDuration.effectiveSeconds(90, 0))
        assertNull(RestTimerLogic.planAfterCompletion(workout(exerciseRest = 90, setRest = 0), 1L))
    }

    @Test
    fun missingRestOnAnExistingPlanDoesNotStartATimer() {
        assertEquals(0, RestDuration.effectiveSeconds(null, null))
        assertNull(RestTimerLogic.planAfterCompletion(workout(exerciseRest = null, setRest = null), 1L))
        assertEquals(RestProgressionMode.STRICT, RestProgressionMode.fromStored(null))
        assertEquals(RestProgressionMode.STRICT, RestProgressionMode.fromStored("STRICT"))
        assertEquals(RestProgressionMode.FLEXIBLE, RestProgressionMode.fromStored("FLEXIBLE"))
    }

    @Test
    fun theFinalSetDoesNotStartRest() {
        val done = workout(exerciseRest = 60, setRest = null).let { aggregate ->
            val item = aggregate.exercises.single()
            aggregate.copy(
                exercises = listOf(
                    item.copy(
                        sets = listOf(
                            item.sets[0].copy(status = SessionSetStatus.COMPLETED),
                            item.sets[1]
                        )
                    )
                )
            )
        }
        assertNull(RestTimerLogic.planAfterCompletion(done, 2L))
    }

    @Test
    fun extendAddsTenSecondsOnceAndAgain() {
        val clock = RestClockSample(1_000L, 5_000L)
        val started = RestTimerLogic.started(
            RestStartPlan(1L, 2L, 60_000L, RestProgressionMode.STRICT),
            clock,
            generation = 1L
        )
        val once = RestTimerLogic.extend(started, clock)!!
        val later = RestClockSample(11_000L, 15_000L)
        val twice = RestTimerLogic.extend(once, later)!!
        val left = RestTimerLogic.remaining(twice, later) as RestRemaining.Active
        assertEquals(70_000L, left.remainingMillis)
        assertEquals(80_000L, twice.durationMillis)
        assertEquals(60_000L, started.durationMillis)
    }

    @Test
    fun restCountdownStartsAtTheIntervalAndExtensionsRaiseThatCap() {
        val clock = RestClockSample(5_000L, 20_000L)
        val started = RestTimerLogic.started(
            RestStartPlan(1L, 2L, 30_000L, RestProgressionMode.STRICT),
            clock,
            generation = 1L
        )
        val stale = RestClockSample(4_100L, 19_100L)
        val initial = RestTimerLogic.remaining(started, stale) as RestRemaining.Active
        assertEquals(30_000L, initial.remainingMillis)
        assertEquals("00:30", RestTimerLogic.formatCountdown(initial.remainingMillis))
        val once = RestTimerLogic.extend(started, clock)!!
        val extended = RestTimerLogic.remaining(once, stale) as RestRemaining.Active
        assertEquals(40_000L, extended.remainingMillis)
        assertEquals("00:40", RestTimerLogic.formatCountdown(extended.remainingMillis))
        val later = RestClockSample(15_000L, 30_000L)
        val twice = RestTimerLogic.extend(once, later)!!
        val beforeSecondSample = RestClockSample(14_200L, 29_200L)
        val doubled = RestTimerLogic.remaining(twice, beforeSecondSample) as RestRemaining.Active
        assertEquals(40_000L, doubled.remainingMillis)
        assertEquals("00:40", RestTimerLogic.formatCountdown(doubled.remainingMillis))
        assertTrue(RestTimerLogic.remaining(started, RestClockSample(35_000L, 50_000L)) is RestRemaining.Expired)
    }

    @Test
    fun deadlineExpiryIsExactAndDoesNotGoNegative() {
        val started = RestTimerLogic.started(
            RestStartPlan(1L, 2L, 1_500L, RestProgressionMode.STRICT),
            RestClockSample(10_000L, 20_000L),
            1L
        )
        val before = RestTimerLogic.remaining(started, RestClockSample(11_499L, 21_499L)) as RestRemaining.Active
        assertEquals(1L, before.remainingMillis)
        assertEquals("00:01", RestTimerLogic.formatCountdown(before.remainingMillis))
        assertTrue(RestTimerLogic.remaining(started, RestClockSample(11_500L, 21_500L)) is RestRemaining.Expired)
        assertEquals("00:00", RestTimerLogic.formatCountdown(0L))
        assertEquals("00:00", RestTimerLogic.formatCountdown(-5_000L))
    }

    @Test
    fun backgroundTimeUsesTheDeadlineRatherThanATick() {
        val started = RestTimerLogic.started(
            RestStartPlan(1L, 2L, 60_000L, RestProgressionMode.STRICT),
            RestClockSample(1_000L, 10_000L),
            1L
        )
        val later = RestTimerLogic.remaining(started, RestClockSample(31_000L, 40_000L)) as RestRemaining.Active
        assertEquals(30_000L, later.remainingMillis)
    }

    @Test
    fun rebootRecoversFromTheWallClockAndDropsAPassedDeadline() {
        val started = RestTimerLogic.started(
            RestStartPlan(1L, 2L, 60_000L, RestProgressionMode.STRICT),
            RestClockSample(50_000L, 100_000L),
            4L
        )
        val rebooted = RestClockSample(elapsedRealtimeMillis = 1_000L, wallClockMillis = 130_000L)
        val anchored = RestTimerLogic.reanchorAfterReboot(started, rebooted)!!
        val left = RestTimerLogic.remaining(anchored, rebooted) as RestRemaining.Active
        assertEquals(30_000L, left.remainingMillis)
        val expiredClock = RestClockSample(500L, 200_000L)
        assertNull(RestTimerLogic.reanchorAfterReboot(started, expiredClock))
        assertTrue(RestTimerLogic.remaining(started, expiredClock) is RestRemaining.Expired)
    }
}

private fun workout(exerciseRest: Int?, setRest: Int?): WorkoutSessionAggregate {
    val exercise = SessionExercise(
        id = 10L,
        sessionId = 1L,
        exerciseId = 7L,
        position = 0,
        name = "Dip",
        category = ExerciseCategory.STRENGTH,
        movementPattern = MovementPattern.VERTICAL_PUSH,
        measurementType = MeasurementType.REPETITIONS,
        resistanceBasis = ResistanceBasis.BODYWEIGHT,
        weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
        primaryMuscle = MuscleGroup.TRICEPS,
        secondaryMuscles = emptyList(),
        notes = null,
        restSeconds = exerciseRest
    )
    return WorkoutSessionAggregate(
        session = WorkoutSession(
            id = 1L,
            templateId = 3L,
            templateName = "Push",
            status = SessionStatus.IN_PROGRESS,
            workoutDate = LocalDate.parse("2026-10-17"),
            startedAt = 1L,
            finishedAt = null,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.UNKNOWN,
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L
        ),
        exercises = listOf(
            SessionExerciseItem(
                exercise = exercise,
                sets = listOf(set(1L, setRest), set(2L, null))
            )
        )
    )
}

private fun set(id: Long, restSeconds: Int?) = SessionSet(
    id = id,
    sessionExerciseId = 10L,
    position = (id - 1L).toInt(),
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
    status = SessionSetStatus.PENDING,
    completedAt = null,
    addedDuringWorkout = false,
    restSeconds = restSeconds
)
