package app.mymusclemap.data.auth

/**
 * Every Strict user signs in before the main app. The Strict user id comes only
 * from the backend response, never from a client-supplied email.
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
                    StrictSignInResult.SignedIn(
                        exchanged.session.userId,
                        exchanged.email,
                        exchanged.displayName
                    )
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
