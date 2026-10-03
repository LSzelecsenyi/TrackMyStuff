package app.mymusclemap

import app.mymusclemap.domain.entitlement.FounderProgramAvailability

/**
 * Pilot composition. Both debug and release currently accept new Founding Testers.
 * Closing the program is a new app version that sets this to [FounderProgramAvailability.Closed].
 * Rules stay in [FounderProgramRuleSelection].
 */
object FounderProgramAvailabilitySelection {
    val availability: FounderProgramAvailability = FounderProgramAvailability.Open
}
