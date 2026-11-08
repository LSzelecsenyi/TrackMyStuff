package app.mymusclemap.domain.entitlement

/**
 * Informational notice for a material privacy-policy text change.
 * Recording the version means the notice was shown and dismissed.
 * It is not a consent decision and it does not start a Pro trial or a purchase.
 */
object PrivacyPolicyNotice {
    const val CURRENT_VERSION = 4

    fun due(acknowledgedVersion: Int): Boolean = acknowledgedVersion < CURRENT_VERSION
}
