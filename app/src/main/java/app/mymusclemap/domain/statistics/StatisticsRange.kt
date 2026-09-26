package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.entitlement.AppFeature
import java.time.LocalDate

/**
 * Continuously browsable Statistics windows, distinct from future period Reports
 * (Monthly / 3-month / 6-month / Yearly).
 *
 * Inclusive of [today] and exclusive of any later date. Bounded ranges start on
 * the calendar date returned by [startInclusive].
 */
enum class StatisticsRange {
    Days30,
    Months3,
    Months6,
    Year1,
    All;

    val requiredFeature: AppFeature?
        get() = if (this == Days30) null else AppFeature.AdvancedStatistics

    val isFree: Boolean get() = requiredFeature == null

    fun startInclusive(today: LocalDate): LocalDate? {
        return when (this) {
            Days30 -> today.minusDays(DAYS_30 - 1)
            Months3 -> today.minusMonths(3)
            Months6 -> today.minusMonths(6)
            Year1 -> today.minusYears(1)
            All -> null
        }
    }

    fun contains(date: LocalDate, today: LocalDate): Boolean {
        if (date.isAfter(today)) return false
        val start = startInclusive(today) ?: return true
        return !date.isBefore(start)
    }

    companion object {
        const val DAYS_30 = 30L

        fun fromName(raw: String?): StatisticsRange {
            return entries.firstOrNull { it.name == raw } ?: Days30
        }
    }
}
