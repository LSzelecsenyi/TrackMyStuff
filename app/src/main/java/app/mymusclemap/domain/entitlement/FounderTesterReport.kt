package app.mymusclemap.domain.entitlement

import java.time.LocalDate

/**
 * One native Strict workout that can qualify for the Founder program.
 * Identity is the session id. Progress is derived from these rows, not from a counter.
 */
data class FounderWorkoutRecord(
    val id: Long,
    val day: LocalDate,
    val name: String
) {
    fun asCompletedWorkout(): CompletedWorkout {
        return CompletedWorkout(day = day, origin = WorkoutOrigin.NativeStrict)
    }
}

/**
 * Local Tester Analytics Report. It is generated on the device from data already stored.
 * Sharing this text is the pilot's submission. The file is user-owned export content and
 * is not an entitlement grant.
 */
object FounderTesterReport {
    fun render(
        versionName: String,
        rules: FounderProgramRules,
        state: FounderProgramState,
        qualification: FounderQualification,
        workouts: List<FounderWorkoutRecord>,
        feedbackText: String
    ): String {
        return buildString {
            appendLine("Strict Tester Analytics Report")
            appendLine("App version: $versionName")
            appendLine("Status: ${state.status.name}")
            appendLine("Enrolled on: ${state.enrolledOn ?: "—"}")
            appendLine("Qualification deadline: ${state.deadline ?: "—"}")
            appendLine(
                "Qualifying native workouts: ${qualification.nativeCompletedWorkouts} / ${rules.founderWorkoutCount}"
            )
            appendLine(
                "Distinct qualifying days: ${qualification.distinctNativeWorkoutDays} / ${rules.requiredDistinctWorkoutDays}"
            )
            appendLine(
                "Temporary Pro workouts: ${qualification.nativeCompletedWorkouts} / ${rules.temporaryProWorkoutCount}"
            )
            appendLine("Temporary Pro unlocked: ${qualification.temporaryProUnlocked}")
            appendLine("Feedback recorded: ${qualification.feedbackRecorded}")
            appendLine("Tester Analytics Report submitted: ${qualification.testerAnalyticsReportSubmitted}")
            appendLine("Requirements complete: ${qualification.testerRequirementsComplete}")
            appendLine()
            appendLine("Qualifying workouts")
            if (workouts.isEmpty()) {
                appendLine("None")
            } else {
                workouts.forEach { workout ->
                    appendLine("${workout.day}  #${workout.id}  ${workout.name}")
                }
            }
            appendLine()
            appendLine("Feedback")
            appendLine(if (feedbackText.isBlank()) "None" else feedbackText.trim())
        }
    }
}
