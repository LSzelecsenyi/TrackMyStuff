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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrHunterTest {
    @Test
    fun theFirstComparablePerformanceIsABaseline() {
        val history = listOf(weighted("base", 1L, 1_000L, 60.0, reps = 5))
        assertTrue(PerformanceRecordEvaluator.events(history).isEmpty())
        assertTrue(PrHunterEvaluator.qualifications(PerformanceRecordEvaluator.events(history)).isEmpty())
    }

    @Test
    fun laterWeightRepAndVolumeRecordsEachCreateEvents() {
        val baseline = weighted("one", 1L, 1_000L, 60.0, reps = 5)
        val higherWeight = weighted("two", 2L, 2_000L, 65.0, reps = 5)
        val higherReps = weighted("three", 3L, 3_000L, 65.0, reps = 8)
        val higherVolume = weighted("four", 4L, 4_000L, 70.0, reps = 8)
        val events = PerformanceRecordEvaluator.events(listOf(baseline, higherWeight, higherReps, higherVolume))
        assertEquals(
            listOf(
                PerformanceRecordKind.WEIGHT,
                PerformanceRecordKind.VOLUME,
                PerformanceRecordKind.REPS,
                PerformanceRecordKind.VOLUME,
                PerformanceRecordKind.WEIGHT,
                PerformanceRecordKind.VOLUME
            ),
            events.map { it.kind }
        )
    }

    @Test
    fun oneSetCanProduceWeightAndRepEventsAndTheWorkoutCanAddVolume() {
        val baseline = weighted("one", 1L, 1_000L, 60.0, reps = 5)
        val both = weighted("two", 2L, 2_000L, 65.0, reps = 6)
        val events = PerformanceRecordEvaluator.events(listOf(baseline, both))
        assertEquals(
            listOf(PerformanceRecordKind.WEIGHT, PerformanceRecordKind.REPS, PerformanceRecordKind.VOLUME),
            events.map { it.kind }
        )
        assertTrue(events.all { it.clientWorkoutId == "two" })
        assertTrue(events.all { it.unlockedAt == 2_000L })
    }

    @Test
    fun equalOrLowerValuesDoNotCreateEvents() {
        val history = listOf(
            weighted("one", 1L, 1_000L, 60.0, reps = 5),
            weighted("two", 2L, 2_000L, 60.0, reps = 5),
            weighted("three", 3L, 3_000L, 55.0, reps = 4)
        )
        assertTrue(PerformanceRecordEvaluator.events(history).isEmpty())
    }

    @Test
    fun abandonedAndInProgressWorkoutsAreExcluded() {
        val history = listOf(
            weighted("base", 1L, 1_000L, 60.0, reps = 5),
            weighted("abandoned", 2L, 2_000L, 90.0, reps = 10, status = SessionStatus.ABANDONED),
            weighted("open", 3L, 3_000L, 90.0, reps = 10, status = SessionStatus.IN_PROGRESS)
        )
        assertTrue(PerformanceRecordEvaluator.events(history).isEmpty())
    }

    @Test
    fun importedCompletedHistoryFollowsTheSameRecordSemantics() {
        val history = listOf(
            weighted("import-base", 1L, 1_000L, 40.0, reps = 5, fingerprint = "csv"),
            weighted("import-pr", 2L, 2_000L, 50.0, reps = 5, fingerprint = "csv")
        )
        val events = PerformanceRecordEvaluator.events(history)
        assertEquals(listOf(PerformanceRecordKind.WEIGHT, PerformanceRecordKind.VOLUME), events.map { it.kind })
    }

    @Test
    fun thresholdsUnlockOnTheCrossingEvent() {
        val history = weightProgression(events = 100)
        val records = PerformanceRecordEvaluator.events(history)
        assertEquals(100, records.size)
        assertTrue(PrHunterEvaluator.qualifications(records.take(9)).isEmpty())
        assertEquals(
            listOf(AchievementId.PR_HUNTER_10),
            PrHunterEvaluator.qualifications(records.take(10)).map { it.achievementId }
        )
        assertEquals(records[9].unlockedAt, PrHunterEvaluator.qualifications(records.take(10)).single().unlockedAt)
        assertEquals(records[24].unlockedAt, qualification(records, AchievementId.PR_HUNTER_25).unlockedAt)
        assertEquals(records[49].unlockedAt, qualification(records, AchievementId.PR_HUNTER_50).unlockedAt)
        assertEquals(records[99].unlockedAt, qualification(records, AchievementId.PR_HUNTER_100).unlockedAt)
        assertEquals("w10", qualification(records, AchievementId.PR_HUNTER_10).clientWorkoutId)
        assertEquals("w100", qualification(records, AchievementId.PR_HUNTER_100).clientWorkoutId)
    }

    @Test
    fun eventOrderIsDeterministicInsideOneWorkout() {
        val baseline = weighted("one", 1L, 1_000L, 60.0, reps = 5)
        val secondSet = performedSet(reps = 8, weight = 70.0, position = 1, id = 2L)
        val firstSet = performedSet(reps = 6, weight = 65.0, position = 0, id = 1L)
        val workout = workout(
            client = "two",
            sessionId = 2L,
            finishedAt = 2_000L,
            item = item(exerciseId = 10L, sets = listOf(secondSet, firstSet))
        )
        val events = PerformanceRecordEvaluator.events(listOf(baseline, workout))
        assertEquals(
            listOf(
                PerformanceRecordKind.WEIGHT,
                PerformanceRecordKind.REPS,
                PerformanceRecordKind.WEIGHT,
                PerformanceRecordKind.REPS,
                PerformanceRecordKind.VOLUME
            ),
            events.map { it.kind }
        )
    }

    @Test
    fun freeCompletionDoesNotEarnAndProUpgradeUsesTheHistoricalTimestamp() {
        val records = PerformanceRecordEvaluator.events(weightProgression(events = 10))
        val qualifications = PrHunterEvaluator.qualifications(records)
        val locked = AchievementReconciler.plan(
            request = request(qualifications, grantsPro = false),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(locked.insertUnlocks.none { it.achievementId == AchievementId.PR_HUNTER_10 })
        val earned = AchievementReconciler.plan(
            request = request(qualifications, grantsPro = true),
            unlocks = emptyList(),
            events = emptyList()
        )
        val row = earned.insertUnlocks.single { it.achievementId == AchievementId.PR_HUNTER_10 }
        assertEquals(qualifications.single().unlockedAt, row.unlockedAt)
        assertEquals(9_000L, row.celebratedAt)
        val kept = AchievementReconciler.plan(
            request = request(emptyList(), grantsPro = false),
            unlocks = listOf(StoredUnlock(AchievementId.PR_HUNTER_10, celebratedAt = 9_000L)),
            events = emptyList()
        )
        assertFalse(kept.revoke.contains(AchievementId.PR_HUNTER_10))
    }

    @Test
    fun almostThereShowsOnlyTheNextHunterTier() {
        val seven = board(records = 7, earned = emptyList())
        assertEquals(
            listOf(AchievementId.PR_HUNTER_10),
            BadgeWallPresenter.present(seven).almostThere
                .filter { it.achievementId.prHunterTarget != null }
                .map { it.achievementId }
        )
        val afterTen = board(records = 10, earned = listOf(AchievementId.PR_HUNTER_10))
        assertEquals(
            listOf(AchievementId.PR_HUNTER_25),
            BadgeWallPresenter.present(afterTen).almostThere
                .filter { it.achievementId.prHunterTarget != null }
                .map { it.achievementId }
        )
    }

    @Test
    fun fewerEventsBeforeUnlockLowerTheDerivedProgress() {
        val nine = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = emptyList(),
            events = emptyList(),
            prEventCount = 9
        )
        val progress = nine.items.single { it.id == AchievementId.PR_HUNTER_10 }.countProgress!!
        assertEquals(9, progress.current)
        assertFalse(nine.items.single { it.id == AchievementId.PR_HUNTER_10 }.requirementMet)
    }

    private fun qualification(events: List<PerformanceRecordEvent>, id: AchievementId): ProQualification {
        return PrHunterEvaluator.qualifications(events).single { it.achievementId == id }
    }

    private fun board(records: Int, earned: List<AchievementId>): AchievementBoard {
        return AchievementBoardAssembler.assemble(
            completedWorkoutCount = 0,
            unlocks = earned.map { UnlockSnapshot(it, 1L, 1L, null) },
            events = emptyList(),
            prEventCount = records
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

    /**
     * One baseline workout plus [events] workouts that each add exactly one rep record.
     * Reps-only sets have no kilogram volume, so the event count stays one per workout.
     */
    private fun weightProgression(events: Int): List<WorkoutSessionAggregate> {
        return (0..events).map { index ->
            repsOnly(
                client = if (index == 0) "base" else "w$index",
                sessionId = index.toLong(),
                finishedAt = 1_000L + index,
                reps = 5 + index
            )
        }
    }

    private fun repsOnly(
        client: String,
        sessionId: Long,
        finishedAt: Long,
        reps: Int
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = sessionId,
                templateId = null,
                templateName = "Workout",
                status = SessionStatus.COMPLETED,
                workoutDate = LocalDate.of(2026, 1, 1),
                startedAt = finishedAt - 30,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt,
                clientWorkoutId = client
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = 10L,
                        sessionId = sessionId,
                        exerciseId = 10L,
                        position = 0,
                        name = "Pull-up",
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = MeasurementType.REPETITIONS,
                        resistanceBasis = ResistanceBasis.BODYWEIGHT,
                        weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                        primaryMuscle = MuscleGroup.FULL_BODY,
                        secondaryMuscles = emptyList(),
                        notes = null
                    ),
                    sets = listOf(
                        SessionSet(
                            id = 1,
                            sessionExerciseId = 1,
                            position = 0,
                            plannedMinReps = reps,
                            plannedMaxReps = reps,
                            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                            plannedWeightKg = null,
                            plannedDurationSeconds = null,
                            plannedDistanceMeters = null,
                            actualReps = reps,
                            actualLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                            actualWeightKg = null,
                            actualDurationSeconds = null,
                            actualDistanceMeters = null,
                            status = SessionSetStatus.COMPLETED,
                            completedAt = 1L,
                            addedDuringWorkout = false
                        )
                    )
                )
            )
        )
    }

    private fun weighted(
        client: String,
        sessionId: Long,
        finishedAt: Long,
        weight: Double,
        reps: Int,
        status: SessionStatus = SessionStatus.COMPLETED,
        fingerprint: String? = null
    ): WorkoutSessionAggregate {
        return workout(
            client = client,
            sessionId = sessionId,
            finishedAt = finishedAt,
            status = status,
            fingerprint = fingerprint,
            item = item(
                exerciseId = 10L,
                sets = listOf(performedSet(reps = reps, weight = weight, position = 0, id = 1L))
            )
        )
    }

    private fun workout(
        client: String,
        sessionId: Long,
        finishedAt: Long,
        item: SessionExerciseItem,
        status: SessionStatus = SessionStatus.COMPLETED,
        fingerprint: String? = null
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = sessionId,
                templateId = null,
                templateName = "Workout",
                status = status,
                workoutDate = LocalDate.of(2026, 1, 1),
                startedAt = finishedAt - 30,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) finishedAt else null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt,
                importFingerprint = fingerprint,
                clientWorkoutId = client
            ),
            exercises = listOf(item)
        )
    }

    private fun item(exerciseId: Long, sets: List<SessionSet>): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId,
                sessionId = 0,
                exerciseId = exerciseId,
                position = 0,
                name = "Exercise $exerciseId",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.FULL_BODY,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun performedSet(reps: Int, weight: Double, position: Int, id: Long): SessionSet {
        return SessionSet(
            id = id,
            sessionExerciseId = 1,
            position = position,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            plannedWeightKg = weight,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = reps,
            actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            actualWeightKg = weight,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = SessionSetStatus.COMPLETED,
            completedAt = 1L,
            addedDuringWorkout = false
        )
    }
}
