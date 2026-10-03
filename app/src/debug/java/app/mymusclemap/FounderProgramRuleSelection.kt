package app.mymusclemap

import app.mymusclemap.domain.entitlement.FounderProgramRules

/**
 * Debug composition only. Release uses a different source file that supplies
 * [FounderProgramRules.Production]. These thresholds are not stored and do not
 * grant a store-backed Founder Lifetime entitlement.
 *
 * A later manual check of two distinct days replaces this value with another
 * [FounderProgramRules] instance. [app.mymusclemap.domain.entitlement.FounderProgramLogic]
 * stays unchanged.
 */
object FounderProgramRuleSelection {
    val rules = FounderProgramRules(
        temporaryProWorkoutCount = 1,
        founderWorkoutCount = 2,
        requiredDistinctWorkoutDays = 1,
        qualificationWindowDays = 45,
        feedbackRequired = true,
        testerAnalyticsReportRequired = true
    )
}
