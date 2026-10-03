package app.mymusclemap.domain.entitlement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class EntitlementResolverTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    @Test
    fun noProSourcesResolveToFree() {
        val resolved = EntitlementResolver.resolve(EntitlementSources(), now)
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.grantsPro)
        assertFalse(resolved.subscriptionValid)
        assertFalse(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
    }

    @Test
    fun subscriptionOnlyResolvesToPro() {
        val resolved = resolve(subscriptionUntil = now.plusSeconds(60))
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.subscriptionValid)
        assertFalse(resolved.founderLifetime)
    }

    @Test
    fun cancelledSubscriptionStaysProUntilThePaidPeriodEnds() {
        val resolved = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = SubscriptionEntitlement(
                    paidUntilInclusive = now,
                    renewalCancelled = true
                )
            ),
            now
        )
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.subscriptionValid)
    }

    @Test
    fun founderLifetimeOnlyResolvesToPro() {
        val resolved = resolve(founderLifetime = true)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.founderLifetime)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun temporaryTesterProResolvesToPro() {
        val resolved = resolve(program = activePro())
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.temporaryTesterPro)
    }

    @Test
    fun pendingApprovalResolvesToPro() {
        val resolved = resolve(program = activePro().copy(status = FounderProgramStatus.PendingApproval))
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.temporaryTesterPro)
    }

    @Test
    fun subscriptionAndFounderLifetimeResolveToPro() {
        val resolved = resolve(subscriptionUntil = now.plusSeconds(60), founderLifetime = true)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.subscriptionValid)
        assertTrue(resolved.founderLifetime)
    }

    @Test
    fun subscriptionExpiryLeavesProWhenFounderLifetimeRemains() {
        val resolved = resolve(
            subscriptionUntil = now.minusSeconds(1),
            founderLifetime = true
        )
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.subscriptionValid)
        assertTrue(resolved.founderLifetime)
    }

    @Test
    fun temporaryProExpiryLeavesProWhenSubscriptionRemains() {
        val resolved = resolve(
            subscriptionUntil = now.plusSeconds(60),
            program = activePro().copy(status = FounderProgramStatus.Expired)
        )
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.temporaryTesterPro)
        assertTrue(resolved.subscriptionValid)
    }

    @Test
    fun losingTheLastProSourceResolvesToFree() {
        val resolved = resolve(
            subscriptionUntil = now.minusSeconds(1),
            program = activePro().copy(status = FounderProgramStatus.Expired)
        )
        assertEquals(EntitlementTier.Free, resolved.tier)
    }

    @Test
    fun approvedProgramResolvesToFounderLifetimeWithoutAStoreGrant() {
        val resolved = resolve(program = activePro().copy(status = FounderProgramStatus.Approved))
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
    }

    @Test
    fun rejectionRemovesOnlyTheFounderProgramSource() {
        val rejected = activePro().copy(status = FounderProgramStatus.Rejected)
        assertEquals(EntitlementTier.Free, resolve(program = rejected).tier)
        val stillSubscribed = resolve(subscriptionUntil = now.plusSeconds(60), program = rejected)
        assertEquals(EntitlementTier.Pro, stillSubscribed.tier)
        assertFalse(stillSubscribed.temporaryTesterPro)
        assertTrue(stillSubscribed.subscriptionValid)
    }

    @Test
    fun inactiveProvidersDoNotGrantProAndDevelopmentProviderDoes() {
        val inactive = EntitlementResolver.resolve(
            EntitlementSources.of(
                subscription = InactiveSubscriptionProvider.current(),
                founderLifetime = InactiveFounderLifetimeProvider.current()
            ),
            now
        )
        assertEquals(EntitlementTier.Free, inactive.tier)
        val development = EntitlementResolver.resolve(
            EntitlementSources.of(subscription = DevelopmentSubscriptionProvider.current()),
            now
        )
        assertEquals(EntitlementTier.Pro, development.tier)
    }

    @Test
    fun composerIncludesTheLocalProgramWithoutDroppingAnotherSource() {
        val program = activePro()
        val composer = EntitlementComposer(
            subscriptionProvider = DevelopmentSubscriptionProvider,
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            founderProgram = { program }
        )
        val resolved = composer.resolve()
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.subscriptionValid)
        assertTrue(resolved.temporaryTesterPro)
        assertEquals(program.status, composer.sources().founderProgram.status)
    }

    @Test
    fun portableRestoreDoesNotReplaceEntitlementSources() {
        val current = resolveSources(program = activePro())
        assertEquals(current, EntitlementAuthority.afterPortableUserDataRestore(current))
        assertEquals(EntitlementTier.Pro, EntitlementResolver.resolve(current, now).tier)
    }

    private fun activePro(): FounderProgramState {
        return FounderProgramState(
            status = FounderProgramStatus.ActivePro,
            enrolledOn = LocalDate.of(2026, 1, 1),
            deadline = LocalDate.of(2026, 2, 15)
        )
    }

    private fun resolve(
        subscriptionUntil: Instant? = null,
        founderLifetime: Boolean = false,
        program: FounderProgramState = FounderProgramState()
    ): EffectiveEntitlement {
        return EntitlementResolver.resolve(resolveSources(subscriptionUntil, founderLifetime, program), now)
    }

    private fun resolveSources(
        subscriptionUntil: Instant? = null,
        founderLifetime: Boolean = false,
        program: FounderProgramState = FounderProgramState()
    ): EntitlementSources {
        return EntitlementSources.of(
            subscription = SubscriptionEntitlement(paidUntilInclusive = subscriptionUntil),
            founderLifetime = FounderLifetimeEntitlement(active = founderLifetime),
            program = program
        )
    }
}
