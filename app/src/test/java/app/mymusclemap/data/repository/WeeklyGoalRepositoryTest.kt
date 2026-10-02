package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PendingWeeklyGoal
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeeklyGoalRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-03-12T08:00:00Z"), ZoneOffset.UTC)
    private val thursday = LocalDate.of(2026, 3, 12)
    private val thisMonday = LocalDate.of(2026, 3, 9)
    private val nextMonday = LocalDate.of(2026, 3, 16)
    private lateinit var database: WeightDatabase
    private lateinit var repository: WeeklyGoalRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WeeklyGoalRepository(database.weeklyWorkoutGoalDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun goalHistorySurvivesRestartAndPendingEditsReplaceTheNextMonday() = runTest {
        repository.setGoal(thursday, 3)
        var history = repository.observe().first()
        assertEquals(1, history.size)
        assertEquals(thisMonday, history.single().effectiveWeekStart)
        assertTrue(history.single().graceWeek)
        assertEquals(3, history.single().workoutsPerWeek)

        repository.setGoal(thursday, 5)
        repository.setGoal(thursday, 6)
        history = repository.observe().first()
        assertEquals(listOf(3, 6), history.map { it.workoutsPerWeek })
        assertEquals(listOf(thisMonday, nextMonday), history.map { it.effectiveWeekStart })

        repository.setGoal(thursday, 3)
        history = repository.observe().first()
        assertEquals(listOf(3), history.map { it.workoutsPerWeek })

        repository.setGoal(thursday, 5)
        repository.disable(thursday)
        history = repository.observe().first()
        assertNull(history.last().workoutsPerWeek)
        assertEquals(nextMonday, history.last().effectiveWeekStart)

        val reopened = WeeklyGoalRepository(database.weeklyWorkoutGoalDao(), clock)
        val status = WeeklyGoalLogic.evaluate(reopened.observe().first(), emptyMap(), thursday)
        assertEquals(3, status.current.goal)
        assertTrue(status.pending is PendingWeeklyGoal.Disable)
        assertEquals(0, database.weeklyWorkoutGoalDao().getAll().count { it.effectiveWeekStart == nextMonday.toString() && it.workoutsPerWeek != null })
    }

    @Test
    fun savingTheSameGoalDoesNotRewriteStoredRows() = runTest {
        repository.setGoal(thursday, 4)
        val first = database.weeklyWorkoutGoalDao().getAll().single()
        repository.setGoal(thursday, 4)
        val second = database.weeklyWorkoutGoalDao().getAll().single()
        assertEquals(first, second)
    }

    @Test
    fun onlyCompletedSessionsCountAndEachSessionCountsOnce() = runTest {
        insertSession(SessionStatus.COMPLETED, "2026-03-10")
        insertSession(SessionStatus.COMPLETED, "2026-03-10")
        insertSession(SessionStatus.COMPLETED, "2026-03-11")
        insertSession(SessionStatus.ABANDONED, "2026-03-11")
        insertSession(SessionStatus.IN_PROGRESS, "2026-03-12", activeLock = 1)
        val counts = database.workoutSessionDao().observeAllCompletedCounts().first()
            .associate { LocalDate.parse(it.date) to it.completedCount }
        assertEquals(2, counts[LocalDate.of(2026, 3, 10)])
        assertEquals(1, counts[LocalDate.of(2026, 3, 11)])
        assertNull(counts[LocalDate.of(2026, 3, 12)])

        repository.setGoal(thursday, 4)
        val status = WeeklyGoalLogic.evaluate(repository.observe().first(), counts, thursday)
        assertEquals(3, status.current.completed)
        assertEquals(0, status.current.streak)
    }

    @Test
    fun firstGoalUsesOnlyCompletedSessionsAndSurvivesANewRepository() = runTest {
        insertSession(SessionStatus.COMPLETED, "2026-03-02")
        insertSession(SessionStatus.COMPLETED, "2026-03-03")
        insertSession(SessionStatus.COMPLETED, "2026-03-04")
        insertSession(SessionStatus.COMPLETED, "2026-03-04")
        insertSession(SessionStatus.COMPLETED, "2026-03-05")
        insertSession(SessionStatus.ABANDONED, "2026-03-06")
        insertSession(SessionStatus.IN_PROGRESS, "2026-03-06", activeLock = 1)
        val aware = WeeklyGoalRepository(database.weeklyWorkoutGoalDao(), clock) {
            database.workoutSessionDao().earliestCompletedWorkoutDate()?.let(LocalDate::parse)
        }
        aware.setGoal(thursday, 4)
        val stored = aware.observe().first()
        assertEquals(LocalDate.of(2026, 3, 2), stored.first().effectiveWeekStart)
        assertEquals(false, stored.first().graceWeek)
        val counts = database.workoutSessionDao().observeAllCompletedCounts().first()
            .associate { LocalDate.parse(it.date) to it.completedCount }
        assertEquals(2, counts[LocalDate.of(2026, 3, 4)])
        assertNull(counts[LocalDate.of(2026, 3, 6)])
        val reopened = WeeklyGoalRepository(database.weeklyWorkoutGoalDao(), clock)
        val status = WeeklyGoalLogic.evaluate(reopened.observe().first(), counts, thursday)
        assertEquals(5, status.progressOn(LocalDate.of(2026, 3, 2)).completed)
        assertEquals(1, status.current.streak)
    }

    private suspend fun insertSession(status: SessionStatus, date: String, activeLock: Int? = null) {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
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
                activeLock = activeLock
            )
        )
    }
}
