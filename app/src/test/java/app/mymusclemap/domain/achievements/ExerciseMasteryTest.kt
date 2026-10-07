package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExerciseMasteryTest {
    @Test
    fun setsFromDifferentExercisesDoNotCombine() {
        val history = listOf(
            session(client = "a", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = 70),
            session(client = "b", sessionId = 2L, finishedAt = 2_000L, exerciseId = 20L, sets = 30)
        )
        assertEquals(70, ExerciseMasteryEvaluator.leadingCount(history))
        assertTrue(ExerciseMasteryEvaluator.qualifications(history).isEmpty())
    }

    @Test
    fun eachThresholdIsProvenByOneExercise() {
        assertTrue(qualifications(sets = 99).isEmpty())
        assertEquals(listOf(AchievementId.EXERCISE_MASTERY_100), ids(100))
        assertEquals(listOf(AchievementId.EXERCISE_MASTERY_100), ids(249))
        assertEquals(
            listOf(AchievementId.EXERCISE_MASTERY_100, AchievementId.EXERCISE_MASTERY_250),
            ids(250)
        )
        assertFalse(ids(499).contains(AchievementId.EXERCISE_MASTERY_500))
        assertTrue(ids(500).contains(AchievementId.EXERCISE_MASTERY_500))
        assertFalse(ids(999).contains(AchievementId.EXERCISE_MASTERY_1000))
        assertTrue(ids(1000).contains(AchievementId.EXERCISE_MASTERY_1000))
    }

    @Test
    fun aDifferentExerciseCanCrossALaterThresholdWithoutSumming() {
        val pullUps = session(client = "pull", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = 100)
        val dips = session(client = "dip", sessionId = 2L, finishedAt = 5_000L, exerciseId = 20L, sets = 250)
        val qualified = ExerciseMasteryEvaluator.qualifications(listOf(pullUps, dips))
        assertEquals(1_000L, qualified.single { it.achievementId == AchievementId.EXERCISE_MASTERY_100 }.unlockedAt)
        assertEquals(5_000L, qualified.single { it.achievementId == AchievementId.EXERCISE_MASTERY_250 }.unlockedAt)
        assertEquals(250, ExerciseMasteryEvaluator.leadingCount(listOf(pullUps, dips)))
    }

    @Test
    fun theCrossingSetUsesTheWorkoutCompletionTime() {
        val history = listOf(
            session(client = "early", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = 99),
            session(client = "cross", sessionId = 2L, finishedAt = 4_000L, exerciseId = 10L, sets = 1)
        )
        val row = ExerciseMasteryEvaluator.qualifications(history).single()
        assertEquals(AchievementId.EXERCISE_MASTERY_100, row.achievementId)
        assertEquals(4_000L, row.unlockedAt)
        assertEquals("cross", row.clientWorkoutId)
    }

    @Test
    fun skippedUnfinishedAbandonedAndInProgressSetsDoNotCount() {
        val completed = session(client = "done", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = 2)
        val withSkipped = completed.copy(
            exercises = listOf(
                completed.exercises.single().copy(
                    sets = completed.exercises.single().sets +
                        set(position = 9, status = SessionSetStatus.SKIPPED) +
                        set(position = 10, status = SessionSetStatus.PENDING)
                )
            )
        )
        val abandoned = session(
            client = "left",
            sessionId = 2L,
            finishedAt = 2_000L,
            exerciseId = 10L,
            sets = 50,
            status = SessionStatus.ABANDONED
        )
        val open = session(
            client = "open",
            sessionId = 3L,
            finishedAt = null,
            exerciseId = 10L,
            sets = 50,
            status = SessionStatus.IN_PROGRESS
        )
        assertEquals(2, ExerciseMasteryEvaluator.leadingCount(listOf(withSkipped, abandoned, open)))
    }

    @Test
    fun completedRepsWeightDurationAndDistanceSetsCount() {
        val history = listOf(
            typed("reps", 1L, MeasurementType.REPETITIONS),
            typed("weight", 2L, MeasurementType.REPETITIONS_AND_WEIGHT),
            typed("duration", 3L, MeasurementType.DURATION),
            typed("distance", 4L, MeasurementType.DISTANCE_AND_DURATION)
        )
        assertEquals(4, ExerciseMasteryEvaluator.leadingCount(history))
    }

    @Test
    fun importedCompletedSetsCount() {
        val history = listOf(
            session(client = "import", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = 3, fingerprint = "csv")
        )
        assertEquals(3, ExerciseMasteryEvaluator.leadingCount(history))
    }

    @Test
    fun freeCompletionDoesNotEarnAndUpgradeUsesTheHistoricalTimestamp() {
        val qualifications = ExerciseMasteryEvaluator.qualifications(
            listOf(session(client = "cross", sessionId = 1L, finishedAt = 4_000L, exerciseId = 10L, sets = 100))
        )
        val locked = AchievementReconciler.plan(
            request = request(qualifications, grantsPro = false),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(locked.insertUnlocks.none { it.achievementId == AchievementId.EXERCISE_MASTERY_100 })
        val earned = AchievementReconciler.plan(
            request = request(qualifications, grantsPro = true),
            unlocks = emptyList(),
            events = emptyList()
        ).insertUnlocks.single { it.achievementId == AchievementId.EXERCISE_MASTERY_100 }
        assertEquals(4_000L, earned.unlockedAt)
        val kept = AchievementReconciler.plan(
            request = request(emptyList(), grantsPro = false),
            unlocks = listOf(StoredUnlock(AchievementId.EXERCISE_MASTERY_100, celebratedAt = 9_000L)),
            events = emptyList()
        )
        assertFalse(kept.revoke.contains(AchievementId.EXERCISE_MASTERY_100))
    }

    @Test
    fun almostThereShowsOnlyTheNextMasteryTier() {
        val early = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = emptyList(),
            events = emptyList(),
            leadingExerciseSets = 83
        )
        assertEquals(
            listOf(AchievementId.EXERCISE_MASTERY_100),
            BadgeWallPresenter.present(early).almostThere
                .filter { it.achievementId.masterySetTarget != null }
                .map { it.achievementId }
        )
        val after = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = listOf(UnlockSnapshot(AchievementId.EXERCISE_MASTERY_100, 1L, 1L, null)),
            events = emptyList(),
            leadingExerciseSets = 173
        )
        assertEquals(
            listOf(AchievementId.EXERCISE_MASTERY_250),
            BadgeWallPresenter.present(after).almostThere
                .filter { it.achievementId.masterySetTarget != null }
                .map { it.achievementId }
        )
    }

    private fun ids(sets: Int): List<AchievementId> = qualifications(sets).map { it.achievementId }

    private fun qualifications(sets: Int): List<ProQualification> {
        return ExerciseMasteryEvaluator.qualifications(
            listOf(session(client = "work", sessionId = 1L, finishedAt = 1_000L, exerciseId = 10L, sets = sets))
        )
    }

    private fun request(qualifications: List<ProQualification>, grantsPro: Boolean): ReconcileRequest {
        return ReconcileRequest(
            initialized = true,
            completedWorkoutCount = 0,
            achievedWeeks = emptyList(),
            nowMillis = 9_000L,
            triggerClientWorkoutId = null,
            grantsPro = grantsPro,
            proQualifications = qualifications
        )
    }

    private fun typed(client: String, sessionId: Long, measurement: MeasurementType): WorkoutSessionAggregate {
        return session(
            client = client,
            sessionId = sessionId,
            finishedAt = sessionId * 1_000L,
            exerciseId = 10L,
            sets = 1,
            measurement = measurement
        )
    }

    private fun session(
        client: String,
        sessionId: Long,
        finishedAt: Long?,
        exerciseId: Long,
        sets: Int,
        status: SessionStatus = SessionStatus.COMPLETED,
        fingerprint: String? = null,
        measurement: MeasurementType = MeasurementType.REPETITIONS
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = sessionId,
                templateId = null,
                templateName = "Workout",
                status = status,
                workoutDate = LocalDate.of(2026, 1, 1),
                startedAt = finishedAt?.minus(30) ?: 1L,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) finishedAt else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt ?: 1L,
                importFingerprint = fingerprint,
                clientWorkoutId = client
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = exerciseId,
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        position = 0,
                        name = "Exercise $exerciseId",
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = measurement,
                        resistanceBasis = ResistanceBasis.NONE,
                        weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                        primaryMuscle = MuscleGroup.FULL_BODY,
                        secondaryMuscles = emptyList(),
                        notes = null
                    ),
                    sets = (0 until sets).map { position -> set(position, SessionSetStatus.COMPLETED) }
                )
            )
        )
    }

    private fun set(position: Int, status: SessionSetStatus): SessionSet {
        return SessionSet(
            id = position.toLong() + 1,
            sessionExerciseId = 1,
            position = position,
            plannedMinReps = 5,
            plannedMaxReps = 5,
            plannedLoadKind = PlannedLoadKind.NONE,
            plannedWeightKg = null,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (status == SessionSetStatus.COMPLETED) 5 else null,
            actualLoadKind = PlannedLoadKind.NONE,
            actualWeightKg = null,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }
}
