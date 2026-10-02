package app.mymusclemap.domain.workout

import app.mymusclemap.domain.calendar.WeekCalendar
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * A weekly goal that takes effect on a Monday.
 * [workoutsPerWeek] null means the goal is off from that week onward.
 * [graceWeek] is true only for the first partial week of a new activation.
 */
data class WeeklyGoalRevision(
    val effectiveWeekStart: LocalDate,
    val workoutsPerWeek: Int?,
    val graceWeek: Boolean
) {
    init {
        require(effectiveWeekStart.dayOfWeek == DayOfWeek.MONDAY) {
            "Weekly goals take effect on Monday"
        }
        require(workoutsPerWeek == null || workoutsPerWeek in WeeklyGoalLogic.MIN_GOAL..WeeklyGoalLogic.MAX_GOAL) {
            "Weekly goal must be ${WeeklyGoalLogic.MIN_GOAL}–${WeeklyGoalLogic.MAX_GOAL}"
        }
        require(workoutsPerWeek != null || !graceWeek) {
            "A disabled week is not a grace week"
        }
    }
}

enum class WeekVerdict {
    NoGoal,
    InProgress,
    GraceMiss,
    Achieved,
    Missed
}

data class WeekProgress(
    val weekStart: LocalDate,
    val goal: Int?,
    val completed: Int,
    val streak: Int,
    val achieved: Boolean,
    val verdict: WeekVerdict
)

sealed interface PendingWeeklyGoal {
    val effectiveWeekStart: LocalDate

    data class Update(
        override val effectiveWeekStart: LocalDate,
        val workoutsPerWeek: Int
    ) : PendingWeeklyGoal

    data class Disable(
        override val effectiveWeekStart: LocalDate
    ) : PendingWeeklyGoal
}

data class WeeklyGoalStatus(
    val today: LocalDate,
    val revisions: List<WeeklyGoalRevision>,
    val completedByDate: Map<LocalDate, Int>,
    val weeks: Map<LocalDate, WeekProgress>,
    val pending: PendingWeeklyGoal?
) {
    val currentWeekStart: LocalDate = WeekCalendar.start(today)
    val current: WeekProgress = weeks[currentWeekStart]
        ?: WeeklyGoalLogic.unevaluated(revisions, completedByDate, currentWeekStart)

    fun progressOn(weekStart: LocalDate): WeekProgress {
        return weeks[weekStart] ?: WeeklyGoalLogic.unevaluated(revisions, completedByDate, weekStart)
    }

    companion object {
        fun none(today: LocalDate): WeeklyGoalStatus {
            val weekStart = WeekCalendar.start(today)
            return WeeklyGoalStatus(
                today = today,
                revisions = emptyList(),
                completedByDate = emptyMap(),
                weeks = emptyMap(),
                pending = null
            ).let { status ->
                status.copy(
                    weeks = mapOf(
                        weekStart to WeekProgress(
                            weekStart = weekStart,
                            goal = null,
                            completed = 0,
                            streak = 0,
                            achieved = false,
                            verdict = WeekVerdict.NoGoal
                        )
                    )
                )
            }
        }
    }
}

object WeeklyGoalLogic {
    const val MIN_GOAL = 1
    const val MAX_GOAL = 7

    fun governing(history: List<WeeklyGoalRevision>, weekStart: LocalDate): WeeklyGoalRevision? {
        return history
            .asSequence()
            .filter { !it.effectiveWeekStart.isAfter(weekStart) }
            .maxByOrNull { it.effectiveWeekStart }
    }

    fun applicableGoal(history: List<WeeklyGoalRevision>, weekStart: LocalDate): Int? {
        return governing(history, weekStart)?.workoutsPerWeek
    }

    /**
     * Revisions stored for the first goal a person has ever configured.
     *
     * Completed weeks before this one are evaluated against that same goal,
     * starting at the Monday of the earliest completed workout. The current
     * week is still a grace week when the goal is set after Monday, so missing
     * it does not wipe the streak those earlier weeks already earned.
     * A later edit must use [propose], which never moves this start date.
     */
    fun planFirstGoal(
        today: LocalDate,
        workoutsPerWeek: Int,
        earliestCompleted: LocalDate?
    ): List<WeeklyGoalRevision> {
        if (workoutsPerWeek !in MIN_GOAL..MAX_GOAL) return emptyList()
        val thisWeek = WeekCalendar.start(today)
        val historyStart = earliestCompleted
            ?.let { WeekCalendar.start(it) }
            ?.takeIf { it.isBefore(thisWeek) }
        val grace = today != thisWeek
        val opening = WeeklyGoalRevision(
            effectiveWeekStart = historyStart ?: thisWeek,
            workoutsPerWeek = workoutsPerWeek,
            graceWeek = historyStart == null && grace
        )
        return if (historyStart != null && grace) {
            listOf(opening, WeeklyGoalRevision(thisWeek, workoutsPerWeek, graceWeek = true))
        } else {
            listOf(opening)
        }
    }

    /**
     * The revision to store for this edit.
     * An already-active goal changes on the next Monday.
     * Turning a goal back on after a gap starts this week and does not
     * re-score older weeks. Mid-week activation is a grace week.
     * Monday activation is a full week.
     * The very first goal is [planFirstGoal], not this function.
     */
    fun propose(
        history: List<WeeklyGoalRevision>,
        today: LocalDate,
        workoutsPerWeek: Int?
    ): WeeklyGoalRevision? {
        if (workoutsPerWeek != null && workoutsPerWeek !in MIN_GOAL..MAX_GOAL) return null
        val thisWeek = WeekCalendar.start(today)
        val active = applicableGoal(history, thisWeek)
        return if (active == null) {
            if (workoutsPerWeek == null) {
                null
            } else {
                WeeklyGoalRevision(
                    effectiveWeekStart = thisWeek,
                    workoutsPerWeek = workoutsPerWeek,
                    graceWeek = today != thisWeek
                )
            }
        } else {
            WeeklyGoalRevision(
                effectiveWeekStart = thisWeek.plusWeeks(1),
                workoutsPerWeek = workoutsPerWeek,
                graceWeek = false
            )
        }
    }

    /**
     * Replaces any revision already stored for the proposal's Monday.
     * Drops the proposal when it would not change the goal inherited from earlier history,
     * so changing a pending value back does not leave a duplicate row.
     */
    fun apply(history: List<WeeklyGoalRevision>, proposal: WeeklyGoalRevision): List<WeeklyGoalRevision> {
        val withoutSameWeek = history.filter { it.effectiveWeekStart != proposal.effectiveWeekStart }
        val inherited = governing(withoutSameWeek, proposal.effectiveWeekStart)?.workoutsPerWeek
        val changesGoal = inherited != proposal.workoutsPerWeek || proposal.graceWeek
        return if (changesGoal) {
            (withoutSameWeek + proposal).sortedBy { it.effectiveWeekStart }
        } else {
            withoutSameWeek.sortedBy { it.effectiveWeekStart }
        }
    }

    fun completedIn(weekStart: LocalDate, completedByDate: Map<LocalDate, Int>): Int {
        return WeekCalendar.dates(weekStart).sumOf { completedByDate[it] ?: 0 }
    }

    fun evaluate(
        history: List<WeeklyGoalRevision>,
        completedByDate: Map<LocalDate, Int>,
        today: LocalDate
    ): WeeklyGoalStatus {
        val thisWeek = WeekCalendar.start(today)
        if (history.isEmpty()) {
            val count = completedIn(thisWeek, completedByDate)
            return WeeklyGoalStatus(
                today = today,
                revisions = emptyList(),
                completedByDate = completedByDate,
                weeks = mapOf(thisWeek to emptyWeek(thisWeek, count)),
                pending = null
            )
        }
        val first = history.minOf { it.effectiveWeekStart }
        var streak = 0
        val weeks = linkedMapOf<LocalDate, WeekProgress>()
        var cursor = first
        while (!cursor.isAfter(thisWeek)) {
            val revision = governing(history, cursor)
            val goal = revision?.workoutsPerWeek
            val count = completedIn(cursor, completedByDate)
            val ended = WeekCalendar.end(cursor).isBefore(today)
            val grace = goal != null && revision.graceWeek && revision.effectiveWeekStart == cursor
            val achieved = goal != null && count >= goal
            val verdict: WeekVerdict
            when {
                goal == null -> {
                    streak = 0
                    verdict = WeekVerdict.NoGoal
                }
                achieved -> {
                    streak += 1
                    verdict = WeekVerdict.Achieved
                }
                grace || !ended -> {
                    verdict = if (!ended) WeekVerdict.InProgress else WeekVerdict.GraceMiss
                }
                else -> {
                    streak = 0
                    verdict = WeekVerdict.Missed
                }
            }
            weeks[cursor] = WeekProgress(
                weekStart = cursor,
                goal = goal,
                completed = count,
                streak = if (goal == null) 0 else streak,
                achieved = achieved,
                verdict = verdict
            )
            cursor = cursor.plusWeeks(1)
        }
        val nextWeek = thisWeek.plusWeeks(1)
        val pendingRevision = history.firstOrNull { it.effectiveWeekStart == nextWeek }
        val pending = when (val workouts = pendingRevision?.workoutsPerWeek) {
            null -> if (pendingRevision == null) null else PendingWeeklyGoal.Disable(nextWeek)
            else -> PendingWeeklyGoal.Update(nextWeek, workouts)
        }
        return WeeklyGoalStatus(
            today = today,
            revisions = history.sortedBy { it.effectiveWeekStart },
            completedByDate = completedByDate,
            weeks = weeks,
            pending = pending
        )
    }

    fun unevaluated(
        history: List<WeeklyGoalRevision>,
        completedByDate: Map<LocalDate, Int>,
        weekStart: LocalDate
    ): WeekProgress {
        val goal = applicableGoal(history, weekStart)
        val count = completedIn(weekStart, completedByDate)
        return WeekProgress(
            weekStart = weekStart,
            goal = goal,
            completed = count,
            streak = 0,
            achieved = goal != null && count >= goal,
            verdict = if (goal == null) WeekVerdict.NoGoal else WeekVerdict.InProgress
        )
    }

    private fun emptyWeek(weekStart: LocalDate, completed: Int): WeekProgress {
        return WeekProgress(
            weekStart = weekStart,
            goal = null,
            completed = completed,
            streak = 0,
            achieved = false,
            verdict = WeekVerdict.NoGoal
        )
    }
}
