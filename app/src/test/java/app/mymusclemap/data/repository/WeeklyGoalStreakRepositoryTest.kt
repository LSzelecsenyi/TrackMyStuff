package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.calendar.WeekCalendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class WeeklyGoalStreakRepositoryTest {
    private val start = WeekCalendar.start(LocalDate.of(2026, 10, 5))
    private val instant = AtomicReference(Instant.parse("2026-10-05T12:00:00Z"))
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant.get()
    }
    private val today = AtomicReference(start.plusDays(6))
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AchievementRepository(database, clock, MovingDate(today))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startupReconcileUnlocksTheTiersAlreadyProvedByHistory() = runTest {
        setGoal(start, 1)
        repeat(9) { week -> insertCompleted(start.plusWeeks(week.toLong())) }
        today.set(start.plusWeeks(8).plusDays(6))
        repository.reconcile()
        val unlocks = database.achievementDao().unlocks().map { it.achievementId }.toSet()
        assertTrue(AchievementId.WEEKLY_GOAL_STREAK_4.name in unlocks)
        assertTrue(AchievementId.WEEKLY_GOAL_STREAK_8.name in unlocks)
        assertTrue(AchievementId.WEEKLY_GOAL_STREAK_12.name !in unlocks)
        val pending = repository.board().pending.filterIsInstance<PendingCelebration.WeeklyStreakUnlocked>()
        assertEquals(listOf(AchievementId.WEEKLY_GOAL_STREAK_8), pending.map { it.achievementId })
        val original = database.achievementDao().unlocks()
            .single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_8.name }
            .unlockedAt
        instant.set(instant.get().plusSeconds(3600))
        repository.reconcile()
        assertEquals(
            original,
            database.achievementDao().unlocks()
                .single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_8.name }
                .unlockedAt
        )
    }

    @Test
    fun theFourthWeekCelebratesTheStreakAndKeepsTheWeeklyEventSilent() = runTest {
        setGoal(start, 1)
        repeat(3) { week -> insertCompleted(start.plusWeeks(week.toLong())) }
        today.set(start.plusWeeks(2).plusDays(6))
        repository.reconcile()
        repository.acknowledge(repository.board().pending.map { it.acknowledgement })
        insertCompleted(start.plusWeeks(3))
        today.set(start.plusWeeks(3).plusDays(6))
        repository.reconcile("week-4")
        val pending = repository.board().pending
        assertTrue(pending.none { it is PendingCelebration.WeeklyGoalCompleted })
        val streak = pending.filterIsInstance<PendingCelebration.WeeklyStreakUnlocked>().single()
        assertEquals(AchievementId.WEEKLY_GOAL_STREAK_4, streak.achievementId)
        val weekly = database.achievementDao().events()
            .single { it.dedupeKey.startsWith("weekly-workout-achieved:") && it.dedupeKey.endsWith(start.plusWeeks(3).toString()) }
        assertTrue(weekly.celebratedAt != null)
        repository.acknowledge(listOf(streak.acknowledgement))
        repository.reconcile()
        assertTrue(repository.board().pending.none { it is PendingCelebration.WeeklyStreakUnlocked })
        assertTrue(repository.board().pending.none { it is PendingCelebration.WeeklyGoalCompleted })
    }

    @Test
    fun missingALaterWeekLeavesTheEarnedStreaksInPlace() = runTest {
        setGoal(start, 1)
        repeat(4) { week -> insertCompleted(start.plusWeeks(week.toLong())) }
        today.set(start.plusWeeks(3).plusDays(6))
        repository.reconcile()
        val unlockedAt = database.achievementDao().unlocks()
            .single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_4.name }
            .unlockedAt
        today.set(start.plusWeeks(5))
        repository.reconcile()
        val row = database.achievementDao().unlocks()
            .single { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_4.name }
        assertEquals(unlockedAt, row.unlockedAt)
        assertEquals(4, repository.board().items.single { it.id == AchievementId.WEEKLY_GOAL_STREAK_8 }.countProgress!!.current)
        assertNull(database.achievementDao().unlocks().find { it.achievementId == AchievementId.WEEKLY_GOAL_STREAK_8.name })
    }

    private suspend fun setGoal(week: LocalDate, workouts: Int) {
        database.weeklyWorkoutGoalDao().insertAll(
            listOf(
                WeeklyWorkoutGoalEntity(
                    effectiveWeekStart = week.toString(),
                    workoutsPerWeek = workouts,
                    graceWeek = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
    }

    private suspend fun insertCompleted(date: LocalDate) {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = "COMPLETED",
                workoutDate = date.toString(),
                startedAt = 10L,
                finishedAt = 20L,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                activeLock = null,
                clientWorkoutId = "streak-$date"
            )
        )
    }
}

private class MovingDate(private val current: AtomicReference<LocalDate>) : DateProvider {
    override fun today(): LocalDate = current.get()
    override fun now(): LocalDateTime = current.get().atTime(LocalTime.NOON)
    override fun observeToday(): Flow<LocalDate> = flowOf(current.get())
}
