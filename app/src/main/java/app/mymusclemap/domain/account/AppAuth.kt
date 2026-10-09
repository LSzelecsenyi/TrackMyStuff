package app.mymusclemap.domain.account

import java.time.Instant
import java.util.UUID

enum class AppAuthState {
    INITIALIZING,
    AUTHENTICATED,
    SIGNED_OUT,
    AUTHENTICATION_REQUIRED,
    RECOVERABLE_ERROR
}

data class AppAuthDecision(
    val state: AppAuthState,
    val offline: Boolean = false
)

/**
 * Startup gate. A previously verified account can enter offline.
 * A first sign-in always needs a successful backend session.
 */
fun resolveAppAuth(
    activeUserId: String?,
    sessionUserId: String?,
    sessionExpiresAt: Instant?,
    now: Instant,
    online: Boolean
): AppAuthDecision {
    val sessionValid = sessionUserId != null &&
        sessionExpiresAt != null &&
        now.isBefore(sessionExpiresAt) &&
        sessionUserId == activeUserId
    if (activeUserId != null && sessionValid) {
        return AppAuthDecision(AppAuthState.AUTHENTICATED)
    }
    if (activeUserId != null && !online && sessionUserId == activeUserId) {
        return AppAuthDecision(AppAuthState.AUTHENTICATED, offline = true)
    }
    if (activeUserId == null && sessionUserId == null) {
        return AppAuthDecision(AppAuthState.SIGNED_OUT, offline = !online)
    }
    return AppAuthDecision(AppAuthState.AUTHENTICATION_REQUIRED, offline = !online)
}

enum class LegacyClaim {
    MOVE,
    ALREADY_MINE,
    OWNED_BY_OTHER,
    NOTHING_TO_CLAIM
}

fun legacyClaim(legacyExists: Boolean, claimedBy: String?, userId: String): LegacyClaim {
    if (claimedBy != null) {
        return if (claimedBy == userId) LegacyClaim.ALREADY_MINE else LegacyClaim.OWNED_BY_OTHER
    }
    if (!legacyExists) {
        return LegacyClaim.NOTHING_TO_CLAIM
    }
    return LegacyClaim.MOVE
}

fun accountDatabaseName(userId: String): String {
    UUID.fromString(userId)
    return "account_$userId.db"
}

fun accountPhotoDirectory(userId: String): String {
    UUID.fromString(userId)
    return "progress_photos_$userId"
}
