package app.mymusclemap.data.auth

/**
 * Account boundary for a later Founder sign-in. Ordinary Free startup does not call [signIn].
 * The Strict user id is taken only from the backend response.
 */
class StrictAuthRepository(
    private val google: GoogleIdentityProvider,
    private val api: StrictBackendApi,
    private val sessions: StrictSessionStore
) {
    fun storedSession(): StoredStrictSession? = sessions.read()

    suspend fun signIn(): StrictSignInResult {
        return when (val googleResult = google.requestIdToken()) {
            GoogleIdTokenRequest.Cancelled -> StrictSignInResult.Cancelled
            GoogleIdTokenRequest.Failed -> StrictSignInResult.GoogleFailed
            GoogleIdTokenRequest.NotConfigured -> StrictSignInResult.GoogleNotConfigured
            is GoogleIdTokenRequest.Issued -> when (val exchanged = api.exchangeGoogleIdToken(googleResult.idToken)) {
                is GoogleExchangeResult.Accepted -> {
                    sessions.write(exchanged.session)
                    StrictSignInResult.SignedIn(exchanged.session.userId)
                }
                GoogleExchangeResult.InvalidGoogleToken -> StrictSignInResult.InvalidGoogleToken
                GoogleExchangeResult.Rejected -> StrictSignInResult.BackendRejected
                GoogleExchangeResult.Unavailable -> StrictSignInResult.Unavailable
            }
        }
    }

    suspend fun currentUser(): StrictCurrentUserResult {
        return when (val call = api.currentUser()) {
            is CurrentUserCall.SignedIn -> StrictCurrentUserResult.SignedIn(call.userId)
            CurrentUserCall.NoSession -> StrictCurrentUserResult.SignedOut
            CurrentUserCall.Rejected -> StrictCurrentUserResult.SessionRejected
            CurrentUserCall.Unavailable -> StrictCurrentUserResult.Unavailable
        }
    }

    /**
     * Deletes the local bearer even when the backend cannot be reached.
     * A failed DELETE leaves the remote session valid until expiry or a later revocation.
     */
    suspend fun logout(): StrictLogoutResult {
        val remote = api.revokeSession()
        sessions.clear()
        return if (remote == RevokeCall.Revoked) {
            StrictLogoutResult.LoggedOut
        } else {
            StrictLogoutResult.LoggedOutLocallyOnly
        }
    }
}
