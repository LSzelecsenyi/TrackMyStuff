package hu.laca.weighttracker.domain.csv

import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.WeightParseResult
import hu.laca.weighttracker.domain.WeightParser
import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Weight CSV backup format (UTF-8):
 *
 * Header (required, exact):
 *   date,weight_kg
 *
 * Each subsequent non-empty row:
 *   - date: ISO-8601 local calendar date `yyyy-MM-dd`
 *   - weight_kg: kilograms using a period decimal separator, at most one decimal place
 *
 * Duplicate dates inside the file are rejected. Duplicate dates against existing
 * measurements replace the stored weight for that date after explicit user confirmation.
 */
object WeightCsv {
    const val HEADER = "date,weight_kg"
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    data class ParsedRow(
        val date: LocalDate,
        val weightKg: Double,
        val lineNumber: Int
    )

    data class RowError(
        val lineNumber: Int,
        val reason: CsvErrorReason,
        val detail: String? = null
    )

    enum class CsvErrorReason {
        MissingHeader,
        InvalidHeader,
        WrongColumnCount,
        InvalidDate,
        FutureDate,
        InvalidWeight,
        DuplicateDateInFile
    }

    sealed class ParseResult {
        data class Success(val rows: List<ParsedRow>) : ParseResult()
        data class Failure(val errors: List<RowError>) : ParseResult()
    }

    fun export(measurements: List<WeightMeasurement>): String {
        val body = measurements
            .sortedBy { it.date }
            .joinToString(separator = "\n") { measurement ->
                "${measurement.date.format(isoDate)},${formatCsvWeight(measurement.weightKg)}"
            }
        return if (body.isEmpty()) {
            HEADER + "\n"
        } else {
            HEADER + "\n" + body + "\n"
        }
    }

    fun parse(
        content: String,
        today: LocalDate
    ): ParseResult {
        val text = content.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        val lines = text.split('\n')
        val errors = mutableListOf<RowError>()
        if (lines.isEmpty() || lines.all { it.isBlank() }) {
            return ParseResult.Failure(
                listOf(RowError(lineNumber = 1, reason = CsvErrorReason.MissingHeader))
            )
        }
        val headerLine = lines.first().trim()
        if (headerLine.isEmpty()) {
            errors += RowError(1, CsvErrorReason.MissingHeader)
        } else if (headerLine != HEADER) {
            errors += RowError(1, CsvErrorReason.InvalidHeader, headerLine)
        }
        val rows = mutableListOf<ParsedRow>()
        val seenDates = mutableMapOf<LocalDate, Int>()
        lines.drop(1).forEachIndexed { index, rawLine ->
            val lineNumber = index + 2
            if (rawLine.isBlank()) return@forEachIndexed
            val columns = rawLine.split(',')
            if (columns.size != 2) {
                errors += RowError(lineNumber, CsvErrorReason.WrongColumnCount, rawLine)
                return@forEachIndexed
            }
            val date = try {
                LocalDate.parse(columns[0].trim(), isoDate)
            } catch (_: DateTimeParseException) {
                errors += RowError(lineNumber, CsvErrorReason.InvalidDate, columns[0].trim())
                return@forEachIndexed
            } catch (_: DateTimeException) {
                errors += RowError(lineNumber, CsvErrorReason.InvalidDate, columns[0].trim())
                return@forEachIndexed
            }
            if (date.isAfter(today)) {
                errors += RowError(lineNumber, CsvErrorReason.FutureDate, date.toString())
            }
            when (val weight = WeightParser.parseCsvWeight(columns[1].trim())) {
                is WeightParseResult.Invalid -> {
                    errors += RowError(
                        lineNumber = lineNumber,
                        reason = CsvErrorReason.InvalidWeight,
                        detail = weight.error.name.lowercase(Locale.US)
                    )
                }
                is WeightParseResult.Valid -> {
                    val previousLine = seenDates.put(date, lineNumber)
                    if (previousLine != null) {
                        errors += RowError(
                            lineNumber = lineNumber,
                            reason = CsvErrorReason.DuplicateDateInFile,
                            detail = date.toString()
                        )
                    } else if (errors.none { it.lineNumber == lineNumber }) {
                        rows += ParsedRow(date, weight.kilograms, lineNumber)
                    }
                }
            }
        }
        return if (errors.isEmpty()) {
            ParseResult.Success(rows)
        } else {
            ParseResult.Failure(errors)
        }
    }

    private fun formatCsvWeight(weightKg: Double): String {
        return String.format(Locale.US, "%.1f", weightKg)
    }
}
