package app.mymusclemap.domain.achievements

/**
 * Lifetime Journey milestones.
 *
 * First Step and Planner are reconstructed from workout sessions and saved plans.
 * Monthly Review has no historical report log. It is recorded only when a monthly
 * report is actually generated, starting with the version that writes
 * [MONTHLY_REPORT_KEY].
 */
data class JourneyQualification(
    val achievementId: AchievementId,
    val unlockedAt: Long
)

object JourneyEvaluator {
    const val MONTHLY_REPORT_KEY = "journey:monthly-report-generated"
    const val MONTHLY_REPORT_PAYLOAD = "monthly"

    fun qualifications(
        earliestNativeCompletedAt: Long?,
        earliestCustomPlanAt: Long?,
        monthlyReportGeneratedAt: Long?
    ): List<JourneyQualification> {
        return buildList {
            if (earliestNativeCompletedAt != null) {
                add(JourneyQualification(AchievementId.FIRST_WORKOUT, earliestNativeCompletedAt))
            }
            if (earliestCustomPlanAt != null) {
                add(JourneyQualification(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN, earliestCustomPlanAt))
            }
            if (monthlyReportGeneratedAt != null) {
                add(JourneyQualification(AchievementId.FIRST_MONTHLY_REPORT, monthlyReportGeneratedAt))
            }
        }
    }

    /** Completion time of a native Strict session. [finishedAt] is the completion instant. */
    fun nativeCompletedAt(finishedAt: Long?, startedAt: Long): Long = finishedAt ?: startedAt

    /**
     * A monthly report counts only when that kind was produced and the closed month
     * contains at least one completed workout. Opening the list, an empty month, or
     * another report kind does not qualify.
     */
    fun monthlyReportQualifies(isMonthly: Boolean, completedWorkoutsInPeriod: Int): Boolean {
        return isMonthly && completedWorkoutsInPeriod > 0
    }
}
