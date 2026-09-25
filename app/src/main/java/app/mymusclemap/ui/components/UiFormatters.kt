package app.mymusclemap.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

object UiFormatters {
    private val locale: Locale = Locale.forLanguageTag("hu-HU")
    private val longDate: DateTimeFormatter =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    private val longDateWithWeekday: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy. MMMM d., EEEE", locale)
    private val chartDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d.", locale)
    private val compactDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM.dd.", locale)
    private val monthTitle: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy. MMMM", locale)
    private val monthAbbrev: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM", locale)

    fun weightKg(value: Double): String {
        return String.format(locale, "%.1f kg", value)
    }

    fun weightValue(value: Double): String {
        return String.format(locale, "%.1f", value)
    }

    fun signedWeightKg(value: Double): String {
        val formatted = String.format(locale, "%.1f kg", abs(value))
        return when {
            value > 0 -> "+$formatted"
            value < 0 -> "−$formatted"
            else -> formatted
        }
    }

    fun longDate(date: LocalDate): String = date.format(longDate)

    fun longDateWithWeekday(date: LocalDate): String = date.format(longDateWithWeekday)

    fun chartDate(date: LocalDate): String = date.format(chartDate)

    fun compactDate(date: LocalDate): String = date.format(compactDate)

    fun monthTitle(month: YearMonth): String = month.format(monthTitle)

    fun inclusiveDateRange(start: LocalDate, end: LocalDate): String {
        return if (start.month == end.month && start.year == end.year) {
            "${start.format(monthAbbrev)} ${start.dayOfMonth}–${end.dayOfMonth}."
        } else {
            "${start.format(chartDate)}–${end.format(chartDate)}"
        }
    }
}
