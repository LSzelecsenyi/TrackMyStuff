package app.mymusclemap.domain.workoutimport

import android.content.res.Resources
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.journal.WorkoutSetCopy
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.QuantityParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WorkoutImportPreviewCopy {
    private val dateFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", AppLocale.UI)
    private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun dateLabel(date: LocalDate): String = date.format(dateFormat)

    fun dateRange(range: ClosedRange<LocalDate>?): String {
        if (range == null) {
            return ""
        }
        val start = dateLabel(range.start)
        val end = dateLabel(range.endInclusive)
        return if (start == end) start else "$start–$end"
    }

    fun timeLabel(value: LocalDateTime): String = value.format(timeFormat)

    fun timeRange(startedAt: LocalDateTime, finishedAt: LocalDateTime): String {
        return "${timeLabel(startedAt)}–${timeLabel(finishedAt)}"
    }

    fun duration(durationMillis: Long): String = ElapsedTime.formatMillis(durationMillis)

    fun setLine(
        resources: Resources,
        set: WorkoutImportResolvedSet,
        interpretation: WeightInterpretation
    ): String {
        return WorkoutSetCopy.formatValue(
            resources = resources,
            repsText = set.reps?.toString(),
            loadKind = set.loadKind,
            weightKg = set.weightKg?.toDouble(),
            durationSeconds = set.durationSeconds,
            distanceMeters = set.distanceMeters?.toDouble(),
            interpretation = interpretation,
            markTotalWeight = interpretation == WeightInterpretation.TOTAL &&
                set.weightKg != null
        )
    }

    fun bodyWeightValue(kilograms: Double?): String {
        if (kilograms == null) {
            return "—"
        }
        return "${QuantityParser.formatDisplay(kilograms)} kg"
    }

    fun bodyWeightSources(plan: WorkoutImportPlan): List<BodyWeightSource> {
        return plan.workouts.map { it.bodyWeight.source }.distinct()
    }
}
