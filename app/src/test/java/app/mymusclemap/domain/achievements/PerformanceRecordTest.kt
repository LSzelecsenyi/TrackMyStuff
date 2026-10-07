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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PerformanceRecordTest {
    @Test
    fun firstWeightEstablishesABaselineAndALaterHeavierSetUnlocks() {
        val baseline = weighted("bench", exerciseId = 1, finishedAt = 100, weight = 60.0, reps = 8)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(baseline)).isEmpty())
        val heavier = weighted("bench-2", exerciseId = 1, finishedAt = 200, weight = 65.0, reps = 8, sessionId = 2)
        val records = PerformanceRecordEvaluator.qualifications(listOf(heavier, baseline))
        assertEquals(200L, unlockedAt(records, AchievementId.WEIGHT_PR))
        assertEquals(200L, unlockedAt(records, AchievementId.FIRST_PR))
        assertTrue(records.none { it.achievementId == AchievementId.REP_RECORD })
    }

    @Test
    fun equalOrLowerWeightDoesNotUnlockAndAnotherExerciseDoesNotCompare() {
        val bench = weighted("bench", exerciseId = 1, finishedAt = 100, weight = 80.0, reps = 5)
        val equal = weighted("equal", exerciseId = 1, finishedAt = 200, weight = 80.0, reps = 5, sessionId = 2)
        val lower = weighted("lower", exerciseId = 1, finishedAt = 300, weight = 70.0, reps = 5, sessionId = 3)
        val squat = weighted("squat", exerciseId = 2, finishedAt = 400, weight = 140.0, reps = 2, sessionId = 4)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(bench, equal, lower, squat)).isEmpty())
    }

    @Test
    fun abandonedSkippedAndZeroRepSetsDoNotCountAsWeightRecords() {
        val baseline = weighted("bench", exerciseId = 1, finishedAt = 100, weight = 60.0, reps = 8)
        val abandoned = weighted(
            "abandoned",
            exerciseId = 1,
            finishedAt = 200,
            weight = 100.0,
            reps = 8,
            sessionId = 2,
            status = SessionStatus.ABANDONED
        )
        val skipped = weighted(
            "skipped",
            exerciseId = 1,
            finishedAt = 300,
            weight = 100.0,
            reps = 8,
            sessionId = 3,
            setStatus = SessionSetStatus.SKIPPED
        )
        val zeroReps = weighted(
            "zero",
            exerciseId = 1,
            finishedAt = 400,
            weight = 100.0,
            reps = 0,
            sessionId = 4
        )
        val inProgress = weighted(
            "active",
            exerciseId = 1,
            finishedAt = null,
            weight = 100.0,
            reps = 8,
            sessionId = 5,
            status = SessionStatus.IN_PROGRESS
        )
        assertTrue(
            PerformanceRecordEvaluator.qualifications(
                listOf(baseline, abandoned, skipped, zeroReps, inProgress)
            ).isEmpty()
        )
    }

    @Test
    fun perSideAndAddedWeightUseTheExistingLoadRules() {
        val total = weighted("total", exerciseId = 1, finishedAt = 100, weight = 70.0, reps = 5)
        val perSide = weighted(
            "side",
            exerciseId = 1,
            finishedAt = 200,
            weight = 40.0,
            reps = 5,
            sessionId = 2,
            interpretation = WeightInterpretation.PER_SIDE
        )
        assertEquals(200L, unlockedAt(PerformanceRecordEvaluator.qualifications(listOf(total, perSide)), AchievementId.WEIGHT_PR))

        val added = weighted(
            "added",
            exerciseId = 7,
            finishedAt = 100,
            weight = 5.0,
            reps = 6,
            kind = PlannedLoadKind.ADDED_WEIGHT,
            resistance = ResistanceBasis.BODYWEIGHT
        )
        val moreAdded = weighted(
            "more-added",
            exerciseId = 7,
            finishedAt = 250,
            weight = 10.0,
            reps = 4,
            sessionId = 8,
            kind = PlannedLoadKind.ADDED_WEIGHT,
            resistance = ResistanceBasis.BODYWEIGHT
        )
        assertEquals(
            250L,
            unlockedAt(PerformanceRecordEvaluator.qualifications(listOf(added, moreAdded)), AchievementId.WEIGHT_PR)
        )
    }

    @Test
    fun bodyweightDurationAndDistanceDoNotInventWeightOrVolume() {
        val bodyweight = weighted(
            "pull",
            exerciseId = 3,
            finishedAt = 100,
            weight = null,
            reps = 8,
            kind = PlannedLoadKind.BODYWEIGHT_ONLY,
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
        val heavierBodyweightField = weighted(
            "pull-2",
            exerciseId = 3,
            finishedAt = 200,
            weight = 20.0,
            reps = 8,
            sessionId = 2,
            kind = PlannedLoadKind.BODYWEIGHT_ONLY,
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
        val run = distance("run", finishedAt = 300, sessionId = 3)
        val hold = duration("hold", finishedAt = 400, sessionId = 4)
        val records = PerformanceRecordEvaluator.qualifications(listOf(bodyweight, heavierBodyweightField, run, hold))
        assertTrue(records.none { it.achievementId == AchievementId.WEIGHT_PR })
        assertTrue(records.none { it.achievementId == AchievementId.VOLUME_RECORD })
        assertTrue(records.none { it.achievementId == AchievementId.FIRST_PR })
    }

    @Test
    fun laterHigherRepsUnlockAndEqualRepsOrAnotherExerciseDoNot() {
        val first = reps("pull", exerciseId = 4, finishedAt = 100, reps = 8)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(first)).isEmpty())
        val equal = reps("equal", exerciseId = 4, finishedAt = 150, reps = 8, sessionId = 2)
        val other = reps("row", exerciseId = 5, finishedAt = 180, reps = 20, sessionId = 3)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(first, equal, other)).isEmpty())
        val more = reps("more", exerciseId = 4, finishedAt = 220, reps = 10, sessionId = 4)
        val records = PerformanceRecordEvaluator.qualifications(listOf(more, first))
        assertEquals(220L, unlockedAt(records, AchievementId.REP_RECORD))
        assertEquals(220L, unlockedAt(records, AchievementId.FIRST_PR))
        assertTrue(records.none { it.achievementId == AchievementId.WEIGHT_PR })
    }

    @Test
    fun repRecordDoesNotRequireTheSameWeight() {
        val heavy = weighted("heavy", exerciseId = 1, finishedAt = 100, weight = 100.0, reps = 5)
        val lighter = weighted("light", exerciseId = 1, finishedAt = 200, weight = 40.0, reps = 12, sessionId = 2)
        val records = PerformanceRecordEvaluator.qualifications(listOf(heavy, lighter))
        assertEquals(200L, unlockedAt(records, AchievementId.REP_RECORD))
        assertTrue(records.none { it.achievementId == AchievementId.WEIGHT_PR })
    }

    @Test
    fun workoutVolumeUnlocksOnlyWhenALaterQualifyingWorkoutIsHigher() {
        val first = weighted("first", exerciseId = 1, finishedAt = 100, weight = 80.0, reps = 10)
        assertEquals(800.0, PerformanceRecordEvaluator.workoutVolumeKg(first)!!, 0.0)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(first)).isEmpty())
        val equal = weighted("equal", exerciseId = 1, finishedAt = 200, weight = 100.0, reps = 8, sessionId = 2)
        val lower = weighted("lower", exerciseId = 1, finishedAt = 300, weight = 50.0, reps = 10, sessionId = 3)
        val withoutAHigherTotal = PerformanceRecordEvaluator.qualifications(listOf(first, equal, lower))
        assertTrue(withoutAHigherTotal.none { it.achievementId == AchievementId.VOLUME_RECORD })
        val higher = weighted("higher", exerciseId = 1, finishedAt = 400, weight = 90.0, reps = 10, sessionId = 4)
        val records = PerformanceRecordEvaluator.qualifications(listOf(lower, higher, first))
        assertEquals(400L, unlockedAt(records, AchievementId.VOLUME_RECORD))
        assertEquals(400L, unlockedAt(records, AchievementId.FIRST_PR))
    }

    @Test
    fun chronologicalHistoryKeepsTheEarliestRecordAndTiedIdsStayStable() {
        val january = weighted("jan", exerciseId = 1, finishedAt = 1_000, weight = 60.0, reps = 8, sessionId = 10)
        val eighth = weighted("eighth", exerciseId = 1, finishedAt = 2_000, weight = 65.0, reps = 8, sessionId = 11)
        val fifteenth = weighted("fifteenth", exerciseId = 1, finishedAt = 3_000, weight = 62.5, reps = 8, sessionId = 12)
        val forward = PerformanceRecordEvaluator.qualifications(listOf(fifteenth, january, eighth))
        assertEquals(2_000L, unlockedAt(forward, AchievementId.WEIGHT_PR))
        assertEquals(forward, PerformanceRecordEvaluator.qualifications(listOf(january, eighth, fifteenth)))

        val earlierIdHeavier = weighted("earlier-id", exerciseId = 1, finishedAt = 500, weight = 70.0, reps = 5, sessionId = 1)
        val laterIdLighter = weighted("later-id", exerciseId = 1, finishedAt = 500, weight = 60.0, reps = 5, sessionId = 2)
        assertTrue(PerformanceRecordEvaluator.qualifications(listOf(laterIdLighter, earlierIdHeavier)).isEmpty())

        val earlierBaseline = weighted("baseline", exerciseId = 1, finishedAt = 500, weight = 60.0, reps = 5, sessionId = 1)
        val laterRecord = weighted("later", exerciseId = 1, finishedAt = 500, weight = 70.0, reps = 5, sessionId = 2)
        val tied = PerformanceRecordEvaluator.qualifications(listOf(laterRecord, earlierBaseline))
        assertEquals(500L, unlockedAt(tied, AchievementId.WEIGHT_PR))
        assertEquals("later", tied.single { it.achievementId == AchievementId.WEIGHT_PR }.clientWorkoutId)
    }

    @Test
    fun importedCompletedWorkoutsStayInTheSamePerformanceHistory() {
        val imported = weighted("imported", exerciseId = 1, finishedAt = 100, weight = 60.0, reps = 8, fingerprint = "csv-1")
        val native = weighted("native", exerciseId = 1, finishedAt = 200, weight = 62.5, reps = 8, sessionId = 2)
        assertEquals(200L, unlockedAt(PerformanceRecordEvaluator.qualifications(listOf(imported, native)), AchievementId.WEIGHT_PR))
    }

    @Test
    fun reconciliationCelebratesOnlyTheSpecificRecordFromTheTriggeringWorkout() {
        val qualifications = listOf(
            PerformanceQualification(AchievementId.FIRST_PR, 200L, "live"),
            PerformanceQualification(AchievementId.WEIGHT_PR, 200L, "live"),
            PerformanceQualification(AchievementId.REP_RECORD, 200L, "live"),
            PerformanceQualification(AchievementId.VOLUME_RECORD, 200L, "live")
        )
        val plan = AchievementReconciler.plan(
            request = request(trigger = "live", performance = qualifications),
            unlocks = emptyList(),
            events = emptyList()
        )
        val pending = plan.insertUnlocks.single { it.celebratedAt == null }
        assertEquals(AchievementId.WEIGHT_PR, pending.achievementId)
        assertEquals("live", pending.triggerClientWorkoutId)
        assertEquals(200L, pending.unlockedAt)
        assertEquals(4, plan.insertUnlocks.size)
        assertTrue(plan.insertUnlocks.filter { it.achievementId != AchievementId.WEIGHT_PR }.all { it.celebratedAt == 1_000L })
        assertTrue(plan.revoke.isEmpty())

        val stored = plan.insertUnlocks.map { StoredUnlock(it.achievementId, it.celebratedAt) }
        val again = AchievementReconciler.plan(
            request = request(trigger = "live", performance = qualifications),
            unlocks = stored,
            events = emptyList()
        )
        assertTrue(again.changesNothing)

        val historical = AchievementReconciler.plan(
            request = request(trigger = null, performance = qualifications),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(historical.insertUnlocks.all { it.celebratedAt == 1_000L })
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 2,
            unlocks = historical.insertUnlocks.map {
                UnlockSnapshot(it.achievementId, it.unlockedAt, it.celebratedAt, it.triggerClientWorkoutId)
            },
            events = emptyList()
        )
        assertTrue(board.pending.none { it is PendingCelebration.PerformanceUnlocked })
        assertTrue(board.items.filter { it.category == AchievementCategory.PERFORMANCE }.all { it.countProgress == null })
    }

    @Test
    fun anEarnedPerformanceAchievementIsNotRevokedWhenHistoryDisappears() {
        val stored = listOf(
            StoredUnlock(AchievementId.FIRST_PR, celebratedAt = 1L),
            StoredUnlock(AchievementId.VOLUME_RECORD, celebratedAt = 1L)
        )
        val plan = AchievementReconciler.plan(
            request = request(trigger = null, performance = emptyList()),
            unlocks = stored,
            events = emptyList()
        )
        assertTrue(plan.insertUnlocks.isEmpty())
        assertTrue(plan.revoke.isEmpty())
    }

    private fun unlockedAt(records: List<PerformanceQualification>, id: AchievementId): Long {
        return records.single { it.achievementId == id }.unlockedAt
    }

    private fun request(
        trigger: String?,
        performance: List<PerformanceQualification>
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = true,
            completedWorkoutCount = 2,
            achievedWeeks = emptyList(),
            nowMillis = 1_000L,
            triggerClientWorkoutId = trigger,
            performanceQualifications = performance
        )
    }

    private fun weighted(
        client: String,
        exerciseId: Long,
        finishedAt: Long?,
        weight: Double?,
        reps: Int,
        sessionId: Long = 1,
        status: SessionStatus = SessionStatus.COMPLETED,
        setStatus: SessionSetStatus = SessionSetStatus.COMPLETED,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        fingerprint: String? = null
    ): WorkoutSessionAggregate {
        return workout(
            client = client,
            sessionId = sessionId,
            finishedAt = finishedAt,
            status = status,
            fingerprint = fingerprint,
            item = item(
                exerciseId = exerciseId,
                measurement = measurement,
                resistance = resistance,
                interpretation = interpretation,
                set = performedSet(reps = reps, weight = weight, kind = kind, status = setStatus)
            )
        )
    }

    private fun reps(
        client: String,
        exerciseId: Long,
        finishedAt: Long,
        reps: Int,
        sessionId: Long = 1
    ): WorkoutSessionAggregate {
        return weighted(
            client = client,
            exerciseId = exerciseId,
            finishedAt = finishedAt,
            weight = null,
            reps = reps,
            sessionId = sessionId,
            kind = PlannedLoadKind.BODYWEIGHT_ONLY,
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE
        )
    }

    private fun distance(client: String, finishedAt: Long, sessionId: Long): WorkoutSessionAggregate {
        return workout(
            client = client,
            sessionId = sessionId,
            finishedAt = finishedAt,
            item = item(
                exerciseId = 9,
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                resistance = ResistanceBasis.NONE,
                interpretation = WeightInterpretation.NOT_APPLICABLE,
                set = performedSet(
                    reps = null,
                    weight = null,
                    kind = PlannedLoadKind.NONE,
                    distance = 5_000.0,
                    duration = 1_800
                )
            )
        )
    }

    private fun duration(client: String, finishedAt: Long, sessionId: Long): WorkoutSessionAggregate {
        return workout(
            client = client,
            sessionId = sessionId,
            finishedAt = finishedAt,
            item = item(
                exerciseId = 10,
                measurement = MeasurementType.DURATION,
                resistance = ResistanceBasis.NONE,
                interpretation = WeightInterpretation.NOT_APPLICABLE,
                set = performedSet(reps = null, weight = null, kind = PlannedLoadKind.NONE, duration = 60)
            )
        )
    }

    private fun workout(
        client: String,
        sessionId: Long,
        finishedAt: Long?,
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
            exercises = listOf(item)
        )
    }

    private fun item(
        exerciseId: Long,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        set: SessionSet
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId,
                sessionId = 0,
                exerciseId = exerciseId,
                position = 0,
                name = "Exercise $exerciseId",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = MuscleGroup.FULL_BODY,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = listOf(set)
        )
    }

    private fun performedSet(
        reps: Int?,
        weight: Double?,
        kind: PlannedLoadKind,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        duration: Int? = null,
        distance: Double? = null
    ): SessionSet {
        return SessionSet(
            id = 1,
            sessionExerciseId = 1,
            position = 0,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = kind,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = distance,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = distance,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }
}
