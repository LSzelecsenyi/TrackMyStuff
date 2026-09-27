package app.mymusclemap.domain.body

import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.model.ChartRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BodyMeasurementLogicTest {
    private val today = LocalDate.of(2026, 9, 26)

    @Test
    fun parserQuantizesOneDecimalAndRejectsGarbage() {
        assertEquals(82.4, (BodyMeasurementParser.parseUserInput("82.4", BodyMeasurementType.WAIST) as BodyMeasurementParseResult.Valid).value, 0.0)
        assertEquals(82.4, (BodyMeasurementParser.parseUserInput("82,4", BodyMeasurementType.WAIST) as BodyMeasurementParseResult.Valid).value, 0.0)
        assertEquals(18.4, (BodyMeasurementParser.parseUserInput("18.4", BodyMeasurementType.BODY_FAT) as BodyMeasurementParseResult.Valid).value, 0.0)
        assertEquals(
            BodyMeasurementParseError.TooManyDecimals,
            (BodyMeasurementParser.parseUserInput("82.45", BodyMeasurementType.WAIST) as BodyMeasurementParseResult.Invalid).error
        )
        assertEquals(
            BodyMeasurementParseError.OutOfRange,
            (BodyMeasurementParser.parseUserInput("10.0", BodyMeasurementType.WAIST) as BodyMeasurementParseResult.Invalid).error
        )
        assertEquals(
            BodyMeasurementParseError.OutOfRange,
            (BodyMeasurementParser.parseUserInput("1.0", BodyMeasurementType.BODY_FAT) as BodyMeasurementParseResult.Invalid).error
        )
        assertEquals(
            BodyMeasurementParseError.OutOfRange,
            (BodyMeasurementParser.parseUserInput("80.0", BodyMeasurementType.BODY_FAT) as BodyMeasurementParseResult.Invalid).error
        )
    }

    @Test
    fun futureDatesAreRejectedAndSameDayWindowDoesNotInventPoints() {
        val future = BodyMeasurementValidator.validate(
            BodyMeasurementType.WAIST,
            today.plusDays(1),
            "80.0",
            today
        )
        assertTrue(future is BodyMeasurementValidationResult.Invalid)
        val rows = listOf(
            measurement(1, BodyMeasurementType.WAIST, today.minusDays(40), 90.0),
            measurement(2, BodyMeasurementType.WAIST, today.minusDays(10), 88.0),
            measurement(3, BodyMeasurementType.WAIST, today.minusDays(2), 87.5)
        )
        val window = BodyMeasurementSeries.window(rows, today, ChartRange.Days30)
        assertEquals(listOf(today.minusDays(10), today.minusDays(2)), window.points.map { it.date })
        assertEquals(-0.5, window.change!!, 0.0)
        assertEquals(today.minusDays(2), window.latest!!.date)
        val single = BodyMeasurementSeries.window(rows.take(1), today, ChartRange.All)
        assertNull(single.change)
        assertEquals(1, single.points.size)
    }

    @Test
    fun freeCanCreateWaistButNotANewProDate() {
        val free = SelectiveFeatureEntitlements(emptySet())
        assertTrue(
            BodyMeasurementAccess.canCreateOnDate(BodyMeasurementType.WAIST, false) { free.hasAccess(it) }
        )
        assertFalse(
            BodyMeasurementAccess.canCreateOnDate(BodyMeasurementType.CHEST, false) { free.hasAccess(it) }
        )
        assertTrue(
            BodyMeasurementAccess.canCreateOnDate(BodyMeasurementType.CHEST, true) { free.hasAccess(it) }
        )
        val pro = SelectiveFeatureEntitlements(setOf(AppFeature.AdvancedBodyMeasurements))
        assertTrue(
            BodyMeasurementAccess.canCreateOnDate(BodyMeasurementType.HIPS, false) { pro.hasAccess(it) }
        )
    }

    private fun measurement(
        id: Long,
        type: BodyMeasurementType,
        date: LocalDate,
        value: Double
    ): BodyMeasurement {
        return BodyMeasurement(id, type.code, date, value, "MANUAL", null, 1L, 1L)
    }
}
