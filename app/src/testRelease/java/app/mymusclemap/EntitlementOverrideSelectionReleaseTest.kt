package app.mymusclemap

import app.mymusclemap.domain.entitlement.BackendFounderEntitlement
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.FounderLifetimeEntitlement
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.SubscriptionEntitlement
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant

class EntitlementOverrideSelectionReleaseTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    @Test
    fun releaseAdjustIsIdentityForEveryGrantSource() {
        val cases = listOf(
            EntitlementSources(),
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(3600))
            ),
            EntitlementSources.of(
                founderLifetime = FounderLifetimeEntitlement(active = true)
            ),
            EntitlementSources.of(
                backendFounder = BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(60)
                )
            ),
            EntitlementSources.of(
                program = FounderProgramState(status = FounderProgramStatus.Approved),
                backendFounder = BackendFounderEntitlement(
                    founderLifetime = true,
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(60)
                )
            )
        )
        cases.forEach { sources ->
            assertSame(sources, EntitlementOverrideSelection.adjust(sources))
        }
    }
}
