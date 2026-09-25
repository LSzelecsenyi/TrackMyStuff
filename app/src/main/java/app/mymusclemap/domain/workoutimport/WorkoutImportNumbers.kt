package app.mymusclemap.domain.workoutimport

import java.math.BigDecimal

internal object WorkoutImportNumbers {
    fun parsePositiveInt(raw: String, max: Int): IntParse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return IntParse.Empty
        }
        if (!trimmed.matches(Regex("""\d+"""))) {
            return IntParse.Invalid(WorkoutImportErrorCode.InvalidInteger)
        }
        val value = trimmed.toIntOrNull()
            ?: return IntParse.Invalid(WorkoutImportErrorCode.OutOfRange)
        if (value <= 0 || value > max) {
            return IntParse.Invalid(WorkoutImportErrorCode.OutOfRange)
        }
        return IntParse.Valid(value)
    }

    fun parseDecimal(
        raw: String,
        maxDecimals: Int,
        minExclusiveZero: Boolean,
        minInclusive: BigDecimal? = null,
        maxInclusive: BigDecimal
    ): DecimalParse {
        val trimmed = raw.trim().replace("\u00A0", "").replace(" ", "")
        if (trimmed.isEmpty()) {
            return DecimalParse.Empty
        }
        val separators = trimmed.count { it == ',' || it == '.' }
        if (separators > 1) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.InvalidDecimal)
        }
        val normalized = trimmed.replace(',', '.')
        if (!normalized.matches(Regex("""\d+(\.\d+)?"""))) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.InvalidDecimal)
        }
        val decimal = try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.InvalidDecimal)
        }
        val fraction = decimal.scale()
        if (fraction > maxDecimals) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.InvalidPrecision)
        }
        if (minExclusiveZero && decimal.compareTo(BigDecimal.ZERO) <= 0) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.OutOfRange)
        }
        if (minInclusive != null && decimal.compareTo(minInclusive) < 0) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.OutOfRange)
        }
        if (decimal.compareTo(maxInclusive) > 0) {
            return DecimalParse.Invalid(WorkoutImportErrorCode.OutOfRange)
        }
        return DecimalParse.Valid(decimal)
    }

    fun canonical(value: BigDecimal?): String {
        if (value == null) {
            return ""
        }
        return value.stripTrailingZeros().toPlainString()
    }

    sealed class IntParse {
        data object Empty : IntParse()
        data class Valid(val value: Int) : IntParse()
        data class Invalid(val code: WorkoutImportErrorCode) : IntParse()
    }

    sealed class DecimalParse {
        data object Empty : DecimalParse()
        data class Valid(val value: BigDecimal) : DecimalParse()
        data class Invalid(val code: WorkoutImportErrorCode) : DecimalParse()
    }
}
