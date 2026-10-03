package app.mymusclemap

import app.mymusclemap.domain.entitlement.FounderProgramRules
import org.junit.Assert.assertEquals
import org.junit.Test

class FounderProgramRuleSelectionTest {
    @Test
    fun debugCompositionUsesTheFastManualRules() {
        val rules = FounderProgramRuleSelection.rules
        assertEquals(1, rules.temporaryProWorkoutCount)
        assertEquals(2, rules.founderWorkoutCount)
        assertEquals(1, rules.requiredDistinctWorkoutDays)
        assertEquals(45, rules.qualificationWindowDays)
        assertEquals(true, rules.feedbackRequired)
        assertEquals(true, rules.testerAnalyticsReportRequired)
        assertEquals(false, rules == FounderProgramRules.Production)
        assertEquals(true, FounderDebugReviewAccess.available)
    }
}
