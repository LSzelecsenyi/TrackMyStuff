package app.mymusclemap.domain.statistics

import app.mymusclemap.domain.WeeklyAverageCalculator
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.SeriesPoint
import app.mymusclemap.domain.workout.ElapsedTime
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.ScheduledWorkoutStatus
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object TrainingStatisticsLogic {
    const val SUMMARY_MUSCLES = 3
    const val SUMMARY_REST = 3
    const val SUMMARY_EXERCISES = 5
    const val TREND_MAX_POINTS = 12

    fun assemble(
        aggregates: List<WorkoutSessionAggregate>,
        scheduled: List<ScheduledWorkout>,
        today: LocalDate,
        range: StatisticsRange
    ): TrainingStatistics {
        val completed = aggregates
            .filter { it.session.status == SessionStatus.COMPLETED }
            .filter { range.contains(it.session.workoutDate, today) }
            .sortedWith(compareBy({ it.session.workoutDate }, { it.session.id }))
        return TrainingStatistics(
            range = range,
            activity = activity(completed),
            adherence = adherence(scheduled, today, range),
            volume = volume(completed, today),
            muscleDistribution = muscleDistribution(completed),
            restBetweenSessions = restBetweenSessions(completed),
            exercises = exerciseProgress(completed)
        )
    }

    fun summaryMuscles(all: List<MuscleTrainingCount>): List<MuscleTrainingCount> {
        return all.take(SUMMARY_MUSCLES)
    }

    fun summaryRest(all: List<MuscleRestSummary>): List<MuscleRestSummary> {
        return all.take(SUMMARY_REST)
    }

    fun summaryExercises(all: List<ExerciseProgressSummary>): List<ExerciseProgressSummary> {
        return all.take(SUMMARY_EXERCISES)
    }

    private fun activity(completed: List<WorkoutSessionAggregate>): TrainingActivity {
        val durations = completed
            .map { ElapsedTime.forSession(it.session) }
            .filter { it >= 1_000L }
        return TrainingActivity(
            workoutCount = completed.size,
            completedSetCount = completed.sumOf { aggregate ->
                aggregate.exercises.sumOf { item -> item.sets.count { WorkoutSetVolume.isCompleted(it) } }
            },
            trainingDayCount = completed.map { it.session.workoutDate }.distinct().size,
            durationMillis = durations.takeIf { it.isNotEmpty() }?.sum()
        )
    }

    private fun adherence(
        scheduled: List<ScheduledWorkout>,
        today: LocalDate,
        range: StatisticsRange
    ): PlanAdherence {
        val due = scheduled.filter { range.contains(it.scheduledDate, today) }
        return PlanAdherence(
            plannedCount = due.size,
            completedCount = due.count { it.status == ScheduledWorkoutStatus.COMPLETED },
            missedCount = due.count { item ->
                item.status == ScheduledWorkoutStatus.PLANNED && item.scheduledDate.isBefore(today)
            },
            inProgressCount = due.count { it.status == ScheduledWorkoutStatus.IN_PROGRESS }
        )
    }

    private fun volume(
        completed: List<WorkoutSessionAggregate>,
        today: LocalDate
    ): TrainingVolume {
        val volumeSets = completed.flatMap { aggregate ->
            aggregate.exercises.flatMap { item ->
                item.sets.mapNotNull { set ->
                    WorkoutSetVolume.volumeKg(item.exercise, set)?.let { kg ->
                        VolumeEligibleSet(aggregate.session.workoutDate, kg)
                    }
                }
            }
        }
        if (volumeSets.isEmpty()) {
            return TrainingVolume()
        }
        return TrainingVolume(
            totalKg = volumeSets.sumOf { it.volumeKg },
            completedSetCount = volumeSets.size,
            trend = volumeTrend(volumeSets, today)
        )
    }

    private fun volumeTrend(
        volumeSets: List<VolumeEligibleSet>,
        today: LocalDate
    ): List<SeriesPoint> {
        val byWeek = volumeSets.groupBy { WeeklyAverageCalculator.isoWeekKey(it.date) }
        val points = byWeek.entries
            .map { (key, sets) ->
                val start = WeeklyAverageCalculator.weekStart(key.year, key.week)
                SeriesPoint(date = start, value = sets.sumOf { it.volumeKg })
            }
            .filter { it.value > 0.0 && !it.date.isAfter(today) }
            .sortedBy { it.date }
        if (points.size < 2) {
            return emptyList()
        }
        return points.takeLast(TREND_MAX_POINTS)
    }

    private fun muscleDistribution(completed: List<WorkoutSessionAggregate>): List<MuscleTrainingCount> {
        data class Acc(var sets: Int, val sessionIds: MutableSet<Long>, var last: LocalDate)
        val acc = mutableMapOf<MuscleGroup, Acc>()
        completed.forEach { aggregate ->
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
        )
    }

    private fun restBetweenSessions(completed: List<WorkoutSessionAggregate>): List<MuscleRestSummary> {
        val datesByMuscle = mutableMapOf<MuscleGroup, MutableSet<LocalDate>>()
        completed.forEach { aggregate ->
            aggregate.exercises.forEach { item ->
                if (item.sets.none { WorkoutSetVolume.isCompleted(it) }) return@forEach
                datesByMuscle.getOrPut(item.exercise.primaryMuscle) { mutableSetOf() }
                    .add(aggregate.session.workoutDate)
            }
        }
        return datesByMuscle.mapNotNull { (muscle, dates) ->
            val ordered = dates.sorted()
            if (ordered.size < 2) {
                return@mapNotNull null
            }
            val gaps = ordered.zipWithNext { first, second ->
                ChronoUnit.DAYS.between(first, second).toInt()
            }
            MuscleRestSummary(
                muscle = muscle,
                sessionDates = ordered.size,
                averageDays = gaps.map { it.toDouble() }.average(),
                shortestDays = gaps.min(),
                longestDays = gaps.max(),
                lastTrained = ordered.last()
            )
        }.sortedWith(
            compareByDescending<MuscleRestSummary> { it.sessionDates }
                .thenBy { it.averageDays }
                .thenBy { it.muscle.name }
        )
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
