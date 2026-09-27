package app.mymusclemap.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.domain.DashboardAssembler
import app.mymusclemap.domain.DashboardSnapshot
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.DateValidationError
import app.mymusclemap.domain.MeasurementValidationResult
import app.mymusclemap.domain.MeasurementValidator
import app.mymusclemap.domain.body.BodyMeasurement
import app.mymusclemap.domain.body.BodyMeasurementAccess
import app.mymusclemap.domain.body.BodyMeasurementParseError
import app.mymusclemap.domain.body.BodyMeasurementParser
import app.mymusclemap.domain.body.BodyMeasurementSeries
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.body.BodyMeasurementValidationResult
import app.mymusclemap.domain.body.BodyMeasurementValidator
import app.mymusclemap.domain.body.BodyMeasurementWindow
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.domain.model.SaveOutcome
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.onboarding.OnboardingGuide
import app.mymusclemap.ui.components.EditorUiState
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.formatWeightInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BodyProgressRow(
    val type: BodyMeasurementType?,
    val typeCode: String,
    val latest: BodyMeasurement?,
    val change: Double?,
    val lockedEmpty: Boolean
)

data class BodyMeasurementDetail(
    val type: BodyMeasurementType?,
    val typeCode: String,
    val range: ChartRange,
    val window: BodyMeasurementWindow,
    val historyNewestFirst: List<BodyMeasurement>,
    val canAdd: Boolean,
    val showChart: Boolean
)

data class BodyEditorUiState(
    val type: BodyMeasurementType,
    val date: LocalDate,
    val valueInput: String,
    val existing: BodyMeasurement?,
    val valueError: BodyMeasurementParseError?,
    val dateError: DateValidationError?,
    val showDeleteConfirm: Boolean = false
) {
    val isUpdating: Boolean get() = existing != null
}

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
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val showWeightChartCoach: Boolean = false,
    val rows: List<BodyProgressRow> = emptyList(),
    val bodyChartRange: ChartRange = ChartRange.Days30,
    val details: Map<String, BodyMeasurementDetail> = emptyMap(),
    val measurementDetailVisible: Boolean = false,
    val bodyEditor: BodyEditorUiState? = null,
    val lockedFeature: AppFeature? = null
)

class WeightDetailsViewModel(
    private val repository: WeightRepository,
    private val dateProvider: DateProvider,
    private val onboardingRepository: OnboardingRepository? = null,
    private val bodyRepository: BodyMeasurementRepository? = null,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements
) : ViewModel() {
    private val chartRange = MutableStateFlow(ChartRange.Days30)
    private val bodyChartRange = MutableStateFlow(ChartRange.Days30)
    private val measurementDetailVisible = MutableStateFlow(false)
    private val editor = MutableStateFlow<EditorUiState?>(null)
    private val bodyEditor = MutableStateFlow<BodyEditorUiState?>(null)
    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val measurements = MutableStateFlow<List<WeightMeasurement>>(emptyList())
    private val bodyMeasurements = MutableStateFlow<List<BodyMeasurement>>(emptyList())
    private val lockedFeature = MutableStateFlow<AppFeature?>(null)

    val uiState: StateFlow<WeightDetailsUiState> = combine(
        combine(measurements, bodyMeasurements, chartRange, bodyChartRange) { weights, body, range, bodyRange ->
            BodySeriesInputs(weights, body, range, bodyRange)
        },
        combine(editor, bodyEditor, lockedFeature, measurementDetailVisible) { weightEditor, measurementEditor, locked, detailVisible ->
            BodyChromeInputs(weightEditor, measurementEditor, locked, detailVisible)
        },
        combine(
            userMessage,
            onboardingRepository?.observe() ?: flowOf(OnboardingGuide.Inactive)
        ) { message, guide ->
            message to guide
        }
    ) { series, chrome, messageGuide ->
        val today = dateProvider.today()
        val (message, guide) = messageGuide
        WeightDetailsUiState(
            snapshot = DashboardAssembler.assemble(series.weights, today, series.range),
            chartRange = series.range,
            editor = chrome.weightEditor,
            userMessage = message,
            today = today,
            showWeightChartCoach = guide.showWeightChartCoach,
            rows = rows(series.body, today),
            bodyChartRange = series.bodyRange,
            details = details(series.body, today, series.bodyRange),
            bodyEditor = chrome.measurementEditor,
            lockedFeature = chrome.locked,
            measurementDetailVisible = chrome.detailVisible
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
        val body = bodyRepository
        if (body != null) {
            viewModelScope.launch {
                body.observeAll().collect { bodyMeasurements.value = it }
            }
        }
    }

    fun onChartRangeSelected(range: ChartRange) {
        chartRange.value = range
    }

    fun onBodyChartRangeSelected(range: ChartRange) {
        bodyChartRange.value = range
    }

    fun showBodyMeasurementLocked(type: BodyMeasurementType) {
        lockedFeature.value = type.requiredFeature
    }

    fun setMeasurementDetailVisible(visible: Boolean) {
        measurementDetailVisible.value = visible
    }

    fun leaveMeasurementDetail() {
        measurementDetailVisible.value = false
        bodyEditor.value = null
        lockedFeature.value = null
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

    fun openBodyEditor(type: BodyMeasurementType, measurement: BodyMeasurement? = null) {
        val date = measurement?.date ?: dateProvider.today()
        val existing = bodyMeasurements.value.firstOrNull { it.typeCode == type.code && it.date == date }
            ?: measurement?.takeIf { it.typeCode == type.code }
        val creatingNewDate = existing == null
        if (!BodyMeasurementAccess.canCreateOnDate(type, !creatingNewDate, entitlements::hasAccess)) {
            lockedFeature.value = type.requiredFeature
            return
        }
        bodyEditor.value = BodyEditorUiState(
            type = type,
            date = existing?.date ?: date,
            valueInput = existing?.let { formatWeightInput(it.value) }.orEmpty(),
            existing = existing,
            valueError = null,
            dateError = null
        )
    }

    fun dismissBodyEditor() {
        bodyEditor.value = null
    }

    fun onBodyEditorDateChange(date: LocalDate) {
        val current = bodyEditor.value ?: return
        val existing = bodyMeasurements.value.firstOrNull { it.typeCode == current.type.code && it.date == date }
        if (existing == null &&
            !BodyMeasurementAccess.canCreateOnDate(current.type, dateAlreadyStored = false, entitlements::hasAccess)
        ) {
            lockedFeature.value = current.type.requiredFeature
            return
        }
        bodyEditor.value = current.copy(
            date = date,
            existing = existing,
            valueInput = existing?.let { formatWeightInput(it.value) } ?: current.valueInput,
            dateError = null,
            showDeleteConfirm = false
        )
    }

    fun onBodyEditorValueChange(value: String) {
        val current = bodyEditor.value ?: return
        bodyEditor.value = current.copy(
            valueInput = BodyMeasurementParser.filterUserInput(value),
            valueError = null
        )
    }

    fun saveBodyEditor() {
        val current = bodyEditor.value ?: return
        val repository = bodyRepository ?: return
        when (
            val result = BodyMeasurementValidator.validate(
                current.type,
                current.date,
                current.valueInput,
                dateProvider.today()
            )
        ) {
            is BodyMeasurementValidationResult.Invalid -> {
                bodyEditor.value = current.copy(
                    valueError = result.valueError,
                    dateError = result.dateError
                )
            }
            is BodyMeasurementValidationResult.Valid -> {
                val alreadyStored = bodyMeasurements.value.any {
                    it.typeCode == current.type.code && it.date == result.date
                }
                if (!BodyMeasurementAccess.canCreateOnDate(current.type, alreadyStored, entitlements::hasAccess)) {
                    lockedFeature.value = current.type.requiredFeature
                    return
                }
                viewModelScope.launch {
                    val outcome = repository.save(current.type.code, result.date, result.value)
                    bodyEditor.value = null
                    userMessage.value = if (outcome == SaveOutcome.Updated) {
                        UserMessage.Updated
                    } else {
                        UserMessage.Created
                    }
                }
            }
        }
    }

    fun requestBodyDelete() {
        val current = bodyEditor.value ?: return
        if (current.existing != null) {
            bodyEditor.value = current.copy(showDeleteConfirm = true)
        }
    }

    fun dismissBodyDelete() {
        bodyEditor.value = bodyEditor.value?.copy(showDeleteConfirm = false)
    }

    fun confirmBodyDelete() {
        val existing = bodyEditor.value?.existing ?: return
        val repository = bodyRepository ?: return
        viewModelScope.launch {
            repository.delete(existing.id)
            bodyEditor.value = null
            userMessage.value = UserMessage.Deleted
        }
    }

    fun dismissLockedFeature() {
        lockedFeature.value = null
    }

    fun consumeMessage() {
        userMessage.value = null
    }

    fun markWeightChartSeen() {
        viewModelScope.launch { onboardingRepository?.markWeightChartSeen() }
    }

    private fun rows(measurements: List<BodyMeasurement>, today: LocalDate): List<BodyProgressRow> {
        return grouped(measurements).map { (type, code, rows) ->
            val window = BodyMeasurementSeries.window(rows, today, ChartRange.All)
            val feature = type?.requiredFeature
            BodyProgressRow(
                type = type,
                typeCode = code,
                latest = window.latest,
                change = window.change,
                lockedEmpty = rows.none { !it.date.isAfter(today) } &&
                    feature != null &&
                    !entitlements.hasAccess(feature)
            )
        }
    }

    private fun details(
        measurements: List<BodyMeasurement>,
        today: LocalDate,
        range: ChartRange
    ): Map<String, BodyMeasurementDetail> {
        return grouped(measurements).associate { (type, code, rows) ->
            val window = BodyMeasurementSeries.window(rows, today, range)
            val todayStored = rows.any { it.date == today }
            val canAdd = type != null && !todayStored
            code to BodyMeasurementDetail(
                type = type,
                typeCode = code,
                range = range,
                window = window,
                historyNewestFirst = window.points.asReversed(),
                canAdd = canAdd,
                showChart = window.points.size >= 2
            )
        }
    }

    private fun grouped(
        measurements: List<BodyMeasurement>
    ): List<Triple<BodyMeasurementType?, String, List<BodyMeasurement>>> {
        val known = BodyMeasurementType.entries.map { type ->
            Triple(type, type.code, measurements.filter { it.typeCode == type.code })
        }
        val unknown = measurements.map { it.typeCode }
            .filter { BodyMeasurementType.known(it) == null }
            .distinct()
            .sorted()
            .map { code ->
                Triple(null, code, measurements.filter { it.typeCode == code })
            }
        return known + unknown
    }

    private data class BodySeriesInputs(
        val weights: List<WeightMeasurement>,
        val body: List<BodyMeasurement>,
        val range: ChartRange,
        val bodyRange: ChartRange
    )

    private data class BodyChromeInputs(
        val weightEditor: EditorUiState?,
        val measurementEditor: BodyEditorUiState?,
        val locked: AppFeature?,
        val detailVisible: Boolean
    )
}
