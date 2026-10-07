package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.calendar.WeekCalendar
import app.mymusclemap.domain.workout.WeeklyGoalStatus
import java.time.LocalDate
import java.time.ZoneOffset

data class StreakQualification(
    val achievementId: AchievementId,
    val unlockedAt: Long
)

/**
 * Reads the streak [app.mymusclemap.domain.workout.WeeklyGoalLogic] already stored
 * on each evaluated week. It does not score weeks again.
 */
object WeeklyGoalStreakEvaluator {
    fun currentStreak(status: WeeklyGoalStatus): Int = status.current.streak.coerceAtLeast(0)

    /** Longest run of achieved weeks in the evaluated history. */
    fun bestStreak(status: WeeklyGoalStatus): Int {
        return status.weeks.values
            .filter { it.achieved }
            .maxOfOrNull { it.streak }
            ?.coerceAtLeast(0)
            ?: 0
    }

    fun qualifications(status: WeeklyGoalStatus): List<StreakQualification> {
        val best = bestStreak(status)
        return AchievementCatalog.weeklyStreaks.mapNotNull { id ->
            val weeks = id.streakWeeks ?: return@mapNotNull null
            if (best < weeks) return@mapNotNull null
            val unlockedAt = unlockedAtMillis(status, weeks) ?: return@mapNotNull null
            StreakQualification(achievementId = id, unlockedAt = unlockedAt)
        }
    }

    /**
     * UTC midnight on the day the qualifying week first reached its goal.
     * The earliest such week wins, so a later run of the same length does not move the date.
     */
    fun unlockedAtMillis(status: WeeklyGoalStatus, streakWeeks: Int): Long? {
        val week = status.weeks.values
            .filter { it.achieved && it.goal != null && it.streak >= streakWeeks }
            .minByOrNull { it.weekStart }
            ?: return null
        val crossedOn = crossingDate(week.weekStart, week.goal!!, status.completedByDate)
        return crossedOn.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun crossingDate(
        weekStart: LocalDate,
        goal: Int,
        completedByDate: Map<LocalDate, Int>
    ): LocalDate {
        var total = 0
        for (date in WeekCalendar.dates(weekStart)) {
            total += completedByDate[date] ?: 0
            if (total >= goal) return date
        }
        return WeekCalendar.end(weekStart)
    }
}
