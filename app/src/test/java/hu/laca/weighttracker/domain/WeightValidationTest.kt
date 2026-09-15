package hu.laca.weighttracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightValidationTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun commaAndPeriodAreAcceptedAndNormalized() {
        val comma = WeightParser.parseUserInput("82,4") as WeightParseResult.Valid
        val period = WeightParser.parseUserInput("82.4") as WeightParseResult.Valid
        assertEquals(82.4, comma.kilograms, 0.0001)
        assertEquals(82.4, period.kilograms, 0.0001)
    }

    @Test
    fun trailingZeroDoesNotCountAsSecondDecimal() {
        val result = WeightParser.parseUserInput("82,40") as WeightParseResult.Valid
        assertEquals(82.4, result.kilograms, 0.0001)
    }

    @Test
    fun invalidWeightsAreRejected() {
        assertEquals(WeightParseError.Empty, errorOf(""))
        assertEquals(WeightParseError.Malformed, errorOf("abc"))
        assertEquals(WeightParseError.Malformed, errorOf("8,2,4"))
        assertEquals(WeightParseError.Malformed, errorOf("82..4"))
        assertEquals(WeightParseError.TooManyDecimals, errorOf("82,45"))
        assertEquals(WeightParseError.OutOfRange, errorOf("29,9"))
        assertEquals(WeightParseError.OutOfRange, errorOf("300,1"))
        assertEquals(WeightParseError.Malformed, errorOf("NaN"))
        assertEquals(WeightParseError.Malformed, errorOf("Infinity"))
    }

    @Test
    fun rangeBoundariesAreAccepted() {
        assertEquals(30.0, (WeightParser.parseUserInput("30") as WeightParseResult.Valid).kilograms, 0.0)
        assertEquals(300.0, (WeightParser.parseUserInput("300,0") as WeightParseResult.Valid).kilograms, 0.0)
    }

    @Test
    fun futureDatesAreRejectedAndTodayIsAllowed() {
        val future = MeasurementValidator.validate(today.plusDays(1), "80,0", today)
        assertTrue(future is MeasurementValidationResult.Invalid)
        assertEquals(
            DateValidationError.Future,
            (future as MeasurementValidationResult.Invalid).dateError
        )
        val todayResult = MeasurementValidator.validate(today, "80,0", today)
        assertTrue(todayResult is MeasurementValidationResult.Valid)
        val past = MeasurementValidator.validate(today.minusDays(3), "80,0", today)
        assertTrue(past is MeasurementValidationResult.Valid)
    }

    @Test
    fun filterKeepsHungarianDecimalInputSafe() {
        assertEquals("82,4", WeightParser.filterUserInput("82,4"))
        assertEquals("82.4", WeightParser.filterUserInput("82.4"))
        assertEquals("82,4", WeightParser.filterUserInput("82,45"))
        assertEquals("82", WeightParser.filterUserInput("82abc"))
    }

    private fun errorOf(raw: String): WeightParseError {
        return (WeightParser.parseUserInput(raw) as WeightParseResult.Invalid).error
    }
}
