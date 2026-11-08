package app.mymusclemap.data.account

import app.mymusclemap.data.auth.AccountDeletionCall

sealed interface AccountDeletionOutcome {
    data object Deleted : AccountDeletionOutcome
    data object NeedsNetwork : AccountDeletionOutcome
    data object Cancelled : AccountDeletionOutcome
    data object GoogleUnavailable : AccountDeletionOutcome
    data object ReauthenticationRequired : AccountDeletionOutcome
    data object WrongAccount : AccountDeletionOutcome
    data object NotFound : AccountDeletionOutcome
    data object Failed : AccountDeletionOutcome
    data object NoAccount : AccountDeletionOutcome
    data class LocalCleanupFailed(val userId: String) : AccountDeletionOutcome
}

/**
 * Deletes the signed-in account only after the backend confirms it.
 * The Strict session names the account. A Google account picker is not used.
 * A network or server failure does not remove local data.
 */
class AccountDeletionCoordinator(
    private val sessionUserId: () -> String?,
    private val deleteSignedIn: suspend () -> AccountDeletionCall,
    private val online: () -> Boolean,
    private val afterServerDeletion: suspend (String) -> Boolean
) {
    suspend fun delete(userId: String): AccountDeletionOutcome {
        if (!online()) {
            return AccountDeletionOutcome.NeedsNetwork
        }
        val sessionUser = sessionUserId()
        if (sessionUser == null) {
            return AccountDeletionOutcome.ReauthenticationRequired
        }
        if (sessionUser != userId) {
            return AccountDeletionOutcome.Failed
        }
        return when (val result = deleteSignedIn()) {
            AccountDeletionCall.Deleted ->
                if (afterServerDeletion(userId)) {
                    AccountDeletionOutcome.Deleted
                } else {
                    AccountDeletionOutcome.LocalCleanupFailed(userId)
                }
            AccountDeletionCall.Unauthenticated,
            AccountDeletionCall.ReauthenticationRequired,
            AccountDeletionCall.InvalidGoogleToken -> AccountDeletionOutcome.ReauthenticationRequired
            AccountDeletionCall.AccountMismatch -> AccountDeletionOutcome.WrongAccount
            AccountDeletionCall.NotFound -> AccountDeletionOutcome.NotFound
            AccountDeletionCall.Unavailable ->
                if (online()) AccountDeletionOutcome.Failed else AccountDeletionOutcome.NeedsNetwork
            AccountDeletionCall.Rejected,
            AccountDeletionCall.Failed -> AccountDeletionOutcome.Failed
        }
    }
}
