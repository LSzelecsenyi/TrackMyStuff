package app.mymusclemap.domain.performance

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionExercise
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSet
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.WorkoutSession
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Random
import kotlin.math.round

/**
 * Reproducible histories for the comparison invariants.
 * The seed is fixed so a failure can be replayed from the history index.
 */
class ExercisePerformanceComparisonStressTest {
    @Test(timeout = 180_000)
    fun tenThousandHistoriesPreserveComparisonInvariants() {
        val random = Random(SEED)
        repeat(HISTORY_COUNT) { index ->
            val generated = generate(random)
            val history = generated.comparable + generated.noise
            val label = "seed $SEED history $index"
            val first = ExercisePerformanceComparisonLogic.compareAll(history, TODAY)
            val second = ExercisePerformanceComparisonLogic.compareAll(history, TODAY)
            assertEquals(label, first, second)
            assertEquals(label, first, ExercisePerformanceComparisonLogic.compareAll(history.shuffled(kotlin.random.Random(index)), TODAY))
            assertEquals(label, first, ExercisePerformanceComparisonLogic.compareAll(generated.comparable, TODAY))
            assertEquals(
                label,
                first,
                ExercisePerformanceComparisonLogic.compareAll(
                    history.map { it.copy(session = it.session.copy(bodyWeightKg = 140.0)) },
                    TODAY
                )
            )
            assertEquals(label, first, ExercisePerformanceComparisonLogic.compareAll(mutateIgnoredSets(history), TODAY))
            first.forEach { result ->
                assertResult(label, result, history)
                val isolated = history.map { aggregate ->
                    aggregate.copy(
                        exercises = aggregate.exercises.filter { it.exercise.exerciseId == result.exerciseId }
                    )
                }
                assertEquals(
                    label,
                    result,
                    ExercisePerformanceComparisonLogic.compareExercise(result.exerciseId, isolated, TODAY)
                )
            }
        }
    }

    private fun assertResult(
        label: String,
        result: ExercisePerformanceComparison,
        history: List<WorkoutSessionAggregate>
    ) {
        assertTrue(label, result.exerciseId == TARGET || result.exerciseId == DISTRACTOR)
        assertTrue(label, !result.currentDate.isAfter(TODAY))
        assertEquals(label, result.matchedConditionCount, result.comparisons.size)
        assertEquals(label, result.includesZeroDelta, result.comparisons.any { it.delta == 0.0 })
        result.comparisons.forEach { match ->
            assertFinite(label, match.delta)
            assertFinite(label, match.current)
            assertFinite(label, match.reference)
            match.matchedEffectiveKilograms?.let { assertFinite(label, it) }
            assertNotEquals(label, PlannedLoadKind.ASSISTANCE, match.loadKind)
            assertNotEquals(label, PlannedLoadKind.NONE, match.loadKind)
            assertNotEquals(label, MeasurementType.DISTANCE_AND_DURATION, match.measurementType)
            assertNotEquals(label, MeasurementType.COMPLETION_ONLY, match.measurementType)
            assertEquals(label, match.current - match.reference, match.delta, 0.0)
        }
        val signs = result.comparisons.map { it.delta }
        when {
            signs.isEmpty() -> {
                assertNotEquals(label, RecordedPerformanceOutcome.STABLE, result.outcome)
                assertNotEquals(label, RecordedPerformanceOutcome.IMPROVED, result.outcome)
                assertNotEquals(label, RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
                assertNotEquals(label, RecordedPerformanceOutcome.MIXED, result.outcome)
            }
            signs.all { it == 0.0 } -> assertEquals(label, RecordedPerformanceOutcome.STABLE, result.outcome)
            signs.any { it > 0.0 } && signs.none { it < 0.0 } ->
                assertEquals(label, RecordedPerformanceOutcome.IMPROVED, result.outcome)
            signs.any { it < 0.0 } && signs.none { it > 0.0 } ->
                assertEquals(label, RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
            signs.any { it > 0.0 } && signs.any { it < 0.0 } ->
                assertEquals(label, RecordedPerformanceOutcome.MIXED, result.outcome)
        }
        val current = history.first { it.session.id == result.currentSessionId }
        assertEquals(label, current.session.workoutDate, result.currentDate)
        assertEquals(label, completionMillis(current.session), result.currentCompletedAt)
        val eligible = history.filter { eligible(it, result.exerciseId) }
        val latest = eligible.maxWith(compareBy<WorkoutSessionAggregate> { completionMillis(it.session)!! }.thenBy { it.session.id })
        assertEquals(label, latest.session.id, result.currentSessionId)
        val referenceId = result.referenceSessionId
        if (referenceId == null) {
            assertTrue(label, result.comparisons.isEmpty())
            return
        }
        val reference = history.first { it.session.id == referenceId }
        assertEquals(label, reference.session.workoutDate, result.referenceDate)
        assertEquals(label, completionMillis(reference.session), result.referenceCompletedAt)
        val days = ChronoUnit.DAYS.between(result.referenceDate, result.currentDate)
        assertTrue(label, days in 0..ExercisePerformanceComparisonLogic.REFERENCE_WINDOW_DAYS)
        val earlier = result.referenceCompletedAt!! < result.currentCompletedAt ||
            (result.referenceCompletedAt == result.currentCompletedAt && referenceId < result.currentSessionId)
        assertTrue(label, earlier)
        result.comparisons.forEach { match ->
            assertBackedBySets(label, current, result.exerciseId, match, match.current)
            assertBackedBySets(label, reference, result.exerciseId, match, match.reference)
        }
    }

    private fun assertBackedBySets(
        label: String,
        aggregate: WorkoutSessionAggregate,
        exerciseId: Long,
        match: MatchedPerformance,
        expected: Double
    ) {
        val rows = aggregate.exercises.filter { it.exercise.exerciseId == exerciseId }
        val supported = rows.flatMap { item ->
            item.sets.filter { set ->
                set.status == SessionSetStatus.COMPLETED &&
                    set.actualLoadKind == match.loadKind &&
                    item.exercise.measurementType == match.measurementType &&
                    item.exercise.resistanceBasis == match.resistanceBasis &&
                    interpreted(item, match.loadKind) == match.weightInterpretation
            }.map { set -> item to set }
        }
        when (match.dimension) {
            ComparisonDimension.REPETITIONS -> {
                val sameLoad = supported.filter { (item, set) -> sameLoad(item, set, match) && (set.actualReps ?: 0) > 0 }
                assertTrue(label, sameLoad.isNotEmpty())
                assertEquals(label, expected, sameLoad.maxOf { it.second.actualReps!! }.toDouble(), 0.0)
            }
            ComparisonDimension.DURATION_SECONDS -> {
                val sameLoad = supported.filter { (item, set) ->
                    sameLoad(item, set, match) && (set.actualDurationSeconds ?: 0) > 0
                }
                assertTrue(label, sameLoad.isNotEmpty())
                assertEquals(label, expected, sameLoad.maxOf { it.second.actualDurationSeconds!! }.toDouble(), 0.0)
            }
            ComparisonDimension.KILOGRAMS -> {
                val sameReps = supported.filter { (_, set) -> set.actualReps == match.matchedRepetitions }
                assertTrue(label, sameReps.isNotEmpty())
                val heaviest = sameReps.mapNotNull { (item, set) ->
                    WorkoutSetVolume.effectiveLoadKg(set, item.exercise.weightInterpretation)
                }.maxOrNull()
                assertEquals(label, expected, heaviest!!, 0.0)
            }
        }
    }

    private fun sameLoad(item: SessionExerciseItem, set: SessionSet, match: MatchedPerformance): Boolean {
        if (match.loadKind == PlannedLoadKind.BODYWEIGHT_ONLY) {
            return match.matchedEffectiveKilograms == null
        }
        val effective = WorkoutSetVolume.effectiveLoadKg(set, item.exercise.weightInterpretation) ?: return false
        val actual = round(effective * 1000.0).toLong()
        val expected = round(match.matchedEffectiveKilograms!! * 1000.0).toLong()
        return actual == expected
    }

    private fun interpreted(item: SessionExerciseItem, kind: PlannedLoadKind): WeightInterpretation {
        return if (kind == PlannedLoadKind.BODYWEIGHT_ONLY) {
            WeightInterpretation.NOT_APPLICABLE
        } else {
            item.exercise.weightInterpretation
        }
    }

    private fun eligible(aggregate: WorkoutSessionAggregate, exerciseId: Long): Boolean {
        val session = aggregate.session
        if (session.status != SessionStatus.COMPLETED || session.workoutDate.isAfter(TODAY)) return false
        if (completionMillis(session) == null) return false
        return aggregate.exercises.any { item ->
            item.exercise.exerciseId == exerciseId && item.sets.any { it.status == SessionSetStatus.COMPLETED }
        }
    }

    private fun completionMillis(session: WorkoutSession): Long? {
        val finished = session.finishedAt
        if (finished != null && finished > 0L) return finished
        return session.startedAt.takeIf { it > 0L }
    }

    private fun assertFinite(label: String, value: Double) {
        assertTrue(label, value.isFinite())
    }

    private fun mutateIgnoredSets(history: List<WorkoutSessionAggregate>): List<WorkoutSessionAggregate> {
        return history.map { aggregate ->
            aggregate.copy(
                exercises = aggregate.exercises.map { item ->
                    item.copy(
                        sets = item.sets.map { set ->
                            if (set.status == SessionSetStatus.COMPLETED) {
                                set
                            } else {
                                set.copy(actualReps = 500, actualWeightKg = 500.0, actualDurationSeconds = 500)
                            }
                        }
                    )
                }
            )
        }
    }

    private data class Generated(
        val comparable: List<WorkoutSessionAggregate>,
        val noise: List<WorkoutSessionAggregate>
    )

    private fun generate(random: Random): Generated {
        val count = 3 + random.nextInt(18)
        val mode = random.nextInt(MODE_COUNT)
        val comparable = ArrayList<WorkoutSessionAggregate>(count)
        var date = TODAY
        var stamp = 1_000_000L
        repeat(count) { ordinal ->
            if (ordinal > 0) {
                val gap = if (random.nextInt(5) == 0) 0 else 1 + random.nextInt(12)
                date = date.minusDays(gap.toLong())
                if (random.nextInt(8) != 0) stamp -= 1_000L
            }
            val perSide = mode == 0 && random.nextInt(4) == 0
            comparable += session(
                id = (ordinal + 1).toLong(),
                date = date,
                finishedAt = stamp,
                status = SessionStatus.COMPLETED,
                bodyWeight = 60.0 + random.nextInt(40),
                exercises = listOf(
                    exercise(
                        exerciseId = TARGET,
                        name = "Target",
                        primary = MuscleGroup.CHEST,
                        ordinal = ordinal,
                        mode = mode,
                        perSide = perSide,
                        random = random
                    ),
                    exercise(
                        exerciseId = DISTRACTOR,
                        name = "Distractor",
                        primary = MuscleGroup.LATS,
                        ordinal = ordinal,
                        mode = 1,
                        perSide = false,
                        random = random
                    )
                )
            )
        }
        val noise = listOf(
            session(
                id = 70_000L,
                date = TODAY.minusDays(1),
                finishedAt = 2_000_000L,
                status = SessionStatus.IN_PROGRESS,
                bodyWeight = 80.0,
                exercises = listOf(extreme(TARGET), extreme(DISTRACTOR))
            ),
            session(
                id = 80_000L,
                date = TODAY.minusDays(1),
                finishedAt = 2_000_000L,
                status = SessionStatus.ABANDONED,
                bodyWeight = 80.0,
                exercises = listOf(extreme(TARGET), extreme(DISTRACTOR))
            ),
            session(
                id = 90_000L,
                date = TODAY.plusDays(3),
                finishedAt = 9_000_000L,
                status = SessionStatus.COMPLETED,
                bodyWeight = 80.0,
                exercises = listOf(extreme(TARGET), extreme(DISTRACTOR))
            )
        )
        return Generated(comparable, noise)
    }

    private fun extreme(exerciseId: Long): SessionExerciseItem {
        return item(
            exerciseId = exerciseId,
            name = if (exerciseId == TARGET) "Target" else "Distractor",
            primary = MuscleGroup.CHEST,
            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistance = ResistanceBasis.EXTERNAL,
            interpretation = WeightInterpretation.TOTAL,
            sets = listOf(performed(999, 999.0, PlannedLoadKind.EXTERNAL_WEIGHT, null, SessionSetStatus.COMPLETED))
        )
    }

    private fun exercise(
        exerciseId: Long,
        name: String,
        primary: MuscleGroup,
        ordinal: Int,
        mode: Int,
        perSide: Boolean,
        random: Random
    ): SessionExerciseItem {
        val profile = profile(mode, perSide)
        val setCount = 1 + random.nextInt(6)
        val sets = buildList {
            repeat(setCount) { setIndex ->
                val missing = mode == 0 && random.nextInt(7) == 0
                val reps = 1 + (ordinal + setIndex) % 8
                val kilograms = (40 + ordinal + setIndex).toDouble()
                add(
                    when (profile.measurement) {
                        MeasurementType.DURATION -> performed(
                            reps = null,
                            weight = null,
                            kind = PlannedLoadKind.BODYWEIGHT_ONLY,
                            duration = 30 + (ordinal * 3 + setIndex) % 40,
                            status = SessionSetStatus.COMPLETED
                        )
                        MeasurementType.DISTANCE_AND_DURATION -> performed(
                            reps = null,
                            weight = null,
                            kind = PlannedLoadKind.NONE,
                            duration = 60,
                            status = SessionSetStatus.COMPLETED,
                            distance = 100.0
                        )
                        else -> performed(
                            reps = reps,
                            weight = if (missing || profile.kind == PlannedLoadKind.BODYWEIGHT_ONLY ||
                                profile.kind == PlannedLoadKind.ASSISTANCE && random.nextBoolean()
                            ) {
                                null
                            } else {
                                kilograms
                            },
                            kind = profile.kind,
                            duration = null,
                            status = SessionSetStatus.COMPLETED
                        )
                    }
                )
            }
            add(performed(999, 999.0, profile.kind, 999, SessionSetStatus.SKIPPED))
            add(performed(998, 998.0, profile.kind, 998, SessionSetStatus.PENDING))
        }
        return item(
            exerciseId = exerciseId,
            name = name,
            primary = primary,
            measurement = profile.measurement,
            resistance = profile.resistance,
            interpretation = profile.interpretation,
            sets = sets
        )
    }

    private data class Profile(
        val measurement: MeasurementType,
        val resistance: ResistanceBasis,
        val interpretation: WeightInterpretation,
        val kind: PlannedLoadKind
    )

    private fun profile(mode: Int, perSide: Boolean): Profile {
        return when (mode) {
            0 -> Profile(
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.EXTERNAL,
                if (perSide) WeightInterpretation.PER_SIDE else WeightInterpretation.TOTAL,
                PlannedLoadKind.EXTERNAL_WEIGHT
            )
            1 -> Profile(
                MeasurementType.REPETITIONS_AND_WEIGHT,
                ResistanceBasis.EXTERNAL,
                WeightInterpretation.TOTAL,
                PlannedLoadKind.EXTERNAL_WEIGHT
            )
            2 -> Profile(
                MeasurementType.REPETITIONS,
                ResistanceBasis.BODYWEIGHT,
                WeightInterpretation.NOT_APPLICABLE,
                PlannedLoadKind.BODYWEIGHT_ONLY
            )
            3 -> Profile(
                MeasurementType.REPETITIONS,
                ResistanceBasis.BODYWEIGHT,
                WeightInterpretation.NOT_APPLICABLE,
                PlannedLoadKind.ADDED_WEIGHT
            )
            4 -> Profile(
                MeasurementType.DURATION,
                ResistanceBasis.BODYWEIGHT,
                WeightInterpretation.NOT_APPLICABLE,
                PlannedLoadKind.BODYWEIGHT_ONLY
            )
            5 -> Profile(
                MeasurementType.REPETITIONS,
                ResistanceBasis.BODYWEIGHT,
                WeightInterpretation.NOT_APPLICABLE,
                PlannedLoadKind.ASSISTANCE
            )
            else -> Profile(
                MeasurementType.DISTANCE_AND_DURATION,
                ResistanceBasis.NONE,
                WeightInterpretation.NOT_APPLICABLE,
                PlannedLoadKind.NONE
            )
        }
    }

    private fun session(
        id: Long,
        date: LocalDate,
        finishedAt: Long,
        status: SessionStatus,
        bodyWeight: Double,
        exercises: List<SessionExerciseItem>
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = WorkoutSession(
                id = id,
                templateId = null,
                templateName = "Generated $id",
                status = status,
                workoutDate = date,
                startedAt = finishedAt - 1,
                finishedAt = finishedAt,
                abandonedAt = if (status == SessionStatus.ABANDONED) finishedAt else null,
                notes = null,
                bodyWeightKg = bodyWeight,
                bodyWeightSource = BodyWeightSource.MANUAL,
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exercises = exercises
        )
    }

    private fun item(
        exerciseId: Long,
        name: String,
        primary: MuscleGroup,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        sets: List<SessionSet>
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = exerciseId,
                sessionId = 0L,
                exerciseId = exerciseId,
                position = 0,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = primary,
                secondaryMuscles = emptyList(),
                notes = null
            ),
            sets = sets
        )
    }

    private fun performed(
        reps: Int?,
        weight: Double?,
        kind: PlannedLoadKind,
        duration: Int?,
        status: SessionSetStatus,
        distance: Double? = null
    ): SessionSet {
        return SessionSet(
            id = 1L,
            sessionExerciseId = 0L,
            position = 0,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = kind,
            plannedWeightKg = weight,
            plannedDurationSeconds = duration,
            plannedDistanceMeters = distance,
            actualReps = reps,
            actualLoadKind = kind,
            actualWeightKg = weight,
            actualDurationSeconds = duration,
            actualDistanceMeters = distance,
            status = status,
            completedAt = if (status == SessionSetStatus.COMPLETED) 1L else null,
            addedDuringWorkout = false
        )
    }

    private companion object {
        const val SEED = 20261016L
        const val HISTORY_COUNT = 10_000
        const val MODE_COUNT = 7
        const val TARGET = 1L
        const val DISTRACTOR = 2L
        val TODAY: LocalDate = LocalDate.of(2026, 10, 16)
    }
}
