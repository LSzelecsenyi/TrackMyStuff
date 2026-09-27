package app.mymusclemap.domain.body

import app.mymusclemap.domain.DateValidationError
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class BodyMeasurementParseError {
    Empty,
    Malformed,
    NotFinite,
    TooManyDecimals,
    OutOfRange
}

sealed class BodyMeasurementParseResult {
    data class Valid(val value: Double) : BodyMeasurementParseResult()
    data class Invalid(val error: BodyMeasurementParseError) : BodyMeasurementParseResult()
}

object BodyMeasurementParser {
    fun parseUserInput(raw: String, type: BodyMeasurementType): BodyMeasurementParseResult {
        return parse(raw, acceptComma = true, minimum = type.minimum, maximum = type.maximum)
    }

    fun filterUserInput(incoming: String): String {
        val builder = StringBuilder()
        var separator: Char? = null
        var integerDigits = 0
        var fractionDigits = 0
        incoming.forEach { char ->
            when {
                char.isDigit() && separator == null && integerDigits < 3 -> {
                    builder.append(char)
                    integerDigits++
                }
                char.isDigit() && separator != null && fractionDigits < 1 -> {
                    builder.append(char)
                    fractionDigits++
                }
                (char == ',' || char == '.') && separator == null && integerDigits > 0 -> {
                    separator = char
                    builder.append(char)
                }
            }
        }
        return builder.toString()
    }

    fun quantize(value: Double): Double {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    private fun parse(
        raw: String,
        acceptComma: Boolean,
        minimum: Double,
        maximum: Double
    ): BodyMeasurementParseResult {
        val trimmed = raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
        if (trimmed.isEmpty()) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.Empty)
        }
        val separatorCount = trimmed.count { it == ',' || it == '.' }
        if (separatorCount > 1) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.Malformed)
        }
        if (!acceptComma && trimmed.contains(',')) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.Malformed)
        }
        val normalized = trimmed.replace(',', '.')
        if (!normalized.matches(Regex("""\d{1,3}(\.\d+)?"""))) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.Malformed)
        }
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.Malformed)
        }
        val stripped = decimal.stripTrailingZeros()
        if (stripped.scale() > 1) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.TooManyDecimals)
        }
        val value = stripped.setScale(1, RoundingMode.HALF_UP).toDouble()
        if (!value.isFinite()) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.NotFinite)
        }
        if (value < minimum || value > maximum) {
            return BodyMeasurementParseResult.Invalid(BodyMeasurementParseError.OutOfRange)
        }
        return BodyMeasurementParseResult.Valid(value)
    }
}

sealed class BodyMeasurementValidationResult {
    data class Valid(
        val date: LocalDate,
        val value: Double
    ) : BodyMeasurementValidationResult()

    data class Invalid(
        val valueError: BodyMeasurementParseError? = null,
        val dateError: DateValidationError? = null
    ) : BodyMeasurementValidationResult()
}

object BodyMeasurementValidator {
    fun validate(
        type: BodyMeasurementType,
        date: LocalDate,
        rawValue: String,
        today: LocalDate
    ): BodyMeasurementValidationResult {
        val parsed = BodyMeasurementParser.parseUserInput(rawValue, type)
        val future = date.isAfter(today)
        return when {
            parsed is BodyMeasurementParseResult.Invalid || future -> BodyMeasurementValidationResult.Invalid(
                valueError = (parsed as? BodyMeasurementParseResult.Invalid)?.error,
                dateError = if (future) DateValidationError.Future else null
            )
            parsed is BodyMeasurementParseResult.Valid -> BodyMeasurementValidationResult.Valid(
                date = date,
                value = parsed.value
            )
            else -> BodyMeasurementValidationResult.Invalid(valueError = BodyMeasurementParseError.Malformed)
        }
    }
}
