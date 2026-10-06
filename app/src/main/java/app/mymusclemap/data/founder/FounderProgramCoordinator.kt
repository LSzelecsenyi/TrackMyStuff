package app.mymusclemap.data.founder

import app.mymusclemap.data.local.WorkoutSessionDao
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.entitlement.FounderProgramLogic
import app.mymusclemap.domain.entitlement.FounderProgramResult
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderQualification
import app.mymusclemap.domain.entitlement.FounderWorkoutRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class FounderProgramView(
    val ready: Boolean = false,
    val state: FounderProgramState = FounderProgramState(),
    val qualification: FounderQualification,
    val workouts: List<FounderWorkoutRecord> = emptyList(),
    val feedbackText: String = ""
)

/**
 * Runs [FounderProgramLogic] against completed native Strict sessions.
 *
 * A qualifying workout is a session whose status is COMPLETED, whose import fingerprint
 * is empty, and whose stored workout date falls on [FounderProgramState.enrolledOn]
 * through the deadline, inclusive. The model stores dates, not enrollment time, so a
 * native workout earlier on the enrollment calendar day counts. A workout on an earlier
 * date does not. Health Connect sessions are not rows in this table and are never added.
 *
 * Calling completion handling twice still counts each session id once.
 *
 * "Submitted" for the Tester Analytics Report means the tester explicitly shared the
 * generated report and Android accepted that share. Opening the Founder screen does not
 * submit it.
 */
class FounderProgramCoordinator(
    private val store: FounderProgramStore,
    private val sessions: WorkoutSessionDao,
    private val dateProvider: DateProvider,
    private val rules: FounderProgramRules,
    private val onEntitlementChanged: () -> Unit = {}
) {
    private val logic = FounderProgramLogic(rules)
    private val mutex = Mutex()
    private val viewState = MutableStateFlow(emptyView(rules))

    val view: StateFlow<FounderProgramView> = viewState.asStateFlow()

    fun currentState(): FounderProgramState = viewState.value.state

    suspend fun refresh() {
        mutex.withLock {
            reevaluate()
        }
    }

    suspend fun enroll() {
        mutex.withLock {
            if (viewState.value.state.backendOwned) {
                return@withLock
            }
            val result = logic.enroll(viewState.value.state, dateProvider.today())
            if (result is FounderProgramResult.Changed) {
                store.save(result.state)
            }
            reevaluate()
        }
    }

    /**
     * Writes the backend enrollment response as the Founder record.
     * Later local refresh does not replace this status, these dates, or these counts.
     */
    suspend fun applyBackendEnrollment(snapshot: BackendFounderSnapshot) {
        mutex.withLock {
            val state = snapshot.toProgramState()
            store.save(state)
            publish(state, qualifyingWorkouts(state), store.loadFeedbackText())
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun onNativeWorkoutCompleted(clientWorkoutId: String = "") {
        refresh()
    }

    suspend fun recordFeedback(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        return mutex.withLock {
            if (viewState.value.state.backendOwned) {
                return@withLock false
            }
            val workouts = qualifyingWorkouts(viewState.value.state)
            val result = logic.recordFeedback(
                state = viewState.value.state,
                text = trimmed,
                workouts = workouts.map { it.asCompletedWorkout() },
                today = dateProvider.today()
            )
            if (result is FounderProgramResult.Changed) {
                store.saveFeedbackText(trimmed)
                store.save(result.state)
            }
            reevaluate()
            result is FounderProgramResult.Changed
        }
    }

    suspend fun submitTesterAnalyticsReport() {
        mutex.withLock {
            if (viewState.value.state.backendOwned) {
                return@withLock
            }
            val workouts = qualifyingWorkouts(viewState.value.state)
            val result = logic.submitTesterAnalyticsReport(
                state = viewState.value.state,
                workouts = workouts.map { it.asCompletedWorkout() },
                today = dateProvider.today()
            )
            if (result is FounderProgramResult.Changed) {
                store.save(result.state)
            }
            reevaluate()
        }
    }

    suspend fun loadFeedbackDraft(): String = store.loadFeedbackText()

    suspend fun saveFeedbackDraft(text: String) {
        store.saveFeedbackText(text)
    }

    suspend fun clearFeedbackDraft() {
        store.saveFeedbackText("")
    }

    suspend fun loadOrCreateSubmissionId(): String {
        store.loadSubmissionId()?.let { return it }
        val created = java.util.UUID.randomUUID().toString()
        store.saveSubmissionId(created)
        return created
    }

    private suspend fun reevaluate() {
        val loaded = store.load()
        val workouts = qualifyingWorkouts(loaded)
        if (loaded.backendOwned) {
            publish(loaded, workouts, store.loadFeedbackText())
            return
        }
        val result = logic.refresh(
            state = loaded,
            workouts = workouts.map { it.asCompletedWorkout() },
            today = dateProvider.today()
        )
        val next = result.state
        if (result is FounderProgramResult.Changed) {
            store.save(next)
        }
        publish(next, workouts, store.loadFeedbackText())
    }

    private fun displayRules(state: FounderProgramState): FounderProgramRules {
        if (state.serverRequiredWorkouts < 1 || state.serverRequiredDistinctDays < 1) {
            return rules
        }
        val temporary = if (state.serverTemporaryProWorkouts >= 1) {
            state.serverTemporaryProWorkouts
        } else {
            rules.temporaryProWorkoutCount
        }
        return rules.copy(
            temporaryProWorkoutCount = temporary,
            founderWorkoutCount = state.serverRequiredWorkouts,
            requiredDistinctWorkoutDays = state.serverRequiredDistinctDays
        )
    }

    private suspend fun qualifyingWorkouts(state: FounderProgramState): List<FounderWorkoutRecord> {
        val start = state.enrolledOn ?: return emptyList()
        val end = state.deadline ?: return emptyList()
        return sessions.completedNativeWorkouts().mapNotNull { row ->
            val day = runCatching { LocalDate.parse(row.workoutDate) }.getOrNull() ?: return@mapNotNull null
            if (day.isBefore(start) || day.isAfter(end)) {
                return@mapNotNull null
            }
            FounderWorkoutRecord(id = row.id, day = day, name = row.templateName)
        }.distinctBy { it.id }
    }

    private fun publish(
        state: FounderProgramState,
        workouts: List<FounderWorkoutRecord>,
        feedbackText: String
    ) {
        val previous = viewState.value
        viewState.value = FounderProgramView(
            ready = true,
            state = state,
            qualification = if (state.backendOwned) {
                FounderQualification(
                    nativeCompletedWorkouts = state.serverQualifyingWorkouts,
                    distinctNativeWorkoutDays = state.serverDistinctDays,
                    feedbackRecorded = state.feedbackRecorded,
                    testerAnalyticsReportSubmitted = state.testerAnalyticsReportSubmitted,
                    rules = displayRules(state)
                )
            } else {
                rules.qualify(
                    workouts = workouts.map { it.asCompletedWorkout() },
                    feedbackRecorded = state.feedbackRecorded,
                    testerAnalyticsReportSubmitted = state.testerAnalyticsReportSubmitted
                )
            },
            workouts = workouts,
            feedbackText = feedbackText
        )
        if (!previous.ready || previous.state != state) {
            onEntitlementChanged()
        }
    }

    companion object {
        fun emptyView(rules: FounderProgramRules): FounderProgramView {
            return FounderProgramView(
                qualification = rules.qualify(
                    workouts = emptyList(),
                    feedbackRecorded = false,
                    testerAnalyticsReportSubmitted = false
                )
            )
        }
    }
}
