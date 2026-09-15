package hu.laca.weighttracker.domain.journal

import hu.laca.weighttracker.domain.MeasurementDiffs
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.domain.workout.SessionStatus
import hu.laca.weighttracker.domain.workout.WorkoutSessionSummary
import java.time.LocalDate

object JournalAssembler {
    fun assemble(
        measurements: List<WeightMeasurement>,
        summaries: List<WorkoutSessionSummary>,
        filter: JournalFilter,
        includeAbandoned: Boolean
    ): JournalTimeline {
        val weightItems = MeasurementDiffs.withChronologicalDifferences(measurements)
            .map(::WeightJournalEntry)
        val workoutItems = summaries
            .asSequence()
            .filter { summary ->
                when (summary.session.status) {
                    SessionStatus.IN_PROGRESS -> false
                    SessionStatus.COMPLETED -> true
                    SessionStatus.ABANDONED -> includeAbandoned
                }
            }
            .map(::WorkoutJournalEntry)
            .toList()
        val visible = when (filter) {
            JournalFilter.ALL -> weightItems + workoutItems
            JournalFilter.WEIGHT -> weightItems
            JournalFilter.WORKOUT -> workoutItems
        }
        val groups = visible
            .groupBy { it.date }
            .toSortedMap(compareByDescending { it })
            .map { (date, entries) ->
                JournalDateGroup(date = date, entries = entries.sortedWith(withinDayOrder))
            }
        val hasJournalData = measurements.isNotEmpty() ||
            summaries.any { it.session.status == SessionStatus.COMPLETED } ||
            (includeAbandoned && summaries.any { it.session.status == SessionStatus.ABANDONED })
        val emptyKind = when {
            visible.isNotEmpty() -> null
            !hasJournalData -> JournalEmptyKind.NoEntries
            else -> JournalEmptyKind.FilterEmpty
        }
        return JournalTimeline(
            groups = groups,
            filter = filter,
            includeAbandoned = includeAbandoned,
            emptyKind = emptyKind
        )
    }

    private val withinDayOrder = compareBy<JournalEntry> { entry ->
        when (entry) {
            is WeightJournalEntry -> 0
            is WorkoutJournalEntry -> 1
        }
    }.thenByDescending { entry ->
        when (entry) {
            is WeightJournalEntry -> entry.item.measurement.id
            is WorkoutJournalEntry -> entry.summary.session.startedAt
        }
    }.thenByDescending { entry ->
        when (entry) {
            is WeightJournalEntry -> entry.item.measurement.id
            is WorkoutJournalEntry -> entry.summary.session.id
        }
    }
}
