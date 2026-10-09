package app.mymusclemap.ui.components

import app.mymusclemap.domain.locale.AppLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

object UiFormatters {
    private fun locale(): Locale = AppLocale.UI
    private fun longDateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale())
    private fun longDateWithWeekdayFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale())
    private fun chartDateFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", locale())
    private fun monthTitleFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", locale())
    private fun monthAbbrevFormatter(): DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM", locale())

    fun weightKg(value: Double): String {
        return String.format(locale(), "%.1f kg", value)
    }

    fun weightValue(value: Double): String {
        return String.format(locale(), "%.1f", value)
    }

    fun signedWeightKg(value: Double): String {
        val formatted = String.format(locale(), "%.1f kg", abs(value))
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "−$formatted"
            else -> formatted
        }
    }

    fun longDate(date: LocalDate): String = date.format(longDateFormatter())

    fun longDateWithWeekday(date: LocalDate): String = date.format(longDateWithWeekdayFormatter())

    fun chartDate(date: LocalDate): String = date.format(chartDateFormatter())

    fun compactDate(date: LocalDate): String = date.format(chartDateFormatter())

    fun monthTitle(month: YearMonth): String = month.format(monthTitleFormatter())

    fun inclusiveDateRange(start: LocalDate, end: LocalDate): String {
        if (start.year != end.year) {
            val withYear = DateTimeFormatter.ofPattern("MMM d, yyyy", locale())
            return "${start.format(withYear)}–${end.format(withYear)}"
        }
        return if (start.month == end.month) {
            "${start.format(monthAbbrevFormatter())} ${start.dayOfMonth}–${end.dayOfMonth}"
        } else {
            "${start.format(chartDateFormatter())}–${end.format(chartDateFormatter())}"
        }
    }
}
