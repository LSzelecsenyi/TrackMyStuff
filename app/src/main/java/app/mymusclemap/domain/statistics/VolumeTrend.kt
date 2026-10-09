package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.locale.AppLocale
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Training Volume chart bucket size for one [StatisticsRange].
 * Range start and end dates stay on [StatisticsRange]; only the grouping changes.
 */
enum class VolumeTrendResolution {
    Daily,
    Weekly,
    Monthly;

    companion object {
        fun forRange(range: StatisticsRange): VolumeTrendResolution {
            return when (range) {
                StatisticsRange.Days30 -> Daily
                StatisticsRange.Months3,
                StatisticsRange.Months6 -> Weekly
                StatisticsRange.Year1,
                StatisticsRange.All -> Monthly
            }
        }
    }
}

object VolumeTrendAxis {
    private fun dayLabel(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", AppLocale.UI)
    private fun dayYearLabel(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yy", AppLocale.UI)
    private fun monthLabel(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM", AppLocale.UI)
    private fun monthYearLabel(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM yyyy", AppLocale.UI)

    fun label(
        date: LocalDate,
        resolution: VolumeTrendResolution,
        seriesFirst: LocalDate,
        seriesLast: LocalDate
    ): String {
        val crossesYears = seriesFirst.year != seriesLast.year
        return when (resolution) {
            VolumeTrendResolution.Daily,
            VolumeTrendResolution.Weekly ->
                date.format(if (crossesYears) dayYearLabel() else dayLabel())
            VolumeTrendResolution.Monthly ->
                date.format(if (crossesYears) monthYearLabel() else monthLabel())
        }
    }
}
