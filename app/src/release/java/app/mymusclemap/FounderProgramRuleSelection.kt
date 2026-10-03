package app.mymusclemap

import app.mymusclemap.domain.entitlement.FounderProgramRules

/** Release composition. Fast manual thresholds are not on this classpath. */
object FounderProgramRuleSelection {
    val rules: FounderProgramRules = FounderProgramRules.Production
}
