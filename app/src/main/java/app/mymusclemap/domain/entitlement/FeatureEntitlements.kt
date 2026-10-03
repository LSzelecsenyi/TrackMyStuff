package app.mymusclemap.domain.entitlement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Application code asks whether a capability is available. Implementations must not expose
 * Google Play Billing types, product IDs, or purchase objects.
 *
 * Product rules live in [FeatureAccessPolicy]. [PolicyBackedEntitlements] answers these
 * checks from that policy. [OpenFeatureEntitlements] remains the test and preview default
 * that grants every capability. [changes] emits when the resolved entitlement may have
 * changed, so screens can re-read [hasAccess] without a process restart.
 */
interface FeatureEntitlements {
    fun hasAccess(feature: AppFeature): Boolean

    fun changes(): Flow<Int> = flowOf(0)
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
