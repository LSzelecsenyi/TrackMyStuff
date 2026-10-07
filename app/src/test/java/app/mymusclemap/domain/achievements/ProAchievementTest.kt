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

class ProAchievementTest {
    @Test
    fun catalogMarksOnlyTheTwoNewAchievementsAsPro() {
        assertEquals(
            listOf(AchievementId.IRON_YEAR, AchievementId.VOLUME_MASTER),
            AchievementCatalog.pro
        )
        assertTrue(AchievementCatalog.free.none { it.access == AchievementAccess.PRO })
        assertTrue(AchievementCatalog.workoutCounts.none { it == AchievementId.IRON_YEAR || it.workoutThreshold == 250 })
        assertEquals(100, AchievementId.WORKOUTS_100.workoutThreshold)
        assertEquals(200, AchievementId.WORKOUTS_200.workoutThreshold)
        assertEquals(250, AchievementId.IRON_YEAR.lifetimeWorkoutTarget)
        assertFalse(AchievementId.IRON_YEAR.revokesWhenWorkoutCountDrops)
        assertFalse(AchievementId.VOLUME_MASTER.revokesWhenWorkoutCountDrops)
        assertEquals(AchievementCatalog.free.size + AchievementCatalog.pro.size, AchievementId.entries.size)
    }

    @Test
    fun volumeMasterUsesCanonicalVolumeAndTheCrossingWorkoutTimestamp() {
        val early = volumeWorkout(id = 2, finishedAt = 2_000, kilograms = 40_000.0, client = "early")
        val crossing = volumeWorkout(id = 5, finishedAt = 5_000, kilograms = 60_000.0, client = "cross")
        val later = volumeWorkout(id = 9, finishedAt = 9_000, kilograms = 10_000.0, client = "later")
        val shuffled = listOf(later, early, crossing)
        assertEquals(110_000.0, ProAchievementEvaluator.lifetimeVolumeKg(shuffled), 0.0)
        val master = ProAchievementEvaluator.volumeMaster(shuffled)
        assertEquals(5_000L, master!!.unlockedAt)
        assertEquals("cross", master.clientWorkoutId)
        assertNull(ProAchievementEvaluator.volumeMaster(listOf(early)))
        val exact = volumeWorkout(id = 1, finishedAt = 100, kilograms = 100_000.0, client = "exact")
        assertEquals(100L, ProAchievementEvaluator.volumeMaster(listOf(exact))!!.unlockedAt)
    }

    @Test
    fun unsupportedAndAbandonedWorkoutsDoNotCreateVolume() {
        val bodyweight = volumeWorkout(
            id = 1,
            finishedAt = 100,
            kilograms = 0.0,
            client = "bw",
            kind = PlannedLoadKind.BODYWEIGHT_ONLY,
            measurement = MeasurementType.REPETITIONS
        )
        val distance = volumeWorkout(
            id = 2,
            finishedAt = 200,
            kilograms = 0.0,
            client = "run",
            measurement = MeasurementType.DISTANCE_AND_DURATION,
            kind = PlannedLoadKind.NONE
        )
        val abandoned = volumeWorkout(
            id = 3,
            finishedAt = 300,
            kilograms = 100_000.0,
            client = "abandoned",
            status = SessionStatus.ABANDONED
        )
        val imported = volumeWorkout(
            id = 4,
            finishedAt = 400,
            kilograms = 100_000.0,
            client = "imported",
            fingerprint = "csv"
        )
        assertEquals(0.0, ProAchievementEvaluator.lifetimeVolumeKg(listOf(bodyweight, distance, abandoned)), 0.0)
        assertNull(ProAchievementEvaluator.volumeMaster(listOf(bodyweight, distance, abandoned)))
        assertEquals(400L, ProAchievementEvaluator.volumeMaster(listOf(imported))!!.unlockedAt)
    }

    @Test
    fun ironYearUsesTheTwoHundredFiftiethCompletedWorkout() {
        val short = List(249) { index -> plainWorkout(id = index + 1L, finishedAt = index + 1L) }
        assertNull(ProAchievementEvaluator.ironYear(short))
        val full = short + plainWorkout(id = 400, finishedAt = 8_000, client = "year")
        val qualification = ProAchievementEvaluator.ironYear(full.reversed())
        assertEquals(8_000L, qualification!!.unlockedAt)
        assertEquals("year", qualification.clientWorkoutId)
        val tiedEarlier = plainWorkout(id = 1, finishedAt = 10, client = "first")
        val tiedLater = List(249) { index -> plainWorkout(id = index + 2L, finishedAt = 10, client = "rest-$index") }
        val tied = ProAchievementEvaluator.ironYear(listOf(tiedLater.last()) + tiedLater.dropLast(1) + tiedEarlier)
        assertEquals("rest-248", tied!!.clientWorkoutId)
    }

    @Test
    fun freeUserDoesNotEarnOrCelebrateASatisfiedProRequirement() {
        val plan = AchievementReconciler.plan(
            request = request(grantsPro = false, qualifications = satisfied()),
            unlocks = emptyList(),
            events = emptyList()
        )
        assertTrue(plan.insertUnlocks.none { it.achievementId.access == AchievementAccess.PRO })
        val board = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 250,
            unlocks = emptyList(),
            events = emptyList(),
            lifetimeVolumeKg = 100_000.0
        )
        val volume = board.items.single { it.id == AchievementId.VOLUME_MASTER }
        assertFalse(volume.unlocked)
        assertTrue(volume.requirementMet)
        assertEquals(BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED, volume.visualState())
        assertNull(volume.unlockedAt)
        assertTrue(board.pending.none { it is PendingCelebration.ProUnlocked })
        assertTrue(BadgeWallPresenter.almostThere(board).none { it.achievementId.access == AchievementAccess.PRO })
    }

    @Test
    fun proUserUnlocksAtTheHistoricalTimestampAndStaysEarnedAfterDowngrade() {
        val qualifications = satisfied()
        val alreadyEarned = WorkoutCountEvaluator.qualified(250).map { StoredUnlock(it, celebratedAt = 1L) }
        val live = AchievementReconciler.plan(
            request = request(grantsPro = true, trigger = "cross", qualifications = qualifications),
            unlocks = alreadyEarned,
            events = emptyList()
        )
        val pending = live.insertUnlocks.single { it.celebratedAt == null }
        assertEquals(AchievementId.VOLUME_MASTER, pending.achievementId)
        assertEquals(5_000L, pending.unlockedAt)
        assertEquals(4_000L, live.insertUnlocks.single { it.achievementId == AchievementId.IRON_YEAR }.unlockedAt)
        assertEquals(1_000L, live.insertUnlocks.single { it.achievementId == AchievementId.IRON_YEAR }.celebratedAt)

        val stored = alreadyEarned + live.insertUnlocks.map { StoredUnlock(it.achievementId, it.celebratedAt) }
        val downgrade = AchievementReconciler.plan(
            request = request(grantsPro = false, qualifications = emptyList()),
            unlocks = stored,
            events = emptyList()
        )
        assertTrue(downgrade.insertUnlocks.isEmpty())
        assertTrue(downgrade.revoke.isEmpty())
        val again = AchievementReconciler.plan(
            request = request(grantsPro = true, trigger = "cross", qualifications = qualifications),
            unlocks = stored,
            events = emptyList()
        )
        assertTrue(again.changesNothing)
    }

    @Test
    fun partialVolumeCanAppearInAlmostThereWithoutACompletedLockDominatingIt() {
        val partial = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 210,
            unlocks = AchievementCatalog.workoutCounts.map { StoredUnlock(it, 1L) }.map {
                UnlockSnapshot(it.achievementId, 1L, 1L, null)
            },
            events = emptyList(),
            lifetimeVolumeKg = 50_000.0
        )
        assertTrue(BadgeWallPresenter.almostThere(partial).any { it.achievementId == AchievementId.VOLUME_MASTER })
        assertTrue(BadgeWallPresenter.almostThere(partial).any { it.achievementId == AchievementId.IRON_YEAR })
        val complete = AchievementBoardAssembler.assemble(
            completedWorkoutCount = 250,
            unlocks = AchievementCatalog.workoutCounts.map {
                UnlockSnapshot(it, 1L, 1L, null)
            },
            events = emptyList(),
            lifetimeVolumeKg = 180_000.0
        )
        assertTrue(
            BadgeWallPresenter.almostThere(complete).none {
                it.achievementId == AchievementId.VOLUME_MASTER || it.achievementId == AchievementId.IRON_YEAR
            }
        )
        assertEquals(
            BadgeVisualState.REQUIREMENT_MET_PRO_LOCKED,
            complete.items.single { it.id == AchievementId.VOLUME_MASTER }.visualState()
        )
    }

    private fun satisfied(): List<ProQualification> {
        return listOf(
            ProQualification(AchievementId.IRON_YEAR, 4_000L, "year"),
            ProQualification(AchievementId.VOLUME_MASTER, 5_000L, "cross")
        )
    }

    private fun request(
        grantsPro: Boolean,
        qualifications: List<ProQualification>,
        trigger: String? = null
    ): ReconcileRequest {
        return ReconcileRequest(
            initialized = true,
            completedWorkoutCount = 250,
            achievedWeeks = emptyList(),
            nowMillis = 1_000L,
            triggerClientWorkoutId = trigger,
            grantsPro = grantsPro,
            proQualifications = qualifications
        )
    }

    private fun plainWorkout(id: Long, finishedAt: Long, client: String = "w$id"): WorkoutSessionAggregate {
        return volumeWorkout(id, finishedAt, kilograms = 0.0, client = client, kind = PlannedLoadKind.BODYWEIGHT_ONLY, measurement = MeasurementType.REPETITIONS)
    }

    private fun volumeWorkout(
        id: Long,
        finishedAt: Long,
        kilograms: Double,
        client: String,
        status: SessionStatus = SessionStatus.COMPLETED,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        fingerprint: String? = null
    ): WorkoutSessionAggregate {
        val reps = if (kilograms <= 0.0) 8 else 1
        val weight = if (kilograms <= 0.0) null else kilograms
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = null,
                templateName = "Workout",
                status = status,
                workoutDate = LocalDate.of(2026, 1, 1),
                startedAt = finishedAt - 1,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = BodyWeightSource.UNKNOWN,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt,
                importFingerprint = fingerprint,
                clientWorkoutId = client
            ),
            exercises = listOf(
                SessionExerciseItem(
                    exercise = SessionExercise(
                        id = id,
                        sessionId = id,
                        exerciseId = 1,
                        position = 0,
                        name = "Lift",
                        category = ExerciseCategory.STRENGTH,
                        movementPattern = MovementPattern.OTHER,
                        measurementType = measurement,
                        resistanceBasis = ResistanceBasis.EXTERNAL,
                        weightInterpretation = WeightInterpretation.TOTAL,
                        primaryMuscle = MuscleGroup.FULL_BODY,
                        secondaryMuscles = emptyList(),
                        notes = null
                    ),
                    sets = listOf(
                        SessionSet(
                            id = id,
                            sessionExerciseId = id,
                            position = 0,
                            plannedMinReps = reps,
                            plannedMaxReps = reps,
                            plannedLoadKind = kind,
                            plannedWeightKg = weight,
                            plannedDurationSeconds = null,
                            plannedDistanceMeters = if (measurement == MeasurementType.DISTANCE_AND_DURATION) 1000.0 else null,
                            actualReps = if (measurement == MeasurementType.DISTANCE_AND_DURATION) null else reps,
                            actualLoadKind = kind,
                            actualWeightKg = weight,
                            actualDurationSeconds = null,
                            actualDistanceMeters = if (measurement == MeasurementType.DISTANCE_AND_DURATION) 1000.0 else null,
                            status = SessionSetStatus.COMPLETED,
                            completedAt = finishedAt,
                            addedDuringWorkout = false
                        )
                    )
                )
            )
        )
    }
}
