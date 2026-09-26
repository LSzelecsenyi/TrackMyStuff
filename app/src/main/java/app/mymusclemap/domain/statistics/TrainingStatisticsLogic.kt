package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.WeeklyAverageCalculator
import app.mymusclemap.domain.WeeklyOverviewLogic
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.LocalDate

object TrainingStatisticsLogic {
    const val DAYS_30 = 30L
    const val VOLUME_TREND_WEEKS = 8
    const val FREQUENCY_WEEKS = 4
    const val MAX_EXERCISE_PROGRESS = 5
    const val MAX_RECENT_MUSCLES = 6
    const val MAX_FREQUENT_MUSCLES = 3

    fun assemble(
        aggregates: List<WorkoutSessionAggregate>,
        today: LocalDate
    ): TrainingStatistics {
        val completed = aggregates
            .filter { it.session.status == SessionStatus.COMPLETED }
            .filter { !it.session.workoutDate.isAfter(today) }
            .sortedWith(compareBy({ it.session.workoutDate }, { it.session.id }))
        if (completed.isEmpty()) {
            return TrainingStatistics()
        }
        val last7Start = WeeklyOverviewLogic.windowStart(today)
        val last30Start = today.minusDays(DAYS_30 - 1)
        val last7 = completed.filter { WeeklyOverviewLogic.inWindow(it.session.workoutDate, today) }
        val last30 = completed.filter { inInclusiveWindow(it.session.workoutDate, last30Start, today) }
        val volumeSets = volumeSets(completed)
        val weightedLoad = weightedLoad(volumeSets, last7Start, last30Start, today)
        return TrainingStatistics(
            completedWorkoutCount = completed.size,
            workoutsLast7Days = last7.size,
            trainingDaysLast7Days = last7.map { it.session.workoutDate }.distinct().size,
            workoutsLast30Days = last30.size,
            trainingDaysLast30Days = last30.map { it.session.workoutDate }.distinct().size,
            recentWeeklyFrequency = weeklyFrequency(completed, today),
            weightedLoad = weightedLoad,
            weeklyVolume = weeklyVolumeTrend(volumeSets, today, includeTrend = weightedLoad != null),
            exerciseProgress = exerciseProgress(completed),
            recentlyTrainedMuscles = muscleSummary(completed, last7Start, today, MAX_RECENT_MUSCLES),
            mostTrainedMuscles = muscleSummary(completed, last30Start, today, MAX_FREQUENT_MUSCLES)
        )
    }

    private fun inInclusiveWindow(date: LocalDate, start: LocalDate, end: LocalDate): Boolean {
        return !date.isBefore(start) && !date.isAfter(end)
    }

    private fun weeklyFrequency(
        completed: List<WorkoutSessionAggregate>,
        today: LocalDate
    ): Double {
        val currentWeekStart = WeeklyAverageCalculator.weekStart(
            WeeklyAverageCalculator.isoWeekKey(today).year,
            WeeklyAverageCalculator.isoWeekKey(today).week
        )
        val windowStart = currentWeekStart.minusWeeks((FREQUENCY_WEEKS - 1).toLong())
        val count = completed.count { inInclusiveWindow(it.session.workoutDate, windowStart, today) }
        return count.toDouble() / FREQUENCY_WEEKS.toDouble()
    }

    private fun volumeSets(completed: List<WorkoutSessionAggregate>): List<VolumeEligibleSet> {
        return completed.flatMap { aggregate ->
            aggregate.exercises.flatMap { item ->
                item.sets.mapNotNull { set ->
                    WorkoutSetVolume.volumeKg(item.exercise, set)?.let { volume ->
                        VolumeEligibleSet(aggregate.session.workoutDate, volume)
                    }
                }
            }
        }
    }

    private fun weightedLoad(
        volumeSets: List<VolumeEligibleSet>,
        last7Start: LocalDate,
        last30Start: LocalDate,
        today: LocalDate
    ): WeightedLoadVolume? {
        if (volumeSets.isEmpty()) return null
        val last7 = volumeSets.filter { inInclusiveWindow(it.date, last7Start, today) }
        val last30 = volumeSets.filter { inInclusiveWindow(it.date, last30Start, today) }
        return WeightedLoadVolume(
            last7DaysKg = last7.sumOf { it.volumeKg },
            last30DaysKg = last30.sumOf { it.volumeKg },
            completedSetCountLast7Days = last7.size,
            completedSetCountLast30Days = last30.size
        )
    }

    private fun weeklyVolumeTrend(
        volumeSets: List<VolumeEligibleSet>,
        today: LocalDate,
        includeTrend: Boolean
    ): List<WeeklyVolumePoint> {
        if (!includeTrend) return emptyList()
        val currentKey = WeeklyAverageCalculator.isoWeekKey(today)
        val currentStart = WeeklyAverageCalculator.weekStart(currentKey.year, currentKey.week)
        val byWeek = volumeSets.groupBy { WeeklyAverageCalculator.isoWeekKey(it.date) }
        val weeks = (VOLUME_TREND_WEEKS - 1 downTo 0).map { offset ->
            val start = currentStart.minusWeeks(offset.toLong())
            val key = WeeklyAverageCalculator.isoWeekKey(start)
            WeeklyVolumePoint(
                weekStart = start,
                volumeKg = byWeek[key].orEmpty().sumOf { it.volumeKg }
            )
        }
        return if (weeks.any { it.volumeKg > 0.0 }) weeks else emptyList()
    }

    private fun exerciseProgress(
        completed: List<WorkoutSessionAggregate>
    ): List<ExerciseProgressSummary> {
        val grouped = linkedMapOf<Long, MutableList<Pair<LocalDate, SessionExerciseItem>>>()
        completed.forEach { aggregate ->
            val date = aggregate.session.workoutDate
            aggregate.exercises.forEach { item ->
                grouped.getOrPut(item.exercise.exerciseId) { mutableListOf() }
                    .add(date to item)
            }
        }
        return grouped.values
            .mapNotNull { appearances -> summarizeExercise(appearances) }
            .sortedWith(
                compareByDescending<ExerciseProgressSummary> { it.lastTrained }
                    .thenBy { it.name.lowercase() }
            )
            .take(MAX_EXERCISE_PROGRESS)
    }

    private fun summarizeExercise(
        appearances: List<Pair<LocalDate, SessionExerciseItem>>
    ): ExerciseProgressSummary? {
        val latest = appearances.maxWith(compareBy({ it.first }, { it.second.exercise.id }))
        val exercise = latest.second.exercise
        val completedAppearances = appearances.map { (date, item) ->
            date to item.sets.filter { WorkoutSetVolume.isCompleted(it) }
        }.filter { it.second.isNotEmpty() }
        if (completedAppearances.isEmpty()) return null
        val lastTrained = completedAppearances.maxOf { it.first }
        return when (exercise.measurementType) {
            MeasurementType.REPETITIONS ->
                repsProgress(exercise, completedAppearances, lastTrained)
            MeasurementType.REPETITIONS_AND_WEIGHT ->
                weightedOrRepsProgress(exercise, completedAppearances, lastTrained)
            MeasurementType.DURATION,
            MeasurementType.DURATION_AND_WEIGHT ->
                durationProgress(exercise, completedAppearances, lastTrained)
            MeasurementType.DISTANCE_AND_DURATION ->
                distanceProgress(exercise, completedAppearances, lastTrained)
            MeasurementType.COMPLETION_ONLY ->
                completionProgress(exercise, completedAppearances, lastTrained)
        }
    }

    private fun repsProgress(
        exercise: SessionExercise,
        appearances: List<Pair<LocalDate, List<SessionSet>>>,
        lastTrained: LocalDate
    ): ExerciseProgressSummary? {
        val byDate = appearances
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sets) -> sets.flatten().mapNotNull { it.actualReps }.filter { it > 0 }.maxOrNull() }
            .filterValues { it != null }
            .mapValues { it.value!! }
        if (byDate.isEmpty()) return null
        val history = historyPoints(byDate) { it.toDouble() }
        val bestReps = byDate.values.max()
        val recentReps = byDate.getValue(byDate.keys.max())
        return ExerciseProgressSummary(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            measurementType = exercise.measurementType,
            lastTrained = lastTrained,
            best = ExerciseBest.Reps(bestReps),
            recent = ExerciseBest.Reps(recentReps),
            history = history,
            historyKind = ExerciseHistoryKind.REPS
        )
    }

    private fun weightedOrRepsProgress(
        exercise: SessionExercise,
        appearances: List<Pair<LocalDate, List<SessionSet>>>,
        lastTrained: LocalDate
    ): ExerciseProgressSummary? {
        val weighted = appearances.flatMap { (date, sets) ->
            sets.mapNotNull { set ->
                val load = WorkoutSetVolume.effectiveLoadKg(set, exercise.weightInterpretation)
                    ?: return@mapNotNull null
                val reps = set.actualReps ?: return@mapNotNull null
                if (reps <= 0) return@mapNotNull null
                WeightedCandidate(
                    date = date,
                    effectiveKg = load,
                    recordedKg = set.actualWeightKg ?: return@mapNotNull null,
                    reps = reps,
                    perSide = exercise.weightInterpretation == WeightInterpretation.PER_SIDE
                )
            }
        }
        if (weighted.isEmpty()) {
            return repsProgress(exercise, appearances, lastTrained)
        }
        val best = weighted.maxWith(weightedComparator)
        val byDate = weighted.groupBy { it.date }.mapValues { (_, sets) ->
            sets.maxWith(weightedComparator)
        }
        val recent = byDate.getValue(byDate.keys.max())
        return ExerciseProgressSummary(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            measurementType = exercise.measurementType,
            lastTrained = lastTrained,
            best = best.toBest(),
            recent = recent.toBest(),
            history = historyPoints(byDate) { it.effectiveKg },
            historyKind = ExerciseHistoryKind.EFFECTIVE_KG
        )
    }

    private fun durationProgress(
        exercise: SessionExercise,
        appearances: List<Pair<LocalDate, List<SessionSet>>>,
        lastTrained: LocalDate
    ): ExerciseProgressSummary? {
        val byDate = appearances
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, sets) ->
                sets.flatten().mapNotNull { it.actualDurationSeconds }.filter { it > 0 }.maxOrNull()
            }
            .filterValues { it != null }
            .mapValues { it.value!! }
        if (byDate.isEmpty()) return null
        val best = byDate.values.max()
        val recent = byDate.getValue(byDate.keys.max())
        return ExerciseProgressSummary(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            measurementType = exercise.measurementType,
            lastTrained = lastTrained,
            best = ExerciseBest.Duration(best),
            recent = ExerciseBest.Duration(recent),
            history = historyPoints(byDate) { it.toDouble() },
            historyKind = ExerciseHistoryKind.DURATION_SECONDS
        )
    }

    private fun distanceProgress(
        exercise: SessionExercise,
        appearances: List<Pair<LocalDate, List<SessionSet>>>,
        lastTrained: LocalDate
    ): ExerciseProgressSummary? {
        data class DistancePoint(val meters: Double, val durationSeconds: Int?)
        val byDate = appearances
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, grouped) ->
                grouped.flatten()
                    .mapNotNull { set ->
                        val meters = set.actualDistanceMeters ?: return@mapNotNull null
                        if (meters <= 0.0) return@mapNotNull null
                        DistancePoint(meters, set.actualDurationSeconds?.takeIf { it > 0 })
                    }
                    .maxByOrNull { it.meters }
            }
            .filterValues { it != null }
            .mapValues { it.value!! }
        if (byDate.isEmpty()) return null
        val best = byDate.values.maxBy { it.meters }
        val recent = byDate.getValue(byDate.keys.max())
        return ExerciseProgressSummary(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            measurementType = exercise.measurementType,
            lastTrained = lastTrained,
            best = ExerciseBest.Distance(best.meters, best.durationSeconds),
            recent = ExerciseBest.Distance(recent.meters, recent.durationSeconds),
            history = historyPoints(byDate) { it.meters },
            historyKind = ExerciseHistoryKind.DISTANCE_METERS
        )
    }

    private fun completionProgress(
        exercise: SessionExercise,
        appearances: List<Pair<LocalDate, List<SessionSet>>>,
        lastTrained: LocalDate
    ): ExerciseProgressSummary {
        val dates = appearances.map { it.first }.distinct().sorted()
        return ExerciseProgressSummary(
            exerciseId = exercise.exerciseId,
            name = exercise.name,
            measurementType = exercise.measurementType,
            lastTrained = lastTrained,
            best = ExerciseBest.Completions(dates.size),
            recent = ExerciseBest.Completions(1),
            history = dates.map { ExerciseHistoryPoint(it, 1.0) },
            historyKind = ExerciseHistoryKind.COMPLETIONS
        )
    }

    private fun <T> historyPoints(
        byDate: Map<LocalDate, T>,
        value: (T) -> Double
    ): List<ExerciseHistoryPoint> {
        return byDate.entries
            .sortedBy { it.key }
            .map { ExerciseHistoryPoint(it.key, value(it.value)) }
    }

    private fun muscleSummary(
        completed: List<WorkoutSessionAggregate>,
        start: LocalDate,
        today: LocalDate,
        limit: Int
    ): List<MuscleTrainingCount> {
        data class Acc(var sets: Int, val sessionIds: MutableSet<Long>, var last: LocalDate)
        val acc = mutableMapOf<MuscleGroup, Acc>()
        completed.filter { inInclusiveWindow(it.session.workoutDate, start, today) }.forEach { aggregate ->
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
            MuscleTrainingCount(
                muscle = muscle,
                completedSetCount = value.sets,
                workoutCount = value.sessionIds.size,
                lastTrained = value.last
            )
        }.sortedWith(
            compareByDescending<MuscleTrainingCount> { it.completedSetCount }
                .thenByDescending { it.lastTrained }
                .thenBy { it.muscle.name }
        ).take(limit)
    }

    private val weightedComparator = compareBy<WeightedCandidate> { it.effectiveKg }
        .thenBy { it.reps }

    private fun WeightedCandidate.toBest(): ExerciseBest.WeightedSet {
        return ExerciseBest.WeightedSet(
            effectiveKg = effectiveKg,
            recordedKg = recordedKg,
            reps = reps,
            perSide = perSide
        )
    }
}
