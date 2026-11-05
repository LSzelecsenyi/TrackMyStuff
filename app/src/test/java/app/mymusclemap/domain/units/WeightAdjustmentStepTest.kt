package app.mymusclemap.domain.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class WeightAdjustmentStepTest {
    @Test
    fun externalWeightStepIsAddedToTheEnteredValue() {
        // Given a current weight of 16.25 kg and an increment of 2.5 kg
        val current = "16.25"
        val increment = BigDecimal("2.5")

        // When the user presses +
        val next = WorkoutWeightInput.step(current, UnitSystem.METRIC, 1, increment)

        // Then the weight becomes 18.75 kg and is not snapped to a multiple of 2.5
        assertEquals("18.75", next)
    }

    @Test
    fun externalWeightStepIsSubtractedFromTheEnteredValue() {
        // Given a current weight of 16.25 kg and an increment of 2.5 kg
        val current = "16.25"
        val increment = BigDecimal("2.5")

        // When the user presses -
        val next = WorkoutWeightInput.step(current, UnitSystem.METRIC, -1, increment)

        // Then the weight becomes 13.75 kg
        assertEquals("13.75", next)
    }

    @Test
    fun decreasingBelowZeroKeepsTheEnteredWeight() {
        // Given a current weight of 1 kg and an increment of 2.5 kg
        val current = "1"
        val increment = BigDecimal("2.5")

        // When the user presses -
        val next = WorkoutWeightInput.step(current, UnitSystem.METRIC, -1, increment)

        // Then the weight does not become negative
        assertEquals("1", next)
        assertFalse(next.startsWith("-"))
    }

    @Test
    fun emptyOrInvalidWeightIsLeftUnchanged() {
        val increment = BigDecimal("2.5")
        assertEquals("", WorkoutWeightInput.step("", UnitSystem.METRIC, 1, increment))
        assertEquals("abc", WorkoutWeightInput.step("abc", UnitSystem.METRIC, 1, increment))
        assertEquals("0", WorkoutWeightInput.step("0", UnitSystem.METRIC, 1, increment))
    }

    @Test
    fun decimalAdditionDoesNotProduceAFloatingPointArtifact() {
        val next = WorkoutWeightInput.step("16.2", UnitSystem.METRIC, 1, BigDecimal("0.1"))
        assertEquals("16.3", next)
        val chained = WorkoutWeightInput.step(next, UnitSystem.METRIC, 1, BigDecimal("1.25"))
        assertEquals("17.55", chained)
    }

    @Test
    fun imperialStepIsAppliedInPoundsAndStillCommitsAsKilograms() {
        val shown = WorkoutWeightInput.displayOf("10", UnitSystem.IMPERIAL)
        val stepped = WorkoutWeightInput.step(shown, UnitSystem.IMPERIAL, 1, BigDecimal("5"))
        val committed = WorkoutWeightInput.commit("10", stepped, UnitSystem.IMPERIAL)
        assertEquals("22", shown)
        assertEquals("27", stepped)
        assertTrue(committed != stepped)
        assertTrue(committed != "10")
        val again = WorkoutWeightInput.step("16.25", UnitSystem.IMPERIAL, -1, BigDecimal("2.5"))
        assertEquals("13.75", again)
    }

    @Test
    fun invalidCustomIncrementsAreRejectedAndPresetsStayRecognizable() {
        // Given an invalid custom increment
        listOf("", "0", "0.0", "-1", "NaN", "Infinity", "-Infinity", "abc", "1.234", "600").forEach { raw ->
            // When the user attempts to save it
            assertNull(raw, WeightAdjustmentStep.parse(raw, UnitSystem.METRIC))
        }
        assertNull(WeightAdjustmentStep.parse("-5", UnitSystem.IMPERIAL))
        val kept = WeightAdjustmentStep.parse("1.25", UnitSystem.METRIC)!!
        assertEquals(0, kept.compareTo(BigDecimal("1.25")))
        assertTrue(WeightAdjustmentStep.isPreset(kept, UnitSystem.METRIC))
        assertEquals(
            listOf("0.5", "1", "1.25", "2.5", "5"),
            WeightAdjustmentStep.presets(UnitSystem.METRIC).map(WeightAdjustmentStep::storedText)
        )
        assertEquals(
            listOf("0.5", "1", "2.5", "5", "10"),
            WeightAdjustmentStep.presets(UnitSystem.IMPERIAL).map(WeightAdjustmentStep::storedText)
        )
        assertEquals("2.5", WeightAdjustmentStep.storedText(WeightAdjustmentStep.defaultFor(UnitSystem.METRIC)))
        assertEquals("5", WeightAdjustmentStep.storedText(WeightAdjustmentStep.defaultFor(UnitSystem.IMPERIAL)))
    }

    @Test
    fun aNonPresetCustomDecimalIsAccepted() {
        val parsed = WeightAdjustmentStep.parse("0,75", UnitSystem.METRIC)
        assertEquals("0.75", WeightAdjustmentStep.storedText(parsed!!))
        assertFalse(WeightAdjustmentStep.isPreset(parsed, UnitSystem.METRIC))
    }
}
