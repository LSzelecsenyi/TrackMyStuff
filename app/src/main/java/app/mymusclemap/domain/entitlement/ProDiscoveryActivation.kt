package app.mymusclemap.domain.entitlement

import java.time.Instant

sealed interface ProDiscoveryActivation {
    data class Started(val expiresAt: Instant) : ProDiscoveryActivation
    data class AlreadyActive(val expiresAt: Instant) : ProDiscoveryActivation
    data object AlreadyUsed : ProDiscoveryActivation
    data object NotEligible : ProDiscoveryActivation
}
