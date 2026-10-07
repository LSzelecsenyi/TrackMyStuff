package app.mymusclemap.data.auth

import app.mymusclemap.data.founder.BackendFounderSnapshot

/**
 * Raw bearer and Google ID token values are redacted from [toString] so a log of the model
 * cannot print the secret. Callers that need the wire value use [value] and must not log it.
 */
@JvmInline
value class StrictBearerToken(val value: String) {
    override fun toString(): String = "StrictBearerToken(redacted)"
}

@JvmInline
value class GoogleIdTokenValue(val value: String) {
    override fun toString(): String = "GoogleIdToken(redacted)"
}

data class StoredStrictSession(
    val accessToken: StrictBearerToken,
    val expiresAt: String,
    val userId: String
)

sealed interface GoogleIdTokenRequest {
    data class Issued(val idToken: GoogleIdTokenValue) : GoogleIdTokenRequest
    data object Cancelled : GoogleIdTokenRequest
    data object NotConfigured : GoogleIdTokenRequest
    data object Failed : GoogleIdTokenRequest
}

sealed interface StrictSignInResult {
    data class SignedIn(val userId: String) : StrictSignInResult
    data object Cancelled : StrictSignInResult
    data object GoogleFailed : StrictSignInResult
    data object GoogleNotConfigured : StrictSignInResult
    data object InvalidGoogleToken : StrictSignInResult
    data object BackendRejected : StrictSignInResult
    data object Unavailable : StrictSignInResult
}

sealed interface StrictCurrentUserResult {
    data class SignedIn(val userId: String) : StrictCurrentUserResult
    data object SignedOut : StrictCurrentUserResult
    data object SessionRejected : StrictCurrentUserResult
    data object Unavailable : StrictCurrentUserResult
}

sealed interface StrictLogoutResult {
    /** Local token is gone. The backend confirmed revocation. */
    data object LoggedOut : StrictLogoutResult

    /**
     * Local token is gone. The backend session may still be valid until it expires,
     * because the device could not complete DELETE /api/v1/auth/session.
     */
    data object LoggedOutLocallyOnly : StrictLogoutResult
}

sealed interface GoogleExchangeResult {
    data class Accepted(val session: StoredStrictSession) : GoogleExchangeResult
    data object InvalidGoogleToken : GoogleExchangeResult
    data object Rejected : GoogleExchangeResult
    data object Unavailable : GoogleExchangeResult
}

sealed interface CurrentUserCall {
    data class SignedIn(val userId: String) : CurrentUserCall
    data object NoSession : CurrentUserCall
    data object Rejected : CurrentUserCall
    data object Unavailable : CurrentUserCall
}

sealed interface RevokeCall {
    data object Revoked : RevokeCall
    data object NoSession : RevokeCall
    data object Failed : RevokeCall
}

sealed interface FounderEnrollmentCall {
    data class Enrolled(val snapshot: BackendFounderSnapshot) : FounderEnrollmentCall
    data object NoSession : FounderEnrollmentCall
    /** Bearer was rejected. The stored session has been cleared. */
    data object Unauthenticated : FounderEnrollmentCall
    data object Rejected : FounderEnrollmentCall
    data object Unavailable : FounderEnrollmentCall
}

sealed interface FounderSnapshotCall {
    data class Loaded(val snapshot: BackendFounderSnapshot) : FounderSnapshotCall
    data object NoSession : FounderSnapshotCall
    data object Unauthenticated : FounderSnapshotCall
    data object Unavailable : FounderSnapshotCall
}

sealed interface FounderReportSubmission {
    data class Accepted(val snapshot: BackendFounderSnapshot) : FounderReportSubmission
    data object NoSession : FounderReportSubmission
    /** Bearer was rejected. The stored session has been cleared. */
    data object Unauthenticated : FounderReportSubmission
    data object Rejected : FounderReportSubmission
    data object Unavailable : FounderReportSubmission
}

sealed interface FounderEntitlementCall {
    data class Loaded(
        val temporaryFounderPro: Boolean,
        val founderLifetime: Boolean,
        val founderGrantedAt: java.time.Instant? = null,
        val specialAchievements: List<app.mymusclemap.domain.entitlement.SpecialAchievementGrant> = emptyList()
    ) : FounderEntitlementCall
    data object NoSession : FounderEntitlementCall
    data object Unauthenticated : FounderEntitlementCall
    data object Unavailable : FounderEntitlementCall
}
