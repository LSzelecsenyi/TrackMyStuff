package hu.laca.weighttracker.domain

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
        today: LocalDate
    ): MeasurementValidationResult {
        val weightResult = WeightParser.parseUserInput(rawWeight)
        val future = date.isAfter(today)
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
}
