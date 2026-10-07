package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.AchievementStateEntity
import app.mymusclemap.data.local.TargetWeightGoalEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.calendar.WeekCalendar
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class AchievementBackupTest {
    private val today = WeekCalendar.start(LocalDate.of(2026, 10, 7))
    private val clock = Clock.fixed(Instant.parse("2026-10-05T15:00:00Z"), ZoneOffset.UTC)
    private lateinit var context: Context
    private lateinit var database: WeightDatabase
    private lateinit var preferences: ThemePreferences
    private lateinit var photoDirectory: File
    private lateinit var store: ProgressPhotoStore
    private lateinit var achievements: AchievementRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = ThemePreferences(context)
        photoDirectory = File(context.cacheDir, "achievement-backup-${System.nanoTime()}").apply { mkdirs() }
        store = ProgressPhotoStore(photoDirectory)
        achievements = AchievementRepository(database, clock, FixedDateProvider(today))
    }

    @After
    fun tearDown() {
        database.close()
        photoDirectory.deleteRecursively()
    }

    @Test
    fun archiveRoundTripPreservesUnlocksEventsAndAcknowledgement() = runTest {
        repeat(5) { insertCompleted("kept-$it") }
        achievements.reconcile()
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        val beforeUnlocks = database.achievementDao().unlocks()
        val beforeEvents = database.achievementDao().events()
        val beforeState = database.achievementDao().state()
        val exported = ByteArrayOutputStream()
        backup().exportArchive(source(), exported)

        database.achievementDao().deleteUnlocks(beforeUnlocks.map { it.achievementId })
        database.achievementDao().deleteEvents(beforeEvents.map { it.dedupeKey })
        val restored = backup().restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupRestoreResult.Success, restored)
        assertEquals(
            beforeUnlocks.sortedBy { it.achievementId },
            database.achievementDao().unlocks().sortedBy { it.achievementId }
        )
        assertEquals(beforeEvents.map { it.copy(id = it.id) }, database.achievementDao().events())
        assertEquals(beforeState, database.achievementDao().state())
        assertTrue(achievements.board().pending.isEmpty())
    }

    @Test
    fun archiveRoundTripPreservesTheTargetWeightGoalAndOnTargetTimestamp() = runTest {
        database.targetWeightGoalDao().insert(
            TargetWeightGoalEntity(
                targetKg = 78.0,
                baselineKg = 87.0,
                direction = "LOSS",
                createdAt = 10L,
                updatedAt = 11L,
                retiredAt = null
            )
        )
        database.achievementDao().insertUnlocks(
            listOf(
                app.mymusclemap.data.local.UnlockedAchievementEntity(
                    achievementId = "TARGET_WEIGHT_REACHED",
                    unlockedAt = 22L,
                    celebratedAt = 23L,
                    triggerClientWorkoutId = null
                )
            )
        )
        database.achievementDao().insertEvents(
            listOf(
                app.mymusclemap.data.local.ProgressEventEntity(
                    dedupeKey = "weight-goal:1:reached",
                    kind = "WEIGHT_GOAL_MILESTONE",
                    payload = "REACHED",
                    occurredAt = 22L,
                    celebratedAt = 23L,
                    triggerClientWorkoutId = null
                )
            )
        )
        val exported = ByteArrayOutputStream()
        backup().exportArchive(source(), exported)
        database.targetWeightGoalDao().retire(1L, retiredAt = 99L, updatedAt = 99L)
        database.achievementDao().deleteUnlocks(listOf("TARGET_WEIGHT_REACHED"))
        database.achievementDao().deleteEvents(listOf("weight-goal:1:reached"))
        val restored = backup().restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupRestoreResult.Success, restored)
        val goal = database.targetWeightGoalDao().active()!!
        assertEquals(1L, goal.id)
        assertEquals(78.0, goal.targetKg, 0.0)
        assertEquals(87.0, goal.baselineKg)
        assertEquals("LOSS", goal.direction)
        assertNull(goal.retiredAt)
        assertEquals(22L, database.achievementDao().unlocks().single().unlockedAt)
        assertEquals("weight-goal:1:reached", database.achievementDao().events().single().dedupeKey)
    }

    @Test
    fun archiveRoundTripPreservesAnEarnedStreakAndRestoredHistoryCanQualifyAgain() = runTest {
        database.weeklyWorkoutGoalDao().insertAll(
            listOf(
                app.mymusclemap.data.local.WeeklyWorkoutGoalEntity(
                    effectiveWeekStart = today.toString(),
                    workoutsPerWeek = 1,
                    graceWeek = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
        repeat(4) { week ->
            insertCompleted("streak-$week", workoutDate = today.plusWeeks(week.toLong()))
        }
        val exported = ByteArrayOutputStream()
        backup().exportArchive(source(), exported)
        val later = AchievementRepository(
            database,
            clock,
            FixedDateProvider(today.plusWeeks(3).plusDays(6))
        )
        val restored = backup(onAfterRestore = { later.reconcile() })
            .restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupRestoreResult.Success, restored)
        val bronze = database.achievementDao().unlocks()
            .single { it.achievementId == "WEEKLY_GOAL_STREAK_4" }
        assertEquals(
            today.plusWeeks(3).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            bronze.unlockedAt
        )
    }

    @Test
    fun olderBackupRestoresAndReconcilesHistoryWithoutIndividualCelebrations() = runTest {
        repeat(10) { insertCompleted("old-$it") }
        achievements.reconcile()
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        val exported = ByteArrayOutputStream()
        backup().exportArchive(source(), exported)
        val legacy = rewriteAsSchema9(exported.toByteArray())
        database.achievementDao().insertUnlocks(
            listOf(
                app.mymusclemap.data.local.UnlockedAchievementEntity(
                    achievementId = "WORKOUTS_200",
                    unlockedAt = 1L,
                    celebratedAt = null,
                    triggerClientWorkoutId = "stale"
                )
            )
        )
        val restored = backup(onAfterRestore = { achievements.reconcile() })
            .restore(ByteArrayInputStream(legacy))
        assertEquals(AppBackupRestoreResult.Success, restored)
        val board = achievements.board()
        assertTrue(board.items.single { it.id == app.mymusclemap.domain.achievements.AchievementId.WORKOUTS_5 }.unlocked)
        assertTrue(board.items.single { it.id == app.mymusclemap.domain.achievements.AchievementId.WORKOUTS_10 }.unlocked)
        assertTrue(board.items.single { it.id == app.mymusclemap.domain.achievements.AchievementId.FIRST_WORKOUT }.unlocked)
        assertEquals(
            2L,
            board.items.single { it.id == app.mymusclemap.domain.achievements.AchievementId.FIRST_WORKOUT }.unlockedAt
        )
        assertTrue(board.items.none { it.id == app.mymusclemap.domain.achievements.AchievementId.WORKOUTS_200 && it.unlocked })
        assertTrue(
            database.achievementDao().unlocks()
                .filter { it.achievementId.startsWith("WORKOUTS_") }
                .all { it.celebratedAt != null }
        )
        assertTrue(board.pending.filterIsInstance<PendingCelebration.HistoryRecognized>().size == 1)
        assertTrue(
            board.pending.filterIsInstance<PendingCelebration.JourneyUnlocked>()
                .single().achievementId == app.mymusclemap.domain.achievements.AchievementId.FIRST_WORKOUT
        )
        assertEquals(AchievementStateEntity.SINGLETON_ID, database.achievementDao().state()!!.id)
        assertTrue(database.achievementDao().state()!!.initialized)
    }

    private fun backup(onAfterRestore: suspend () -> Unit = {}): AppBackupRepository {
        return AppBackupRepository(
            database,
            preferences,
            progressPhotoStore = store,
            onAfterRestore = onAfterRestore,
            instantSource = { Instant.parse("2026-10-06T00:00:00Z") }
        )
    }

    private fun source() = AppBackupSource("app.mymusclemap", "test")

    private suspend fun insertCompleted(clientId: String, workoutDate: LocalDate = today) {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = "COMPLETED",
                workoutDate = workoutDate.toString(),
                startedAt = 1L,
                finishedAt = 2L,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                activeLock = null,
                clientWorkoutId = java.util.UUID.nameUUIDFromBytes(clientId.toByteArray()).toString()
            )
        )
    }

    private fun rewriteAsSchema9(bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val body = input.readBytes()
                    val next = when (entry.name) {
                        AppBackupFormat.ARCHIVE_MANIFEST -> {
                            val manifest = JSONObject(body.toString(Charsets.UTF_8))
                            manifest.put("dataSchemaVersion", AppBackupFormat.ARCHIVE_PHOTO_SCHEMA_VERSION)
                            manifest.toString().toByteArray()
                        }
                        AppBackupFormat.ARCHIVE_DATA -> {
                            val data = JSONObject(body.toString(Charsets.UTF_8))
                            data.put("schemaVersion", AppBackupFormat.ARCHIVE_PHOTO_SCHEMA_VERSION)
                            val tables = data.getJSONObject("tables")
                            tables.remove(AppBackupFormat.TABLE_UNLOCKED_ACHIEVEMENTS)
                            tables.remove(AppBackupFormat.TABLE_PROGRESS_EVENTS)
                            tables.remove(AppBackupFormat.TABLE_ACHIEVEMENT_STATE)
                            data.toString().toByteArray()
                        }
                        else -> body
                    }
                    zip.putNextEntry(ZipEntry(entry.name))
                    zip.write(next)
                    zip.closeEntry()
                }
            }
        }
        return output.toByteArray()
    }
}
