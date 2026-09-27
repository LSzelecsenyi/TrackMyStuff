package app.mymusclemap.domain.reports

import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.model.WeightMeasurement
import app.mymusclemap.domain.statistics.VolumeTrendResolution
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

object ReportLogic {
    fun summarize(
        period: ReportPeriod,
        inputs: ReportInputs,
        today: LocalDate,
        historyStart: LocalDate?
    ): ReportSummary {
        require(period.isClosed(today)) {
            "Reports summarize closed periods only"
        }
        val current = metrics(period, inputs)
        val coverage = coverage(period, historyStart)
        val previousPeriod = period.previous()
        val previous = if (historyStart != null && !previousPeriod.endInclusive.isBefore(historyStart)) {
            compare(previousPeriod, current, metrics(previousPeriod, inputs))
        } else {
            null
        }
        return ReportSummary(
            period = period,
            coverage = coverage,
            historyStart = historyStart,
            activity = current.activity,
            adherence = current.adherence,
            volume = current.volume,
            muscles = current.muscles,
            exerciseHighlights = current.exerciseHighlights,
            bodyWeight = current.bodyWeight,
            previous = previous
        )
    }

    fun volumeResolution(kind: ReportKind): VolumeTrendResolution {
        return when (kind) {
            ReportKind.Monthly -> VolumeTrendResolution.Daily
            ReportKind.Quarterly,
            ReportKind.HalfYear -> VolumeTrendResolution.Weekly
            ReportKind.Yearly -> VolumeTrendResolution.Monthly
        }
    }

    private fun coverage(period: ReportPeriod, historyStart: LocalDate?): ReportHistoryCoverage? {
        if (historyStart == null || period.endInclusive.isBefore(historyStart)) {
            return null
        }
        return ReportCatalog.coverage(period, historyStart)
    }

    private fun metrics(period: ReportPeriod, inputs: ReportInputs): PeriodMetrics {
        val activitySessions = inputs.sessions
            .filter { it.session.status == SessionStatus.COMPLETED }
            .filter { period.contains(it.session.workoutDate) }
            .sortedWith(compareBy({ it.session.workoutDate }, { it.session.id }))
        return PeriodMetrics(
            activity = activity(activitySessions),
            adherence = adherence(inputs.scheduled, period),
            volume = volume(activitySessions, period),
            muscles = muscles(activitySessions),
            exerciseHighlights = highlights(activitySessions),
            bodyWeight = bodyWeight(inputs.bodyWeights, period)
        )
    }

    private fun activity(sessions: List<WorkoutSessionAggregate>): ReportActivity {
        val durations = sessions
            .map { ElapsedTime.forSession(it.session) }
            .filter { it >= 1_000L }
        return ReportActivity(
            workoutCount = sessions.size,
            trainingDayCount = sessions.map { it.session.workoutDate }.distinct().size,
            completedSetCount = sessions.sumOf { aggregate ->
                aggregate.exercises.sumOf { item -> item.sets.count { WorkoutSetVolume.isCompleted(it) } }
            },
            durationMillis = durations.takeIf { it.isNotEmpty() }?.sum()
        )
    }

    private fun adherence(scheduled: List<ScheduledWorkout>, period: ReportPeriod): ReportAdherence {
        val due = scheduled.filter { occurrence ->
            !occurrence.isCancelled && period.contains(occurrence.scheduledDate)
        }
        return ReportAdherence(
            plannedCount = due.size,
            completedCount = due.count { it.sessionStatus == SessionStatus.COMPLETED }
        )
    }

    private fun volume(sessions: List<WorkoutSessionAggregate>, period: ReportPeriod): ReportVolume {
        val sets = sessions.flatMap { aggregate ->
            aggregate.exercises.flatMap { item ->
                item.sets.mapNotNull { set ->
                    WorkoutSetVolume.volumeKg(item.exercise, set)?.let { kg ->
                        aggregate.session.workoutDate to kg
                    }
                }
            }
        }
        val resolution = volumeResolution(period.kind)
        val total = sets.sumOf { it.second }
        return ReportVolume(
            totalKg = if (sets.isEmpty()) null else total,
            completedSetCount = sets.size,
            resolution = resolution,
            trend = volumeTrend(sets, period, resolution)
        )
    }

    private fun volumeTrend(
        sets: List<Pair<LocalDate, Double>>,
        period: ReportPeriod,
        resolution: VolumeTrendResolution
    ): List<SeriesPoint> {
        val start = bucketStart(period.startInclusive, resolution)
        val end = bucketStart(period.endInclusive, resolution)
        val sums = HashMap<LocalDate, Double>()
        sets.forEach { (date, kg) ->
            val key = bucketStart(date, resolution)
            if (!key.isBefore(start) && !key.isAfter(end)) {
                sums[key] = (sums[key] ?: 0.0) + kg
            }
        }
        val points = ArrayList<SeriesPoint>()
        var cursor = start
        while (!cursor.isAfter(end)) {
            points += SeriesPoint(date = cursor, value = sums[cursor] ?: 0.0)
            cursor = nextBucket(cursor, resolution)
        }
        return points
    }

    private fun bucketStart(date: LocalDate, resolution: VolumeTrendResolution): LocalDate {
        return when (resolution) {
            VolumeTrendResolution.Daily -> date
            VolumeTrendResolution.Weekly ->
                date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            VolumeTrendResolution.Monthly -> date.withDayOfMonth(1)
        }
    }

    private fun nextBucket(date: LocalDate, resolution: VolumeTrendResolution): LocalDate {
        return when (resolution) {
            VolumeTrendResolution.Daily -> date.plusDays(1)
            VolumeTrendResolution.Weekly -> date.plusWeeks(1)
            VolumeTrendResolution.Monthly -> date.plusMonths(1)
        }
    }

    private fun muscles(sessions: List<WorkoutSessionAggregate>): List<ReportMuscleCount> {
        data class Acc(var sets: Int, val sessionIds: MutableSet<Long>, var last: LocalDate)
        val acc = mutableMapOf<MuscleGroup, Acc>()
        sessions.forEach { aggregate ->
            aggregate.exercises.forEach { item ->
                val completedSets = item.sets.count { WorkoutSetVolume.isCompleted(it) }
                if (completedSets == 0) return@forEach
                val current = acc.getOrPut(item.exercise.primaryMuscle) {
                    Acc(0, mutableSetOf(), aggregate.session.workoutDate)
                }
                current.sets += completedSets
                current.sessionIds += aggregate.session.id
                if (aggregate.session.workoutDate.isAfter(current.last)) {
                    current.last = aggregate.session.workoutDate
                }
            }
        }
        return acc.map { (muscle, value) ->
            ReportMuscleCount(
                muscle = muscle,
                completedSetCount = value.sets,
                workoutCount = value.sessionIds.size,
                lastTrained = value.last
            )
        }.sortedWith(
            compareByDescending<ReportMuscleCount> { it.completedSetCount }
                .thenByDescending { it.lastTrained }
                .thenBy { it.muscle.name }
        )
    }

    private fun highlights(sessions: List<WorkoutSessionAggregate>): List<ReportExerciseHighlight> {
        val byExercise = linkedMapOf<Long, MutableList<Pair<LocalDate, SessionExerciseItem>>>()
        sessions.forEach { aggregate ->
            aggregate.exercises.forEach { item ->
                byExercise.getOrPut(item.exercise.exerciseId) { mutableListOf() }
                    .add(aggregate.session.workoutDate to item)
            }
        }
        val highlights = byExercise.mapNotNull { (exerciseId, appearances) ->
            highlight(exerciseId, appearances)
        }
        val byKind = highlights.groupBy { it.kind }
        return listOf(
            ReportPerformanceKind.EffectiveKg,
            ReportPerformanceKind.Reps,
            ReportPerformanceKind.DurationSeconds,
            ReportPerformanceKind.DistanceMeters
        ).flatMap { kind ->
            byKind[kind].orEmpty().sortedWith(
                compareByDescending<ReportExerciseHighlight> { abs(it.delta) }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.exerciseId }
            )
        }.take(ReportSummary.HIGHLIGHT_LIMIT)
    }

    private fun highlight(
        exerciseId: Long,
        appearances: List<Pair<LocalDate, SessionExerciseItem>>
    ): ReportExerciseHighlight? {
        val grouped = appearances.groupBy { it.first }
        val days = ArrayList<DayPerformance>()
        for ((date, items) in grouped) {
            val day = dayPerformance(date, items.map { it.second }) ?: continue
            if (day.incompatible) return null
            days += day
        }
        days.sortBy { it.date }
        if (days.size < 2) return null
        val types = days.map { it.measurementType }.distinct()
        if (types.size != 1) return null
        val first = days.first()
        val last = days.last()
        val measurement = types.single()
        val compared = compareDays(measurement, first, last) ?: return null
        return ReportExerciseHighlight(
            exerciseId = exerciseId,
            name = last.name,
            measurementType = measurement,
            kind = compared.kind,
            baselineDate = first.date,
            latestDate = last.date,
            baseline = compared.baseline,
            latest = compared.latest
        )
    }

    private fun compareDays(
        measurement: MeasurementType,
        first: DayPerformance,
        last: DayPerformance
    ): ComparedPerformance? {
        return when (measurement) {
            MeasurementType.REPETITIONS -> repsComparison(first, last)
            MeasurementType.REPETITIONS_AND_WEIGHT -> weightedOrRepsComparison(first, last)
            MeasurementType.DURATION,
            MeasurementType.DURATION_AND_WEIGHT -> durationComparison(first, last)
            MeasurementType.DISTANCE_AND_DURATION -> distanceComparison(first, last)
            MeasurementType.COMPLETION_ONLY -> null
        }
    }

    private fun weightedOrRepsComparison(first: DayPerformance, last: DayPerformance): ComparedPerformance? {
        val firstLoad = first.effectiveKg
        val lastLoad = last.effectiveKg
        if (firstLoad != null && lastLoad != null) {
            return ComparedPerformance(ReportPerformanceKind.EffectiveKg, firstLoad, lastLoad)
        }
        if (firstLoad == null && lastLoad == null) {
            return repsComparison(first, last)
        }
        return null
    }

    private fun repsComparison(first: DayPerformance, last: DayPerformance): ComparedPerformance? {
        val baseline = first.reps ?: return null
        val latest = last.reps ?: return null
        return ComparedPerformance(ReportPerformanceKind.Reps, baseline.toDouble(), latest.toDouble())
    }

    private fun durationComparison(first: DayPerformance, last: DayPerformance): ComparedPerformance? {
        val baseline = first.durationSeconds ?: return null
        val latest = last.durationSeconds ?: return null
        return ComparedPerformance(
            ReportPerformanceKind.DurationSeconds,
            baseline.toDouble(),
            latest.toDouble()
        )
    }

    private fun distanceComparison(first: DayPerformance, last: DayPerformance): ComparedPerformance? {
        val baseline = first.distanceMeters ?: return null
        val latest = last.distanceMeters ?: return null
        return ComparedPerformance(ReportPerformanceKind.DistanceMeters, baseline, latest)
    }

    private fun dayPerformance(date: LocalDate, items: List<SessionExerciseItem>): DayPerformance? {
        val performed = items.map { item ->
            item to item.sets.filter { WorkoutSetVolume.isCompleted(it) }
        }.filter { it.second.isNotEmpty() }
        if (performed.isEmpty()) return null
        val types = performed.map { it.first.exercise.measurementType }.distinct()
        if (types.size != 1) {
            return DayPerformance(
                date = date,
                name = performed.first().first.exercise.name,
                measurementType = types.first(),
                reps = null,
                effectiveKg = null,
                durationSeconds = null,
                distanceMeters = null,
                incompatible = true
            )
        }
        val sets = performed.flatMap { it.second }
        val exercise = performed.maxBy { it.first.exercise.id }.first.exercise
        val loads = sets.mapNotNull { set -> weightedCandidate(set, exercise.weightInterpretation) }
        val bestLoad = loads.maxWithOrNull(compareBy<WeightedLoad> { it.kg }.thenBy { it.reps })
        return DayPerformance(
            date = date,
            name = exercise.name,
            measurementType = types.single(),
            reps = sets.mapNotNull { it.actualReps }.filter { it > 0 }.maxOrNull(),
            effectiveKg = bestLoad?.kg,
            durationSeconds = sets.mapNotNull { it.actualDurationSeconds }.filter { it > 0 }.maxOrNull(),
            distanceMeters = sets.mapNotNull { it.actualDistanceMeters }.filter { it > 0.0 }.maxOrNull(),
            incompatible = false
        )
    }

    private fun weightedCandidate(set: SessionSet, interpretation: WeightInterpretation): WeightedLoad? {
        val kg = WorkoutSetVolume.effectiveLoadKg(set, interpretation) ?: return null
        val reps = set.actualReps ?: return null
        if (reps <= 0) return null
        return WeightedLoad(kg, reps)
    }

    private fun bodyWeight(measurements: List<WeightMeasurement>, period: ReportPeriod): ReportBodyWeight? {
        val inPeriod = measurements
            .filter { period.contains(it.date) }
            .sortedWith(compareBy({ it.date }, { it.id }))
        val first = inPeriod.firstOrNull() ?: return null
        if (inPeriod.size == 1) {
            return ReportBodyWeight(firstDate = first.date, firstKg = first.weightKg)
        }
        val last = inPeriod.last()
        return ReportBodyWeight(
            firstDate = first.date,
            firstKg = first.weightKg,
            lastDate = last.date,
            lastKg = last.weightKg,
            changeKg = last.weightKg - first.weightKg,
            averageKg = inPeriod.map { it.weightKg }.average()
        )
    }

    private fun compare(
        previousPeriod: ReportPeriod,
        current: PeriodMetrics,
        previous: PeriodMetrics
    ): ReportPeriodComparison {
        val previousWorkouts = previous.activity.workoutCount
        val workoutPercent = if (previousWorkouts > 0) {
            (current.activity.workoutCount - previousWorkouts) * 100.0 / previousWorkouts
        } else {
            null
        }
        val previousVolume = previous.volume.totalKg
        val volumePercent = if (previousVolume != null && previousVolume > 0.0) {
            ((current.volume.totalKg ?: 0.0) - previousVolume) * 100.0 / previousVolume
        } else {
            null
        }
        val currentPercent = current.adherence.percent
        val previousPercent = previous.adherence.percent
        return ReportPeriodComparison(
            period = previousPeriod,
            workoutCountDelta = current.activity.workoutCount - previousWorkouts,
            workoutCountPercent = workoutPercent,
            volumeDeltaKg = (current.volume.totalKg ?: 0.0) - (previousVolume ?: 0.0),
            volumePercent = volumePercent,
            adherencePointDelta = if (currentPercent != null && previousPercent != null) {
                currentPercent - previousPercent
            } else {
                null
            }
        )
    }

    private data class PeriodMetrics(
        val activity: ReportActivity,
        val adherence: ReportAdherence,
        val volume: ReportVolume,
        val muscles: List<ReportMuscleCount>,
        val exerciseHighlights: List<ReportExerciseHighlight>,
        val bodyWeight: ReportBodyWeight?
    )

    private data class DayPerformance(
        val date: LocalDate,
        val name: String,
        val measurementType: MeasurementType,
        val reps: Int?,
        val effectiveKg: Double?,
        val durationSeconds: Int?,
        val distanceMeters: Double?,
        val incompatible: Boolean = false
    )

    private data class WeightedLoad(val kg: Double, val reps: Int)

    private data class ComparedPerformance(
        val kind: ReportPerformanceKind,
        val baseline: Double,
        val latest: Double
    )
}
