package app.mymusclemap.domain.entitlement

/**
 * Informational notice for a material privacy-policy text change.
 * Recording the version means the notice was shown and dismissed.
 * It is not a consent decision and it does not enable activity upload.
 */
object PrivacyPolicyNotice {
    const val CURRENT_VERSION = 2

    fun due(acknowledgedVersion: Int): Boolean = acknowledgedVersion < CURRENT_VERSION
}

enum class ProDiscoverySyncChoice {
    UNSET,
    ENABLED,
    DISABLED
}

enum class ProDiscoverySyncDeletion {
    NONE,
    PENDING,
    FAILED
}

/**
 * Per-account choice for uploading Pro Discovery workout dates.
 * Missing state is unset, which does not allow upload.
 */
data class ProDiscoverySyncPermission(
    val choice: ProDiscoverySyncChoice = ProDiscoverySyncChoice.UNSET,
    val generation: Long = 0,
    val scopeVersion: Int = SCOPE_VERSION,
    val deletion: ProDiscoverySyncDeletion = ProDiscoverySyncDeletion.NONE
) {
    val allowsUpload: Boolean =
        choice == ProDiscoverySyncChoice.ENABLED && scopeVersion == SCOPE_VERSION && generation > 0

    companion object {
        const val SCOPE_VERSION = 1
    }
}

data class ProDiscoverySyncUi(
    val prompt: Boolean = false,
    val enabled: Boolean = false,
    val deletionPending: Boolean = false,
    val deletionFailed: Boolean = false
)

fun ProDiscoverySyncPermission.toUi(): ProDiscoverySyncUi {
    return ProDiscoverySyncUi(
        prompt = choice == ProDiscoverySyncChoice.UNSET,
        enabled = allowsUpload,
        deletionPending = deletion == ProDiscoverySyncDeletion.PENDING,
        deletionFailed = deletion == ProDiscoverySyncDeletion.FAILED
    )
}
