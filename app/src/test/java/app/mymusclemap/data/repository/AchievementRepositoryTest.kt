package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.WeeklyGoalCompletionEvaluator
import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
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

@RunWith(RobolectricTestRunner::class)
class AchievementRepositoryTest {
    private val monday = WeekCalendar.start(LocalDate.of(2026, 10, 7))
    private val dates = ShiftDate(monday)
    private val clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var context: Context
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AchievementRepository(database, clock, dates)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun historicalBackfillUnlocksCountAwardsWithoutIndividualCelebrations() = runTest {
        repeat(137) { index ->
            insertCompleted(date = monday.minusDays((index % 20).toLong()), clientId = "c-$index")
        }
        repository.reconcile()
        val board = repository.board()
        assertEquals(
            setOf(
                AchievementId.WORKOUTS_5,
                AchievementId.WORKOUTS_10,
                AchievementId.WORKOUTS_30,
                AchievementId.WORKOUTS_50,
                AchievementId.WORKOUTS_100,
                AchievementId.FIRST_WORKOUT
            ),
            board.items.filter { it.unlocked }.map { it.id }.toSet()
        )
        assertEquals(20L, board.items.single { it.id == AchievementId.FIRST_WORKOUT }.unlockedAt)
        assertEquals(137, board.items.single { it.id == AchievementId.WORKOUTS_200 }.countProgress!!.current)
        assertTrue(board.items.filter { it.unlocked }.all { it.unlockedAt != null })
        val workoutUnlocks = database.achievementDao().unlocks()
            .filter { AchievementId.fromStorage(it.achievementId)?.workoutThreshold != null }
        assertTrue(workoutUnlocks.all { it.celebratedAt != null })
        assertNull(
            database.achievementDao().unlocks()
                .single { it.achievementId == AchievementId.FIRST_WORKOUT.name }
                .celebratedAt
        )
        val history = board.pending.filterIsInstance<PendingCelebration.HistoryRecognized>().single()
        assertEquals(5, history.badgeCount)
        assertTrue(
            board.pending.filterIsInstance<PendingCelebration.JourneyUnlocked>()
                .single().achievementId == AchievementId.FIRST_WORKOUT
        )
        repository.reconcile()
        assertEquals(2, repository.board().pending.size)
        repository.acknowledge(repository.board().pending.map { it.acknowledgement })
        assertTrue(repository.board().pending.isEmpty())
        repository.reconcile()
        assertTrue(repository.board().pending.isEmpty())
    }

    @Test
    fun crossingFiftyAfterInitializationQueuesOneCelebrationThatSurvivesANewRepository() = runTest {
        repeat(49) { insertCompleted(date = monday, clientId = "before-$it") }
        repository.reconcile()
        repository.acknowledge(repository.board().pending.map { it.acknowledgement })
        insertCompleted(date = monday, clientId = "the-fiftieth")
        repository.reconcile("the-fiftieth")
        val pending = repository.board().pending.single()
        val award = pending as PendingCelebration.WorkoutCountUnlocked
        assertEquals(AchievementId.WORKOUTS_50, award.achievementId)
        assertEquals("the-fiftieth", award.triggerClientWorkoutId)
        val restarted = AchievementRepository(database, clock, dates)
        assertEquals(repository.board().pending, restarted.board().pending)
        restarted.acknowledge(listOf(award.acknowledgement))
        assertTrue(repository.board().pending.isEmpty())
        restarted.reconcile("the-fiftieth")
        assertTrue(restarted.board().pending.isEmpty())
    }

    @Test
    fun deletingBelowAThresholdRevokesItAndReachingItAgainCanCelebrateAgain() = runTest {
        val ids = (1..5).map { insertCompleted(date = monday, clientId = "w-$it") }
        repository.reconcile()
        repository.acknowledge(repository.board().pending.map { it.acknowledgement })
        assertTrue(repository.board().items.single { it.id == AchievementId.WORKOUTS_5 }.unlocked)
        database.workoutSessionDao().deleteSessionById(ids.last())
        repository.reconcile()
        assertTrue(repository.board().items.none { it.id == AchievementId.WORKOUTS_5 && it.unlocked })
        insertCompleted(date = monday, clientId = "w-again")
        repository.reconcile("w-again")
        val again = repository.board().pending.single() as PendingCelebration.WorkoutCountUnlocked
        assertEquals(AchievementId.WORKOUTS_5, again.achievementId)
    }

    @Test
    fun importedCompletedSessionsCountAndInProgressSessionsDoNot() = runTest {
        insertCompleted(date = monday, clientId = "native", fingerprint = null)
        insertCompleted(date = monday, clientId = "imported", fingerprint = "import-1")
        database.workoutSessionDao().insertSession(
            session(
                date = monday,
                clientId = "active",
                status = "IN_PROGRESS",
                activeLock = 1,
                finishedAt = null
            )
        )
        repository.reconcile()
        assertEquals(2, repository.board().completedWorkoutCount)
        assertTrue(repository.board().items.none { it.unlocked && it.id.workoutThreshold != null })
        assertTrue(repository.board().items.single { it.id == AchievementId.FIRST_WORKOUT }.unlocked)
    }

    @Test
    fun weeklyCompletionIsOneEventPerMondayAndANewMondayStartsAnother() = runTest {
        repository.reconcile()
        setGoal(monday, 1)
        insertCompleted(date = monday, clientId = "week-1")
        repository.reconcile("week-1")
        val first = repository.board().pending.filterIsInstance<PendingCelebration.WeeklyGoalCompleted>().single()
        assertEquals(1, first.completed)
        assertEquals(1, first.goal)
        assertEquals(
            WeeklyGoalCompletionEvaluator.dedupeKey(monday),
            first.acknowledgement.progressEventKey
        )
        insertCompleted(date = monday.plusDays(1), clientId = "extra")
        repository.reconcile("extra")
        assertEquals(
            1,
            repository.board().pending.filterIsInstance<PendingCelebration.WeeklyGoalCompleted>().size
        )
        dates.current = monday.plusWeeks(1)
        insertCompleted(date = dates.current, clientId = "week-2")
        repository.reconcile("week-2")
        val keys = database.achievementDao().events()
            .filter { it.kind == "WEEKLY_GOAL_COMPLETED" }
            .map { it.dedupeKey }
            .toSet()
        assertEquals(
            setOf(
                WeeklyGoalCompletionEvaluator.dedupeKey(monday),
                WeeklyGoalCompletionEvaluator.dedupeKey(monday.plusWeeks(1))
            ),
            keys
        )
    }

    @Test
    fun workoutDateKeepsASundaySessionInThatWeekWhenFinishMillisAreMonday() = runTest {
        val sunday = monday.plusDays(6)
        val nextMonday = sunday.plusDays(1)
        repository.reconcile()
        setGoal(monday, 1)
        val finish = nextMonday.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        insertCompleted(date = sunday, clientId = "late", finishedAt = finish)
        dates.current = nextMonday
        repository.reconcile("late")
        val keys = database.achievementDao().events()
            .filter { it.kind == "WEEKLY_GOAL_COMPLETED" }
            .map { it.dedupeKey }
        assertEquals(listOf(WeeklyGoalCompletionEvaluator.dedupeKey(monday)), keys)
    }

    @Test
    fun founderProgramStateIsLeftUntouched() = runTest {
        val store = FounderProgramStore(context)
        val before = store.load()
        val marked = FounderProgramState(
            status = FounderProgramStatus.ActiveFree,
            enrolledOn = monday,
            deadline = monday.plusDays(45),
            rejectionReason = "achievement-test-marker"
        )
        store.save(marked)
        try {
            insertCompleted(date = monday, clientId = "founder-check")
            repository.reconcile("founder-check")
            assertEquals(marked, store.load())
        } finally {
            store.save(before)
        }
    }

    private suspend fun setGoal(weekStart: LocalDate, workouts: Int) {
        database.weeklyWorkoutGoalDao().insertAll(
            listOf(
                WeeklyWorkoutGoalEntity(
                    effectiveWeekStart = weekStart.toString(),
                    workoutsPerWeek = workouts,
                    graceWeek = false,
                    createdAt = 1,
                    updatedAt = 1
                )
            )
        )
    }

    private suspend fun insertCompleted(
        date: LocalDate,
        clientId: String,
        fingerprint: String? = null,
        finishedAt: Long = 20L
    ): Long {
        return database.workoutSessionDao().insertSession(
            session(
                date = date,
                clientId = clientId,
                status = "COMPLETED",
                activeLock = null,
                finishedAt = finishedAt,
                fingerprint = fingerprint
            )
        )
    }

    private fun session(
        date: LocalDate,
        clientId: String,
        status: String,
        activeLock: Int?,
        finishedAt: Long?,
        fingerprint: String? = null
    ): WorkoutSessionEntity {
        return WorkoutSessionEntity(
            templateId = null,
            templateName = "Push",
            status = status,
            workoutDate = date.toString(),
            startedAt = 10L,
            finishedAt = finishedAt,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = "UNKNOWN",
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L,
            activeLock = activeLock,
            importFingerprint = fingerprint,
            clientWorkoutId = clientId
        )
    }
}

private class ShiftDate(initial: LocalDate) : DateProvider {
    var current: LocalDate = initial

    override fun today(): LocalDate = current

    override fun now(): LocalDateTime = current.atTime(LocalTime.NOON)

    override fun observeToday(): Flow<LocalDate> = flowOf(current)
}
