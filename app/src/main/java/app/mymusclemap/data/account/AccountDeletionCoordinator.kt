package app.mymusclemap.data.account

import app.mymusclemap.data.auth.AccountDeletionCall
import app.mymusclemap.data.auth.GoogleIdTokenRequest
import app.mymusclemap.data.auth.GoogleIdTokenValue

sealed interface AccountDeletionOutcome {
    data object Deleted : AccountDeletionOutcome
    data object NeedsNetwork : AccountDeletionOutcome
    data object Cancelled : AccountDeletionOutcome
    data object GoogleUnavailable : AccountDeletionOutcome
    data object ReauthenticationRequired : AccountDeletionOutcome
    data object NotFound : AccountDeletionOutcome
    data object Failed : AccountDeletionOutcome
    data object NoAccount : AccountDeletionOutcome
    data class LocalCleanupFailed(val userId: String) : AccountDeletionOutcome
}

/**
 * Deletes the signed-in account only after the backend confirms it.
 * A network or server failure does not remove local data.
 */
class AccountDeletionCoordinator(
    private val requestIdToken: suspend () -> GoogleIdTokenRequest,
    private val deleteAuthenticated: suspend (GoogleIdTokenValue) -> AccountDeletionCall,
    private val deletePublic: suspend (GoogleIdTokenValue) -> AccountDeletionCall,
    private val online: () -> Boolean,
    private val afterServerDeletion: suspend (String) -> Boolean
) {
    suspend fun delete(userId: String): AccountDeletionOutcome {
        if (!online()) {
            return AccountDeletionOutcome.NeedsNetwork
        }
        val token = when (val google = requestIdToken()) {
            GoogleIdTokenRequest.Cancelled -> return AccountDeletionOutcome.Cancelled
            GoogleIdTokenRequest.Failed, GoogleIdTokenRequest.NotConfigured ->
                return AccountDeletionOutcome.GoogleUnavailable
            is GoogleIdTokenRequest.Issued -> google.idToken
        }
        val authenticated = deleteAuthenticated(token)
        val result = if (authenticated == AccountDeletionCall.Unauthenticated) {
            deletePublic(token)
        } else {
            authenticated
        }
        return when (result) {
            AccountDeletionCall.Deleted ->
                if (afterServerDeletion(userId)) {
                    AccountDeletionOutcome.Deleted
                } else {
                    AccountDeletionOutcome.LocalCleanupFailed(userId)
                }
            AccountDeletionCall.ReauthenticationRequired -> AccountDeletionOutcome.ReauthenticationRequired
            AccountDeletionCall.NotFound -> AccountDeletionOutcome.NotFound
            AccountDeletionCall.Unavailable ->
                if (online()) AccountDeletionOutcome.Failed else AccountDeletionOutcome.NeedsNetwork
            AccountDeletionCall.Unauthenticated,
            AccountDeletionCall.Rejected,
            AccountDeletionCall.Failed -> AccountDeletionOutcome.Failed
        }
    }
}
