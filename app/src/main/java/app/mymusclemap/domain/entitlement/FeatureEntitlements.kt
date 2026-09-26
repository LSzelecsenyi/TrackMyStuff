package app.mymusclemap.domain.entitlement

/**
 * Application code asks whether a capability is available. Implementations must not expose
 * Google Play Billing types, product IDs, or purchase objects.
 *
 * Alpha and local development use [OpenFeatureEntitlements], which grants every capability
 * so existing functionality stays available. A Billing-backed implementation can replace
 * that object in [app.mymusclemap.AppContainer] without changing UI call sites.
 */
interface FeatureEntitlements {
    fun hasAccess(feature: AppFeature): Boolean
}

/**
 * Grants every [AppFeature]. Use this until real Free/Pro limits are enforced.
 */
object OpenFeatureEntitlements : FeatureEntitlements {
    override fun hasAccess(feature: AppFeature): Boolean = true
}

/**
 * Explicit allow-list for tests and future local overrides.
 */
class SelectiveFeatureEntitlements(
    private val granted: Set<AppFeature>
) : FeatureEntitlements {
    override fun hasAccess(feature: AppFeature): Boolean = feature in granted
}

object ProAccess {
    fun run(
        entitlements: FeatureEntitlements,
        feature: AppFeature,
        onLocked: () -> Unit,
        onAllowed: () -> Unit
    ) {
        if (entitlements.hasAccess(feature)) {
            onAllowed()
        } else {
            onLocked()
        }
    }
}
