package app.mymusclemap.data.founder

import app.mymusclemap.data.auth.FounderEnrollmentCall
import app.mymusclemap.data.auth.StrictAuthRepository
import app.mymusclemap.data.auth.StrictBackendApi
import app.mymusclemap.data.auth.StrictCurrentUserResult
import app.mymusclemap.data.auth.StrictSignInResult
import kotlinx.coroutines.sync.Mutex
import java.time.ZoneId

enum class FounderJoinResult {
    Enrolled,
    Cancelled,
    GoogleFailed,
    NotConfigured,
    Unavailable,
    Rejected,
    InProgress
}

/**
 * Join is explicit. This does not run at startup, and it does not upload workouts.
 * A stored bearer is checked with `/me` before it is reused. Google starts only when
 * that check says there is no usable session.
 */
class FounderJoinCoordinator(
    private val auth: StrictAuthRepository,
    private val api: StrictBackendApi,
    private val applyEnrollment: suspend (BackendFounderSnapshot) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private val gate = Mutex()

    suspend fun join(): FounderJoinResult {
        if (!gate.tryLock()) {
            return FounderJoinResult.InProgress
        }
        try {
            return joinLocked()
        } finally {
            gate.unlock()
        }
    }

    private suspend fun joinLocked(): FounderJoinResult {
        var usedGoogle = false
        repeat(2) {
            when (val session = ensureSession(allowGoogle = !usedGoogle)) {
                SessionStep.Ready -> Unit
                is SessionStep.Stopped -> return session.result
                SessionStep.GoogleUsed -> usedGoogle = true
            }
            if (usedGoogle && auth.storedSession() == null) {
                return FounderJoinResult.Rejected
            }
            when (val enrolled = api.enrollFounder(zone)) {
                is FounderEnrollmentCall.Enrolled -> {
                    applyEnrollment(enrolled.snapshot)
                    return FounderJoinResult.Enrolled
                }
                FounderEnrollmentCall.Unauthenticated -> usedGoogle = usedGoogle
                FounderEnrollmentCall.NoSession -> Unit
                FounderEnrollmentCall.Rejected -> return FounderJoinResult.Rejected
                FounderEnrollmentCall.Unavailable -> return FounderJoinResult.Unavailable
            }
        }
        return FounderJoinResult.Rejected
    }

    private suspend fun ensureSession(allowGoogle: Boolean): SessionStep {
        return when (auth.currentUser()) {
            is StrictCurrentUserResult.SignedIn -> SessionStep.Ready
            StrictCurrentUserResult.Unavailable -> SessionStep.Stopped(FounderJoinResult.Unavailable)
            StrictCurrentUserResult.SignedOut, StrictCurrentUserResult.SessionRejected -> {
                if (!allowGoogle) {
                    return SessionStep.Stopped(FounderJoinResult.Rejected)
                }
                when (auth.signIn()) {
                    is StrictSignInResult.SignedIn -> SessionStep.GoogleUsed
                    StrictSignInResult.Cancelled -> SessionStep.Stopped(FounderJoinResult.Cancelled)
                    StrictSignInResult.GoogleFailed -> SessionStep.Stopped(FounderJoinResult.GoogleFailed)
                    StrictSignInResult.GoogleNotConfigured -> SessionStep.Stopped(FounderJoinResult.NotConfigured)
                    StrictSignInResult.InvalidGoogleToken,
                    StrictSignInResult.BackendRejected -> SessionStep.Stopped(FounderJoinResult.Rejected)
                    StrictSignInResult.Unavailable -> SessionStep.Stopped(FounderJoinResult.Unavailable)
                }
            }
        }
    }

    private sealed interface SessionStep {
        data object Ready : SessionStep
        data object GoogleUsed : SessionStep
        data class Stopped(val result: FounderJoinResult) : SessionStep
    }
}
