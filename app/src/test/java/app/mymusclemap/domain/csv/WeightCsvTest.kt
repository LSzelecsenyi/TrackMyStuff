package app.mymusclemap.domain.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightCsvTest {
    private val today = LocalDate.of(2026, 3, 11)

    @Test
    fun parsesValidFileAndRejectsPartialImportOnAnyError() {
        val valid = """
            date,weight_kg
            2026-03-01,80.0
            2026-03-02,80.5
        """.trimIndent()
        val parsed = WeightCsv.parse(valid, today) as WeightCsv.ParseResult.Success
        assertEquals(2, parsed.rows.size)
        assertEquals(80.5, parsed.rows.last().weightKg, 0.0)

        val invalid = """
            date,weight_kg
            2026-03-01,80.0
            not-a-date,81.0
        """.trimIndent()
        val failed = WeightCsv.parse(invalid, today) as WeightCsv.ParseResult.Failure
        assertTrue(failed.errors.any { it.reason == WeightCsv.CsvErrorReason.InvalidDate })
    }

    @Test
    fun duplicateDatesInFileFailTheEntireFile() {
        val csv = """
            date,weight_kg
            2026-03-01,80.0
            2026-03-01,81.0
        """.trimIndent()
        val failed = WeightCsv.parse(csv, today) as WeightCsv.ParseResult.Failure
        assertTrue(failed.errors.any { it.reason == WeightCsv.CsvErrorReason.DuplicateDateInFile })
    }

    @Test
    fun csvRequiresPeriodDecimalAndIsoDates() {
        val commaDecimal = """
            date,weight_kg
            2026-03-01,80,4
        """.trimIndent()
        val failedColumns = WeightCsv.parse(commaDecimal, today) as WeightCsv.ParseResult.Failure
        assertTrue(failedColumns.errors.any { it.reason == WeightCsv.CsvErrorReason.WrongColumnCount })

        val future = """
            date,weight_kg
            2026-03-12,80.0
        """.trimIndent()
        val failedFuture = WeightCsv.parse(future, today) as WeightCsv.ParseResult.Failure
        assertTrue(failedFuture.errors.any { it.reason == WeightCsv.CsvErrorReason.FutureDate })
    }

    @Test
    fun exportUsesStableHeaderAndPeriodDecimals() {
        val csv = WeightCsv.export(
            listOf(
                app.mymusclemap.measurement("2026-03-02", 80.5),
                app.mymusclemap.measurement("2026-03-01", 80.0)
            )
        )
        assertTrue(csv.startsWith("date,weight_kg\n"))
        assertTrue(csv.contains("2026-03-01,80.0"))
        assertTrue(csv.contains("2026-03-02,80.5"))
    }
}
