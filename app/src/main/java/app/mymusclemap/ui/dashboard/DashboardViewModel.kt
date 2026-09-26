package app.mymusclemap.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.DashboardAssembler
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.DaySheetFactory
import app.mymusclemap.domain.DaySheetState
import app.mymusclemap.domain.MeasurementValidationResult
import app.mymusclemap.domain.WeeklyOverview
import app.mymusclemap.domain.WeeklyOverviewLogic
import app.mymusclemap.domain.MeasurementValidator
import app.mymusclemap.domain.WeightParseError
import app.mymusclemap.domain.calendar.MonthGrid
import app.mymusclemap.domain.calendar.MonthGridCalculator
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleHeatmapState
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.domain.model.SaveOutcome
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.onboarding.OnboardingGuide
import app.mymusclemap.domain.workout.RescheduleWorkoutResult
import app.mymusclemap.domain.workout.ScheduleWorkoutResult
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.ScheduledWorkoutUiLogic
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateListItem
import app.mymusclemap.domain.workout.UnscheduleWorkoutResult
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import app.mymusclemap.ui.components.EditorUiState
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.formatWeightInput
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
    val weeklyOverview: WeeklyOverview = WeeklyOverview(),
    val displayedMonth: YearMonth = YearMonth.of(1970, 1),
    val monthGrid: MonthGrid = MonthGridCalculator.grid(
        month = YearMonth.of(1970, 1),
        today = LocalDate.of(1970, 1, 1),
        measuredDates = emptySet()
    ),
    val daySheet: DaySheetState? = null,
    val showDayDeleteConfirm: Boolean = false,
    val heatmap: MuscleHeatmapState = MuscleHeatmapAssembler.assemble(emptyList(), LocalDate.of(1970, 1, 1)),
    val schedulePickerVisible: Boolean = false,
    val availableTemplates: List<TemplateListItem> = emptyList(),
    val rescheduleTarget: ScheduledWorkout? = null,
    val removeTarget: ScheduledWorkout? = null,
    val scheduleBusy: Boolean = false,
    val scheduleActionError: UserMessage? = null,
    val startedSessionId: Long? = null,
    val journalSessionId: Long? = null,
    val onboarding: OnboardingGuide = OnboardingGuide.Inactive,
    val onboardingWeightInput: String = "",
    val onboardingWeightError: WeightParseError? = null,
    val openWeightDetailsForOnboarding: Boolean = false
)

private data class DashboardChrome(
    val range: ChartRange,
    val editor: EditorUiState?,
    val message: UserMessage?,
    val month: YearMonth,
    val selectedDay: LocalDate?
)

private data class SessionSignals(
    val counts: Map<LocalDate, Int>,
    val dayWorkouts: List<WorkoutSessionSummary>,
    val weekSessions: List<WorkoutSessionSummary>
)

private data class ScheduleSignals(
    val plannedCounts: Map<LocalDate, Int>,
    val daySchedules: List<ScheduledWorkout>,
    val templates: List<TemplateListItem>
)

private data class DialogChrome(
    val pickerVisible: Boolean,
    val reschedule: ScheduledWorkout?,
    val remove: ScheduledWorkout?,
    val busy: Boolean,
    val actionError: UserMessage?,
    val startedSessionId: Long?,
    val journalSessionId: Long?
)

private data class OnboardingChrome(
    val guide: OnboardingGuide,
    val weightInput: String,
    val weightError: WeightParseError?,
    val openWeightDetails: Boolean
)

class DashboardViewModel(
    private val repository: WeightRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val dateProvider: DateProvider,
    private val scheduledWorkoutRepository: ScheduledWorkoutRepository,
    private val templateRepository: WorkoutTemplateRepository,
    private val onboardingRepository: OnboardingRepository? = null
) : ViewModel() {
    private val chartRange = MutableStateFlow(ChartRange.Days30)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())
    private val displayedMonth = MutableStateFlow(YearMonth.from(dateProvider.today()))
    private val selectedDay = MutableStateFlow<LocalDate?>(null)
    private val showDayDeleteConfirm = MutableStateFlow(false)
    private val schedulePickerOpen = MutableStateFlow(false)
    private val rescheduleTarget = MutableStateFlow<ScheduledWorkout?>(null)
    private val removeTarget = MutableStateFlow<ScheduledWorkout?>(null)
    private val scheduleBusy = MutableStateFlow(false)
    private val scheduleActionError = MutableStateFlow<UserMessage?>(null)
    private val startedSessionId = MutableStateFlow<Long?>(null)
    private val journalSessionId = MutableStateFlow<Long?>(null)
    private val starting = MutableStateFlow(false)
    private val onboardingWeightInput = MutableStateFlow("")
    private val onboardingWeightError = MutableStateFlow<WeightParseError?>(null)
    private val openWeightDetailsForOnboarding = MutableStateFlow(false)
    private val onboardingGuide = onboardingRepository?.observe() ?: flowOf(OnboardingGuide.Inactive)

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthSchedules = displayedMonth.flatMapLatest { month ->
        scheduledWorkoutRepository.observeBetween(
            MonthGridCalculator.gridStart(month),
            MonthGridCalculator.gridEnd(month)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val daySchedules = selectedDay.flatMapLatest { date ->
        if (date == null) {
            flowOf(emptyList())
        } else {
            scheduledWorkoutRepository.observeOnDate(date)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val weekSessions = dateProvider.observeToday().flatMapLatest { today ->
        sessionRepository.observeSummariesBetween(
            WeeklyOverviewLogic.windowStart(today),
            today
        )
    }

    private val scheduleSignals = combine(
        monthSchedules,
        daySchedules,
        templateRepository.observeActive()
    ) { monthItems, dayItems, templates ->
        ScheduleSignals(
            plannedCounts = ScheduledWorkoutUiLogic.plannedMarkerCounts(monthItems),
            daySchedules = dayItems,
            templates = LocalizedLabelOrder.sorted(
                templates,
                label = { it.template.name },
                key = { it.template.id.toString() }
            )
        )
    }

    private val dialogs = combine(
        combine(schedulePickerOpen, rescheduleTarget, removeTarget, scheduleBusy) {
            picker, reschedule, remove, busy ->
            Quad(picker, reschedule, remove, busy)
        },
        combine(scheduleActionError, startedSessionId, journalSessionId) { error, started, journal ->
            Triple(error, started, journal)
        }
    ) { openState, navState ->
        DialogChrome(
            pickerVisible = openState.first,
            reschedule = openState.second,
            remove = openState.third,
            busy = openState.fourth,
            actionError = navState.first,
            startedSessionId = navState.second,
            journalSessionId = navState.third
        )
    }

    private val onboardingChrome = combine(
        onboardingGuide,
        onboardingWeightInput,
        onboardingWeightError,
        openWeightDetailsForOnboarding
    ) { guide, input, error, open ->
        OnboardingChrome(guide, input, error, open)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            measurements,
            chrome,
            showDayDeleteConfirm,
            combine(completedCounts, dayWorkouts, weekSessions) { counts, workouts, week ->
                SessionSignals(counts, workouts, week)
            },
            combine(scheduleSignals, dateProvider.observeToday(), ::Pair)
        ) { items, chromeState, deleteConfirm, sessions, schedulesAndToday ->
            val schedules = schedulesAndToday.first
            val today = schedulesAndToday.second
            val snapshot = DashboardAssembler.assemble(items, today, chromeState.range)
            DashboardUiState(
                snapshot = snapshot,
                chartRange = chromeState.range,
                editor = chromeState.editor,
                userMessage = chromeState.message,
                today = today,
                weeklyOverview = WeeklyOverviewLogic.assemble(today, sessions.weekSessions, items),
                displayedMonth = chromeState.month,
                monthGrid = MonthGridCalculator.grid(
                    month = chromeState.month,
                    today = today,
                    measuredDates = snapshot.measurementDates,
                    completedWorkoutCounts = sessions.counts,
                    plannedWorkoutCounts = schedules.plannedCounts
                ),
                daySheet = chromeState.selectedDay?.let { date ->
                    DaySheetFactory.create(
                        date = date,
                        measurements = items,
                        today = today,
                        workouts = sessions.dayWorkouts,
                        scheduledWorkouts = schedules.daySchedules
                    )
                },
                showDayDeleteConfirm = deleteConfirm,
                availableTemplates = ScheduledWorkoutUiLogic.availableTemplates(
                    schedules.templates,
                    schedules.daySchedules
                )
            )
        },
        sessionRepository.observeHeatmapExercises(),
        dialogs,
        onboardingChrome
    ) { state, exercises, dialogState, onboardingState ->
        state.copy(
            heatmap = MuscleHeatmapAssembler.assemble(exercises, state.today),
            schedulePickerVisible = dialogState.pickerVisible,
            rescheduleTarget = dialogState.reschedule,
            removeTarget = dialogState.remove,
            scheduleBusy = dialogState.busy,
            scheduleActionError = dialogState.actionError,
            startedSessionId = dialogState.startedSessionId,
            journalSessionId = dialogState.journalSessionId,
            onboarding = onboardingState.guide,
            onboardingWeightInput = onboardingState.weightInput,
            onboardingWeightError = onboardingState.weightError,
            openWeightDetailsForOnboarding = onboardingState.openWeightDetails
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DashboardUiState(
            today = dateProvider.today(),
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
        selectedDay.value = date
        showDayDeleteConfirm.value = false
        schedulePickerOpen.value = false
        rescheduleTarget.value = null
        removeTarget.value = null
        scheduleActionError.value = null
    }

    fun dismissDaySheet() {
        selectedDay.value = null
        showDayDeleteConfirm.value = false
        schedulePickerOpen.value = false
        rescheduleTarget.value = null
        removeTarget.value = null
        scheduleActionError.value = null
    }

    fun recordSelectedDay() {
        val date = selectedDay.value ?: return
        if (date.isAfter(dateProvider.today())) {
            return
        }
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
        if (date.isAfter(dateProvider.today())) {
            return
        }
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

    fun openSchedulePicker() {
        if (selectedDay.value == null) {
            return
        }
        scheduleActionError.value = null
        schedulePickerOpen.value = true
    }

    fun dismissSchedulePicker() {
        if (scheduleBusy.value) {
            return
        }
        schedulePickerOpen.value = false
        scheduleActionError.value = null
    }

    fun scheduleTemplate(templateId: Long) {
        val date = selectedDay.value ?: return
        if (scheduleBusy.value) {
            return
        }
        scheduleBusy.value = true
        viewModelScope.launch {
            try {
                when (scheduledWorkoutRepository.schedule(templateId, date)) {
                    is ScheduleWorkoutResult.Scheduled -> {
                        schedulePickerOpen.value = false
                        scheduleActionError.value = null
                    }
                    ScheduleWorkoutResult.Duplicate -> {
                        scheduleActionError.value = UserMessage.ScheduleDuplicate
                    }
                    ScheduleWorkoutResult.TemplateArchived -> {
                        scheduleActionError.value = UserMessage.ScheduleTemplateArchived
                    }
                    ScheduleWorkoutResult.TemplateNotFound -> {
                        scheduleActionError.value = UserMessage.ScheduleTemplateNotFound
                    }
                }
            } finally {
                scheduleBusy.value = false
            }
        }
    }

    fun openReschedule(item: ScheduledWorkout) {
        val actions = ScheduledWorkoutUiLogic.actions(item, dateProvider.today())
        if (!actions.canReschedule) {
            return
        }
        scheduleActionError.value = null
        rescheduleTarget.value = item
    }

    fun dismissReschedule() {
        if (scheduleBusy.value) {
            return
        }
        rescheduleTarget.value = null
        scheduleActionError.value = null
    }

    fun confirmReschedule(date: LocalDate) {
        val target = rescheduleTarget.value ?: return
        if (scheduleBusy.value) {
            return
        }
        if (!ScheduledWorkoutUiLogic.canSelectRescheduleDate(date, dateProvider.today())) {
            return
        }
        if (date == target.scheduledDate) {
            rescheduleTarget.value = null
            return
        }
        scheduleBusy.value = true
        viewModelScope.launch {
            try {
                when (scheduledWorkoutRepository.reschedule(target.id, date)) {
                    RescheduleWorkoutResult.Moved -> {
                        rescheduleTarget.value = null
                        scheduleActionError.value = null
                    }
                    RescheduleWorkoutResult.Duplicate -> {
                        scheduleActionError.value = UserMessage.ScheduleDuplicate
                    }
                    RescheduleWorkoutResult.LinkedToSession -> {
                        scheduleActionError.value = UserMessage.ScheduleLinked
                    }
                    RescheduleWorkoutResult.NotFound -> {
                        scheduleActionError.value = UserMessage.ScheduleTemplateNotFound
                    }
                }
            } finally {
                scheduleBusy.value = false
            }
        }
    }

    fun openRemove(item: ScheduledWorkout) {
        val actions = ScheduledWorkoutUiLogic.actions(item, dateProvider.today())
        if (!actions.canUnschedule) {
            return
        }
        removeTarget.value = item
    }

    fun dismissRemove() {
        if (scheduleBusy.value) {
            return
        }
        removeTarget.value = null
    }

    fun confirmRemove() {
        val target = removeTarget.value ?: return
        if (scheduleBusy.value) {
            return
        }
        scheduleBusy.value = true
        viewModelScope.launch {
            try {
                when (scheduledWorkoutRepository.unschedule(target.id)) {
                    UnscheduleWorkoutResult.Removed -> {
                        removeTarget.value = null
                        userMessage.value = UserMessage.ScheduleRemoved
                    }
                    UnscheduleWorkoutResult.LinkedToSession -> {
                        removeTarget.value = null
                        userMessage.value = UserMessage.ScheduleLinked
                    }
                    UnscheduleWorkoutResult.NotFound -> {
                        removeTarget.value = null
                    }
                }
            } finally {
                scheduleBusy.value = false
            }
        }
    }

    fun startScheduled(id: Long) {
        val item = uiState.value.daySheet?.scheduledWorkouts?.firstOrNull { it.id == id } ?: return
        val actions = ScheduledWorkoutUiLogic.actions(item, dateProvider.today())
        if (!actions.canStart) {
            return
        }
        val templateId = item.templateId ?: return
        if (starting.value || scheduleBusy.value) {
            return
        }
        starting.value = true
        scheduleBusy.value = true
        viewModelScope.launch {
            try {
                when (val result = sessionRepository.start(templateId, item.id)) {
                    is StartWorkoutResult.Started -> {
                        startedSessionId.value = result.sessionId
                    }
                    StartWorkoutResult.AlreadyActive,
                    StartWorkoutResult.ScheduleAlreadyStarted -> {
                        userMessage.value = UserMessage.WorkoutAlreadyActive
                    }
                    StartWorkoutResult.TemplateArchived -> {
                        userMessage.value = UserMessage.WorkoutTemplateArchived
                    }
                    StartWorkoutResult.TemplateEmpty -> {
                        userMessage.value = UserMessage.WorkoutTemplateEmpty
                    }
                    StartWorkoutResult.TemplateNotFound,
                    StartWorkoutResult.ScheduleNotFound,
                    StartWorkoutResult.ScheduleNotOnToday,
                    StartWorkoutResult.ScheduleTemplateMismatch -> {
                        userMessage.value = UserMessage.ScheduleTemplateNotFound
                    }
                    is StartWorkoutResult.InvalidBodyWeight -> {
                        userMessage.value = UserMessage.WorkoutTemplateEmpty
                    }
                }
            } finally {
                starting.value = false
                scheduleBusy.value = false
            }
        }
    }

    fun continueScheduled(id: Long) {
        val item = uiState.value.daySheet?.scheduledWorkouts?.firstOrNull { it.id == id } ?: return
        val actions = ScheduledWorkoutUiLogic.actions(item, dateProvider.today())
        if (!actions.canContinue) {
            return
        }
        val sessionId = item.sessionId ?: return
        startedSessionId.value = sessionId
    }

    fun openScheduledJournal(id: Long) {
        val item = uiState.value.daySheet?.scheduledWorkouts?.firstOrNull { it.id == id } ?: return
        val actions = ScheduledWorkoutUiLogic.actions(item, dateProvider.today())
        if (!actions.canOpenJournal) {
            return
        }
        val sessionId = item.sessionId ?: return
        journalSessionId.value = sessionId
    }

    fun consumeStartedSession() {
        startedSessionId.value = null
    }

    fun consumeJournalSession() {
        journalSessionId.value = null
    }

    fun consumeOpenWeightDetailsForOnboarding() {
        openWeightDetailsForOnboarding.value = false
    }

    fun markHeatmapSeen() {
        viewModelScope.launch { onboardingRepository?.markHeatmapSeen() }
    }

    fun markCalendarSeen() {
        viewModelScope.launch { onboardingRepository?.markCalendarSeen() }
    }

    fun dismissOnboardingReminder() {
        viewModelScope.launch { onboardingRepository?.dismissReminder() }
    }

    fun onOnboardingWeightChange(value: String) {
        onboardingWeightInput.value = value
        onboardingWeightError.value = null
    }

    fun skipOnboardingWeight() {
        viewModelScope.launch {
            onboardingRepository?.markWeightIntroduced()
            onboardingWeightInput.value = ""
            onboardingWeightError.value = null
        }
    }

    fun saveOnboardingWeight() {
        val input = onboardingWeightInput.value
        when (val result = MeasurementValidator.validate(dateProvider.today(), input, dateProvider.today())) {
            is MeasurementValidationResult.Invalid -> {
                onboardingWeightError.value = result.weightError
            }
            is MeasurementValidationResult.Valid -> {
                viewModelScope.launch {
                    repository.save(result.date, result.weightKg)
                    onboardingRepository?.markWeightIntroduced()
                    onboardingWeightInput.value = ""
                    onboardingWeightError.value = null
                    openWeightDetailsForOnboarding.value = true
                }
            }
        }
    }

    fun consumeMessage() {
        userMessage.value = null
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
