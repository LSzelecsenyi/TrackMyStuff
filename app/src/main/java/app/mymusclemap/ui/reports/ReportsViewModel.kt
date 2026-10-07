package app.mymusclemap.ui.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.ProAccess
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.reports.AvailableReport
import app.mymusclemap.domain.achievements.JourneyEvaluator
import app.mymusclemap.domain.reports.ReportCatalog
import app.mymusclemap.domain.reports.ReportHistory
import app.mymusclemap.domain.reports.ReportHistoryEvidence
import app.mymusclemap.domain.reports.ReportInputs
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportLogic
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.reports.ReportSummary
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReportsUiState(
    val loading: Boolean = true,
    val kind: ReportKind = ReportKind.Monthly,
    /** Closed periods for [kind], newest first, as returned by [ReportCatalog]. */
    val reports: List<AvailableReport> = emptyList(),
    val summaries: Map<String, ReportSummary> = emptyMap(),
    /** Longer report kinds the current entitlements do not grant. */
    val lockedKinds: Set<ReportKind> = emptySet(),
    val lockedFeature: AppFeature? = null
) {
    val kindLocked: Boolean get() = kind in lockedKinds
}

class ReportsViewModel(
    sessionRepository: WorkoutSessionRepository,
    scheduledWorkoutRepository: ScheduledWorkoutRepository,
    weightRepository: WeightRepository,
    dateProvider: DateProvider,
    private val entitlements: FeatureEntitlements = OpenFeatureEntitlements,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val onMonthlyReportGenerated: suspend () -> Unit = {}
) : ViewModel() {
    private val selectedKind = savedStateHandle.getStateFlow(KIND, ReportKind.Monthly.name)
    private val previewKind = MutableStateFlow<ReportKind?>(null)
    private val lockedFeature = MutableStateFlow<AppFeature?>(null)

    val uiState: StateFlow<ReportsUiState> = combine(
        combine(
            sessionRepository.observeSessions(),
            sessionRepository.observeCompletedAggregates(),
            scheduledWorkoutRepository.observeHistorical(),
            weightRepository.observeAll()
        ) { sessions, aggregates, scheduled, weights ->
            PersistedReports(sessions, aggregates, scheduled, weights)
        },
        dateProvider.observeToday(),
        selectedKind,
        previewKind,
        combine(lockedFeature, entitlements.changes()) { locked, _ -> locked }
    ) { persisted, today, kindName, preview, locked ->
        val historyStart = ReportHistory.earliestDate(
            ReportHistoryEvidence.fromPersisted(
                sessions = persisted.sessions,
                scheduled = persisted.scheduled,
                bodyWeights = persisted.weights
            ),
            today
        )
        val inputs = ReportInputs(
            sessions = persisted.aggregates,
            scheduled = persisted.scheduled,
            bodyWeights = persisted.weights
        )
        val savedKind = coerced(resolvedKind(kindName))
        val lockedKinds = ReportKind.entries.filter { !canAccess(it) }.toSet()
        val displayedKind = if (preview != null && preview in lockedKinds) preview else savedKind
        val summaries = ReportKind.entries
            .filter { canAccess(it) }
            .flatMap { reportKind -> ReportCatalog.available(reportKind, today, historyStart) }
            .associate { available ->
                reportKey(available.period) to ReportLogic.summarize(
                    period = available.period,
                    inputs = inputs,
                    today = today,
                    historyStart = historyStart
                )
            }
        ReportsUiState(
            loading = false,
            kind = displayedKind,
            reports = ReportCatalog.available(displayedKind, today, historyStart),
            summaries = summaries,
            lockedKinds = lockedKinds,
            lockedFeature = locked
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ReportsUiState()
    )

    fun onKindSelected(kind: ReportKind) {
        val feature = kind.requiredFeature
        if (feature == null) {
            previewKind.value = null
            lockedFeature.value = null
            savedStateHandle[KIND] = kind.name
            return
        }
        ProAccess.run(
            entitlements = entitlements,
            feature = feature,
            onLocked = {
                previewKind.value = kind
                lockedFeature.value = feature
            },
            onAllowed = {
                previewKind.value = null
                lockedFeature.value = null
                savedStateHandle[KIND] = kind.name
            }
        )
    }

    fun openReport(period: ReportPeriod, onAllowed: () -> Unit) {
        val feature = period.kind.requiredFeature
        val allow = {
            recordMonthlyReportIfGenerated(period)
            onAllowed()
        }
        if (feature == null) {
            allow()
            return
        }
        ProAccess.run(
            entitlements = entitlements,
            feature = feature,
            onLocked = { lockedFeature.value = feature },
            onAllowed = allow
        )
    }

    private fun recordMonthlyReportIfGenerated(period: ReportPeriod) {
        val workouts = summary(period.kind.name, period.startInclusive.toString())
            ?.activity
            ?.workoutCount
            ?: 0
        if (!JourneyEvaluator.monthlyReportQualifies(period.kind == ReportKind.Monthly, workouts)) {
            return
        }
        viewModelScope.launch { onMonthlyReportGenerated() }
    }

    fun showLockedReports() {
        lockedFeature.value = AppFeature.AdvancedReports
    }

    fun consumeLockedFeature() {
        lockedFeature.value = null
    }

    fun denial(kindName: String?): AppFeature? {
        val kind = ReportKind.entries.firstOrNull { it.name == kindName } ?: return null
        val feature = kind.requiredFeature ?: return null
        return if (entitlements.hasAccess(feature)) null else feature
    }

    fun summary(kindName: String?, startIso: String?): ReportSummary? {
        if (denial(kindName) != null || kindName.isNullOrBlank() || startIso.isNullOrBlank()) {
            return null
        }
        return uiState.value.summaries["$kindName|$startIso"]
    }

    private fun resolvedKind(kindName: String): ReportKind {
        return ReportKind.entries.firstOrNull { it.name == kindName } ?: ReportKind.Monthly
    }

    private fun coerced(kind: ReportKind): ReportKind {
        return if (canAccess(kind)) kind else ReportKind.Monthly
    }

    private fun canAccess(kind: ReportKind): Boolean {
        val feature = kind.requiredFeature ?: return true
        return entitlements.hasAccess(feature)
    }

    private companion object {
        const val KIND = "reports_kind"
    }
}

internal fun reportKey(period: ReportPeriod): String {
    return "${period.kind.name}|${period.startInclusive}"
}

private data class PersistedReports(
    val sessions: List<WorkoutSession>,
    val aggregates: List<WorkoutSessionAggregate>,
    val scheduled: List<ScheduledWorkout>,
    val weights: List<WeightMeasurement>
)
