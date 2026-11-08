package app.mymusclemap.data.account

import app.mymusclemap.data.auth.AccountDeletionCall
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCoordinatorTest {

    @Test
    fun givenTheDeviceIsOffline_whenDeletionIsRequested_thenNothingIsCalled() = runBlocking {
        val harness = Harness(online = false)
        assertEquals(AccountDeletionOutcome.NeedsNetwork, harness.coordinator.delete(USER))
        assertEquals(0, harness.calls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheSignedInSession_whenTheServerConfirms_thenLocalDataIsRemoved() = runBlocking {
        val harness = Harness()
        assertEquals(AccountDeletionOutcome.Deleted, harness.coordinator.delete(USER))
        assertEquals(1, harness.calls)
        assertEquals(USER, harness.erasedUser)
        assertTrue(harness.erased)
    }

    @Test
    fun givenTheSessionBelongsToAnotherAccount_whenDeletionIsRequested_thenTheServerIsNotCalled() = runBlocking {
        val harness = Harness(sessionUserId = OTHER)
        assertEquals(AccountDeletionOutcome.Failed, harness.coordinator.delete(USER))
        assertEquals(0, harness.calls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheSessionIsMissing_whenDeletionIsRequested_thenNothingIsRemoved() = runBlocking {
        val harness = Harness(sessionUserId = null)
        assertEquals(AccountDeletionOutcome.ReauthenticationRequired, harness.coordinator.delete(USER))
        assertEquals(0, harness.calls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerSessionIsExpired_whenDeletionIsRequested_thenLocalDataStays() = runBlocking {
        val harness = Harness(result = AccountDeletionCall.Unauthenticated)
        assertEquals(AccountDeletionOutcome.ReauthenticationRequired, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerRejectsDeletion_whenTheCallFinishes_thenLocalDataStays() = runBlocking {
        val harness = Harness(result = AccountDeletionCall.Failed)
        assertEquals(AccountDeletionOutcome.Failed, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerConfirmsDeletion_whenLocalCleanupFails_thenSuccessIsNotReported() = runBlocking {
        val harness = Harness(erase = false)
        assertEquals(AccountDeletionOutcome.LocalCleanupFailed(USER), harness.coordinator.delete(USER))
        assertEquals(1, harness.calls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerHasNotConfirmed_whenDeletionFails_thenLocalCleanupDoesNotRun() = runBlocking {
        val harness = Harness(result = AccountDeletionCall.NotFound)
        assertEquals(AccountDeletionOutcome.NotFound, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    private class Harness(
        online: Boolean = true,
        sessionUserId: String? = USER,
        var result: AccountDeletionCall = AccountDeletionCall.Deleted,
        var erase: Boolean = true
    ) {
        var calls = 0
        var erased = false
        var erasedUser: String? = null
        val coordinator = AccountDeletionCoordinator(
            sessionUserId = { sessionUserId },
            deleteSignedIn = {
                calls += 1
                result
            },
            online = { online },
            afterServerDeletion = { userId ->
                erased = erase
                erasedUser = userId
                erase
            }
        )
    }

    private companion object {
        const val USER = "11111111-1111-1111-1111-111111111111"
        const val OTHER = "22222222-2222-2222-2222-222222222222"
    }
}
