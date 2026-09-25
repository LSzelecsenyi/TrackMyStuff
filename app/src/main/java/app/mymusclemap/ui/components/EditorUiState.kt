package app.mymusclemap.ui.components

import app.mymusclemap.domain.DateValidationError
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.locale.AppLocale
import java.time.LocalDate

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
    return String.format(AppLocale.UI, "%.1f", weightKg)
}

sealed interface UserMessage {
    data object Created : UserMessage
    data object Updated : UserMessage
    data object Deleted : UserMessage
    data object WorkoutDeleted : UserMessage
    data object WorkoutDeleteFailed : UserMessage
    data class ImportSucceeded(val created: Int, val updated: Int) : UserMessage
    data object ExportSucceeded : UserMessage
    data object ExportFailed : UserMessage
    data object ImportReadFailed : UserMessage
    data object PaletteSaved : UserMessage
    data object ScheduleRemoved : UserMessage
    data object ScheduleDuplicate : UserMessage
    data object ScheduleLinked : UserMessage
    data object ScheduleTemplateArchived : UserMessage
    data object ScheduleTemplateNotFound : UserMessage
    data object WorkoutAlreadyActive : UserMessage
    data object WorkoutTemplateEmpty : UserMessage
    data object WorkoutTemplateArchived : UserMessage
}
