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
    data object WrongAccount : AccountDeletionOutcome
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
    suspend fun delete(userId: String, expectedEmail: String? = null): AccountDeletionOutcome {
        if (!online()) {
            return AccountDeletionOutcome.NeedsNetwork
        }
        val selected = when (val google = requestIdToken()) {
            GoogleIdTokenRequest.Cancelled -> return AccountDeletionOutcome.Cancelled
            GoogleIdTokenRequest.Failed, GoogleIdTokenRequest.NotConfigured ->
                return AccountDeletionOutcome.GoogleUnavailable
            is GoogleIdTokenRequest.Issued -> google
        }
        val authenticated = deleteAuthenticated(selected.idToken)
        val result = when {
            authenticated != AccountDeletionCall.Unauthenticated -> authenticated
            emailsMatch(expectedEmail, selected.email) -> deletePublic(selected.idToken)
            else -> AccountDeletionCall.AccountMismatch
        }
        return when (result) {
            AccountDeletionCall.Deleted ->
                if (afterServerDeletion(userId)) {
                    AccountDeletionOutcome.Deleted
                } else {
                    AccountDeletionOutcome.LocalCleanupFailed(userId)
                }
            AccountDeletionCall.ReauthenticationRequired,
            AccountDeletionCall.InvalidGoogleToken -> AccountDeletionOutcome.ReauthenticationRequired
            AccountDeletionCall.AccountMismatch -> AccountDeletionOutcome.WrongAccount
            AccountDeletionCall.NotFound -> AccountDeletionOutcome.NotFound
            AccountDeletionCall.Unavailable ->
                if (online()) AccountDeletionOutcome.Failed else AccountDeletionOutcome.NeedsNetwork
            AccountDeletionCall.Unauthenticated,
            AccountDeletionCall.Rejected,
            AccountDeletionCall.Failed -> AccountDeletionOutcome.Failed
        }
    }
}

internal fun emailsMatch(expectedEmail: String?, selectedEmail: String?): Boolean {
    val expected = expectedEmail?.trim().orEmpty()
    val selected = selectedEmail?.trim().orEmpty()
    return expected.isNotEmpty() && expected.equals(selected, ignoreCase = true)
}
