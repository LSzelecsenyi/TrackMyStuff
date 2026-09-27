package app.mymusclemap.ui.reports

import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.reports.ReportHistoryCoverage
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.statistics.VolumeTrendAxis
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import app.mymusclemap.ui.components.UiFormatters
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.round

private val monthAbbrev: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", AppLocale.UI)

internal fun ReportPeriod.displayTitle(): String {
    return when (kind) {
        ReportKind.Monthly -> UiFormatters.monthTitle(YearMonth.from(startInclusive))
        ReportKind.Yearly -> startInclusive.year.toString()
        ReportKind.Quarterly,
        ReportKind.HalfYear -> {
            val start = YearMonth.from(startInclusive).format(monthAbbrev)
            val end = YearMonth.from(endInclusive).format(monthAbbrev)
            "$start–$end ${startInclusive.year}"
        }
    }
}

internal fun ReportPeriod.displayRange(): String {
    return "${UiFormatters.longDate(startInclusive)} – ${UiFormatters.longDate(endInclusive)}"
}

internal fun signedCount(value: Int): String {
    return when {
        value > 0 -> "+$value"
        value < 0 -> "−${abs(value)}"
        else -> "0"
    }
}

internal fun signedPercent(value: Double): String {
    val rounded = round(value).toInt()
    return when {
        rounded > 0 -> "+$rounded%"
        rounded < 0 -> "−${abs(rounded)}%"
        else -> "0%"
    }
}

internal fun reportDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        minutes > 0L -> "${minutes}m"
        else -> "${seconds}s"
    }
}

internal object ReportVolumeFormat {
    fun kilograms(value: Double): String = "${magnitude(value)} kg"

    fun signedKilograms(value: Double): String {
        val body = kilograms(abs(value))
        return when {
            value > 0.0 -> "+$body"
            value < 0.0 -> "−$body"
            else -> body
        }
    }

    fun axis(value: Double): String = magnitude(value)

    private fun magnitude(value: Double): String {
        val absolute = abs(value)
        if (absolute < 1_000.0) {
            return String.format(AppLocale.UI, "%.1f", absolute)
        }
        val (scaled, suffix) = if (absolute >= 1_000_000.0) {
            absolute / 1_000_000.0 to "M"
        } else {
            absolute / 1_000.0 to "k"
        }
        var rounded = round(scaled * 10.0) / 10.0
        var unit = suffix
        if (unit == "k" && rounded >= 1_000.0) {
            rounded /= 1_000.0
            unit = "M"
        }
        return String.format(AppLocale.UI, "%.1f%s", rounded, unit)
    }
}

/**
 * Chart-only view of a report volume trend. The stored trend and report period stay intact.
 * Partial history drops buckets that end before [historyStart]. Complete reports keep every bucket.
 */
internal object ReportChartSeries {
    fun plotted(
        trend: List<SeriesPoint>,
        resolution: VolumeTrendResolution,
        coverage: ReportHistoryCoverage?,
        historyStart: LocalDate?
    ): List<SeriesPoint> {
        if (coverage != ReportHistoryCoverage.Partial || historyStart == null) {
            return trend
        }
        return trend.filter { point ->
            !bucketEnd(point.date, resolution).isBefore(historyStart)
        }
    }

    fun axisLabel(
        date: LocalDate,
        resolution: VolumeTrendResolution,
        period: ReportPeriod,
        plotted: List<SeriesPoint>
    ): String {
        val visible = date.clamp(period.startInclusive, period.endInclusive)
        val first = plotted.first().date.clamp(period.startInclusive, period.endInclusive)
        val last = plotted.last().date.clamp(period.startInclusive, period.endInclusive)
        return VolumeTrendAxis.label(visible, resolution, first, last)
    }

    private fun bucketEnd(start: LocalDate, resolution: VolumeTrendResolution): LocalDate {
        return when (resolution) {
            VolumeTrendResolution.Daily -> start
            VolumeTrendResolution.Weekly -> start.plusDays(6)
            VolumeTrendResolution.Monthly -> start.withDayOfMonth(start.lengthOfMonth())
        }
    }

    private fun LocalDate.clamp(start: LocalDate, endInclusive: LocalDate): LocalDate {
        return when {
            isBefore(start) -> start
            isAfter(endInclusive) -> endInclusive
            else -> this
        }
    }
}
