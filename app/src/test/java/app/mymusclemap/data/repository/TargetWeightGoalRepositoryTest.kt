package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.ProgressEventKind
import app.mymusclemap.domain.achievements.TargetWeightDirection
import app.mymusclemap.domain.achievements.TargetWeightProgressEvaluator
import app.mymusclemap.domain.achievements.WeightMilestone
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class TargetWeightGoalRepositoryTest {
    private val day = LocalDate.of(2026, 10, 6)
    private val instant = AtomicReference(Instant.parse("2026-10-06T12:00:00Z"))
    private val clock = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant.get()
    }
    private lateinit var database: WeightDatabase
    private lateinit var achievements: AchievementRepository
    private lateinit var weights: WeightRepository
    private lateinit var goals: TargetWeightGoalRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        achievements = AchievementRepository(database, clock, FixedDateProvider(day))
        weights = WeightRepository(database.weightMeasurementDao(), clock) { achievements.reconcile() }
        goals = TargetWeightGoalRepository(database.targetWeightGoalDao(), clock) { achievements.reconcile() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun creatingATargetSnapshotsTheLatestWeightAndLossDirection() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        val active = goals.active()!!
        assertEquals(78.0, active.targetKg, 0.0)
        assertEquals(87.0, active.baselineKg)
        assertEquals(TargetWeightDirection.LOSS, active.direction)
        assertTrue(database.achievementDao().events().isEmpty())
    }

    @Test
    fun creatingATargetWithoutAWeightWaitsForTheFirstMeasurement() = runTest {
        goals.setTarget(78.0, latestWeightKg = null)
        val waiting = goals.active()!!
        assertNull(waiting.baselineKg)
        assertNull(waiting.direction)
        weights.save(day, 70.0)
        val established = goals.active()!!
        assertEquals(waiting.id, established.id)
        assertEquals(70.0, established.baselineKg)
        assertEquals(TargetWeightDirection.GAIN, established.direction)
        assertTrue(database.achievementDao().events().isEmpty())
    }

    @Test
    fun anEqualBaselineReachesImmediatelyAndAMatchingTargetDoesNotStartANewGoal() = runTest {
        weights.save(day, 78.0)
        goals.setTarget(78.0, latestWeightKg = 78.0)
        val first = goals.active()!!
        assertEquals(TargetWeightDirection.ALREADY_THERE, first.direction)
        val unlock = database.achievementDao().unlocks().single()
        assertEquals(AchievementId.TARGET_WEIGHT_REACHED.name, unlock.achievementId)
        goals.setTarget(78.04, latestWeightKg = 78.0)
        assertEquals(first.id, goals.active()!!.id)
    }

    @Test
    fun lossAndGainJourneysReachAndOnlyTheFirstUnlocksOnTarget() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        weights.save(day.plusDays(1), 78.0)
        val lossGoal = goals.active()!!
        val firstUnlock = database.achievementDao().unlocks().single()
        assertEquals(AchievementId.TARGET_WEIGHT_REACHED.name, firstUnlock.achievementId)
        val pending = achievements.board().pending.filterIsInstance<PendingCelebration.TargetWeightMilestone>()
        assertEquals(listOf(WeightMilestone.REACHED), pending.map { it.milestone })
        assertTrue(pending.single().includesLifetimeUnlock)
        val recorded = database.achievementDao().events().map { it.dedupeKey }.toSet()
        assertEquals(
            setOf("halfway", "remaining-5", "remaining-2", "remaining-1", "reached").map {
                "weight-goal:${lossGoal.id}:$it"
            }.toSet(),
            recorded
        )
        assertEquals(
            1,
            database.achievementDao().events().count { it.celebratedAt == null }
        )
        achievements.acknowledge(pending.map { it.acknowledgement })
        assertTrue(achievements.board().pending.isEmpty())

        instant.set(instant.get().plusSeconds(60))
        goals.setTarget(82.0, latestWeightKg = 78.0)
        val gainGoal = goals.active()!!
        assertTrue(gainGoal.id != lossGoal.id)
        assertEquals(TargetWeightDirection.GAIN, gainGoal.direction)
        weights.save(day.plusDays(2), 82.0)
        assertEquals(firstUnlock.unlockedAt, database.achievementDao().unlocks().single().unlockedAt)
        assertEquals(1, database.achievementDao().unlocks().size)
        val second = achievements.board().pending.filterIsInstance<PendingCelebration.TargetWeightMilestone>().single()
        assertEquals(WeightMilestone.REACHED, second.milestone)
        assertFalse(second.includesLifetimeUnlock)
        assertTrue(second.acknowledgement.progressEventKey!!.contains(gainGoal.id.toString()))
    }

    @Test
    fun aLaterMeasurementDoesNotChangeDirectionOrRevokeTheAward() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        val id = goals.active()!!.id
        weights.save(day.plusDays(1), 76.0)
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        val unlockedAt = database.achievementDao().unlocks().single().unlockedAt
        weights.save(day.plusDays(2), 90.0)
        val after = goals.active()!!
        assertEquals(id, after.id)
        assertEquals(TargetWeightDirection.LOSS, after.direction)
        assertEquals(87.0, after.baselineKg)
        assertEquals(unlockedAt, database.achievementDao().unlocks().single().unlockedAt)
        assertTrue(achievements.board().pending.none { it is PendingCelebration.TargetWeightMilestone })
    }

    @Test
    fun replacingOrRemovingATargetRetiresTheOldJourney() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        weights.save(day.plusDays(1), 82.0)
        val first = goals.active()!!.id
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        goals.setTarget(75.0, latestWeightKg = 82.0)
        val second = goals.active()!!
        assertTrue(second.id != first)
        assertEquals(82.0, second.baselineKg)
        assertTrue(
            database.achievementDao().events().any {
                it.dedupeKey.startsWith("weight-goal:$first:") && it.celebratedAt != null
            }
        )
        goals.clear()
        assertNull(goals.active())
        weights.save(day.plusDays(2), 70.0)
        assertTrue(
            database.achievementDao().events().none { it.dedupeKey.startsWith("weight-goal:${second.id}:") }
        )
    }

    @Test
    fun twoKilogramMessageIsCreatedOnceAcrossAFluctuation() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        weights.save(day.plusDays(1), 80.1)
        weights.save(day.plusDays(2), 79.9)
        val goalId = goals.active()!!.id
        val key = TargetWeightProgressEvaluator.dedupeKey(goalId, WeightMilestone.REMAINING_2)
        assertEquals(1, database.achievementDao().events().count { it.dedupeKey == key })
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        weights.save(day.plusDays(3), 80.2)
        weights.save(day.plusDays(4), 79.8)
        assertEquals(1, database.achievementDao().events().count { it.dedupeKey == key })
        assertTrue(achievements.board().pending.none { it.acknowledgement.progressEventKey == key })
    }

    @Test
    fun editingAwayAnUnseenCrossingRemovesItAndACelebratedOneStays() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        val goalId = goals.active()!!.id
        weights.save(day.plusDays(1), 79.9)
        val twoKg = TargetWeightProgressEvaluator.dedupeKey(goalId, WeightMilestone.REMAINING_2)
        assertTrue(database.achievementDao().events().any { it.dedupeKey == twoKg && it.celebratedAt == null })
        weights.save(day.plusDays(1), 84.0)
        assertTrue(database.achievementDao().events().none { it.dedupeKey == twoKg })
        weights.save(day.plusDays(1), 79.9)
        achievements.acknowledge(achievements.board().pending.map { it.acknowledgement })
        weights.save(day.plusDays(1), 84.0)
        val kept = database.achievementDao().events().single { it.dedupeKey == twoKg }
        assertTrue(kept.celebratedAt != null)
    }

    @Test
    fun deletingTheWeighInThatReachedTheTargetDropsTheUnseenEventButNotTheAward() = runTest {
        weights.save(day, 87.0)
        goals.setTarget(78.0, latestWeightKg = 87.0)
        weights.save(day.plusDays(1), 78.0)
        val reached = database.achievementDao().events().single {
            it.kind == ProgressEventKind.WEIGHT_GOAL_MILESTONE.name && it.payload == WeightMilestone.REACHED.name
        }
        assertNull(reached.celebratedAt)
        val unlockedAt = database.achievementDao().unlocks().single().unlockedAt
        val id = weights.all().single { it.date == day.plusDays(1) }.id
        weights.delete(id)
        assertTrue(
            database.achievementDao().events().none { it.dedupeKey.endsWith(":reached") && it.celebratedAt == null }
        )
        assertEquals(unlockedAt, database.achievementDao().unlocks().single().unlockedAt)
        val restarted = AchievementRepository(database, clock, FixedDateProvider(day))
        restarted.reconcile()
        assertEquals(unlockedAt, database.achievementDao().unlocks().single().unlockedAt)
        assertTrue(restarted.board().items.single { it.id == AchievementId.TARGET_WEIGHT_REACHED }.unlocked)
    }

    @Test
    fun startupReconcileIsIdempotent() = runTest {
        weights.save(day, 70.0)
        goals.setTarget(78.0, latestWeightKg = 70.0)
        weights.save(day.plusDays(1), 78.0)
        val before = database.achievementDao().events().map { it.dedupeKey to it.celebratedAt }
        achievements.reconcile()
        achievements.reconcile()
        assertEquals(before, database.achievementDao().events().map { it.dedupeKey to it.celebratedAt })
    }
}
