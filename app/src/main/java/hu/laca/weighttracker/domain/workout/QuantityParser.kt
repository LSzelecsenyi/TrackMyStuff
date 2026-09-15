package hu.laca.weighttracker.domain.workout

import java.math.BigDecimal
import java.math.RoundingMode

enum class QuantityParseError {
    Empty,
    Malformed,
    NotFinite,
    Negative,
    TooManyDecimals,
    OutOfRange
}

sealed class DecimalParseResult {
    data class Valid(val value: Double) : DecimalParseResult()
    data class Invalid(val error: QuantityParseError) : DecimalParseResult()
}

sealed class IntParseResult {
    data class Valid(val value: Int) : IntParseResult()
    data class Invalid(val error: QuantityParseError) : IntParseResult()
}

object QuantityParser {
    const val WEIGHT_MAX_DECIMALS = 2
    const val DISTANCE_MAX_DECIMALS = 3
    const val MAX_WEIGHT_KG = 500.0
    const val MAX_DISTANCE_METERS = 100_000.0
    const val MAX_REPS = 200
    const val MAX_MINUTES = 600
    const val MAX_SECONDS_COMPONENT = 59

    fun parseDecimal(
        raw: String,
        maxDecimals: Int,
        minExclusiveZero: Boolean,
        max: Double
    ): DecimalParseResult {
        val trimmed = raw.trim()
            .replace("\u00A0", "")
            .replace(" ", "")
        if (trimmed.isEmpty()) {
            return DecimalParseResult.Invalid(QuantityParseError.Empty)
        }
        val separatorCount = trimmed.count { it == ',' || it == '.' }
        if (separatorCount > 1) {
            return DecimalParseResult.Invalid(QuantityParseError.Malformed)
        }
        val normalized = trimmed.replace(',', '.')
        if (!normalized.matches(Regex("""\d+(\.\d+)?"""))) {
            return DecimalParseResult.Invalid(QuantityParseError.Malformed)
        }
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return DecimalParseResult.Invalid(QuantityParseError.Malformed)
        }
        val stripped = decimal.stripTrailingZeros()
        if (stripped.scale() > maxDecimals) {
            return DecimalParseResult.Invalid(QuantityParseError.TooManyDecimals)
        }
        val value = stripped.setScale(maxDecimals.coerceAtLeast(0), RoundingMode.HALF_UP).toDouble()
        if (!value.isFinite()) {
            return DecimalParseResult.Invalid(QuantityParseError.NotFinite)
        }
        if (value < 0.0) {
            return DecimalParseResult.Invalid(QuantityParseError.Negative)
        }
        if (minExclusiveZero && value == 0.0) {
            return DecimalParseResult.Invalid(QuantityParseError.OutOfRange)
        }
        if (value > max) {
            return DecimalParseResult.Invalid(QuantityParseError.OutOfRange)
        }
        return DecimalParseResult.Valid(value)
    }

    fun parseInt(raw: String, min: Int, max: Int): IntParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return IntParseResult.Invalid(QuantityParseError.Empty)
        }
        if (!trimmed.matches(Regex("""\d+"""))) {
            return IntParseResult.Invalid(QuantityParseError.Malformed)
        }
        val value = trimmed.toIntOrNull()
            ?: return IntParseResult.Invalid(QuantityParseError.Malformed)
        if (value < min) {
            return IntParseResult.Invalid(QuantityParseError.OutOfRange)
        }
        if (value > max) {
            return IntParseResult.Invalid(QuantityParseError.OutOfRange)
        }
        return IntParseResult.Valid(value)
    }

    fun toSeconds(minutes: Int, seconds: Int): Int {
        return minutes * 60 + seconds
    }

    fun fromSeconds(totalSeconds: Int): Pair<Int, Int> {
        val safe = totalSeconds.coerceAtLeast(0)
        return safe / 60 to safe % 60
    }

    fun toMeters(value: Double, unit: DistanceUnit): Double {
        return if (unit == DistanceUnit.KILOMETERS) {
            BigDecimal.valueOf(value).multiply(BigDecimal(1000)).toDouble()
        } else {
            value
        }
    }

    fun fromMeters(meters: Double, unit: DistanceUnit): Double {
        return if (unit == DistanceUnit.KILOMETERS) {
            BigDecimal.valueOf(meters).divide(BigDecimal(1000)).toDouble()
        } else {
            meters
        }
    }

    fun formatDisplay(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString().replace('.', ',')
        }
    }
}

object RepetitionTarget {
    fun display(minReps: Int, maxReps: Int): String {
        return if (minReps == maxReps) {
            minReps.toString()
        } else {
            "$minReps–$maxReps"
        }
    }

    fun fromExact(value: Int): Pair<Int, Int> = value to value
}
