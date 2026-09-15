package hu.laca.weighttracker.ui.components

import hu.laca.weighttracker.domain.DateValidationError
import hu.laca.weighttracker.domain.WeightParseError
import hu.laca.weighttracker.domain.model.WeightMeasurement
import java.time.LocalDate
import java.util.Locale

data class EditorUiState(
    val date: LocalDate,
    val weightInput: String,
    val existing: WeightMeasurement?,
    val weightError: WeightParseError?,
    val dateError: DateValidationError?,
    val showDeleteConfirm: Boolean = false
) {
    val isUpdating: Boolean get() = existing != null
}

fun formatWeightInput(weightKg: Double): String {
    return String.format(Locale.forLanguageTag("hu-HU"), "%.1f", weightKg)
        .replace('.', ',')
}

sealed interface UserMessage {
    data object Created : UserMessage
    data object Updated : UserMessage
    data object Deleted : UserMessage
    data class ImportSucceeded(val created: Int, val updated: Int) : UserMessage
    data object ExportSucceeded : UserMessage
    data object ExportFailed : UserMessage
    data object ImportReadFailed : UserMessage
    data object PaletteSaved : UserMessage
}
