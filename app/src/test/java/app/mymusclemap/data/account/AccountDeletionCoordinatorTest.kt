package app.mymusclemap.data.account

import app.mymusclemap.data.auth.AccountDeletionCall
import app.mymusclemap.data.auth.GoogleIdTokenRequest
import app.mymusclemap.data.auth.GoogleIdTokenValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDeletionCoordinatorTest {

    @Test
    fun offlineDoesNotDeleteAnything() = runBlocking {
        val harness = Harness(online = false)
        assertEquals(AccountDeletionOutcome.NeedsNetwork, harness.coordinator.delete(USER))
        assertEquals(0, harness.authenticatedCalls)
        assertEquals(0, harness.publicCalls)
        assertFalse(harness.erased)
    }

    @Test
    fun aServerFailureLeavesLocalData() = runBlocking {
        val harness = Harness(authenticated = AccountDeletionCall.Failed)
        assertEquals(AccountDeletionOutcome.Failed, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    @Test
    fun successErasesOnlyAfterTheServerAgrees() = runBlocking {
        val harness = Harness()
        assertEquals(AccountDeletionOutcome.Deleted, harness.coordinator.delete(USER))
        assertTrue(harness.erased)
        assertEquals(USER, harness.erasedUser)
    }

    @Test
    fun localCleanupFailureIsExplicitAndRetryable() = runBlocking {
        val harness = Harness(
            google = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("fresh-token"), "owner@example.com"),
            erase = false
        )
        val first = harness.coordinator.delete(USER)
        assertEquals(AccountDeletionOutcome.LocalCleanupFailed(USER), first)
        harness.erase = true
        harness.authenticated = AccountDeletionCall.Unauthenticated
        harness.publicResult = AccountDeletionCall.Deleted
        assertEquals(
            AccountDeletionOutcome.Deleted,
            harness.coordinator.delete(USER, "owner@example.com")
        )
        assertTrue(harness.erased)
    }

    @Test
    fun aMissingSessionUsesTheVerifiedPublicDeletion() = runBlocking {
        val harness = Harness(
            google = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("fresh-token"), "owner@example.com"),
            authenticated = AccountDeletionCall.Unauthenticated
        )
        assertEquals(AccountDeletionOutcome.Deleted, harness.coordinator.delete(USER, "owner@example.com"))
        assertEquals(1, harness.publicCalls)
        assertTrue(harness.erased)
    }

    @Test
    fun givenNoSessionAndNoMatchingEmail_whenDeletionIsRequested_thenAnotherAccountIsNotDeleted() = runBlocking {
        val harness = Harness(authenticated = AccountDeletionCall.Unauthenticated)
        assertEquals(AccountDeletionOutcome.WrongAccount, harness.coordinator.delete(USER, "owner@example.com"))
        assertEquals(0, harness.publicCalls)
        assertFalse(harness.erased)
    }

    @Test
    fun cancellationDoesNotCallTheServer() = runBlocking {
        val harness = Harness(google = GoogleIdTokenRequest.Cancelled)
        assertEquals(AccountDeletionOutcome.Cancelled, harness.coordinator.delete(USER))
        assertEquals(0, harness.authenticatedCalls)
        assertEquals(0, harness.publicCalls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheSignedInGoogleAccount_whenReauthenticationSucceeds_thenTheAccountIsDeleted() = runBlocking {
        val harness = Harness(
            google = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("fresh-token"), "owner@example.com")
        )
        assertEquals(
            AccountDeletionOutcome.Deleted,
            harness.coordinator.delete(USER, "owner@example.com")
        )
        assertEquals(1, harness.authenticatedCalls)
        assertEquals(0, harness.publicCalls)
        assertTrue(harness.erased)
    }

    @Test
    fun givenAnotherGoogleAccountAndNoSession_whenDeletionIsRequested_thenThePublicEndpointIsNotCalled() =
        runBlocking {
            val harness = Harness(
                google = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("other-token"), "other@example.com"),
                authenticated = AccountDeletionCall.Unauthenticated
            )
            assertEquals(
                AccountDeletionOutcome.WrongAccount,
                harness.coordinator.delete(USER, "owner@example.com")
            )
            assertEquals(0, harness.publicCalls)
            assertFalse(harness.erased)
        }

    @Test
    fun givenTheServerRejectsADifferentGoogleAccount_whenDeletionIsRequested_thenNothingLocalIsRemoved() =
        runBlocking {
            val harness = Harness(
                google = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("other-token"), "other@example.com"),
                authenticated = AccountDeletionCall.AccountMismatch
            )
            assertEquals(
                AccountDeletionOutcome.WrongAccount,
                harness.coordinator.delete(USER, "owner@example.com")
            )
            assertEquals(0, harness.publicCalls)
            assertFalse(harness.erased)
        }

    @Test
    fun givenTheUserCancelsAccountSelection_whenDeletionIsRequested_thenTheServerIsNotCalled() = runBlocking {
        val harness = Harness(google = GoogleIdTokenRequest.Cancelled)
        assertEquals(AccountDeletionOutcome.Cancelled, harness.coordinator.delete(USER, "owner@example.com"))
        assertEquals(0, harness.authenticatedCalls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenAnExpiredOrInvalidGoogleToken_whenDeletionIsRequested_thenLocalDataStays() = runBlocking {
        val expired = Harness(authenticated = AccountDeletionCall.ReauthenticationRequired)
        assertEquals(AccountDeletionOutcome.ReauthenticationRequired, expired.coordinator.delete(USER))
        assertFalse(expired.erased)
        assertEquals(0, expired.publicCalls)

        val invalid = Harness(authenticated = AccountDeletionCall.InvalidGoogleToken)
        assertEquals(AccountDeletionOutcome.ReauthenticationRequired, invalid.coordinator.delete(USER))
        assertFalse(invalid.erased)
        assertEquals(0, invalid.publicCalls)
    }

    @Test
    fun givenTheServerRejectsDeletion_whenTheCallFinishes_thenLocalDataStays() = runBlocking {
        val harness = Harness(authenticated = AccountDeletionCall.Failed)
        assertEquals(AccountDeletionOutcome.Failed, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerConfirmsDeletion_whenLocalCleanupFails_thenSuccessIsNotReported() = runBlocking {
        val harness = Harness(erase = false)
        assertEquals(AccountDeletionOutcome.LocalCleanupFailed(USER), harness.coordinator.delete(USER))
        assertEquals(1, harness.authenticatedCalls)
        assertFalse(harness.erased)
    }

    @Test
    fun givenTheServerHasNotConfirmed_whenDeletionFails_thenLocalCleanupDoesNotRun() = runBlocking {
        val harness = Harness(authenticated = AccountDeletionCall.NotFound)
        assertEquals(AccountDeletionOutcome.NotFound, harness.coordinator.delete(USER))
        assertFalse(harness.erased)
    }

    private class Harness(
        online: Boolean = true,
        google: GoogleIdTokenRequest = GoogleIdTokenRequest.Issued(GoogleIdTokenValue("fresh-token")),
        var authenticated: AccountDeletionCall = AccountDeletionCall.Deleted,
        var publicResult: AccountDeletionCall = AccountDeletionCall.Deleted,
        var erase: Boolean = true
    ) {
        var authenticatedCalls = 0
        var publicCalls = 0
        var erased = false
        var erasedUser: String? = null
        val coordinator = AccountDeletionCoordinator(
            requestIdToken = { google },
            deleteAuthenticated = {
                authenticatedCalls += 1
                authenticated
            },
            deletePublic = {
                publicCalls += 1
                publicResult
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
    }
}
