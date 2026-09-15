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
        isFuture: Boolean = false
    ): String {
        val parts = mutableListOf(date.format(longDate))
        if (isToday) {
            parts += "ma"
        }
        parts += entryState(hasMeasurement, completedWorkoutCount)
        if (isFuture) {
            parts += "jövőbeli nap, nem rögzíthető"
        }
        return parts.joinToString(", ")
    }

    fun entryState(hasMeasurement: Boolean, completedWorkoutCount: Int): String {
        return when {
            hasMeasurement && completedWorkoutCount == 1 -> "testsúlymérés és 1 befejezett edzés"
            hasMeasurement && completedWorkoutCount > 1 ->
                "testsúlymérés és $completedWorkoutCount befejezett edzés"
            hasMeasurement -> "testsúlymérés"
            completedWorkoutCount == 1 -> "1 befejezett edzés"
            completedWorkoutCount > 1 -> "$completedWorkoutCount befejezett edzés"
            else -> "nincs bejegyzés"
        }
    }
}
