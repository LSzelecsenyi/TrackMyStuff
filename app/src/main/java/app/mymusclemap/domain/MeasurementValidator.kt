package app.mymusclemap.domain

import app.mymusclemap.domain.units.MeasurementInput
import app.mymusclemap.domain.units.UnitConversions
import app.mymusclemap.domain.units.UnitSystem
import java.time.LocalDate

enum class DateValidationError {
    Future
}

sealed class MeasurementValidationResult {
    data class Valid(
        val date: LocalDate,
        val weightKg: Double
    ) : MeasurementValidationResult()

    data class Invalid(
        val weightError: WeightParseError? = null,
        val dateError: DateValidationError? = null
    ) : MeasurementValidationResult() {
        val hasError: Boolean get() = weightError != null || dateError != null
    }
}

object MeasurementValidator {
    fun validate(
        date: LocalDate,
        rawWeight: String,
        today: LocalDate,
        system: UnitSystem = UnitSystem.METRIC,
        storedKilograms: Double? = null
    ): MeasurementValidationResult {
        val future = date.isAfter(today)
        if (storedKilograms != null &&
            MeasurementInput.sameText(rawWeight, MeasurementInput.bodyWeightInput(storedKilograms, system))
        ) {
            return if (future) {
                MeasurementValidationResult.Invalid(dateError = DateValidationError.Future)
            } else {
                MeasurementValidationResult.Valid(date = date, weightKg = storedKilograms)
            }
        }
        if (system == UnitSystem.IMPERIAL) {
            return validateImperial(date, rawWeight, future)
        }
        val weightResult = WeightParser.parseUserInput(rawWeight)
        return when {
            weightResult is WeightParseResult.Invalid || future -> MeasurementValidationResult.Invalid(
                weightError = (weightResult as? WeightParseResult.Invalid)?.error,
                dateError = if (future) DateValidationError.Future else null
            )
            weightResult is WeightParseResult.Valid -> MeasurementValidationResult.Valid(
                date = date,
                weightKg = weightResult.kilograms
            )
            else -> MeasurementValidationResult.Invalid(weightError = WeightParseError.Malformed)
        }
    }

    private fun validateImperial(
        date: LocalDate,
        rawWeight: String,
        future: Boolean
    ): MeasurementValidationResult {
        val trimmed = rawWeight.trim()
        if (trimmed.isEmpty()) {
            return MeasurementValidationResult.Invalid(
                weightError = WeightParseError.Empty,
                dateError = if (future) DateValidationError.Future else null
            )
        }
        val pounds = MeasurementInput.parseDecimal(rawWeight, 1)
        if (pounds == null) {
            val error = if (hasTooManyDecimals(rawWeight)) {
                WeightParseError.TooManyDecimals
            } else {
                WeightParseError.Malformed
            }
            return MeasurementValidationResult.Invalid(
                weightError = error,
                dateError = if (future) DateValidationError.Future else null
            )
        }
        val kilograms = UnitConversions.lbToKg(pounds)
        val minimum = UnitConversions.kgToLb(MeasurementInput.BODY_MIN_KG.toDouble())
            .setScale(1, java.math.RoundingMode.HALF_UP)
        val maximum = UnitConversions.kgToLb(MeasurementInput.BODY_MAX_KG.toDouble())
            .setScale(1, java.math.RoundingMode.HALF_UP)
        if (pounds < minimum || pounds > maximum) {
            return MeasurementValidationResult.Invalid(
                weightError = WeightParseError.OutOfRange,
                dateError = if (future) DateValidationError.Future else null
            )
        }
        if (future) {
            return MeasurementValidationResult.Invalid(dateError = DateValidationError.Future)
        }
        return MeasurementValidationResult.Valid(date = date, weightKg = kilograms.toDouble())
    }

    private fun hasTooManyDecimals(raw: String): Boolean {
        val normalized = raw.trim().replace(',', '.')
        val fraction = normalized.substringAfter('.', "")
        return fraction.length > 1 && fraction.all { it.isDigit() }
    }
}
