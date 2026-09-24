package hu.laca.weighttracker.domain.calendar

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CalendarDayCopy {
    private val locale = Locale.forLanguageTag("hu-HU")
    private val longDate = DateTimeFormatter.ofPattern("yyyy. MMMM d.", locale)

    fun description(
        date: LocalDate,
        hasMeasurement: Boolean,
        completedWorkoutCount: Int,
        isToday: Boolean = false,
        isFuture: Boolean = false,
        plannedWorkoutCount: Int = 0
    ): String {
        val parts = mutableListOf(date.format(longDate))
        if (isToday) {
            parts += "ma"
        }
        parts += entryState(hasMeasurement, completedWorkoutCount, plannedWorkoutCount)
        if (isFuture) {
            parts += "jövőbeli nap"
        }
        return parts.joinToString(", ")
    }

    fun entryState(
        hasMeasurement: Boolean,
        completedWorkoutCount: Int,
        plannedWorkoutCount: Int = 0
    ): String {
        val parts = mutableListOf<String>()
        if (hasMeasurement) {
            parts += "testsúlymérés"
        }
        when {
            completedWorkoutCount == 1 -> parts += "1 befejezett edzés"
            completedWorkoutCount > 1 -> parts += "$completedWorkoutCount befejezett edzés"
        }
        when {
            plannedWorkoutCount == 1 -> parts += "1 tervezett edzés"
            plannedWorkoutCount > 1 -> parts += "$plannedWorkoutCount tervezett edzés"
        }
        return when (parts.size) {
            0 -> "nincs bejegyzés"
            1 -> parts[0]
            2 -> "${parts[0]} és ${parts[1]}"
            else -> parts.dropLast(1).joinToString(", ") + " és " + parts.last()
        }
    }
}
