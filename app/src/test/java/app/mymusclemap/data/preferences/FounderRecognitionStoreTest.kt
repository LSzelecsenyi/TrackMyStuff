package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
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
class FounderRecognitionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var sessionUserId: String? = "account-a"
    private val grantedAt = Instant.parse("2026-03-15T18:45:01Z")

    @Test
    fun recognitionSurvivesRestartAndCacheExpiryWithoutGrantingPro() = runBlocking {
        store().clear()
        val store = store()
        store.confirm("account-a", grantedAt)
        assertTrue(store.current().recognized)
        assertEquals(grantedAt, store.current().grantedAt)

        val restarted = store()
        restarted.load()
        assertTrue(restarted.current().recognized)
        assertEquals(grantedAt, restarted.current().grantedAt)

        sessionUserId = null
        assertFalse(restarted.current().recognized)
        sessionUserId = "account-b"
        assertFalse(restarted.current().recognized)
        assertNull(restarted.current().grantedAt)
        sessionUserId = "account-a"
        assertTrue(restarted.current().recognized)
        assertEquals(grantedAt, restarted.current().grantedAt)

        val expiresAt = grantedAt.atZone(java.time.ZoneOffset.UTC).plusMonths(12).toInstant()
        val cached = BackendFounderEntitlement(
            founderRecognized = true,
            founderGrantedAt = grantedAt,
            founderProExpiresAt = expiresAt,
            validUntil = grantedAt.plusSeconds(60),
            userId = "account-a"
        )
        val afterTrust = EntitlementResolver.resolve(
            EntitlementSources.of(backendFounder = cached),
            cached.validUntil!!.plusSeconds(1)
        )
        assertEquals(EntitlementTier.Free, afterTrust.tier)
        assertFalse(afterTrust.grantsPro)
        assertFalse(afterTrust.founderRecognized)
        assertFalse(afterTrust.founderLifetime)
        assertTrue(restarted.current().recognized)
    }

    @Test
    fun aSecondAccountKeepsItsOwnRecognition() = runBlocking {
        store().clear()
        val store = store()
        store.confirm("account-a", grantedAt)
        val later = grantedAt.plusSeconds(86_400)
        store.confirm("account-b", later)
        sessionUserId = "account-b"
        assertEquals(later, store.current().grantedAt)
        sessionUserId = "account-a"
        assertEquals(grantedAt, store.current().grantedAt)
    }

    private fun store(): FounderRecognitionStore {
        return FounderRecognitionStore(
            context = context,
            sessionUserId = { sessionUserId }
        )
    }
}
