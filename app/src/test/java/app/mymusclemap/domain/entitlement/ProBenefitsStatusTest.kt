package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProBenefitsStatusTest {
    private val expiresAt = Instant.parse("2027-06-01T12:00:00Z")

    @Test
    fun activeFounderProKeepsTheAuthoritativeExpiration() {
        val status = proBenefitsStatus(
            grantsPro = true,
            founderProActive = true,
            founderLifetime = false,
            founderProExpiresAt = expiresAt
        )
        assertTrue(status.proActive)
        assertEquals(expiresAt, status.expiresAt)
    }

    @Test
    fun expiredOrLifetimeAccessDoesNotInventADate() {
        assertNull(
            proBenefitsStatus(
                grantsPro = true,
                founderProActive = false,
                founderLifetime = false,
                founderProExpiresAt = expiresAt
            ).expiresAt
        )
        assertNull(
            proBenefitsStatus(
                grantsPro = true,
                founderProActive = true,
                founderLifetime = true,
                founderProExpiresAt = expiresAt
            ).expiresAt
        )
    }

    @Test
    fun freeAccessStaysInactive() {
        val status = proBenefitsStatus(
            grantsPro = false,
            founderProActive = false,
            founderLifetime = false,
            founderProExpiresAt = null
        )
        assertFalse(status.proActive)
        assertNull(status.expiresAt)
    }
}
