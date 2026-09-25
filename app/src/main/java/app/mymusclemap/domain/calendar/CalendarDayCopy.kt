package app.mymusclemap.domain.calendar

import android.content.res.Resources
import app.mymusclemap.R
import app.mymusclemap.domain.locale.AppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CalendarDayCopy {
    private val longDate = DateTimeFormatter.ofPattern("MMMM d, yyyy", AppLocale.UI)

    fun description(
        resources: Resources,
        date: LocalDate,
        hasMeasurement: Boolean,
        completedWorkoutCount: Int,
        isToday: Boolean = false,
        isFuture: Boolean = false,
        plannedWorkoutCount: Int = 0
    ): String {
        val parts = mutableListOf(date.format(longDate))
        if (isToday) {
            parts += resources.getString(R.string.calendar_today_label)
        }
        parts += entryState(
            resources,
            hasMeasurement,
            completedWorkoutCount,
            plannedWorkoutCount
        )
        if (isFuture) {
            parts += resources.getString(R.string.calendar_future_label)
        }
        return parts.joinToString(", ")
    }

    fun entryState(
        resources: Resources,
        hasMeasurement: Boolean,
        completedWorkoutCount: Int,
        plannedWorkoutCount: Int = 0
    ): String {
        val parts = mutableListOf<String>()
        if (hasMeasurement) {
            parts += resources.getString(R.string.calendar_has_weight)
        }
        if (completedWorkoutCount > 0) {
            parts += resources.getQuantityString(
                R.plurals.calendar_completed_workouts,
                completedWorkoutCount,
                completedWorkoutCount
            )
        }
        if (plannedWorkoutCount > 0) {
            parts += resources.getQuantityString(
                R.plurals.calendar_planned_workouts,
                plannedWorkoutCount,
                plannedWorkoutCount
            )
        }
        return when (parts.size) {
            0 -> resources.getString(R.string.calendar_no_entry)
            1 -> parts[0]
            2 -> resources.getString(R.string.calendar_entry_two, parts[0], parts[1])
            else -> resources.getString(
                R.string.calendar_entry_list,
                parts.dropLast(1).joinToString(", "),
                parts.last()
            )
        }
    }
}
