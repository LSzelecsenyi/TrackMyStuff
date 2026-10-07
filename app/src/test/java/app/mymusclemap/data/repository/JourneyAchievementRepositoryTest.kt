package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.JourneyEvaluator
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class JourneyAchievementRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 5)
    private lateinit var context: Context
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun noCompletedWorkoutLeavesFirstStepLocked() = runTest {
        repository.reconcile()
        assertTrue(repository.board().items.none { it.id == AchievementId.FIRST_WORKOUT && it.unlocked })
    }

    @Test
    fun abandonedAndImportedWorkoutsDoNotQualify() = runTest {
        insertSession(status = "ABANDONED", finishedAt = null, abandonedAt = 30L, fingerprint = null)
        insertSession(status = "COMPLETED", finishedAt = 40L, fingerprint = "csv-1", clientId = "imported")
        repository.reconcile()
        assertTrue(repository.board().items.none { it.id == AchievementId.FIRST_WORKOUT && it.unlocked })
        repository.reconcile()
        assertTrue(repository.board().items.none { it.id == AchievementId.FIRST_WORKOUT && it.unlocked })
    }

    @Test
    fun firstNativeWorkoutUnlocksAtItsFinishTimeAndStaysAfterDeletion() = runTest {
        val earlier = insertSession(status = "COMPLETED", finishedAt = 80L, startedAt = 10L, clientId = "early")
        val later = insertSession(status = "COMPLETED", finishedAt = 200L, startedAt = 20L, clientId = "later")
        repository.reconcile("early")
        val first = repository.board().items.single { it.id == AchievementId.FIRST_WORKOUT }
        assertTrue(first.unlocked)
        assertEquals(80L, first.unlockedAt)
        assertNull(first.countProgress)
        val pending = repository.board().pending.filterIsInstance<PendingCelebration.JourneyUnlocked>().single()
        assertEquals(AchievementId.FIRST_WORKOUT, pending.achievementId)
        assertEquals("early", pending.triggerClientWorkoutId)
        repository.reconcile("early")
        assertEquals(80L, unlockAt(AchievementId.FIRST_WORKOUT))
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.FIRST_WORKOUT.name })
        database.workoutSessionDao().deleteSessionById(earlier)
        database.workoutSessionDao().deleteSessionById(later)
        assertNull(database.workoutSessionDao().earliestNativeCompleted())
        repository.reconcile()
        assertEquals(80L, unlockAt(AchievementId.FIRST_WORKOUT))
        assertTrue(repository.board().items.single { it.id == AchievementId.FIRST_WORKOUT }.unlocked)
    }

    @Test
    fun noCustomPlanLeavesPlannerLockedEvenWhenExercisesExist() = runTest {
        val exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        exercises.save(pullUp())
        repository.reconcile()
        assertEquals(0, database.workoutTemplateDao().countAll())
        assertTrue(repository.board().items.none { it.id == AchievementId.FIRST_CUSTOM_WORKOUT_PLAN && it.unlocked })
    }

    @Test
    fun savingTheFirstPlanUnlocksPlannerAtTheEarliestCreatedAt() = runTest {
        val templates = database.workoutTemplateDao()
        val older = templates.insertTemplate(template(createdAt = 40L, name = "Older"))
        val newer = templates.insertTemplate(template(createdAt = 90L, name = "Newer"))
        repository.reconcile()
        assertEquals(40L, unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        templates.deleteTemplate(older)
        templates.deleteTemplate(newer)
        repository.reconcile()
        assertEquals(40L, unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        assertEquals(0, templates.countAll())
        repository.reconcile()
        assertEquals(40L, unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
    }

    @Test
    fun persistedPlanSaveNotifiesReconciliation() = runTest {
        val exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        val templates = WorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            clock = clock,
            sessionDao = database.workoutSessionDao(),
            scheduledWorkoutDao = database.scheduledWorkoutDao(),
            onPlansChanged = { repository.reconcile() }
        )
        val exerciseId = (exercises.save(pullUp()) as ExerciseSaveResult.Created).id
        val created = templates.save(
            TemplateDraft(
                name = "Pull",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -2L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        templates.deletePermanently(created.id)
        repository.reconcile()
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
    }

    @Test
    fun monthlyReviewIsProspectiveAndKeepsTheFirstGenerationTime() = runTest {
        repository.reconcile()
        assertTrue(repository.board().items.none { it.id == AchievementId.FIRST_MONTHLY_REPORT && it.unlocked })
        assertTrue(database.achievementDao().events().none { it.dedupeKey == JourneyEvaluator.MONTHLY_REPORT_KEY })
        repository.recordMonthlyReportGenerated()
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_MONTHLY_REPORT))
        val marker = database.achievementDao().events().single { it.dedupeKey == JourneyEvaluator.MONTHLY_REPORT_KEY }
        assertEquals(clock.millis(), marker.occurredAt)
        assertEquals(clock.millis(), marker.celebratedAt)
        repository.recordMonthlyReportGenerated()
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_MONTHLY_REPORT))
        assertEquals(1, database.achievementDao().events().count { it.dedupeKey == JourneyEvaluator.MONTHLY_REPORT_KEY })
        val restarted = AchievementRepository(database, clock, FixedDateProvider(today))
        restarted.reconcile()
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_MONTHLY_REPORT))
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.FIRST_MONTHLY_REPORT.name })
    }

    @Test
    fun journeyUnlocksAndTheMonthlyMarkerSurviveBackupWithoutTheSourcePlan() = runTest {
        database.workoutTemplateDao().insertTemplate(template(createdAt = 40L, name = "Push"))
        insertSession(status = "COMPLETED", finishedAt = 80L, clientId = "native")
        repository.recordMonthlyReportGenerated()
        repository.acknowledge(repository.board().pending.map { it.acknowledgement })
        database.workoutTemplateDao().deleteTemplate(1L)
        val exported = ByteArrayOutputStream()
        val photos = File(context.cacheDir, "journey-backup-${System.nanoTime()}").apply { mkdirs() }
        val backup = AppBackupRepository(
            database,
            ThemePreferences(context),
            progressPhotoStore = ProgressPhotoStore(photos),
            instantSource = { Instant.parse("2026-10-06T00:00:00Z") }
        )
        backup.exportArchive(AppBackupSource("app.mymusclemap", "test"), exported)
        database.achievementDao().deleteUnlocks(database.achievementDao().unlocks().map { it.achievementId })
        database.achievementDao().deleteEvents(database.achievementDao().events().map { it.dedupeKey })
        val restored = backup.restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupRestoreResult.Success, restored)
        assertEquals(80L, unlockAt(AchievementId.FIRST_WORKOUT))
        assertEquals(40L, unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        assertEquals(clock.millis(), unlockAt(AchievementId.FIRST_MONTHLY_REPORT))
        assertEquals(1, database.achievementDao().events().count { it.dedupeKey == JourneyEvaluator.MONTHLY_REPORT_KEY })
        repository.reconcile()
        assertEquals(40L, unlockAt(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN))
        photos.deleteRecursively()
    }

    private suspend fun unlockAt(id: AchievementId): Long {
        return database.achievementDao().unlocks().single { it.achievementId == id.name }.unlockedAt
    }

    private suspend fun insertSession(
        status: String,
        finishedAt: Long?,
        startedAt: Long = 10L,
        abandonedAt: Long? = null,
        fingerprint: String? = null,
        clientId: String = "native"
    ): Long {
        return database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = status,
                workoutDate = today.toString(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                abandonedAt = abandonedAt,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                activeLock = null,
                importFingerprint = fingerprint,
                clientWorkoutId = UUID.nameUUIDFromBytes(clientId.toByteArray()).toString()
            )
        )
    }

    private fun template(createdAt: Long, name: String): WorkoutTemplateEntity {
        return WorkoutTemplateEntity(
            name = name,
            normalizedName = name.lowercase(),
            notes = null,
            archived = false,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun pullUp(): ExerciseDraft {
        return ExerciseDraft(
            name = "Pull-up",
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.LATS
        )
    }
}
