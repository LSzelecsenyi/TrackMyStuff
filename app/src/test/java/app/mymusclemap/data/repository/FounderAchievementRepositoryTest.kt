package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.PendingCelebration
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
    private var founderLifetime = false
    private var grantsPro = false
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
            grantsPro = { grantsPro },
            founderLifetime = { founderLifetime }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun founderLifetimePersistsOnceAndStaysAfterTheGrantEnds() = runTest {
        grantsPro = true
        repository.reconcile()
        assertNull(unlockOrNull())

        founderLifetime = true
        repository.reconcile()
        val unlockedAt = unlockOrNull()!!.unlockedAt
        assertEquals(now.toEpochMilli(), unlockedAt)
        assertEquals(now.toEpochMilli(), unlockOrNull()!!.celebratedAt)
        assertTrue(repository.board().pending.none { it is PendingCelebration.ProUnlocked })

        now = now.plusSeconds(3_600)
        repository.reconcile()
        assertEquals(unlockedAt, unlockOrNull()!!.unlockedAt)

        founderLifetime = false
        grantsPro = false
        now = now.plusSeconds(3_600)
        repository.reconcile()
        assertEquals(unlockedAt, unlockOrNull()!!.unlockedAt)
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.FOUNDER.name })

        founderLifetime = true
        grantsPro = true
        now = now.plusSeconds(3_600)
        repository.reconcile()
        assertEquals(unlockedAt, unlockOrNull()!!.unlockedAt)
        assertEquals(1, database.achievementDao().unlocks().count { it.achievementId == AchievementId.FOUNDER.name })
    }

    private suspend fun unlockOrNull() =
        database.achievementDao().unlocks().singleOrNull { it.achievementId == AchievementId.FOUNDER.name }
}
