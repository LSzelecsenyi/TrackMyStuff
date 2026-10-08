package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.UnlockedAchievementEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.AccountAchievementAuthority
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
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

@RunWith(RobolectricTestRunner::class)
class FounderAchievementRepositoryTest {
    private var now = Instant.parse("2026-10-07T08:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
    private var authority = AccountAchievementAuthority()
    private lateinit var database: WeightDatabase
    private lateinit var repository: AchievementRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AchievementRepository(
            database = database,
            clock = clock,
            dateProvider = FixedDateProvider(LocalDate.of(2026, 10, 7)),
            grantsPro = { false },
            accountAuthority = { authority }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun founderFollowsTrustedAuthorityAndIgnoresALegacyRow() = runTest {
        database.achievementDao().insertUnlocks(
            listOf(
                UnlockedAchievementEntity(
                    achievementId = AchievementId.FOUNDER.name,
                    unlockedAt = 1L,
                    celebratedAt = 1L,
                    triggerClientWorkoutId = null
                )
            )
        )
        val ignored = repository.board().items.single { it.id == AchievementId.FOUNDER }
        assertFalse(ignored.unlocked)
        assertNull(ignored.unlockedAt)

        val grantedAt = Instant.parse("2024-03-01T00:00:00Z").toEpochMilli()
        authority = AccountAchievementAuthority(
            founderRecognized = true,
            founderGrantedAtMillis = grantedAt
        )
        repository.notifyEntitlementChanged()
        val earned = repository.board().items.single { it.id == AchievementId.FOUNDER }
        assertTrue(earned.unlocked)
        assertEquals(grantedAt, earned.unlockedAt)
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })

        now = now.plusSeconds(3_600)
        repository.reconcile()
        assertEquals(1L, unlockOrNull()!!.unlockedAt)
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.FOUNDER.name })

        authority = AccountAchievementAuthority()
        repository.notifyEntitlementChanged()
        assertFalse(repository.board().items.single { it.id == AchievementId.FOUNDER }.unlocked)
        assertEquals(1L, unlockOrNull()!!.unlockedAt)
    }

    private suspend fun unlockOrNull() =
        database.achievementDao().unlocks().singleOrNull { it.achievementId == AchievementId.FOUNDER.name }
}
