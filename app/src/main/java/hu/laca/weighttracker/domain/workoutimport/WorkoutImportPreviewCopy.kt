package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.journal.WorkoutSetCopy
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.ElapsedTime
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.QuantityParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WorkoutImportPreviewCopy {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
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

    fun setLine(set: WorkoutImportResolvedSet, interpretation: WeightInterpretation): String {
        val formatted = WorkoutSetCopy.formatValue(
            repsText = set.reps?.toString(),
            loadKind = set.loadKind,
            weightKg = set.weightKg?.toDouble(),
            durationSeconds = set.durationSeconds,
            distanceMeters = set.distanceMeters?.toDouble(),
            interpretation = interpretation
        )
        if (set.loadKind == PlannedLoadKind.EXTERNAL_WEIGHT &&
            set.weightKg != null &&
            interpretation == WeightInterpretation.TOTAL &&
            !formatted.contains("összesen")
        ) {
            val weight = QuantityParser.formatDisplay(set.weightKg.toDouble())
            return formatted.replace("$weight kg", "$weight kg összesen")
        }
        return formatted
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
