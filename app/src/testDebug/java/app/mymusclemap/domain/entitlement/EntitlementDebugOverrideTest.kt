package app.mymusclemap.domain.entitlement

import app.mymusclemap.BuildConfig
import app.mymusclemap.EntitlementDebugOverride
import app.mymusclemap.EntitlementOverrideMode
import app.mymusclemap.EntitlementOverrideSelection
import app.mymusclemap.ui.membership.MembershipBadge
import app.mymusclemap.ui.membership.MembershipDetail
import app.mymusclemap.ui.membership.MembershipPresentation
import app.mymusclemap.ui.membership.membershipPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class EntitlementDebugOverrideTest {
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    @Test
    fun autoLeavesEmptySourcesFree() {
        val sources = EntitlementSources()
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Auto)
        assertSame(sources, adjusted)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.grantsPro)
        assertFalse(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun autoPreservesATrustedBackendFounderGrant() {
        val sources = EntitlementSources.of(
            backendFounder = BackendFounderEntitlement(
                founderLifetime = true,
                validUntil = now.plusSeconds(60)
            )
        )
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Auto)
        assertSame(sources, adjusted)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
    }

    @Test
    fun autoPreservesSubscriptionPro() {
        val sources = EntitlementSources.of(
            subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(3600))
        )
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Auto)
        assertSame(sources, adjusted)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.subscriptionValid)
        assertFalse(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
    }

    @Test
    fun founderLifetimeIsDerivedByTheRealResolver() {
        val sources = EntitlementSources()
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Founder)
        assertFalse(sources.founderLifetime.active)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.founderLifetime)
        assertTrue(resolved.founderRecognized)
        assertTrue(resolved.founderProActive)
        assertFalse(resolved.temporaryTesterPro)
        assertFalse(resolved.subscriptionValid)
        assertEquals(
            MembershipPresentation(MembershipBadge.Founder, MembershipDetail.FoundingMember),
            membershipPresentation(resolved, FounderProgramStatus.NotEnrolled)
        )
    }

    @Test
    fun founderKeepsSubscriptionAndSuppressesTemporaryPro() {
        val sources = EntitlementSources.of(
            subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(3600)),
            backendFounder = BackendFounderEntitlement(
                temporaryFounderPro = true,
                validUntil = now.plusSeconds(60)
            )
        )
        val resolved = resolve(
            EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Founder)
        )
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.founderLifetime)
        assertTrue(resolved.founderRecognized)
        assertTrue(resolved.founderProActive)
        assertTrue(resolved.subscriptionValid)
        assertFalse(resolved.temporaryTesterPro)
    }

    @Test
    fun nonFounderLeavesEmptySourcesFree() {
        val resolved = resolve(
            EntitlementDebugOverride.adjust(EntitlementSources(), EntitlementOverrideMode.NonFounder)
        )
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.grantsPro)
    }

    @Test
    fun nonFounderHidesATrustedBackendGrantWithoutChangingIt() {
        val backend = BackendFounderEntitlement(
            temporaryFounderPro = true,
            founderLifetime = true,
            validUntil = now.plusSeconds(60)
        )
        val sources = EntitlementSources.of(
            program = FounderProgramState(status = FounderProgramStatus.Approved),
            backendFounder = backend
        )
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.NonFounder)
        assertEquals(backend, sources.backendFounder)
        assertEquals(FounderProgramStatus.Approved, sources.founderProgram.status)
        assertEquals(FounderProgramStatus.Approved, adjusted.founderProgram.status)
        assertNull(adjusted.backendFounder.validUntil)
        assertFalse(adjusted.founderLifetime.active)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.founderLifetime)
        assertFalse(resolved.temporaryTesterPro)
        assertEquals(
            MembershipPresentation.None,
            membershipPresentation(resolved, FounderProgramStatus.Approved)
        )
    }

    @Test
    fun nonFounderHidesAnActiveSubscriptionWithoutChangingIt() {
        val paidUntil = now.plusSeconds(3600)
        val sources = EntitlementSources.of(
            subscription = SubscriptionEntitlement(
                paidUntilInclusive = paidUntil,
                renewalCancelled = true
            )
        )
        val adjusted = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.NonFounder)
        assertEquals(paidUntil, sources.subscription.paidUntilInclusive)
        assertTrue(sources.subscription.renewalCancelled)
        assertNull(adjusted.subscription.paidUntilInclusive)
        val resolved = resolve(adjusted)
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.subscriptionValid)
    }

    @Test
    fun theSameModeAlwaysResolvesTheSameWay() {
        val sources = EntitlementSources.of(
            subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(3600)),
            backendFounder = BackendFounderEntitlement(
                temporaryFounderPro = true,
                validUntil = now.plusSeconds(60)
            )
        )
        EntitlementOverrideMode.entries.forEach { mode ->
            val first = EntitlementDebugOverride.adjust(sources, mode)
            val second = EntitlementDebugOverride.adjust(sources, mode)
            assertEquals(first, second)
            assertEquals(resolve(first), resolve(second))
        }
    }

    @Test
    fun selectionAppliesTheCompiledBuildConfigMode() {
        val sources = EntitlementSources.of(
            subscription = SubscriptionEntitlement(paidUntilInclusive = now.plusSeconds(60))
        )
        val mode = when (val configured = BuildConfig.STRICT_ENTITLEMENT_OVERRIDE) {
            "AUTO" -> EntitlementOverrideMode.Auto
            "FOUNDER" -> EntitlementOverrideMode.Founder
            "NON_FOUNDER" -> EntitlementOverrideMode.NonFounder
            else -> error("Unsupported strict.entitlementOverride \"$configured\".")
        }
        assertEquals(
            EntitlementDebugOverride.adjust(sources, mode),
            EntitlementOverrideSelection.adjust(sources)
        )
    }

    @Test
    fun defaultComposerUsesRealSources() {
        val composer = EntitlementComposer(
            subscriptionProvider = InactiveSubscriptionProvider,
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            backendFounder = {
                BackendFounderEntitlement(
                    temporaryFounderPro = true,
                    validUntil = now.plusSeconds(60)
                )
            }
        )
        val resolved = composer.resolve()
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertTrue(resolved.temporaryTesterPro)
        assertFalse(resolved.founderLifetime)
        assertEquals(resolved, EntitlementResolver.resolve(composer.sources(), now))
    }

    @Test
    fun injectedFounderAdjustmentDoesNotChangeTheProgramState() {
        val program = FounderProgramState(
            status = FounderProgramStatus.NotEnrolled,
            enrolledOn = null
        )
        val composer = EntitlementComposer(
            subscriptionProvider = InactiveSubscriptionProvider,
            founderLifetimeProvider = InactiveFounderLifetimeProvider,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            founderProgram = { program },
            adjustSources = { sources ->
                EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Founder)
            }
        )
        val resolved = composer.resolve()
        assertEquals(EntitlementTier.Pro, resolved.tier)
        assertFalse(resolved.founderLifetime)
        assertTrue(resolved.founderRecognized)
        assertTrue(resolved.founderProActive)
        assertFalse(resolved.temporaryTesterPro)
        assertEquals(FounderProgramStatus.NotEnrolled, composer.sources().founderProgram.status)
        assertEquals(program, FounderProgramState(status = FounderProgramStatus.NotEnrolled))
        assertEquals(null, program.enrolledOn)
    }

    @Test
    fun nonFounderHidesPromotionalProAndAutoLeavesItAlone() {
        val expiresAt = now.plusSeconds(120)
        val sources = EntitlementSources(
            promotionalPro = PromotionalProEntitlement(expiresAt = expiresAt)
        )
        assertSame(sources, EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Auto))
        val hidden = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.NonFounder)
        assertFalse(resolve(hidden).grantsPro)
        assertFalse(hidden.promotionalPro.isActive(now))
        val founder = EntitlementDebugOverride.adjust(sources, EntitlementOverrideMode.Founder)
        assertEquals(expiresAt, founder.promotionalPro.expiresAt)
        assertTrue(resolve(founder).founderProActive)
    }

    private fun resolve(sources: EntitlementSources): EffectiveEntitlement {
        return EntitlementResolver.resolve(sources, now)
    }
}
