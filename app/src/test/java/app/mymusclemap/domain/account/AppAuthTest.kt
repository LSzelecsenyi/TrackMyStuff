package app.mymusclemap.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AppAuthTest {
    private val now = Instant.parse("2026-10-08T12:00:00Z")
    private val user = "11111111-1111-1111-1111-111111111111"

    @Test
    fun firstLaunchRequiresSignIn() {
        val decision = resolveAppAuth(null, null, null, now, online = true)
        assertEquals(AppAuthState.SIGNED_OUT, decision.state)
        assertFalse(decision.offline)
    }

    @Test
    fun aValidSessionOpensTheApp() {
        val decision = resolveAppAuth(user, user, now.plusSeconds(60), now, online = true)
        assertEquals(AppAuthState.AUTHENTICATED, decision.state)
    }

    @Test
    fun anExpiredSessionOfflineKeepsTheVerifiedAccount() {
        val decision = resolveAppAuth(user, user, now.minusSeconds(1), now, online = false)
        assertEquals(AppAuthState.AUTHENTICATED, decision.state)
        assertTrue(decision.offline)
    }

    @Test
    fun anExpiredSessionOnlineAsksForSignInAgain() {
        val decision = resolveAppAuth(user, user, now, now, online = true)
        assertEquals(AppAuthState.AUTHENTICATION_REQUIRED, decision.state)
    }

    @Test
    fun theFirstSignInCannotProceedOffline() {
        val decision = resolveAppAuth(null, null, null, now, online = false)
        assertEquals(AppAuthState.SIGNED_OUT, decision.state)
        assertTrue(decision.offline)
    }

    @Test
    fun aDifferentSessionDoesNotOpenThePreviousAccount() {
        val decision = resolveAppAuth(user, "22222222-2222-2222-2222-222222222222", now.plusSeconds(60), now, true)
        assertEquals(AppAuthState.AUTHENTICATION_REQUIRED, decision.state)
    }

    @Test
    fun legacyDataIsClaimedOnceByTheFirstAccount() {
        assertEquals(LegacyClaim.MOVE, legacyClaim(true, null, user))
        assertEquals(LegacyClaim.ALREADY_MINE, legacyClaim(true, user, user))
        assertEquals(LegacyClaim.OWNED_BY_OTHER, legacyClaim(true, user, "22222222-2222-2222-2222-222222222222"))
        assertEquals(LegacyClaim.NOTHING_TO_CLAIM, legacyClaim(false, null, user))
        assertEquals(LegacyClaim.OWNED_BY_OTHER, legacyClaim(false, user, "22222222-2222-2222-2222-222222222222"))
        assertEquals("account_$user.db", accountDatabaseName(user))
    }
}
