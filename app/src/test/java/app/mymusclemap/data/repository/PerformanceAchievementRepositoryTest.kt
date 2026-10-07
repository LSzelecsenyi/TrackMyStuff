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
import app.mymusclemap.domain.achievements.PendingCelebration
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
class PerformanceAchievementRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 5)
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AchievementRepository(database, clock, FixedDateProvider(today))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun historicalWeightRecordUnlocksOnceAndStaysAfterTheWorkoutIsDeleted() = runTest {
        val exerciseId = insertExercise("Bench Press")
        val baseline = insertWorkout(
            client = "baseline",
            finishedAt = 1_000L,
            exerciseId = exerciseId,
            weight = 60.0,
            reps = 8
        )
        val record = insertWorkout(
            client = "record",
            finishedAt = 2_000L,
            exerciseId = exerciseId,
            weight = 65.0,
            reps = 8
        )
        insertWorkout(
            client = "abandoned",
            finishedAt = 1_500L,
            exerciseId = exerciseId,
            weight = 200.0,
            reps = 1,
            status = SessionStatus.ABANDONED
        )
        repository.reconcile(triggerClientWorkoutId = "record")
        assertEquals(2_000L, unlockAt(AchievementId.WEIGHT_PR))
        assertEquals(2_000L, unlockAt(AchievementId.FIRST_PR))
        val pending = repository.board().pending.filterIsInstance<PendingCelebration.PerformanceUnlocked>().single()
        assertEquals(AchievementId.WEIGHT_PR, pending.achievementId)
        assertEquals("record", pending.triggerClientWorkoutId)
        assertTrue(
            repository.board().items.single { it.id == AchievementId.FIRST_PR }.unlocked
        )
        assertEquals(null, repository.board().items.single { it.id == AchievementId.REP_RECORD }.countProgress)
        repository.reconcile(triggerClientWorkoutId = "record")
        assertEquals(2_000L, unlockAt(AchievementId.WEIGHT_PR))
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.WEIGHT_PR.name })
        database.workoutSessionDao().deleteSessionById(baseline)
        database.workoutSessionDao().deleteSessionById(record)
        repository.reconcile()
        assertEquals(2_000L, unlockAt(AchievementId.WEIGHT_PR))
        assertEquals(2_000L, unlockAt(AchievementId.FIRST_PR))
    }

    private suspend fun unlockAt(id: AchievementId): Long {
        return database.achievementDao().unlocks().single { it.achievementId == id.name }.unlockedAt
    }

    private suspend fun insertExercise(name: String): Long {
        return database.exerciseDao().insert(
            ExerciseEntity(
                name = name,
                normalizedName = name.lowercase(),
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

    private suspend fun insertWorkout(
        client: String,
        finishedAt: Long,
        exerciseId: Long,
        weight: Double,
        reps: Int,
        status: SessionStatus = SessionStatus.COMPLETED
    ): Long {
        val sessionId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = status.name,
                workoutDate = today.toString(),
                startedAt = finishedAt - 10,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) finishedAt else null,
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
                plannedMinReps = reps,
                plannedMaxReps = reps,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = weight,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = reps,
                actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                actualWeightKg = weight,
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
