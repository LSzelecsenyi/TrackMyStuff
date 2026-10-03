package app.mymusclemap

import app.mymusclemap.domain.entitlement.FounderProgramRules
import org.junit.Assert.assertEquals
import org.junit.Test

class FounderProgramRuleSelectionTest {
    @Test
    fun releaseCompositionUsesProductionRules() {
        assertEquals(FounderProgramRules.Production, FounderProgramRuleSelection.rules)
        assertEquals(false, FounderDebugReviewAccess.available)
    }
}
