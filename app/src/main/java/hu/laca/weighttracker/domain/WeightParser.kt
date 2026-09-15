package hu.laca.weighttracker.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class WeightParseError {
    Empty,
    Malformed,
    NotFinite,
    TooManyDecimals,
    OutOfRange
}

sealed class WeightParseResult {
    data class Valid(val kilograms: Double) : WeightParseResult()
    data class Invalid(val error: WeightParseError) : WeightParseResult()
}

object WeightParser {
    const val MIN_KG = 30.0
    const val MAX_KG = 300.0

    fun parseUserInput(raw: String): WeightParseResult = parse(raw, acceptComma = true)

    fun parseCsvWeight(raw: String): WeightParseResult = parse(raw, acceptComma = false)

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

    private fun parse(raw: String, acceptComma: Boolean): WeightParseResult {
        val trimmed = raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
        if (trimmed.isEmpty()) {
            return WeightParseResult.Invalid(WeightParseError.Empty)
        }
        val separatorCount = trimmed.count { it == ',' || it == '.' }
        if (separatorCount > 1) {
            return WeightParseResult.Invalid(WeightParseError.Malformed)
        }
        if (!acceptComma && trimmed.contains(',')) {
            return WeightParseResult.Invalid(WeightParseError.Malformed)
        }
        val normalized = trimmed.replace(',', '.')
        if (!normalized.matches(Regex("""\d{1,3}(\.\d+)?"""))) {
            return WeightParseResult.Invalid(WeightParseError.Malformed)
        }
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return WeightParseResult.Invalid(WeightParseError.Malformed)
        }
        val stripped = decimal.stripTrailingZeros()
        if (stripped.scale() > 1) {
            return WeightParseResult.Invalid(WeightParseError.TooManyDecimals)
        }
        val kilograms = stripped.setScale(1, RoundingMode.HALF_UP).toDouble()
        if (!kilograms.isFinite()) {
            return WeightParseResult.Invalid(WeightParseError.NotFinite)
        }
        if (kilograms < MIN_KG || kilograms > MAX_KG) {
            return WeightParseResult.Invalid(WeightParseError.OutOfRange)
        }
        return WeightParseResult.Valid(kilograms)
    }
}
