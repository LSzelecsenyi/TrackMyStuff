package app.mymusclemap.domain.muscleinsights

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.statistics.StatisticsRange
import java.time.LocalDate

/**
 * Chart window for Muscle Insights. The rolling 7-day and previous-28-day
 * comparison does not follow this window.
 */
enum class MuscleInsightsPeriod {
    Weeks4,
    Months3,
    Months6,
    Year1,
    All;

    fun startInclusive(today: LocalDate): LocalDate? {
        val currentWeek = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        return when (this) {
            Weeks4 -> currentWeek.minusWeeks(3)
            Months3 -> weekStart(today.minusMonths(3))
            Months6 -> weekStart(today.minusMonths(6))
            Year1 -> weekStart(today.minusYears(1))
            All -> null
        }
    }

    fun contains(date: LocalDate, today: LocalDate): Boolean {
        if (date.isAfter(today)) return false
        val start = startInclusive(today) ?: return true
        return !date.isBefore(start)
    }

    companion object {
        fun fromName(raw: String?): MuscleInsightsPeriod {
            return entries.firstOrNull { it.name == raw } ?: Weeks4
        }

        fun fromStatistics(range: StatisticsRange): MuscleInsightsPeriod {
            return when (range) {
                StatisticsRange.Days30 -> Weeks4
                StatisticsRange.Months3 -> Months3
                StatisticsRange.Months6 -> Months6
                StatisticsRange.Year1 -> Year1
                StatisticsRange.All -> All
            }
        }
    }
}

private fun weekStart(date: LocalDate): LocalDate {
    return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
}

/**
 * Elapsed time since the latest eligible stimulus. Minutes under one hour,
 * whole hours through 23 hours, then complete 24-hour days rounded down.
 * Not a recovery estimate, and not a calendar-day count.
 */
sealed class StimulusAge {
    data object None : StimulusAge()
    data class Minutes(val minutes: Long) : StimulusAge()
    data class Hours(val hours: Long) : StimulusAge()
    data class Days(val days: Int) : StimulusAge()
}

/**
 * Recent 7-day primary-set count compared with the previous 28 days / 4.
 * Within 15% of that weekly figure is [NEAR]. Missing coverage is [INSUFFICIENT],
 * not a zero baseline.
 */
enum class LoadComparison {
    ABOVE,
    NEAR,
    BELOW,
    INSUFFICIENT
}

/**
 * Whether the fixed 28-day window has enough recorded history to compare.
 * [PARTIAL] still uses that same 28-day window and a divisor of 4.
 */
enum class BaselineCoverage {
    FULL,
    PARTIAL,
    INSUFFICIENT
}

data class TrainingSpacing(
    val sessionDates: Int,
    val averageDays: Double,
    val shortestDays: Int,
    val longestDays: Int
)

data class WeeklySetPoint(
    val weekStart: LocalDate,
    val primarySets: Int
)

data class MuscleInsight(
    val muscle: MuscleGroup,
    val lastStimulus: StimulusAge,
    val recentPrimarySets: Int,
    val baselineWeeklySets: Double?,
    val comparison: LoadComparison,
    val coverage: BaselineCoverage = BaselineCoverage.INSUFFICIENT,
    /** Days from the first recorded workout to the baseline end. Set only for [BaselineCoverage.PARTIAL]. */
    val partialHistoryDays: Int? = null,
    val weeklySets: List<WeeklySetPoint>,
    val periodPrimarySets: Int,
    val periodSecondarySets: Int,
    val spacing: TrainingSpacing?
) {
    val hasPeriodSets: Boolean get() = periodPrimarySets > 0

    companion object {
        fun empty(muscle: MuscleGroup): MuscleInsight {
            return MuscleInsight(
                muscle = muscle,
                lastStimulus = StimulusAge.None,
                recentPrimarySets = 0,
                baselineWeeklySets = null,
                comparison = LoadComparison.INSUFFICIENT,
                coverage = BaselineCoverage.INSUFFICIENT,
                partialHistoryDays = null,
                weeklySets = emptyList(),
                periodPrimarySets = 0,
                periodSecondarySets = 0,
                spacing = null
            )
        }
    }
}

data class MuscleQuickInsight(
    val muscle: MuscleGroup,
    val lastStimulus: StimulusAge,
    val recentPrimarySets: Int
)

data class MuscleSetBar(
    val muscle: MuscleGroup,
    val primarySets: Int
)

data class MuscleInsightsPreview(
    val demo: Boolean = false,
    val bars: List<MuscleSetBar> = emptyList(),
    val featuredMuscle: MuscleGroup? = null,
    val featuredStimulus: StimulusAge = StimulusAge.None
) {
    val isEmpty: Boolean get() = bars.isEmpty()
}

enum class MuscleInsightTopic {
    LAST_STIMULUS,
    RECENT_LOAD,
    CHART,
    SPACING,
    CONTRIBUTION
}
