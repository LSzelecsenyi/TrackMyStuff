package app.mymusclemap.domain.reports

import app.mymusclemap.domain.entitlement.AppFeature
import java.time.LocalDate
import java.time.YearMonth

/**
 * A named historical report span with fixed calendar boundaries, not a rolling window.
 *
 * Identity is [kind] plus [startInclusive]. [endInclusive] is the matching calendar boundary.
 */
enum class ReportKind {
    Monthly,
    Quarterly,
    HalfYear,
    Yearly;

    /** Monthly reports are free. Longer closed periods use [AppFeature.AdvancedReports]. */
    val requiredFeature: AppFeature?
        get() = if (this == Monthly) null else AppFeature.AdvancedReports
}

data class ReportPeriod(
    val kind: ReportKind,
    val startInclusive: LocalDate,
    val endInclusive: LocalDate
) {
    init {
        val bounds = bounds(kind, startInclusive)
        require(startInclusive == bounds.first && endInclusive == bounds.second) {
            "Report period must cover the canonical $kind span starting $startInclusive"
        }
    }

    fun previous(): ReportPeriod = containing(kind, startInclusive.minusDays(1))

    fun next(): ReportPeriod = containing(kind, endInclusive.plusDays(1))

    /** Closed periods have ended before [today]. A period that contains [today] is still open. */
    fun isClosed(today: LocalDate): Boolean = endInclusive.isBefore(today)

    fun contains(date: LocalDate): Boolean {
        return !date.isBefore(startInclusive) && !date.isAfter(endInclusive)
    }

    companion object {
        fun containing(kind: ReportKind, date: LocalDate): ReportPeriod {
            val (start, end) = bounds(kind, date)
            return ReportPeriod(kind, start, end)
        }

        private fun bounds(kind: ReportKind, date: LocalDate): Pair<LocalDate, LocalDate> {
            return when (kind) {
                ReportKind.Monthly -> {
                    val month = YearMonth.from(date)
                    month.atDay(1) to month.atEndOfMonth()
                }
                ReportKind.Quarterly -> {
                    val startMonth = ((date.monthValue - 1) / 3) * 3 + 1
                    val start = LocalDate.of(date.year, startMonth, 1)
                    val end = YearMonth.of(date.year, startMonth + 2).atEndOfMonth()
                    start to end
                }
                ReportKind.HalfYear -> {
                    if (date.monthValue <= 6) {
                        LocalDate.of(date.year, 1, 1) to LocalDate.of(date.year, 6, 30)
                    } else {
                        LocalDate.of(date.year, 7, 1) to LocalDate.of(date.year, 12, 31)
                    }
                }
                ReportKind.Yearly -> {
                    LocalDate.of(date.year, 1, 1) to LocalDate.of(date.year, 12, 31)
                }
            }
        }
    }
}
