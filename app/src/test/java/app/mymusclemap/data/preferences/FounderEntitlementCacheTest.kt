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
    fun cachedFounderProCannotOutliveItsExpiration() = runBlocking {
        val expiresAt = Instant.parse("2027-03-15T18:45:01Z")
        cache.save(
            temporaryFounderPro = false,
            founderLifetime = false,
            validUntil = expiresAt.plusSeconds(86_400),
            userId = "account-a",
            founderGrantedAt = Instant.parse("2026-03-15T18:45:01Z"),
            founderRecognized = true,
            founderProExpiresAt = expiresAt
        )
        val stored = cache.current()
        assertTrue(stored.founderRecognized)
        assertEquals(expiresAt, stored.founderProExpiresAt)
        val before = app.mymusclemap.domain.entitlement.EntitlementResolver.resolve(
            app.mymusclemap.domain.entitlement.EntitlementSources.of(backendFounder = stored),
            expiresAt.minusNanos(1)
        )
        val atExpiry = app.mymusclemap.domain.entitlement.EntitlementResolver.resolve(
            app.mymusclemap.domain.entitlement.EntitlementSources.of(backendFounder = stored),
            expiresAt
        )
        assertTrue(before.grantsPro)
        assertTrue(before.founderRecognized)
        assertFalse(atExpiry.grantsPro)
        assertTrue(atExpiry.founderRecognized)
        assertFalse(atExpiry.founderLifetime)
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
