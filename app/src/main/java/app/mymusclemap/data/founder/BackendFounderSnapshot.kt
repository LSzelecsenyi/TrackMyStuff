package app.mymusclemap.data.founder

import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Founder enrollment fields taken from `POST /api/v1/founder/enrollment`.
 * The request does not send a status, a tier, or a Lifetime flag.
 */
data class BackendFounderSnapshot(
    val status: FounderProgramStatus,
    val enrolledOn: LocalDate,
    val deadline: LocalDate,
    val qualifyingWorkouts: Int,
    val distinctWorkoutDays: Int,
    val feedbackSubmitted: Boolean,
    val reportSubmitted: Boolean
) {
    fun toProgramState(): FounderProgramState {
        return FounderProgramState(
            status = status,
            enrolledOn = enrolledOn,
            deadline = deadline,
            feedbackRecorded = feedbackSubmitted,
            testerAnalyticsReportSubmitted = reportSubmitted,
            backendOwned = true,
            serverQualifyingWorkouts = qualifyingWorkouts,
            serverDistinctDays = distinctWorkoutDays
        )
    }

    companion object {
        fun parse(raw: String, zone: ZoneId): BackendFounderSnapshot? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val status = status(json.optString("status")) ?: return null
            val enrolledAt = instant(json.optString("enrolledAt")) ?: return null
            val deadlineAt = instant(json.optString("deadlineAt")) ?: return null
            val progress = json.optJSONObject("progress") ?: return null
            val feedback = json.optJSONObject("feedback") ?: return null
            val report = json.optJSONObject("testerReport") ?: return null
            val workouts = progress.optInt("qualifyingWorkouts", -1)
            val days = progress.optInt("distinctWorkoutDays", -1)
            if (workouts < 0 || days < 0) {
                return null
            }
            return BackendFounderSnapshot(
                status = status,
                enrolledOn = enrolledAt.atZone(zone).toLocalDate(),
                deadline = deadlineAt.atZone(zone).toLocalDate(),
                qualifyingWorkouts = workouts,
                distinctWorkoutDays = days,
                feedbackSubmitted = feedback.optBoolean("submitted", false),
                reportSubmitted = report.optBoolean("submitted", false)
            )
        }

        private fun status(raw: String): FounderProgramStatus? {
            return when (raw) {
                "ACTIVE_FREE" -> FounderProgramStatus.ActiveFree
                "ACTIVE_PRO" -> FounderProgramStatus.ActivePro
                "PENDING_APPROVAL" -> FounderProgramStatus.PendingApproval
                "APPROVED" -> FounderProgramStatus.Approved
                "EXPIRED" -> FounderProgramStatus.Expired
                "REJECTED" -> FounderProgramStatus.Rejected
                else -> null
            }
        }

        private fun instant(raw: String): Instant? {
            return runCatching { Instant.parse(raw) }.getOrNull()
        }
    }
}
