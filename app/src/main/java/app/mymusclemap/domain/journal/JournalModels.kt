package app.mymusclemap.domain.journal

import app.mymusclemap.domain.model.MeasurementListItem
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionSummary
import java.time.LocalDate

enum class JournalFilter {
    ALL,
    WEIGHT,
    WORKOUT
}

enum class JournalEmptyKind {
    NoEntries,
    FilterEmpty
}

/**
 * Combined journal of weight measurements and workout sessions.
 *
 * Ordering:
 * 1. newest local calendar date first;
 * 2. within a day, the weight measurement (date-only, no time of day) comes first;
 * 3. then workouts by startedAt descending, then session id descending.
 *
 * Abandoned sessions stay persisted but are omitted unless [includeAbandoned] is true.
 * They are never treated as completed.
 */
sealed interface JournalEntry {
    val key: String
    val date: LocalDate
}

data class WeightJournalEntry(
    val item: MeasurementListItem
) : JournalEntry {
    override val key: String get() = "weight:${item.measurement.id}"
    override val date: LocalDate get() = item.measurement.date
}

data class WorkoutJournalEntry(
    val summary: WorkoutSessionSummary
) : JournalEntry {
    override val key: String get() = "workout:${summary.session.id}"
    override val date: LocalDate get() = summary.session.workoutDate
    val abandoned: Boolean get() = summary.session.status == SessionStatus.ABANDONED
}

data class JournalDateGroup(
    val date: LocalDate,
    val entries: List<JournalEntry>
)

data class JournalTimeline(
    val groups: List<JournalDateGroup>,
    val filter: JournalFilter,
    val includeAbandoned: Boolean,
    val emptyKind: JournalEmptyKind?
) {
    val entries: List<JournalEntry> get() = groups.flatMap { it.entries }
}
