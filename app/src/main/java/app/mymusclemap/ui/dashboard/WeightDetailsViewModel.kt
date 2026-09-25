package app.mymusclemap.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.domain.DashboardAssembler
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.MeasurementValidationResult
import app.mymusclemap.domain.MeasurementValidator
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.domain.model.SaveOutcome
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.ui.components.EditorUiState
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.formatWeightInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeightDetailsUiState(
    val snapshot: DashboardSnapshot = DashboardSnapshot(
        isEmpty = true,
        latest = null,
        changeFromPreviousKg = null,
        currentWeek = null,
        previousWeekChangeKg = null,
        recentWeeks = emptyList(),
        chartPoints = emptyList(),
        recentItems = emptyList(),
        todayHasMeasurement = false
    ),
    val chartRange: ChartRange = ChartRange.Days30,
    val editor: EditorUiState? = null,
    val userMessage: UserMessage? = null,
    val today: LocalDate = LocalDate.of(1970, 1, 1)
)

class WeightDetailsViewModel(
    private val repository: WeightRepository,
    private val dateProvider: DateProvider
) : ViewModel() {
    private val chartRange = MutableStateFlow(ChartRange.Days30)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())

    val uiState: StateFlow<WeightDetailsUiState> = combine(
        measurements,
        chartRange,
        editor,
        userMessage
    ) { items, range, editorState, message ->
        val today = dateProvider.today()
        WeightDetailsUiState(
            snapshot = DashboardAssembler.assemble(items, today, range),
            chartRange = range,
            editor = editorState,
            userMessage = message,
            today = today
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WeightDetailsUiState(today = dateProvider.today())
    )

    init {
        viewModelScope.launch {
            repository.observeAll().collect { measurements.value = it }
        }
    }

    fun onChartRangeSelected(range: ChartRange) {
        chartRange.value = range
    }

    fun openEditor(date: LocalDate = dateProvider.today()) {
        val existing = measurements.value.find { it.date == date }
        editor.value = EditorUiState(
            date = date,
            weightInput = existing?.let { formatWeightInput(it.weightKg) }.orEmpty(),
            existing = existing,
            weightError = null,
            dateError = null
        )
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun onEditorDateChange(date: LocalDate) {
        val current = editor.value ?: return
        val existing = measurements.value.find { it.date == date }
        editor.value = current.copy(
            date = date,
            existing = existing,
            weightInput = existing?.let { formatWeightInput(it.weightKg) } ?: current.weightInput,
            dateError = null,
            showDeleteConfirm = false
        )
    }

    fun onEditorWeightChange(value: String) {
        val current = editor.value ?: return
        editor.value = current.copy(weightInput = value, weightError = null)
    }

    fun saveEditor() {
        val current = editor.value ?: return
        when (val result = MeasurementValidator.validate(current.date, current.weightInput, dateProvider.today())) {
            is MeasurementValidationResult.Invalid -> {
                editor.value = current.copy(
                    weightError = result.weightError,
                    dateError = result.dateError
                )
            }
            is MeasurementValidationResult.Valid -> {
                viewModelScope.launch {
                    val outcome = repository.save(result.date, result.weightKg)
                    editor.value = null
                    userMessage.value = if (outcome == SaveOutcome.Updated) {
                        UserMessage.Updated
                    } else {
                        UserMessage.Created
                    }
                }
            }
        }
    }

    fun requestDelete() {
        val current = editor.value ?: return
        if (current.existing != null) {
            editor.value = current.copy(showDeleteConfirm = true)
        }
    }

    fun dismissDelete() {
        editor.value = editor.value?.copy(showDeleteConfirm = false)
    }

    fun confirmDelete() {
        val existing = editor.value?.existing ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            editor.value = null
            userMessage.value = UserMessage.Deleted
        }
    }

    fun consumeMessage() {
        userMessage.value = null
    }
}
