package app.mymusclemap.domain.performance

import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.muscleinsights.MuscleInsightsPeriod
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Deterministic cases for recorded exercise-performance comparison.
 * Matrix numbers in each method name follow the Phase 1 specification.
 */
class ExercisePerformanceComparisonTest {
    @Test
    fun sameLoadRepetitionIncrease() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        val match = result.only()
        assertEquals(ComparisonDimension.REPETITIONS, match.dimension)
        assertEquals(2.0, match.delta, 0.0)
        assertEquals(80.0, match.matchedEffectiveKilograms!!, 0.0)
        assertEquals(TODAY, result.currentDate)
        assertEquals(TODAY.minusDays(12), result.referenceDate)
        val otherExercise = weighted(
            id = 9,
            daysAgo = 0,
            weight = 200.0,
            reps = 1,
            exerciseId = 99L,
            name = "Squat",
            primary = MuscleGroup.QUADRICEPS
        )
        assertEquals(
            result,
            compared(
                weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10),
                otherExercise,
                weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
            )
        )
    }

    @Test
    fun sameLoadRepetitionDecrease() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 6),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertEquals(-2.0, result.only().delta, 0.0)
        assertEquals(ComparisonDimension.REPETITIONS, result.only().dimension)
    }

    @Test
    fun sameLoadEqualRepetitions() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.STABLE, result.outcome)
        assertEquals(0.0, result.only().delta, 0.0)
        assertTrue(result.includesZeroDelta)
        assertEquals(ComparisonLimitation.NONE, result.limitation)
    }

    @Test
    fun sameRepetitionLoadIncrease() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(70.0, 8), lift(85.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(60.0, 8), lift(80.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        val match = result.only()
        assertEquals(ComparisonDimension.KILOGRAMS, match.dimension)
        assertEquals(5.0, match.delta, 0.0)
        assertEquals(85.0, match.current, 0.0)
        assertEquals(80.0, match.reference, 0.0)
        assertEquals(8, match.matchedRepetitions)
        assertNull(match.matchedEffectiveKilograms)
    }

    @Test
    fun sameRepetitionLoadDecrease() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 75.0, reps = 8),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        val match = result.only()
        assertEquals(ComparisonDimension.KILOGRAMS, match.dimension)
        assertEquals(-5.0, match.delta, 0.0)
        assertEquals(8, match.matchedRepetitions)
    }

    @Test
    fun bothLoadAndRepetitionsChangeWithoutAValidMatch() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 85.0, reps = 10),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertEquals(ComparisonLimitation.NO_MATCHING_CONDITION, result.limitation)
        assertTrue(result.comparisons.isEmpty())
        assertNull(result.referenceDate)
        assertFalse(result.includesZeroDelta)
    }

    @Test
    fun multipleSetsAtTheSameLoadUseTheHighestRepetitionCount() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(80.0, 6), lift(80.0, 10), lift(80.0, 7))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(80.0, 8), lift(80.0, 5))
            )
        )
        val match = result.only()
        assertEquals(2.0, match.delta, 0.0)
        assertEquals(10.0, match.current, 0.0)
        assertEquals(8.0, match.reference, 0.0)
    }

    @Test
    fun multipleLoadsInOneOccurrenceStaySeparateComparisons() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(60.0, 12), lift(80.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(60.0, 10), lift(80.0, 6))
            )
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        assertEquals(2, result.matchedConditionCount)
        assertEquals(listOf(60.0, 80.0), result.comparisons.map { it.matchedEffectiveKilograms })
        assertEquals(listOf(2.0, 2.0), result.comparisons.map { it.delta })
    }

    @Test
    fun mixedPositiveAndNegativeComparisons() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(60.0, 12), lift(80.0, 5))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(60.0, 10), lift(80.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.MIXED, result.outcome)
        assertEquals(listOf(2.0, -3.0), result.comparisons.map { it.delta })
        assertFalse(result.includesZeroDelta)
    }

    @Test
    fun positiveAndZeroComparisonsStayImproved() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(60.0, 12), lift(80.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(60.0, 10), lift(80.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        assertTrue(result.includesZeroDelta)
        assertTrue(result.comparisons.any { it.positive })
        assertTrue(result.comparisons.any { it.zero })
        assertTrue(result.comparisons.none { it.negative })
    }

    @Test
    fun negativeAndZeroComparisonsStayLowerRecordedPerformance() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(60.0, 8), lift(80.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 12,
                sets = listOf(lift(60.0, 10), lift(80.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertTrue(result.includesZeroDelta)
        assertEquals(listOf(-2.0, 0.0), result.comparisons.map { it.delta })
    }

    @Test
    fun duplicateMatchedEvidenceCollapsesTheSameUnchangedPoint() {
        val unchanged = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(1, unchanged.comparisons.size)
        assertEquals(ComparisonDimension.REPETITIONS, unchanged.only().dimension)
        assertTrue(unchanged.comparisons.none { it.dimension == ComparisonDimension.KILOGRAMS })

        val differentFacts = compared(
            weighted(
                id = 4,
                daysAgo = 0,
                sets = listOf(lift(80.0, 8), lift(85.0, 8))
            ),
            weighted(id = 3, daysAgo = 12, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, differentFacts.outcome)
        assertTrue(differentFacts.includesZeroDelta)
        assertEquals(2, differentFacts.matchedConditionCount)
        assertEquals(0.0, differentFacts.comparisons[0].delta, 0.0)
        assertEquals(ComparisonDimension.REPETITIONS, differentFacts.comparisons[0].dimension)
        assertEquals(5.0, differentFacts.comparisons[1].delta, 0.0)
        assertEquals(ComparisonDimension.KILOGRAMS, differentFacts.comparisons[1].dimension)
    }

    @Test
    fun nearestComparableOccurrenceWins() {
        val result = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(id = 2, daysAgo = 1, weight = 80.0, reps = 6),
            weighted(id = 1, daysAgo = 40, weight = 80.0, reps = 12)
        )
        assertEquals(2L, result.referenceSessionId)
        assertEquals(2.0, result.only().delta, 0.0)
        assertEquals(TODAY.minusDays(1), result.referenceDate)
    }

    @Test
    fun olderFavorableOccurrenceCannotOverrideANewerNegativeComparison() {
        val result = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 5),
            weighted(id = 2, daysAgo = 1, weight = 80.0, reps = 9),
            weighted(id = 1, daysAgo = 40, weight = 80.0, reps = 1)
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertEquals(2L, result.referenceSessionId)
        assertEquals(-4.0, result.only().delta, 0.0)
    }

    @Test
    fun newerNoncomparableOccurrenceCanBeSkipped() {
        val result = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 8),
            bodyweight(id = 2, daysAgo = 1, reps = 30),
            weighted(id = 1, daysAgo = 15, weight = 80.0, reps = 5)
        )
        assertEquals(1L, result.referenceSessionId)
        assertEquals(3.0, result.only().delta, 0.0)
        assertEquals(ComparisonDimension.REPETITIONS, result.only().dimension)
    }

    @Test
    fun referenceExactlyNinetyDaysOldIsEligible() {
        assertEquals(90L, ExercisePerformanceComparisonLogic.REFERENCE_WINDOW_DAYS)
        val boundary = TODAY.minusDays(90)
        val older = TODAY.minusDays(91)
        val result = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 10),
            weighted(id = 2, daysAgo = 90, weight = 80.0, reps = 8),
            weighted(id = 1, daysAgo = 91, weight = 80.0, reps = 1)
        )
        assertEquals(boundary, result.referenceDate)
        assertEquals(2L, result.referenceSessionId)
        assertEquals(2.0, result.only().delta, 0.0)
        assertEquals(90L, ChronoUnit.DAYS.between(boundary, TODAY))
        assertTrue(older.isBefore(boundary))
    }

    @Test
    fun referenceNinetyOneDaysOldIsOutsideTheWindow() {
        val excluded = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 5),
            weighted(id = 1, daysAgo = 91, weight = 80.0, reps = 20)
        )
        assertEquals(RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, excluded.outcome)
        assertEquals(ComparisonLimitation.NO_EARLIER_OCCURRENCE_IN_WINDOW, excluded.limitation)
        assertNull(excluded.referenceSessionId)

        val nearerMismatch = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 5),
            bodyweight(id = 2, daysAgo = 10, reps = 12),
            weighted(id = 1, daysAgo = 91, weight = 80.0, reps = 20)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, nearerMismatch.outcome)
        assertNull(nearerMismatch.referenceDate)
    }

    @Test
    fun equalTimestampsUseTheHigherSessionIdAsNewer() {
        val result = compared(
            weighted(id = 5, daysAgo = 0, weight = 80.0, reps = 6, finishedAt = 4_000L),
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10, finishedAt = 4_000L)
        )
        assertEquals(5L, result.currentSessionId)
        assertEquals(2L, result.referenceSessionId)
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertEquals(-4.0, result.only().delta, 0.0)
    }

    @Test
    fun finishedAtFallsBackToStartedAt() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10, finishedAt = 200L, startedAt = 50L),
            weighted(id = 1, daysAgo = 4, weight = 80.0, reps = 8, finishedAt = null, startedAt = 100L)
        )
        assertEquals(2L, result.currentSessionId)
        assertEquals(1L, result.referenceSessionId)
        assertEquals(100L, result.referenceCompletedAt)
        assertEquals(2.0, result.only().delta, 0.0)

        assertNull(
            ExercisePerformanceComparisonLogic.compareExercise(
                1L,
                listOf(weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 8, finishedAt = null, startedAt = 0L)),
                TODAY
            )
        )
    }

    @Test
    fun onlyOneEligibleOccurrenceIsInsufficientHistory() {
        val result = compared(weighted(id = 1, daysAgo = 0, weight = 80.0, reps = 8))
        assertEquals(RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, result.outcome)
        assertEquals(ComparisonLimitation.ONLY_ONE_OCCURRENCE, result.limitation)
        assertNull(result.referenceDate)
        assertEquals(0, result.matchedConditionCount)
        assertNull(
            ExercisePerformanceComparisonLogic.compareExercise(
                42L,
                listOf(weighted(id = 8, daysAgo = 0, weight = 80.0, reps = 8)),
                TODAY
            )
        )
    }

    @Test
    fun multipleOccurrencesOnTheSameCalendarDateFollowCompletionTime() {
        val result = compared(
            weighted(id = 1, daysAgo = 0, weight = 80.0, reps = 10, finishedAt = 200L),
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 6, finishedAt = 100L)
        )
        assertEquals(TODAY, result.currentDate)
        assertEquals(TODAY, result.referenceDate)
        assertEquals(1L, result.currentSessionId)
        assertEquals(2L, result.referenceSessionId)
        assertEquals(4.0, result.only().delta, 0.0)
    }

    @Test
    fun sparseHistoryWithNoMatchingConditions() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(id = 1, daysAgo = 20, weight = 100.0, reps = 4)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertEquals(ComparisonLimitation.NO_MATCHING_CONDITION, result.limitation)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun bodyweightOnlyRepetitionIncreaseDoesNotInferBodyMass() {
        val light = compared(
            bodyweight(id = 2, daysAgo = 0, reps = 12, bodyWeightKg = 55.0),
            bodyweight(id = 1, daysAgo = 10, reps = 9, bodyWeightKg = 95.0)
        )
        val heavy = compared(
            bodyweight(id = 2, daysAgo = 0, reps = 12, bodyWeightKg = 95.0),
            bodyweight(id = 1, daysAgo = 10, reps = 9, bodyWeightKg = 55.0)
        )
        assertEquals(light, heavy)
        val match = light.only()
        assertEquals(ComparisonDimension.REPETITIONS, match.dimension)
        assertEquals(3.0, match.delta, 0.0)
        assertNull(match.matchedEffectiveKilograms)
        assertEquals(PlannedLoadKind.BODYWEIGHT_ONLY, match.loadKind)
        assertEquals(WeightInterpretation.NOT_APPLICABLE, match.weightInterpretation)
    }

    @Test
    fun bodyweightOnlyDoesNotCombineWithAddedWeight() {
        val result = compared(
            bodyweight(id = 2, daysAgo = 0, reps = 12),
            added(id = 1, daysAgo = 10, weight = 10.0, reps = 12)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun addedWeightRepetitionsAtTheSameKilograms() {
        val result = compared(
            added(id = 2, daysAgo = 0, weight = 15.0, reps = 10),
            added(id = 1, daysAgo = 10, weight = 15.0, reps = 8)
        )
        val match = result.only()
        assertEquals(ComparisonDimension.REPETITIONS, match.dimension)
        assertEquals(2.0, match.delta, 0.0)
        assertEquals(15.0, match.matchedEffectiveKilograms!!, 0.0)
        assertEquals(PlannedLoadKind.ADDED_WEIGHT, match.loadKind)
    }

    @Test
    fun addedWeightKilogramsAtTheSameRepetitionCount() {
        val result = compared(
            added(id = 2, daysAgo = 0, weight = 15.0, reps = 8),
            added(id = 1, daysAgo = 10, weight = 10.0, reps = 8)
        )
        val match = result.only()
        assertEquals(ComparisonDimension.KILOGRAMS, match.dimension)
        assertEquals(5.0, match.delta, 0.0)
        assertEquals(8, match.matchedRepetitions)
        assertNull(match.matchedEffectiveKilograms)
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
    }

    @Test
    fun assistanceIsExcludedWithoutFallingBackToAnOlderExternalSession() {
        val result = compared(
            bodyweight(id = 2, daysAgo = 0, reps = 8, kind = PlannedLoadKind.ASSISTANCE, weight = 20.0),
            weighted(id = 1, daysAgo = 10, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, result.outcome)
        assertEquals(ComparisonLimitation.ASSISTANCE_EXCLUDED, result.limitation)
        assertNull(result.referenceSessionId)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun externalResistanceDoesNotMatchBodyweightResistance() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 8),
            bodyweight(id = 1, daysAgo = 10, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun totalAndPerSideStayDistinct() {
        val crossed = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                weight = 20.0,
                reps = 8,
                interpretation = WeightInterpretation.PER_SIDE
            ),
            weighted(id = 1, daysAgo = 10, weight = 40.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, crossed.outcome)

        val perSide = compared(
            weighted(
                id = 4,
                daysAgo = 0,
                weight = 25.0,
                reps = 8,
                interpretation = WeightInterpretation.PER_SIDE
            ),
            weighted(
                id = 3,
                daysAgo = 10,
                weight = 20.0,
                reps = 8,
                interpretation = WeightInterpretation.PER_SIDE
            )
        )
        val match = perSide.only()
        assertEquals(ComparisonDimension.KILOGRAMS, match.dimension)
        assertEquals(10.0, match.delta, 0.0)
        assertEquals(50.0, match.current, 0.0)
        assertEquals(40.0, match.reference, 0.0)
        assertEquals(WeightInterpretation.PER_SIDE, match.weightInterpretation)
    }

    @Test
    fun missingLoadIsInvalidAndDoesNotUseAnOlderValidSession() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = null, reps = 8),
            weighted(id = 1, daysAgo = 10, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, result.outcome)
        assertEquals(ComparisonLimitation.INVALID_OR_MISSING_LOAD, result.limitation)
        assertNull(result.referenceDate)
    }

    @Test
    fun zeroOrNegativeLoadIsInvalid() {
        val invalid = compared(
            weighted(
                id = 1,
                daysAgo = 0,
                sets = listOf(lift(0.0, 10), lift(-5.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, invalid.outcome)
        assertEquals(ComparisonLimitation.INVALID_OR_MISSING_LOAD, invalid.limitation)

        val ignored = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(0.0, 10), lift(80.0, 8))
            ),
            weighted(id = 1, daysAgo = 10, weight = 80.0, reps = 6)
        )
        assertEquals(2.0, ignored.only().delta, 0.0)
        assertEquals(80.0, ignored.only().matchedEffectiveKilograms!!, 0.0)
    }

    @Test
    fun decimalKilogramsMatchAtTheMilligram() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 0.1 + 0.2, reps = 8),
            weighted(id = 1, daysAgo = 10, weight = 0.3, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.STABLE, result.outcome)
        assertEquals(0.0, result.only().delta, 0.0)
        assertEquals(0.3, result.only().matchedEffectiveKilograms!!, 0.0)
    }

    @Test
    fun differentMeasurementSnapshotsDoNotMatch() {
        val result = compared(
            hold(id = 2, daysAgo = 0, seconds = 60),
            bodyweight(id = 1, daysAgo = 10, reps = 10)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertEquals(ComparisonLimitation.NO_MATCHING_CONDITION, result.limitation)
        assertEquals(1L, result.exerciseId)
    }

    @Test
    fun longerHoldAtTheSameCondition() {
        val result = compared(
            hold(id = 2, daysAgo = 0, seconds = 60),
            hold(id = 1, daysAgo = 8, seconds = 45)
        )
        val match = result.only()
        assertEquals(ComparisonDimension.DURATION_SECONDS, match.dimension)
        assertEquals(15.0, match.delta, 0.0)
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        assertTrue(result.comparisons.none { it.dimension == ComparisonDimension.KILOGRAMS })
    }

    @Test
    fun shorterHold() {
        val result = compared(
            hold(id = 2, daysAgo = 0, seconds = 40),
            hold(id = 1, daysAgo = 8, seconds = 45)
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertEquals(-5.0, result.only().delta, 0.0)
        assertEquals(ComparisonDimension.DURATION_SECONDS, result.only().dimension)
    }

    @Test
    fun weightedDurationDoesNotMatchUnweightedDuration() {
        val result = compared(
            hold(id = 2, daysAgo = 0, seconds = 60),
            hold(
                id = 1,
                daysAgo = 8,
                seconds = 60,
                kind = PlannedLoadKind.ADDED_WEIGHT,
                weight = 10.0
            )
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun distanceAndDurationIsExcluded() {
        val result = compared(
            unsupported(
                id = 2,
                daysAgo = 0,
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                resistance = ResistanceBasis.NONE,
                kind = PlannedLoadKind.NONE,
                duration = 600,
                distance = 1_000.0
            ),
            hold(id = 1, daysAgo = 5, seconds = 60)
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, result.outcome)
        assertEquals(ComparisonLimitation.UNSUPPORTED_TYPE, result.limitation)
        assertNull(result.referenceSessionId)
    }

    @Test
    fun completionOnlyIsExcluded() {
        val result = compared(
            unsupported(
                id = 2,
                daysAgo = 0,
                measurement = MeasurementType.COMPLETION_ONLY,
                resistance = ResistanceBasis.NONE,
                kind = PlannedLoadKind.NONE
            ),
            weighted(id = 1, daysAgo = 5, weight = 80.0, reps = 5)
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, result.outcome)
        assertEquals(ComparisonLimitation.UNSUPPORTED_TYPE, result.limitation)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun skippedSetsAreExcluded() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(80.0, 100, SessionSetStatus.SKIPPED), lift(80.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 6,
                sets = listOf(lift(80.0, 100, SessionSetStatus.SKIPPED), lift(80.0, 5))
            )
        )
        assertEquals(3.0, result.only().delta, 0.0)
        assertEquals(8.0, result.only().current, 0.0)
    }

    @Test
    fun pendingSetsAreExcluded() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(80.0, 100, SessionSetStatus.PENDING), lift(80.0, 8))
            ),
            weighted(
                id = 1,
                daysAgo = 6,
                sets = listOf(lift(80.0, 100, SessionSetStatus.PENDING), lift(80.0, 5))
            )
        )
        assertEquals(3.0, result.only().delta, 0.0)
        assertEquals(5.0, result.only().reference, 0.0)
    }

    @Test
    fun abandonedWorkoutsAreExcluded() {
        val result = compared(
            weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(
                id = 2,
                daysAgo = 1,
                weight = 80.0,
                reps = 20,
                sessionStatus = SessionStatus.ABANDONED
            ),
            weighted(id = 1, daysAgo = 10, weight = 80.0, reps = 6)
        )
        assertEquals(1L, result.referenceSessionId)
        assertEquals(2.0, result.only().delta, 0.0)
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
    }

    @Test
    fun futureSessionsAreExcluded() {
        val future = weighted(id = 9, daysAgo = -1, weight = 80.0, reps = 30, finishedAt = 500_000L)
        val result = compared(
            future,
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 8),
            weighted(id = 1, daysAgo = 10, weight = 80.0, reps = 6)
        )
        assertEquals(2L, result.currentSessionId)
        assertEquals(TODAY, result.currentDate)
        assertEquals(1L, result.referenceSessionId)
        assertEquals(2.0, result.only().delta, 0.0)
    }

    @Test
    fun renamedExerciseRetainsExerciseId() {
        val merged = WorkoutSessionAggregate(
            session = sessionShell(id = 2, daysAgo = 0),
            exercises = listOf(
                exercise(
                    exerciseId = 1L,
                    sessionId = 2L,
                    name = "Old slot",
                    position = 0,
                    sets = listOf(lift(80.0, 6))
                ),
                exercise(
                    exerciseId = 1L,
                    sessionId = 2L,
                    name = "Barbell bench press",
                    position = 1,
                    sets = listOf(lift(80.0, 10))
                )
            )
        )
        val previous = weighted(id = 1, daysAgo = 12, weight = 80.0, reps = 8, name = "Bench press")
        val result = compared(merged, previous)
        assertEquals("Barbell bench press", result.displayName)
        assertEquals(1L, result.exerciseId)
        assertEquals(2.0, result.only().delta, 0.0)
        val all = ExercisePerformanceComparisonLogic.compareAll(listOf(merged, previous), TODAY)
        assertEquals(listOf(1L), all.map { it.exerciseId })
    }

    @Test
    fun changedPrimaryMusclePreservesTheHistoricalSnapshot() {
        val olderChest = weighted(
            id = 1,
            daysAgo = 20,
            weight = 80.0,
            reps = 8,
            name = "Flat bench",
            primary = MuscleGroup.CHEST
        )
        val laterTriceps = weighted(
            id = 2,
            daysAgo = 1,
            weight = 80.0,
            reps = 3,
            name = "Close grip",
            primary = MuscleGroup.TRICEPS
        )
        val currentChest = weighted(
            id = 3,
            daysAgo = 0,
            weight = 80.0,
            reps = 10,
            name = "Flat bench",
            primary = MuscleGroup.CHEST
        )
        val history = listOf(laterTriceps, olderChest, currentChest)
        val chest = ExercisePerformanceComparisonLogic.summarizeMuscle(
            history,
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        val chestExercise = chest.exercises.single()
        assertEquals(3L, chestExercise.currentSessionId)
        assertEquals(1L, chestExercise.referenceSessionId)
        assertEquals(2.0, chestExercise.only().delta, 0.0)
        assertEquals("Flat bench", chestExercise.displayName)

        val triceps = ExercisePerformanceComparisonLogic.summarizeMuscle(
            history,
            MuscleGroup.TRICEPS,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        val tricepsExercise = triceps.exercises.single()
        assertEquals(RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, tricepsExercise.outcome)
        assertEquals("Close grip", tricepsExercise.displayName)
        assertEquals(2L, tricepsExercise.currentSessionId)
    }

    @Test
    fun secondaryOnlyExerciseIsExcludedFromTheSelectedPrimaryMuscle() {
        val sessions = listOf(
            weighted(
                id = 2,
                daysAgo = 0,
                weight = 50.0,
                reps = 12,
                exerciseId = 7L,
                name = "Face pull",
                primary = MuscleGroup.REAR_DELTOID,
                secondary = listOf(MuscleGroup.CHEST)
            ),
            weighted(
                id = 1,
                daysAgo = 8,
                weight = 50.0,
                reps = 8,
                exerciseId = 7L,
                name = "Face pull",
                primary = MuscleGroup.REAR_DELTOID,
                secondary = listOf(MuscleGroup.CHEST)
            )
        )
        val chest = ExercisePerformanceComparisonLogic.summarizeMuscle(
            sessions,
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        assertTrue(chest.exercises.isEmpty())
        val rear = ExercisePerformanceComparisonLogic.summarizeMuscle(
            sessions,
            MuscleGroup.REAR_DELTOID,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, rear.exercises.single().outcome)
    }

    @Test
    fun historyOutsideTheReportingPeriodCanStillBeTheReference() {
        val current = weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10)
        val reference = weighted(id = 1, daysAgo = 60, weight = 80.0, reps = 8)
        val summary = ExercisePerformanceComparisonLogic.summarizeMuscle(
            listOf(current, reference),
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        val exercise = summary.exercises.single()
        assertEquals(TODAY.minusDays(60), exercise.referenceDate)
        assertEquals(2.0, exercise.only().delta, 0.0)
        assertTrue(exercise.referenceDate!!.isBefore(MuscleInsightsPeriod.Weeks4.startInclusive(TODAY)))
    }

    @Test
    fun exerciseAbsentFromTheSelectedReportingPeriodIsNotListed() {
        val old = weighted(id = 1, daysAgo = 76, weight = 80.0, reps = 8, exerciseId = 4L, name = "Old press")
        val recent = weighted(id = 2, daysAgo = 0, weight = 40.0, reps = 10, exerciseId = 5L, name = "Recent press")
        val summary = ExercisePerformanceComparisonLogic.summarizeMuscle(
            listOf(old, recent),
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        assertEquals(listOf(5L), summary.exercises.map { it.exerciseId })
        assertEquals(
            RecordedPerformanceOutcome.INSUFFICIENT_HISTORY,
            ExercisePerformanceComparisonLogic.compareExercise(4L, listOf(old), TODAY)!!.outcome
        )
    }

    @Test
    fun deletedCompletedWorkoutNoLongerAffectsTheDerivedResult() {
        val current = weighted(id = 3, daysAgo = 0, weight = 80.0, reps = 5)
        val nearer = weighted(id = 2, daysAgo = 2, weight = 80.0, reps = 8)
        val older = weighted(id = 1, daysAgo = 20, weight = 80.0, reps = 2)
        val withNearer = compared(current, nearer, older)
        val deleted = compared(current, older)
        assertEquals(2L, withNearer.referenceSessionId)
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, withNearer.outcome)
        assertEquals(1L, deleted.referenceSessionId)
        assertEquals(RecordedPerformanceOutcome.IMPROVED, deleted.outcome)
        assertEquals(3.0, deleted.only().delta, 0.0)
    }

    @Test
    fun importedCompletedWorkoutIsEligibleWhenStructurallyValid() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 80.0, reps = 10, importFingerprint = "import-current"),
            weighted(id = 1, daysAgo = 9, weight = 80.0, reps = 8, importFingerprint = "import-reference")
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        assertEquals(1L, result.referenceSessionId)
        assertEquals(2.0, result.only().delta, 0.0)
    }

    @Test
    fun improvedClassification() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 100.0, reps = 3),
            weighted(id = 1, daysAgo = 7, weight = 100.0, reps = 1)
        )
        assertEquals(RecordedPerformanceOutcome.IMPROVED, result.outcome)
        assertEquals(ComparisonLimitation.NONE, result.limitation)
        assertFalse(result.includesZeroDelta)
        assertEquals(1, result.matchedConditionCount)
    }

    @Test
    fun lowerRecordedPerformanceClassification() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 100.0, reps = 1),
            weighted(id = 1, daysAgo = 7, weight = 100.0, reps = 4)
        )
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, result.outcome)
        assertEquals(-3.0, result.only().delta, 0.0)
        assertFalse(result.includesZeroDelta)
    }

    @Test
    fun stableClassification() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 40.0, reps = 8),
            weighted(id = 1, daysAgo = 7, weight = 40.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.STABLE, result.outcome)
        assertTrue(result.includesZeroDelta)
        assertTrue(result.comparisons.all { it.zero })
    }

    @Test
    fun mixedClassification() {
        val result = compared(
            weighted(
                id = 2,
                daysAgo = 0,
                sets = listOf(lift(50.0, 12), lift(70.0, 4))
            ),
            weighted(
                id = 1,
                daysAgo = 7,
                sets = listOf(lift(50.0, 10), lift(70.0, 8))
            )
        )
        assertEquals(RecordedPerformanceOutcome.MIXED, result.outcome)
        assertTrue(result.comparisons.any { it.positive })
        assertTrue(result.comparisons.any { it.negative })
    }

    @Test
    fun notDirectlyComparable() {
        val result = compared(
            weighted(id = 2, daysAgo = 0, weight = 90.0, reps = 5),
            weighted(id = 1, daysAgo = 7, weight = 40.0, reps = 12)
        )
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, result.outcome)
        assertEquals(ComparisonLimitation.NO_MATCHING_CONDITION, result.limitation)
        assertEquals(0, result.matchedConditionCount)
    }

    @Test
    fun insufficientHistory() {
        val result = compared(weighted(id = 1, daysAgo = 0, weight = 30.0, reps = 10))
        assertEquals(RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, result.outcome)
        assertEquals(ComparisonLimitation.ONLY_ONE_OCCURRENCE, result.limitation)
        assertFalse(result.outcome == RecordedPerformanceOutcome.STABLE)
    }

    @Test
    fun unsupportedMeasurement() {
        val result = compared(
            unsupported(
                id = 2,
                daysAgo = 0,
                measurement = MeasurementType.COMPLETION_ONLY,
                resistance = ResistanceBasis.NONE,
                kind = PlannedLoadKind.NONE
            ),
            weighted(id = 1, daysAgo = 4, weight = 80.0, reps = 8)
        )
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, result.outcome)
        assertEquals(ComparisonLimitation.UNSUPPORTED_TYPE, result.limitation)
        assertTrue(result.comparisons.isEmpty())
    }

    @Test
    fun muscleSummaryCountsEveryCategory() {
        val summary = ExercisePerformanceComparisonLogic.summarizeMuscle(
            categoryHistory(),
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        assertEquals(7, summary.exercises.size)
        assertEquals(1, summary.improved)
        assertEquals(1, summary.lowerRecordedPerformance)
        assertEquals(1, summary.stable)
        assertEquals(1, summary.mixed)
        assertEquals(3, summary.insufficientOrNotComparable)
        assertEquals(
            summary.exercises.size,
            summary.improved + summary.lowerRecordedPerformance + summary.stable +
                summary.mixed + summary.insufficientOrNotComparable
        )
        assertFalse(summary.exercises.any { it.exerciseId == 8L || it.exerciseId == 9L })
        val outcomes = summary.exercises.associate { it.exerciseId to it.outcome }
        assertEquals(RecordedPerformanceOutcome.IMPROVED, outcomes[1L])
        assertEquals(RecordedPerformanceOutcome.LOWER_RECORDED_PERFORMANCE, outcomes[2L])
        assertEquals(RecordedPerformanceOutcome.STABLE, outcomes[3L])
        assertEquals(RecordedPerformanceOutcome.MIXED, outcomes[4L])
        assertEquals(RecordedPerformanceOutcome.INSUFFICIENT_HISTORY, outcomes[5L])
        assertEquals(RecordedPerformanceOutcome.UNSUPPORTED, outcomes[6L])
        assertEquals(RecordedPerformanceOutcome.NOT_DIRECTLY_COMPARABLE, outcomes[7L])
    }

    @Test
    fun muscleSummaryHasNoStrengthPercentage() {
        val summary = ExercisePerformanceComparisonLogic.summarizeMuscle(
            categoryHistory(),
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        val numericFields = MuscleExercisePerformance::class.java.declaredFields.filter { field ->
            field.type == Double::class.javaPrimitiveType ||
                field.type == Float::class.javaPrimitiveType ||
                field.type == Double::class.javaObjectType ||
                field.type == Float::class.javaObjectType
        }
        assertTrue(numericFields.isEmpty())
        assertEquals(
            summary.exercises.size,
            summary.improved + summary.lowerRecordedPerformance + summary.stable +
                summary.mixed + summary.insufficientOrNotComparable
        )
        assertTrue(summary.exercises.none { it.outcome == RecordedPerformanceOutcome.STABLE && it.comparisons.isEmpty() })
    }

    @Test
    fun outputOrderingIsStable() {
        val squat = weighted(id = 1, daysAgo = 0, weight = 100.0, reps = 5, exerciseId = 30L, name = "Squat")
        val lowerId = weighted(id = 2, daysAgo = 0, weight = 40.0, reps = 8, exerciseId = 10L, name = "bench press")
        val higherId = weighted(id = 3, daysAgo = 0, weight = 40.0, reps = 8, exerciseId = 20L, name = "Bench press")
        val forward = ExercisePerformanceComparisonLogic.compareAll(listOf(squat, higherId, lowerId), TODAY)
        val reversed = ExercisePerformanceComparisonLogic.compareAll(listOf(higherId, squat, lowerId), TODAY)
        assertEquals(forward, reversed)
        assertEquals(listOf(10L, 20L, 30L), forward.map { it.exerciseId })
        val summary = ExercisePerformanceComparisonLogic.summarizeMuscle(
            listOf(higherId, squat, lowerId),
            MuscleGroup.CHEST,
            MuscleInsightsPeriod.Weeks4,
            TODAY
        )
        assertEquals(listOf(10L, 20L, 30L), summary.exercises.map { it.exerciseId })
    }

    private fun categoryHistory(): List<WorkoutSessionAggregate> {
        return listOf(
            weighted(id = 11, daysAgo = 0, weight = 80.0, reps = 10, exerciseId = 1L, name = "Improved"),
            weighted(id = 12, daysAgo = 10, weight = 80.0, reps = 8, exerciseId = 1L, name = "Improved"),
            weighted(id = 21, daysAgo = 0, weight = 60.0, reps = 5, exerciseId = 2L, name = "Lower"),
            weighted(id = 22, daysAgo = 10, weight = 60.0, reps = 8, exerciseId = 2L, name = "Lower"),
            weighted(id = 31, daysAgo = 0, weight = 40.0, reps = 8, exerciseId = 3L, name = "Stable"),
            weighted(id = 32, daysAgo = 10, weight = 40.0, reps = 8, exerciseId = 3L, name = "Stable"),
            weighted(
                id = 41,
                daysAgo = 0,
                exerciseId = 4L,
                name = "Mixed",
                sets = listOf(lift(50.0, 12), lift(70.0, 4))
            ),
            weighted(
                id = 42,
                daysAgo = 10,
                exerciseId = 4L,
                name = "Mixed",
                sets = listOf(lift(50.0, 10), lift(70.0, 8))
            ),
            weighted(id = 51, daysAgo = 0, weight = 30.0, reps = 10, exerciseId = 5L, name = "Once"),
            unsupported(
                id = 61,
                daysAgo = 0,
                exerciseId = 6L,
                name = "Carry",
                measurement = MeasurementType.DISTANCE_AND_DURATION,
                resistance = ResistanceBasis.NONE,
                kind = PlannedLoadKind.NONE,
                duration = 60,
                distance = 100.0
            ),
            weighted(id = 71, daysAgo = 0, weight = 90.0, reps = 5, exerciseId = 7L, name = "Unmatched"),
            weighted(id = 72, daysAgo = 10, weight = 40.0, reps = 12, exerciseId = 7L, name = "Unmatched"),
            weighted(
                id = 81,
                daysAgo = 0,
                weight = 20.0,
                reps = 10,
                exerciseId = 8L,
                name = "Secondary",
                primary = MuscleGroup.UPPER_BACK,
                secondary = listOf(MuscleGroup.CHEST)
            ),
            weighted(id = 91, daysAgo = 76, weight = 80.0, reps = 5, exerciseId = 9L, name = "Outside period")
        )
    }

    private fun compared(vararg sessions: WorkoutSessionAggregate): ExercisePerformanceComparison {
        return ExercisePerformanceComparisonLogic.compareExercise(1L, sessions.toList(), TODAY)!!
    }

    private fun weighted(
        id: Int,
        daysAgo: Int,
        weight: Double? = 80.0,
        reps: Int? = 8,
        sets: List<SessionSet>? = null,
        exerciseId: Long = 1L,
        name: String = "Bench press",
        primary: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList(),
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        finishedAt: Long? = 100_000L - daysAgo.toLong() * 100,
        startedAt: Long = if ((finishedAt ?: 0L) > 0L) finishedAt!! - 1 else 1L,
        sessionStatus: SessionStatus = SessionStatus.COMPLETED,
        importFingerprint: String? = null,
        bodyWeightKg: Double? = 80.0,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT
    ): WorkoutSessionAggregate {
        val performed = sets ?: listOf(lift(weight, reps, kind = kind))
        return workout(
            id = id,
            daysAgo = daysAgo,
            finishedAt = finishedAt,
            startedAt = startedAt,
            sessionStatus = sessionStatus,
            importFingerprint = importFingerprint,
            bodyWeightKg = bodyWeightKg,
            items = listOf(
                exercise(
                    exerciseId = exerciseId,
                    sessionId = id.toLong(),
                    name = name,
                    measurement = measurement,
                    resistance = resistance,
                    interpretation = interpretation,
                    primary = primary,
                    secondary = secondary,
                    sets = performed
                )
            )
        )
    }

    private fun bodyweight(
        id: Int,
        daysAgo: Int,
        reps: Int,
        kind: PlannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
        weight: Double? = null,
        bodyWeightKg: Double? = 80.0,
        exerciseId: Long = 1L,
        name: String = "Push-up"
    ): WorkoutSessionAggregate {
        return weighted(
            id = id,
            daysAgo = daysAgo,
            weight = weight,
            reps = reps,
            exerciseId = exerciseId,
            name = name,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            resistance = ResistanceBasis.BODYWEIGHT,
            measurement = MeasurementType.REPETITIONS,
            kind = kind,
            bodyWeightKg = bodyWeightKg
        )
    }

    private fun added(
        id: Int,
        daysAgo: Int,
        weight: Double,
        reps: Int,
        exerciseId: Long = 1L
    ): WorkoutSessionAggregate {
        return weighted(
            id = id,
            daysAgo = daysAgo,
            weight = weight,
            reps = reps,
            exerciseId = exerciseId,
            name = "Pull-up",
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            resistance = ResistanceBasis.BODYWEIGHT,
            measurement = MeasurementType.REPETITIONS,
            kind = PlannedLoadKind.ADDED_WEIGHT
        )
    }

    private fun hold(
        id: Int,
        daysAgo: Int,
        seconds: Int,
        kind: PlannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
        weight: Double? = null,
        exerciseId: Long = 1L
    ): WorkoutSessionAggregate {
        val measurement = if (kind == PlannedLoadKind.EXTERNAL_WEIGHT) {
            MeasurementType.DURATION_AND_WEIGHT
        } else {
            MeasurementType.DURATION
        }
        val resistance = if (kind == PlannedLoadKind.EXTERNAL_WEIGHT) {
            ResistanceBasis.EXTERNAL
        } else {
            ResistanceBasis.BODYWEIGHT
        }
        val interpretation = if (kind == PlannedLoadKind.EXTERNAL_WEIGHT) {
            WeightInterpretation.TOTAL
        } else if (kind == PlannedLoadKind.ADDED_WEIGHT) {
            WeightInterpretation.NOT_APPLICABLE
        } else {
            WeightInterpretation.NOT_APPLICABLE
        }
        return workout(
            id = id,
            daysAgo = daysAgo,
            items = listOf(
                exercise(
                    exerciseId = exerciseId,
                    sessionId = id.toLong(),
                    name = "Plank",
                    measurement = measurement,
                    resistance = resistance,
                    interpretation = interpretation,
                    sets = listOf(lift(weight = weight, reps = null, kind = kind, duration = seconds))
                )
            )
        )
    }

    private fun unsupported(
        id: Int,
        daysAgo: Int,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        kind: PlannedLoadKind,
        exerciseId: Long = 1L,
        name: String = "Unsupported",
        duration: Int? = null,
        distance: Double? = null
    ): WorkoutSessionAggregate {
        return workout(
            id = id,
            daysAgo = daysAgo,
            items = listOf(
                exercise(
                    exerciseId = exerciseId,
                    sessionId = id.toLong(),
                    name = name,
                    measurement = measurement,
                    resistance = resistance,
                    interpretation = WeightInterpretation.NOT_APPLICABLE,
                    sets = listOf(
                        lift(
                            weight = null,
                            reps = null,
                            kind = kind,
                            duration = duration,
                            distance = distance
                        )
                    )
                )
            )
        )
    }

    private fun workout(
        id: Int,
        daysAgo: Int,
        items: List<SessionExerciseItem>,
        finishedAt: Long? = 100_000L - daysAgo.toLong() * 100,
        startedAt: Long = if ((finishedAt ?: 0L) > 0L) finishedAt!! - 1 else 1L,
        sessionStatus: SessionStatus = SessionStatus.COMPLETED,
        importFingerprint: String? = null,
        bodyWeightKg: Double? = 80.0
    ): WorkoutSessionAggregate {
        return WorkoutSessionAggregate(
            session = sessionShell(
                id = id,
                daysAgo = daysAgo,
                finishedAt = finishedAt,
                startedAt = startedAt,
                sessionStatus = sessionStatus,
                importFingerprint = importFingerprint,
                bodyWeightKg = bodyWeightKg
            ),
            exercises = items
        )
    }

    private fun sessionShell(
        id: Int,
        daysAgo: Int,
        finishedAt: Long? = 100_000L - daysAgo.toLong() * 100,
        startedAt: Long = if ((finishedAt ?: 0L) > 0L) finishedAt!! - 1 else 1L,
        sessionStatus: SessionStatus = SessionStatus.COMPLETED,
        importFingerprint: String? = null,
        bodyWeightKg: Double? = 80.0
    ): WorkoutSession {
        return WorkoutSession(
            id = id.toLong(),
            templateId = null,
            templateName = "Session $id",
            status = sessionStatus,
            workoutDate = TODAY.minusDays(daysAgo.toLong()),
            startedAt = startedAt,
            finishedAt = finishedAt,
            abandonedAt = if (sessionStatus == SessionStatus.ABANDONED) startedAt else null,
            notes = null,
            bodyWeightKg = bodyWeightKg,
            bodyWeightSource = BodyWeightSource.MANUAL,
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L,
            importFingerprint = importFingerprint
        )
    }

    private fun exercise(
        exerciseId: Long,
        sessionId: Long,
        name: String,
        sets: List<SessionSet>,
        position: Int = 0,
        measurement: MeasurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
        resistance: ResistanceBasis = ResistanceBasis.EXTERNAL,
        interpretation: WeightInterpretation = WeightInterpretation.TOTAL,
        primary: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList()
    ): SessionExerciseItem {
        return SessionExerciseItem(
            exercise = SessionExercise(
                id = rowIds++,
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = position,
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.OTHER,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = primary,
                secondaryMuscles = secondary,
                notes = null
            ),
            sets = sets
        )
    }

    private fun lift(
        weight: Double?,
        reps: Int?,
        status: SessionSetStatus = SessionSetStatus.COMPLETED,
        kind: PlannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        duration: Int? = null,
        distance: Double? = null
    ): SessionSet {
        return SessionSet(
            id = setIds++,
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

    private fun ExercisePerformanceComparison.only(): MatchedPerformance {
        assertEquals(1, comparisons.size)
        return comparisons.single()
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 16)
        var rowIds: Long = 1L
        var setIds: Long = 1L
    }
}
