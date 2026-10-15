package app.mymusclemap.domain.muscleinsights

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Personal muscle training facts derived from completed sets.
 *
 * Primary-muscle completed sets are the comparable volume. Secondary listings
 * are counted separately and are not given a fractional weight. A set is never
 * counted twice for the same muscle.
 *
 * Recent volume is the last 7 calendar days including today. The baseline is
 * the preceding 28 calendar days, divided by 4. Near baseline means the recent
 * count is within [NEAR_TOLERANCE] of that weekly figure.
 *
 * Full coverage means the earliest eligible completed set is on or before the
 * baseline start. Partial coverage allows that set to fall as late as seven
 * calendar days after the baseline start. The weekly average still divides the
 * fixed 28-day primary-set count by 4. Later or missing history is
 * [LoadComparison.INSUFFICIENT]. Days before the first recorded workout are
 * not treated as confirmed rest.
 *
 * This is not a recovery, readiness, or fatigue measurement.
 */
object MuscleInsightsLogic {
    const val RECENT_DAYS = 7
    const val BASELINE_DAYS = 28
    const val BASELINE_WEEKS = 4
    const val COVERAGE_TOLERANCE_DAYS = 7L
    const val NEAR_TOLERANCE = 0.15
    const val PREVIEW_BARS = 3
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR

    fun index(aggregates: List<WorkoutSessionAggregate>, zone: ZoneId): InsightIndex {
        val stimuli = ArrayList<Stimulus>()
        var earliest: LocalDate? = null
        aggregates.forEach { aggregate ->
            val session = aggregate.session
            if (session.status != SessionStatus.COMPLETED) return@forEach
            aggregate.exercises.forEach { item ->
                val primary = item.exercise.primaryMuscle
                val secondary = item.exercise.secondaryMuscles
                    .asSequence()
                    .distinct()
                    .filter { it != primary }
                    .filter { it in anatomical }
                    .toList()
                item.sets.forEach { set ->
                    if (set.status != SessionSetStatus.COMPLETED) return@forEach
                    val at = stimulusAt(set.completedAt, session, zone)
                    val date = at.toLocalDate()
                    earliest = earlier(earliest, date)
                    if (primary in anatomical) {
                        stimuli += Stimulus(set.id, date, at, primary, Involvement.PRIMARY)
                    }
                    secondary.forEach { muscle ->
                        stimuli += Stimulus(set.id, date, at, muscle, Involvement.SECONDARY)
                    }
                }
            }
        }
        return InsightIndex(stimuli, earliest)
    }

    class InsightIndex internal constructor(
        private val stimuli: List<Stimulus>,
        private val earliestEligibleDate: LocalDate?
    ) {
        fun insight(
            muscle: MuscleGroup,
            today: LocalDate,
            now: LocalDateTime,
            period: MuscleInsightsPeriod
        ): MuscleInsight {
            if (muscle !in anatomical) {
                return MuscleInsight.empty(muscle)
            }
            val mine = stimuli.filter { it.muscle == muscle && !it.date.isAfter(today) }
            val primary = mine.filter { it.role == Involvement.PRIMARY }.distinctBy { it.setId }
            val secondary = mine.filter { it.role == Involvement.SECONDARY }.distinctBy { it.setId }
            val recentStart = today.minusDays((RECENT_DAYS - 1).toLong())
            val baselineEnd = recentStart.minusDays(1)
            val baselineStart = baselineEnd.minusDays((BASELINE_DAYS - 1).toLong())
            val recentSets = primary.count { !it.date.isBefore(recentStart) }
            val baselineSets = primary.count { !it.date.isBefore(baselineStart) && !it.date.isAfter(baselineEnd) }
            val coverage = coverage(earliestEligibleDate, baselineStart)
            val comparison = classify(recentSets, baselineSets, coverage)
            val inPeriod = { date: LocalDate -> period.contains(date, today) }
            val periodPrimary = primary.filter { inPeriod(it.date) }
            val spacingDates = periodPrimary.map { it.date }.distinct().sorted()
            return MuscleInsight(
                muscle = muscle,
                lastStimulus = age(latest(mine), now),
                recentPrimarySets = recentSets,
                baselineWeeklySets = if (coverage == BaselineCoverage.INSUFFICIENT) {
                    null
                } else {
                    baselineSets.toDouble() / BASELINE_WEEKS
                },
                comparison = comparison,
                coverage = coverage,
                partialHistoryDays = if (coverage == BaselineCoverage.PARTIAL && earliestEligibleDate != null) {
                    ChronoUnit.DAYS.between(earliestEligibleDate, baselineEnd).toInt()
                } else {
                    null
                },
                weeklySets = weekly(periodPrimary, today, period),
                periodPrimarySets = periodPrimary.size,
                periodSecondarySets = secondary.count { inPeriod(it.date) },
                spacing = spacing(spacingDates)
            )
        }

        fun quick(today: LocalDate, now: LocalDateTime): Map<MuscleGroup, MuscleQuickInsight> {
            return anatomical.associateWith { muscle ->
                val insight = insight(muscle, today, now, MuscleInsightsPeriod.Weeks4)
                MuscleQuickInsight(muscle, insight.lastStimulus, insight.recentPrimarySets)
            }
        }

        fun preview(today: LocalDate, now: LocalDateTime, range: StatisticsRange): MuscleInsightsPreview {
            val bars = anatomical.map { muscle ->
                val sets = stimuli.count { stimulus ->
                    stimulus.muscle == muscle &&
                        stimulus.role == Involvement.PRIMARY &&
                        range.contains(stimulus.date, today)
                }
                MuscleSetBar(muscle, sets)
            }.filter { it.primarySets > 0 }
                .sortedWith(
                    compareByDescending<MuscleSetBar> { it.primarySets }
                        .thenByDescending { bar ->
                            stimuli.filter { it.muscle == bar.muscle }.maxOfOrNull { it.at } ?: LocalDateTime.MIN
                        }
                        .thenBy { it.muscle.name }
                )
                .take(PREVIEW_BARS)
            val featured = bars.firstOrNull()?.muscle
            val stimulus = featured?.let { muscle ->
                age(latest(stimuli.filter { it.muscle == muscle && !it.date.isAfter(today) }), now)
            } ?: StimulusAge.None
            return MuscleInsightsPreview(
                bars = bars,
                featuredMuscle = featured,
                featuredStimulus = stimulus
            )
        }
    }

    private fun coverage(earliest: LocalDate?, baselineStart: LocalDate): BaselineCoverage {
        if (earliest == null) return BaselineCoverage.INSUFFICIENT
        if (!earliest.isAfter(baselineStart)) return BaselineCoverage.FULL
        val toleranceEnd = baselineStart.plusDays(COVERAGE_TOLERANCE_DAYS)
        return if (!earliest.isAfter(toleranceEnd)) {
            BaselineCoverage.PARTIAL
        } else {
            BaselineCoverage.INSUFFICIENT
        }
    }

    private fun classify(recentSets: Int, baselineSets: Int, coverage: BaselineCoverage): LoadComparison {
        if (coverage == BaselineCoverage.INSUFFICIENT) return LoadComparison.INSUFFICIENT
        val weekly = baselineSets.toDouble() / BASELINE_WEEKS
        if (weekly == 0.0) {
            return if (recentSets == 0) LoadComparison.NEAR else LoadComparison.ABOVE
        }
        val ratio = recentSets / weekly
        return when {
            ratio > 1.0 + NEAR_TOLERANCE -> LoadComparison.ABOVE
            ratio < 1.0 - NEAR_TOLERANCE -> LoadComparison.BELOW
            else -> LoadComparison.NEAR
        }
    }

    private fun age(stimulus: Stimulus?, now: LocalDateTime): StimulusAge {
        stimulus ?: return StimulusAge.None
        val minutes = ChronoUnit.MINUTES.between(stimulus.at, now).coerceAtLeast(0)
        return when {
            minutes < MINUTES_PER_HOUR -> StimulusAge.Minutes(minutes)
            minutes < MINUTES_PER_DAY -> StimulusAge.Hours(minutes / MINUTES_PER_HOUR)
            else -> StimulusAge.Days((minutes / MINUTES_PER_DAY).toInt())
        }
    }

    private fun latest(stimuli: List<Stimulus>): Stimulus? {
        return stimuli.maxWithOrNull(compareBy<Stimulus> { it.at }.thenBy { it.setId })
    }

    private fun weekly(
        primary: List<Stimulus>,
        today: LocalDate,
        period: MuscleInsightsPeriod
    ): List<WeeklySetPoint> {
        if (primary.isEmpty()) return emptyList()
        val end = weekStart(today)
        val start = period.startInclusive(today)
            ?: primary.minOfOrNull { weekStart(it.date) }
            ?: return emptyList()
        if (start.isAfter(end)) return emptyList()
        val counts = primary.groupingBy { weekStart(it.date) }.eachCount()
        val points = ArrayList<WeeklySetPoint>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            points += WeeklySetPoint(cursor, counts[cursor] ?: 0)
            cursor = cursor.plusWeeks(1)
        }
        return points
    }

    private fun spacing(dates: List<LocalDate>): TrainingSpacing? {
        if (dates.size < 2) return null
        val gaps = dates.zipWithNext { first, second ->
            ChronoUnit.DAYS.between(first, second).toInt()
        }
        return TrainingSpacing(
            sessionDates = dates.size,
            averageDays = gaps.average(),
            shortestDays = gaps.min(),
            longestDays = gaps.max()
        )
    }

    private fun stimulusAt(completedAt: Long?, session: WorkoutSession, zone: ZoneId): LocalDateTime {
        val millis = completedAt ?: session.finishedAt
        return if (millis != null) {
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        } else {
            session.workoutDate.atStartOfDay()
        }
    }

    private fun weekStart(date: LocalDate): LocalDate {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun earlier(current: LocalDate?, candidate: LocalDate): LocalDate {
        return if (current == null || candidate.isBefore(current)) candidate else current
    }

    private val anatomical = MuscleHeatmapAssembler.anatomicalGroups.toSet()

    internal enum class Involvement { PRIMARY, SECONDARY }

    internal data class Stimulus(
        val setId: Long,
        val date: LocalDate,
        val at: LocalDateTime,
        val muscle: MuscleGroup,
        val role: Involvement
    )
}
