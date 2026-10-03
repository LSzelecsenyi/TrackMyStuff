package app.mymusclemap.domain.entitlement

import java.time.LocalDate

enum class FounderProgramStatus {
    NotEnrolled,
    ActiveFree,
    ActivePro,
    PendingApproval,
    Approved,
    Expired,
    Rejected;

    /** Temporary Tester Pro. Approved uses Founder Lifetime instead. */
    fun grantsTemporaryPro(): Boolean {
        return this == ActivePro || this == PendingApproval
    }
}

enum class WorkoutOrigin {
    NativeStrict,
    HealthConnect
}

data class CompletedWorkout(
    val day: LocalDate,
    val origin: WorkoutOrigin
)

data class FounderProgramState(
    val status: FounderProgramStatus = FounderProgramStatus.NotEnrolled,
    val enrolledOn: LocalDate? = null,
    val deadline: LocalDate? = null,
    val feedbackRecorded: Boolean = false,
    val testerAnalyticsReportSubmitted: Boolean = false,
    val rejectionReason: String? = null
)

data class FounderQualification(
    val nativeCompletedWorkouts: Int,
    val distinctNativeWorkoutDays: Int,
    val feedbackRecorded: Boolean,
    val testerAnalyticsReportSubmitted: Boolean,
    val rules: FounderProgramRules
) {
    val temporaryProUnlocked: Boolean
        get() = nativeCompletedWorkouts >= rules.temporaryProWorkoutCount

    val testerRequirementsComplete: Boolean
        get() {
            val workoutsMet = nativeCompletedWorkouts >= rules.founderWorkoutCount &&
                distinctNativeWorkoutDays >= rules.requiredDistinctWorkoutDays
            val feedbackMet = !rules.feedbackRequired || feedbackRecorded
            val reportMet = !rules.testerAnalyticsReportRequired || testerAnalyticsReportSubmitted
            return workoutsMet && feedbackMet && reportMet
        }
}

/**
 * Business thresholds for one Founder program. [FounderProgramLogic] reads only this value.
 * [Production] is the published program. Other instances are for tests or a variant-specific
 * composition choice; they are not an entitlement grant.
 */
data class FounderProgramRules(
    val temporaryProWorkoutCount: Int,
    val founderWorkoutCount: Int,
    val requiredDistinctWorkoutDays: Int,
    val qualificationWindowDays: Int,
    val feedbackRequired: Boolean,
    val testerAnalyticsReportRequired: Boolean
) {
    fun deadline(enrolledOn: LocalDate): LocalDate {
        return enrolledOn.plusDays(qualificationWindowDays.toLong())
    }

    fun qualify(
        workouts: List<CompletedWorkout>,
        feedbackRecorded: Boolean,
        testerAnalyticsReportSubmitted: Boolean
    ): FounderQualification {
        val native = workouts.filter { it.origin == WorkoutOrigin.NativeStrict }
        return FounderQualification(
            nativeCompletedWorkouts = native.size,
            distinctNativeWorkoutDays = native.map { it.day }.toSet().size,
            feedbackRecorded = feedbackRecorded,
            testerAnalyticsReportSubmitted = testerAnalyticsReportSubmitted,
            rules = this
        )
    }

    companion object {
        val Production = FounderProgramRules(
            temporaryProWorkoutCount = 5,
            founderWorkoutCount = 10,
            requiredDistinctWorkoutDays = 6,
            qualificationWindowDays = 45,
            feedbackRequired = true,
            testerAnalyticsReportRequired = true
        )
    }
}

sealed interface FounderProgramResult {
    val state: FounderProgramState

    data class Changed(override val state: FounderProgramState) : FounderProgramResult
    data class Unchanged(override val state: FounderProgramState) : FounderProgramResult
}

/**
 * Founder-program transitions. Approval and rejection are the manual review boundary.
 * They do not talk to a store.
 *
 * The deadline date is inclusive when tester-controlled requirements are already complete.
 * If those requirements are still incomplete on the deadline date, the program expires.
 * [FounderProgramStatus.PendingApproval] is not expired by later review delay.
 */
class FounderProgramLogic(
    private val rules: FounderProgramRules
) {
    fun enroll(state: FounderProgramState, today: LocalDate): FounderProgramResult {
        if (state.status != FounderProgramStatus.NotEnrolled) {
            return FounderProgramResult.Unchanged(state)
        }
        return FounderProgramResult.Changed(
            FounderProgramState(
                status = FounderProgramStatus.ActiveFree,
                enrolledOn = today,
                deadline = rules.deadline(today)
            )
        )
    }

    fun recordFeedback(state: FounderProgramState, text: String, workouts: List<CompletedWorkout>, today: LocalDate): FounderProgramResult {
        if (!acceptsTesterInput(state.status) || text.isBlank()) {
            return FounderProgramResult.Unchanged(state)
        }
        return advance(state.copy(feedbackRecorded = true), workouts, today)
    }

    fun submitTesterAnalyticsReport(
        state: FounderProgramState,
        workouts: List<CompletedWorkout>,
        today: LocalDate
    ): FounderProgramResult {
        if (!acceptsTesterInput(state.status)) {
            return FounderProgramResult.Unchanged(state)
        }
        return advance(state.copy(testerAnalyticsReportSubmitted = true), workouts, today)
    }

    fun refresh(state: FounderProgramState, workouts: List<CompletedWorkout>, today: LocalDate): FounderProgramResult {
        if (state.status != FounderProgramStatus.ActiveFree && state.status != FounderProgramStatus.ActivePro) {
            return FounderProgramResult.Unchanged(state)
        }
        return advance(state, workouts, today)
    }

    fun approve(state: FounderProgramState): FounderProgramResult {
        if (state.status != FounderProgramStatus.PendingApproval) {
            return FounderProgramResult.Unchanged(state)
        }
        return FounderProgramResult.Changed(state.copy(status = FounderProgramStatus.Approved))
    }

    fun reject(state: FounderProgramState, reason: String): FounderProgramResult {
        if (state.status != FounderProgramStatus.PendingApproval || reason.isBlank()) {
            return FounderProgramResult.Unchanged(state)
        }
        return FounderProgramResult.Changed(
            state.copy(
                status = FounderProgramStatus.Rejected,
                rejectionReason = reason.trim()
            )
        )
    }

    private fun advance(
        state: FounderProgramState,
        workouts: List<CompletedWorkout>,
        today: LocalDate
    ): FounderProgramResult {
        val qualification = rules.qualify(
            workouts = workoutsInsideWindow(state, workouts),
            feedbackRecorded = state.feedbackRecorded,
            testerAnalyticsReportSubmitted = state.testerAnalyticsReportSubmitted
        )
        val deadline = state.deadline
        val next = when {
            qualification.testerRequirementsComplete && deadline != null && !today.isAfter(deadline) ->
                FounderProgramStatus.PendingApproval
            deadline != null && !today.isBefore(deadline) ->
                FounderProgramStatus.Expired
            qualification.temporaryProUnlocked || state.status == FounderProgramStatus.ActivePro ->
                FounderProgramStatus.ActivePro
            else -> FounderProgramStatus.ActiveFree
        }
        val updated = state.copy(status = next)
        return if (updated == state) FounderProgramResult.Unchanged(state) else FounderProgramResult.Changed(updated)
    }

    private fun acceptsTesterInput(status: FounderProgramStatus): Boolean {
        return status == FounderProgramStatus.ActiveFree || status == FounderProgramStatus.ActivePro
    }

    private fun workoutsInsideWindow(
        state: FounderProgramState,
        workouts: List<CompletedWorkout>
    ): List<CompletedWorkout> {
        val start = state.enrolledOn ?: return emptyList()
        val end = state.deadline ?: return emptyList()
        return workouts.filter { workout ->
            !workout.day.isBefore(start) && !workout.day.isAfter(end)
        }
    }
}
