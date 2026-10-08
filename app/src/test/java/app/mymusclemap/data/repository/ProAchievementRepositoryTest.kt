package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.BadgeVisualState
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.visualState
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ProAchievementRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 5)
    private var grantsPro = false
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        grantsPro = false
        repository = AchievementRepository(
            database = database,
            clock = clock,
            dateProvider = FixedDateProvider(today),
            grantsPro = { grantsPro }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun satisfiedVolumeStaysLockedUntilProAndRelocksOnDowngrade() = runTest {
        val exerciseId = insertExercise()
        val baseline = insertVolume(client = "baseline", finishedAt = 1_000L, exerciseId = exerciseId, kilograms = 20_000.0)
        val crossing = insertVolume(client = "cross", finishedAt = 2_000L, exerciseId = exerciseId, kilograms = 80_000.0)
        repository.reconcile(triggerClientWorkoutId = "cross")
        val locked = repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }
        assertFalse(locked.unlocked)
        assertTrue(locked.requirementMet)
        assertEquals(BadgeVisualState.LOCKED, locked.visualState())
        assertEquals(100_000.0, locked.volumeProgress!!.currentKg, 0.0)
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })
        assertNull(unlockOrNull(AchievementId.VOLUME_MASTER))

        grantsPro = true
        repository.reconcile()
        assertEquals(2_000L, unlockAt(AchievementId.VOLUME_MASTER))
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })
        val earnedAt = unlockAt(AchievementId.VOLUME_MASTER)
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.VOLUME_MASTER.name })

        grantsPro = false
        repository.reconcile()
        val downgraded = repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }
        assertFalse(downgraded.unlocked)
        assertTrue(downgraded.requirementMet)
        assertEquals(BadgeVisualState.LOCKED, downgraded.visualState())
        assertEquals(earnedAt, downgraded.unlockedAt)
        assertEquals(100_000.0, downgraded.volumeProgress!!.currentKg, 0.0)
        assertEquals(earnedAt, unlockAt(AchievementId.VOLUME_MASTER))
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })

        grantsPro = true
        repository.reconcile()
        val restored = repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }
        assertTrue(restored.unlocked)
        assertEquals(earnedAt, restored.unlockedAt)
        assertEquals(earnedAt, unlockAt(AchievementId.VOLUME_MASTER))
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.VOLUME_MASTER.name })
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })

        database.workoutSessionDao().deleteSessionById(baseline)
        database.workoutSessionDao().deleteSessionById(crossing)
        repository.reconcile()
        val forgotten = repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }
        assertFalse(forgotten.requirementMet)
        assertFalse(forgotten.unlocked)
        assertEquals(0.0, forgotten.volumeProgress!!.currentKg, 0.0)
        assertEquals(earnedAt, unlockAt(AchievementId.VOLUME_MASTER))
    }

    @Test
    fun deletingHistoryBeforeUnlockLowersDerivedVolume() = runTest {
        val exerciseId = insertExercise()
        val first = insertVolume(client = "first", finishedAt = 1_000L, exerciseId = exerciseId, kilograms = 80_000.0)
        insertVolume(client = "second", finishedAt = 2_000L, exerciseId = exerciseId, kilograms = 30_000.0)
        repository.reconcile()
        assertTrue(repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }.requirementMet)
        database.workoutSessionDao().deleteSessionById(first)
        repository.reconcile()
        val volume = repository.board().items.single { it.id == AchievementId.VOLUME_MASTER }
        assertFalse(volume.requirementMet)
        assertEquals(30_000.0, volume.volumeProgress!!.currentKg, 0.0)
        assertNull(unlockOrNull(AchievementId.VOLUME_MASTER))
    }

    private suspend fun unlockAt(id: AchievementId): Long = unlockOrNull(id)!!.unlockedAt

    private suspend fun unlockOrNull(id: AchievementId) =
        database.achievementDao().unlocks().singleOrNull { it.achievementId == id.name }

    private suspend fun insertExercise(): Long {
        return database.exerciseDao().insert(
            ExerciseEntity(
                name = "Bench Press",
                normalizedName = "bench press",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    private suspend fun insertVolume(
        client: String,
        finishedAt: Long,
        exerciseId: Long,
        kilograms: Double
    ): Long {
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = SessionStatus.COMPLETED.name,
                workoutDate = today.toString(),
                startedAt = finishedAt - 10,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = finishedAt,
                activeLock = null,
                clientWorkoutId = client
            )
        )
        val sessionExerciseId = database.workoutSessionDao().insertExercise(
            WorkoutSessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = 0,
                name = "Bench Press",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                primaryMuscle = MuscleGroup.CHEST.name,
                notes = null
            )
        )
        database.workoutSessionDao().insertSet(
            WorkoutSessionSetEntity(
                sessionExerciseId = sessionExerciseId,
                position = 0,
                plannedMinReps = 1,
                plannedMaxReps = 1,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = kilograms,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = 1,
                actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                actualWeightKg = kilograms,
                actualDurationSeconds = null,
                actualDistanceMeters = null,
                status = SessionSetStatus.COMPLETED.name,
                completedAt = finishedAt,
                addedDuringWorkout = false
            )
        )
        return sessionId
    }
}
