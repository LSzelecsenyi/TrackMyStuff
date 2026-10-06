package app.mymusclemap.domain.achievements

import app.mymusclemap.domain.workout.WeeklyGoalStatus
import java.time.DayOfWeek
import java.time.LocalDate

data class AchievedWeek(
    val weekStart: LocalDate,
    val completed: Int,
    val goal: Int
)

/**
 * Weekly completion follows [app.mymusclemap.domain.workout.WeeklyGoalLogic].
 * A week contributes one event only when that logic already marks it achieved.
 * The streak counter is not read.
 */
object WeeklyGoalCompletionEvaluator {
    const val KEY_PREFIX = "weekly-workout-achieved:"
    const val HISTORY_RECOGNIZED_KEY = "history-recognized"

    fun dedupeKey(weekStart: LocalDate): String = "$KEY_PREFIX$weekStart"

    fun weekStartFromKey(key: String): LocalDate? {
        if (!key.startsWith(KEY_PREFIX)) return null
        val date = runCatching { LocalDate.parse(key.removePrefix(KEY_PREFIX)) }.getOrNull() ?: return null
        if (date.dayOfWeek != DayOfWeek.MONDAY) return null
        return date
    }

    fun achievedWeeks(status: WeeklyGoalStatus): List<AchievedWeek> {
        return status.weeks.values
            .asSequence()
            .filter { it.achieved && it.goal != null }
            .sortedBy { it.weekStart }
            .map { week ->
                AchievedWeek(
                    weekStart = week.weekStart,
                    completed = week.completed,
                    goal = week.goal!!
                )
            }
            .toList()
    }

    fun payload(week: AchievedWeek): String = "${week.weekStart}|${week.completed}|${week.goal}"

    fun parsePayload(payload: String): AchievedWeek? {
        val parts = payload.split('|')
        if (parts.size != 3) return null
        val start = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        if (start.dayOfWeek != DayOfWeek.MONDAY) return null
        val completed = parts[1].toIntOrNull() ?: return null
        val goal = parts[2].toIntOrNull() ?: return null
        if (completed < 0 || goal <= 0) return null
        return AchievedWeek(start, completed, goal)
    }
}
