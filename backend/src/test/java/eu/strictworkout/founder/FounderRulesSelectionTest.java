package eu.strictworkout.founder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FounderRulesSelectionTest {

    @Test
    void unsetOrProductionStaysOnThePublishedRules() {
        assertEquals(FounderRules.PRODUCTION, FounderRulesSelection.select(null, false));
        assertEquals(FounderRules.PRODUCTION, FounderRulesSelection.select("  ", false));
        assertEquals(FounderRules.PRODUCTION, FounderRulesSelection.select("production", false));
        assertEquals(FounderRules.PRODUCTION, FounderRulesSelection.select("production", true));
    }

    @Test
    void fastRulesAreTheLocalManualThresholds() {
        FounderRules fast = FounderRulesSelection.select("fast", false);
        assertEquals(1, fast.temporaryProWorkoutCount());
        assertEquals(2, fast.founderWorkoutCount());
        assertEquals(1, fast.requiredDistinctWorkoutDays());
        assertEquals(45, fast.qualificationWindowDays());
    }

    @Test
    void productionProfileRejectsFastRules() {
        assertThrows(IllegalStateException.class, () -> FounderRulesSelection.select("fast", true));
    }

    @Test
    void unknownSelectionFailsClosed() {
        assertThrows(IllegalStateException.class, () -> FounderRulesSelection.select("debug", false));
    }
}
