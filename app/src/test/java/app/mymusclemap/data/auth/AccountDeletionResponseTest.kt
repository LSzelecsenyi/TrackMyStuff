package app.mymusclemap.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeletionResponseTest {

    @Test
    fun givenTheServerConfirmsDeletion_whenTheStatusIsRead_thenOnlyNoContentIsSuccess() {
        assertEquals(AccountDeletionCall.Deleted, accountDeletionCall(204, null))
        assertEquals(AccountDeletionCall.Rejected, accountDeletionCall(200, null))
    }

    @Test
    fun givenADifferentGoogleAccount_whenTheServerResponds_thenTheCallIsAMismatch() {
        assertEquals(
            AccountDeletionCall.AccountMismatch,
            accountDeletionCall(403, "ACCOUNT_MISMATCH")
        )
    }

    @Test
    fun givenAnExpiredGoogleToken_whenTheServerResponds_thenReauthenticationIsRequired() {
        assertEquals(
            AccountDeletionCall.ReauthenticationRequired,
            accountDeletionCall(401, "REAUTHENTICATION_REQUIRED")
        )
        assertEquals(
            AccountDeletionCall.InvalidGoogleToken,
            accountDeletionCall(401, "INVALID_GOOGLE_TOKEN")
        )
        assertEquals(
            AccountDeletionCall.Unauthenticated,
            accountDeletionCall(401, "UNAUTHENTICATED")
        )
    }

    @Test
    fun givenTheServerFails_whenTheStatusIsRead_thenDeletionIsNotSuccess() {
        assertEquals(AccountDeletionCall.Failed, accountDeletionCall(500, "INTERNAL_ERROR"))
        assertEquals(AccountDeletionCall.NotFound, accountDeletionCall(404, "NOT_FOUND"))
    }
}
