package app.mymusclemap.data.promotion

import java.time.Instant

sealed interface TrialAuthorization {
    data object Local : TrialAuthorization
    data class Granted(val activatedAt: Instant, val expiresAt: Instant) : TrialAuthorization
    data object AlreadyUsed : TrialAuthorization
    data object Rejected : TrialAuthorization
    data object Unavailable : TrialAuthorization
}
