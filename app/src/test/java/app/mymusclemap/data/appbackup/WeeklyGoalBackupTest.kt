package app.mymusclemap.data.appbackup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WeekVerdict
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
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
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WeeklyGoalBackupTest {
    private lateinit var sourceDb: WeightDatabase
    private lateinit var targetDb: WeightDatabase
    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = openDb(context, "goal-backup-source")
        targetDb = openDb(context, "goal-backup-target")
        themePreferences = ThemePreferences(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun goalHistoryIncludingAPendingChangeAndADisabledGapRoundTrips() = runTest {
        val tables = emptyTables().copy(
            weightMeasurements = listOf(WeightMeasurementEntity(1, "2026-03-01", 81.0, 1, 1)),
            weeklyWorkoutGoals = listOf(
                goal(1, "2026-01-05", 3, graceWeek = false),
                goal(2, "2026-03-02", null, graceWeek = false),
                goal(3, "2026-03-09", 4, graceWeek = false),
                goal(4, "2026-03-16", 5, graceWeek = false)
            ),
            workoutSessions = sessions()
        )
        sourceDb.appBackupDao().replaceAll(tables)
        val json = AppBackupRepository(sourceDb, themePreferences) {
            Instant.parse("2026-03-12T18:00:00Z")
        }.exportJson(AppBackupSource("app.mymusclemap.debug", "1.0-debug"))
        assertTrue(json.contains("weekly_workout_goals"))

        targetDb.appBackupDao().replaceAll(
            emptyTables().copy(weightMeasurements = listOf(WeightMeasurementEntity(9, "2020-01-01", 70.0, 1, 1)))
        )
        val restored = AppBackupRepository(targetDb, themePreferences).restoreJson(json)
        assertEquals(AppBackupRestoreResult.Success, restored)

        val loaded = targetDb.appBackupDao().loadTables()
        assertEquals(tables.weeklyWorkoutGoals, loaded.weeklyWorkoutGoals)
        assertEquals(tables.workoutSessions.map { it.id }, loaded.workoutSessions.map { it.id })
        assertEquals(81.0, loaded.weightMeasurements.single().weightKg, 0.0)

        val before = evaluate(tables.weeklyWorkoutGoals, tables.workoutSessions)
        val after = evaluate(loaded.weeklyWorkoutGoals, loaded.workoutSessions)
        assertEquals(before.weeks.mapValues { it.value.streak }, after.weeks.mapValues { it.value.streak })
        assertEquals(4, after.current.goal)
        assertEquals(2, after.current.completed)
        assertEquals(0, after.current.streak)
        assertEquals(WeekVerdict.InProgress, after.current.verdict)
        assertEquals(5, after.pending?.let { (it as app.mymusclemap.domain.workout.PendingWeeklyGoal.Update).workoutsPerWeek })
        assertEquals(2, after.progressOn(LocalDate.of(2026, 1, 12)).streak)
        assertEquals(WeekVerdict.Missed, after.progressOn(LocalDate.of(2026, 1, 19)).verdict)
        assertEquals(WeekVerdict.NoGoal, after.progressOn(LocalDate.of(2026, 3, 2)).verdict)
        assertEquals(0, after.progressOn(LocalDate.of(2026, 3, 2)).streak)
    }

    @Test
    fun backupWithoutWeeklyGoalsRestoresAsNoGoal() = runTest {
        val original = emptySnapshot().copy(
            tables = emptyTables().copy(
                weightMeasurements = listOf(WeightMeasurementEntity(3, "2026-02-02", 79.5, 4, 5)),
                workoutSessions = listOf(session(1, "2026-02-02", SessionStatus.COMPLETED))
            )
        )
        val root = JSONObject(AppBackupJson.encode(original))
        root.getJSONObject("tables").remove(AppBackupFormat.TABLE_WEEKLY_WORKOUT_GOALS)
        val parsed = AppBackupJson.parse(root.toString()) as AppBackupParseResult.Success
        assertTrue(parsed.snapshot.tables.weeklyWorkoutGoals.isEmpty())
        assertEquals(79.5, parsed.snapshot.tables.weightMeasurements.single().weightKg, 0.0)

        val restored = AppBackupRepository(targetDb, themePreferences).restoreJson(root.toString())
        assertEquals(AppBackupRestoreResult.Success, restored)
        val loaded = targetDb.appBackupDao().loadTables()
        assertTrue(loaded.weeklyWorkoutGoals.isEmpty())
        assertEquals("2026-02-02", loaded.workoutSessions.single().workoutDate)
        val counts = mapOf(LocalDate.of(2026, 2, 2) to 1)
        val status = WeeklyGoalLogic.evaluate(emptyList(), counts, LocalDate.of(2026, 2, 5))
        assertNull(status.current.goal)
        assertEquals(WeekVerdict.NoGoal, status.current.verdict)
        assertEquals(0, status.current.streak)
    }

    private fun evaluate(
        goals: List<WeeklyWorkoutGoalEntity>,
        sessions: List<WorkoutSessionEntity>
    ) = WeeklyGoalLogic.evaluate(
        goals.map { row ->
            WeeklyGoalRevision(
                effectiveWeekStart = LocalDate.parse(row.effectiveWeekStart),
                workoutsPerWeek = row.workoutsPerWeek,
                graceWeek = row.graceWeek
            )
        },
        sessions.filter { it.status == SessionStatus.COMPLETED.name }
            .groupingBy { LocalDate.parse(it.workoutDate) }
            .eachCount(),
        LocalDate.of(2026, 3, 12)
    )

    private fun sessions(): List<WorkoutSessionEntity> {
        val dates = listOf(
            "2026-01-05", "2026-01-07", "2026-01-09",
            "2026-01-12", "2026-01-14", "2026-01-16",
            "2026-01-19",
            "2026-03-03",
            "2026-03-10", "2026-03-11"
        ).mapIndexed { index, date -> session(index + 1L, date, SessionStatus.COMPLETED) }
        return dates + session(100, "2026-03-11", SessionStatus.ABANDONED)
    }

    private fun session(id: Long, date: String, status: SessionStatus): WorkoutSessionEntity {
        return WorkoutSessionEntity(
            id = id,
            templateId = null,
            templateName = "Session",
            status = status.name,
            workoutDate = date,
            startedAt = 1L,
            finishedAt = if (status == SessionStatus.COMPLETED) 2L else null,
            abandonedAt = if (status == SessionStatus.ABANDONED) 2L else null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.UNKNOWN.name,
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L,
            activeLock = null
        )
    }

    private fun goal(id: Long, monday: String, workouts: Int?, graceWeek: Boolean): WeeklyWorkoutGoalEntity {
        return WeeklyWorkoutGoalEntity(
            id = id,
            effectiveWeekStart = monday,
            workoutsPerWeek = workouts,
            graceWeek = graceWeek,
            createdAt = id,
            updatedAt = id
        )
    }

    private fun openDb(context: Context, name: String): WeightDatabase {
        context.deleteDatabase(name)
        return Room.databaseBuilder(context, WeightDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
    }
}
