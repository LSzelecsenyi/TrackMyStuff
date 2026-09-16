package hu.laca.weighttracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.domain.DashboardAssembler
import hu.laca.weighttracker.domain.DashboardSnapshot
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.DaySheetFactory
import hu.laca.weighttracker.domain.DaySheetState
import hu.laca.weighttracker.domain.Greeting
import hu.laca.weighttracker.domain.GreetingSelector
import hu.laca.weighttracker.domain.MeasurementValidationResult
import hu.laca.weighttracker.domain.MeasurementValidator
import hu.laca.weighttracker.domain.calendar.MonthGrid
import hu.laca.weighttracker.domain.calendar.MonthGridCalculator
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapAssembler
import hu.laca.weighttracker.domain.musclemap.MuscleHeatmapState
import hu.laca.weighttracker.domain.model.ChartRange
import hu.laca.weighttracker.domain.model.SaveOutcome
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.EditorUiState
import hu.laca.weighttracker.ui.components.UserMessage
import hu.laca.weighttracker.ui.components.formatWeightInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class DashboardUiState(
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
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val greeting: Greeting = Greeting.Day,
    val displayedMonth: YearMonth = YearMonth.of(1970, 1),
    val monthGrid: MonthGrid = MonthGridCalculator.grid(
        month = YearMonth.of(1970, 1),
        today = LocalDate.of(1970, 1, 1),
        measuredDates = emptySet()
    ),
    val daySheet: DaySheetState? = null,
    val showDayDeleteConfirm: Boolean = false,
    val heatmap: MuscleHeatmapState = MuscleHeatmapAssembler.assemble(emptyList(), LocalDate.of(1970, 1, 1))
)

private data class DashboardChrome(
    val range: ChartRange,
    val editor: EditorUiState?,
    val message: UserMessage?,
    val month: YearMonth,
    val selectedDay: LocalDate?
)

class DashboardViewModel(
    private val repository: WeightRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider
) : ViewModel() {
    private val chartRange = MutableStateFlow(ChartRange.Days30)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())
    private val displayedMonth = MutableStateFlow(YearMonth.from(dateProvider.today()))
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val showDayDeleteConfirm = MutableStateFlow(false)

    private val chrome = combine(
        chartRange,
        editor,
        userMessage,
        displayedMonth,
        selectedDay
    ) { range, editorState, message, month, day ->
        DashboardChrome(range, editorState, message, month, day)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val completedCounts = displayedMonth.flatMapLatest { month ->
        sessionRepository.observeCompletedCounts(
            MonthGridCalculator.gridStart(month),
            MonthGridCalculator.gridEnd(month)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dayWorkouts = selectedDay.flatMapLatest { date ->
        if (date == null) {
            flowOf(emptyList())
        } else {
            sessionRepository.observeSummariesOnDate(date)
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            measurements,
            chrome,
            showDayDeleteConfirm,
            completedCounts,
            dayWorkouts
        ) { items, chromeState, deleteConfirm, counts, workouts ->
            val today = dateProvider.today()
            val snapshot = DashboardAssembler.assemble(items, today, chromeState.range)
            DashboardUiState(
                snapshot = snapshot,
                chartRange = chromeState.range,
                editor = chromeState.editor,
                userMessage = chromeState.message,
                today = today,
                greeting = GreetingSelector.from(dateProvider.now().toLocalTime()),
                displayedMonth = chromeState.month,
                monthGrid = MonthGridCalculator.grid(
                    month = chromeState.month,
                    today = today,
                    measuredDates = snapshot.measurementDates,
                    completedWorkoutCounts = counts
                ),
                daySheet = chromeState.selectedDay?.let { date ->
                    DaySheetFactory.create(date, items, today, workouts)
                },
                showDayDeleteConfirm = deleteConfirm
            )
        },
        sessionRepository.observeHeatmapExercises()
    ) { state, exercises ->
        state.copy(heatmap = MuscleHeatmapAssembler.assemble(exercises, state.today))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(
            today = dateProvider.today(),
            greeting = GreetingSelector.from(dateProvider.now().toLocalTime()),
            displayedMonth = YearMonth.from(dateProvider.today()),
            monthGrid = MonthGridCalculator.grid(
                month = YearMonth.from(dateProvider.today()),
                today = dateProvider.today(),
                measuredDates = emptySet()
            )
        )
    )

    init {
        viewModelScope.launch {
            repository.observeAll().collect { measurements.value = it }
        }
    }

    fun onChartRangeSelected(range: ChartRange) {
        chartRange.value = range
    }

    fun onPreviousMonth() {
        displayedMonth.value = displayedMonth.value.minusMonths(1)
    }

    fun onNextMonth() {
        displayedMonth.value = displayedMonth.value.plusMonths(1)
    }

    fun selectDay(date: LocalDate) {
        if (!MonthGridCalculator.canOpenDay(date, dateProvider.today())) {
            return
        }
        selectedDay.value = date
        showDayDeleteConfirm.value = false
    }

    fun dismissDaySheet() {
        selectedDay.value = null
        showDayDeleteConfirm.value = false
    }

    fun recordSelectedDay() {
        val date = selectedDay.value ?: return
        selectedDay.value = null
        showDayDeleteConfirm.value = false
        openEditor(date)
    }

    fun requestDayDelete() {
        if (uiState.value.daySheet?.measurement != null) {
            showDayDeleteConfirm.value = true
        }
    }

    fun dismissDayDelete() {
        showDayDeleteConfirm.value = false
    }

    fun confirmDayDelete() {
        val existing = uiState.value.daySheet?.measurement ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            selectedDay.value = null
            showDayDeleteConfirm.value = false
            userMessage.value = UserMessage.Deleted
        }
    }

    fun openDelete(date: LocalDate) {
        openEditor(date)
        editor.value = editor.value?.copy(showDeleteConfirm = true)
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
