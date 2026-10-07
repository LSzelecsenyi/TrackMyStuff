package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.entitlement.SpecialAchievementGrant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FounderEntitlementCacheTest {
    private var sessionUserId: String? = "account-a"
    private val cache = FounderEntitlementCache(
        context = ApplicationProvider.getApplicationContext<Context>(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        sessionUserId = { sessionUserId }
    )

    @Test
    fun logoutAndADifferentAccountCannotReadThePreviousSpecials() = runBlocking {
        val until = Instant.parse("2026-10-08T00:00:00Z")
        val early = Instant.parse("2026-01-15T00:00:00Z")
        cache.save(
            temporaryFounderPro = false,
            founderLifetime = true,
            validUntil = until,
            userId = "account-a",
            founderGrantedAt = Instant.parse("2024-03-01T00:00:00Z"),
            specialGrants = listOf(
                SpecialAchievementGrant("EARLY_ADOPTER", early),
                SpecialAchievementGrant("DEVELOPER", early)
            )
        )
        assertTrue(cache.current().founderLifetime)
        assertEquals(2, cache.current().specialGrants.size)

        sessionUserId = "account-b"
        assertFalse(cache.current().founderLifetime)
        assertTrue(cache.current().specialGrants.isEmpty())

        cache.retainAccount("account-b")
        assertNull(cache.current().userId)
        assertTrue(cache.current().specialGrants.isEmpty())
        sessionUserId = "account-a"
        assertFalse(cache.current().founderLifetime)
    }

    @Test
    fun rejectedBearerDropsTheTrustedCache() = runBlocking {
        cache.save(
            temporaryFounderPro = false,
            founderLifetime = false,
            validUntil = Instant.parse("2026-10-08T00:00:00Z"),
            userId = "account-a",
            specialGrants = listOf(SpecialAchievementGrant("DEVELOPER", Instant.parse("2026-02-01T00:00:00Z")))
        )
        cache.drop()
        assertTrue(cache.current().specialGrants.isEmpty())
        assertFalse(cache.current().founderLifetime)
    }
}
