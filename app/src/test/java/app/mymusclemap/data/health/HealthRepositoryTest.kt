package app.mymusclemap.data.health

import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.entitlement.CompletedWorkout
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.WorkoutOrigin
import app.mymusclemap.domain.health.HealthAvailability
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthExerciseSession
import app.mymusclemap.domain.health.HealthGrants
import app.mymusclemap.domain.health.HealthHrvSample
import app.mymusclemap.domain.health.HealthMetric
import app.mymusclemap.domain.health.HealthMetricBucket
import app.mymusclemap.domain.health.HealthSleepSpan
import app.mymusclemap.domain.health.HealthSource
import app.mymusclemap.domain.health.HealthWindow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class HealthRepositoryTest {
    private val today = LocalDate.of(2024, 6, 15)
    private val source = FakeHealthSource()
    private val repository = HealthRepository(source, FixedDateProvider(today))

    @Test
    fun unavailableDoesNotReadAndDoesNotInventReadings() = runTest {
        source.availability = HealthAvailability.Unavailable
        repository.refresh()
        assertEquals(HealthAvailability.Unavailable, repository.access.value.availability)
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(0, source.stepReads)
        assertEquals(0, source.heartReads)
    }

    @Test
    fun providerUpdateRequiredDoesNotRead() = runTest {
        source.availability = HealthAvailability.ProviderUpdateRequired
        repository.refresh()
        assertEquals(HealthAvailability.ProviderUpdateRequired, repository.access.value.availability)
        assertEquals(0, source.permissionReads)
        assertEquals(0, source.stepReads)
    }

    @Test
    fun availableWithoutPermissionsShowsNothingAsReadings() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = false)
        source.steps = listOf(HealthMetricBucket(today, 8_000))
        repository.refresh()
        assertTrue(repository.access.value.checked)
        assertFalse(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertFalse(repository.readings.value.readFailed)
        assertEquals(0, source.stepReads)
        assertEquals(0, source.heartReads)
    }

    @Test
    fun stepsOnlyStillReadsStepsWhenHeartRateIsDenied() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = false)
        source.steps = listOf(
            HealthMetricBucket(today.minusDays(1), null),
            HealthMetricBucket(today, 7_300)
        )
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertEquals(1, repository.readings.value.steps.size)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertEquals(0, source.heartReads)
        assertNull(repository.readings.value.steps.firstOrNull { it.date == today.minusDays(1) })
    }

    @Test
    fun heartRateOnlyStillReadsHeartRateWhenStepsAreDenied() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = true)
        source.heart = listOf(HealthMetricBucket(today, 59))
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertEquals(59L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(0, source.stepReads)
    }

    @Test
    fun bothGrantedMapDailyValuesInsideTheThirtyDayQuery() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(
            HealthMetricBucket(today.minusDays(2), 6_500),
            HealthMetricBucket(today, 7_300),
            HealthMetricBucket(today.plusDays(1), 99_999),
            HealthMetricBucket(HealthWindow.start(today).minusDays(1), 111)
        )
        source.heart = listOf(HealthMetricBucket(today, 59), HealthMetricBucket(today.minusDays(4), 62))
        repository.refresh()
        val (start, end) = HealthWindow.queryRange(today)
        assertEquals(start, source.stepStart)
        assertEquals(end, source.stepEnd)
        assertEquals(start, source.heartStart)
        assertEquals(end, source.heartEnd)
        assertEquals(
            listOf(today.minusDays(2), today),
            repository.readings.value.steps.map { it.date }
        )
        assertEquals(listOf(62L, 59L), repository.readings.value.restingHeartRate.map { it.beatsPerMinute })
    }

    @Test
    fun noDataStaysEmptyWithoutFailure() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        repository.refresh()
        assertTrue(repository.readings.value.steps.isEmpty())
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun revokedStepsClearStepsAndKeepHeartRate() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 5_100))
        source.heart = listOf(HealthMetricBucket(today, 61))
        repository.refresh()
        assertEquals(5_100L, repository.readings.value.steps.single().steps)

        source.grants = HealthGrants(steps = false, restingHeartRate = true)
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.access.value.restingHeartRateGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertEquals(61L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
    }

    @Test
    fun revokedHeartRateClearsHeartRateAndKeepsSteps() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 7_300))
        source.heart = listOf(HealthMetricBucket(today, 59))
        repository.refresh()

        source.grants = HealthGrants(steps = true, restingHeartRate = false)
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertFalse(repository.access.value.restingHeartRateGranted)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun revokedBothClearsReadingsWithoutFailure() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(HealthMetricBucket(today, 1))
        repository.refresh()
        source.grants = HealthGrants(steps = false, restingHeartRate = false)
        repository.refresh()
        assertFalse(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun dayAfterTheLastReadingKeepsYesterdayAndDoesNotInventToday() = runTest {
        val today = LocalDate.of(2026, 9, 29)
        val source = FakeHealthSource()
        val repository = HealthRepository(source, FixedDateProvider(today))
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.steps = listOf(
            HealthMetricBucket(LocalDate.of(2026, 9, 28), 7_300),
            HealthMetricBucket(today, null)
        )
        source.heart = listOf(HealthMetricBucket(LocalDate.of(2026, 9, 28), 59))
        repository.refresh()
        val (start, end) = HealthWindow.queryRange(today)
        assertEquals(start, source.stepStart)
        assertEquals(end, source.stepEnd)
        assertEquals(LocalDate.of(2026, 9, 28), repository.readings.value.steps.single().date)
        assertEquals(7_300L, repository.readings.value.steps.single().steps)
        assertEquals(59L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(repository.readings.value.steps.none { it.date == today })
        assertTrue(repository.readings.value.restingHeartRate.none { it.date == today })
    }

    @Test
    fun readFailureDoesNotKeepPartialNumbers() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true)
        source.failReads = true
        repository.refresh()
        assertTrue(repository.access.value.stepsGranted)
        assertTrue(repository.readings.value.readFailed)
        assertTrue(repository.readings.value.steps.isEmpty())
        assertTrue(repository.readings.value.restingHeartRate.isEmpty())
    }

    @Test
    fun exerciseGrantDoesNotReadStepsAndKeepsExternalSessions() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = false, exercise = true)
        val start = today.atTime(18, 0).toInstant(ZoneOffset.UTC)
        source.exercise = listOf(
            HealthExerciseSession(
                start = start,
                end = start.plus(Duration.ofMinutes(45)),
                zone = ZoneOffset.UTC,
                kind = HealthExerciseKind.STRENGTH
            )
        )
        repository.refresh()
        assertEquals(0, source.stepReads)
        assertEquals(0, source.heartReads)
        assertEquals(1, source.exerciseReads)
        assertEquals(1, repository.readings.value.exercise.single().strength.sessions)
        assertEquals(Duration.ofMinutes(45), repository.readings.value.exercise.single().strength.duration)
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun deniedSleepLeavesStepsUsable() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = false, sleep = false)
        source.steps = listOf(HealthMetricBucket(today, 4_200))
        repository.refresh()
        assertEquals(4_200L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.sleep.isEmpty())
        assertEquals(0, source.sleepReads)
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun hrvFailureKeepsStepsAndMarksOnlyHrv() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = false, hrv = true)
        source.steps = listOf(HealthMetricBucket(today, 8_100))
        source.failHrv = true
        repository.refresh()
        assertEquals(8_100L, repository.readings.value.steps.single().steps)
        assertTrue(repository.readings.value.hrv.isEmpty())
        assertEquals(setOf(HealthMetric.HRV), repository.readings.value.failed)
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun sleepFailureDoesNotClearSteps() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true, sleep = true)
        source.steps = listOf(HealthMetricBucket(today, 6_500))
        source.heart = listOf(HealthMetricBucket(today, 58))
        source.failSleep = true
        repository.refresh()
        assertEquals(6_500L, repository.readings.value.steps.single().steps)
        assertEquals(58L, repository.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(repository.readings.value.failed.contains(HealthMetric.SLEEP))
        assertFalse(repository.readings.value.readFailed)
    }

    @Test
    fun revokeExerciseClearsSessionsAndKeepsSteps() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = false, exercise = true)
        val start = today.atTime(18, 0).toInstant(ZoneOffset.UTC)
        source.steps = listOf(HealthMetricBucket(today, 9_000))
        source.exercise = listOf(
            HealthExerciseSession(start, start.plus(Duration.ofMinutes(50)), ZoneOffset.UTC, HealthExerciseKind.STRENGTH)
        )
        repository.refresh()
        assertEquals(1, repository.readings.value.exercise.single().strength.sessions)
        source.grants = HealthGrants(steps = true, restingHeartRate = false, exercise = false)
        repository.refresh()
        assertTrue(repository.readings.value.exercise.isEmpty())
        assertEquals(9_000L, repository.readings.value.steps.single().steps)
        assertEquals(1, source.exerciseReads)
    }

    @Test
    fun revokeHrvClearsThePreviousSample() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = false, hrv = true)
        source.hrv = listOf(HealthHrvSample(today.atTime(7, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC, 48.0))
        repository.refresh()
        assertEquals(48.0, repository.readings.value.hrv.single().millis, 0.0)
        source.grants = HealthGrants(steps = false, restingHeartRate = false, hrv = false)
        repository.refresh()
        assertTrue(repository.readings.value.hrv.isEmpty())
        assertEquals(1, source.hrvReads)
    }

    @Test
    fun futureExerciseAndHrvAreDropped() = runTest {
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = false, restingHeartRate = false, exercise = true, hrv = true, sleep = true)
        val tomorrow = today.plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC)
        source.exercise = listOf(
            HealthExerciseSession(
                tomorrow,
                tomorrow.plus(Duration.ofMinutes(30)),
                ZoneOffset.UTC,
                HealthExerciseKind.RUNNING
            )
        )
        source.hrv = listOf(HealthHrvSample(tomorrow, ZoneOffset.UTC, 40.0))
        source.sleep = listOf(
            HealthSleepSpan(tomorrow, tomorrow.plus(Duration.ofHours(7)), ZoneOffset.UTC)
        )
        repository.refresh()
        assertTrue(repository.readings.value.exercise.isEmpty())
        assertTrue(repository.readings.value.hrv.isEmpty())
        assertTrue(repository.readings.value.sleep.isEmpty())
    }

    @Test
    fun freeStillReadsStepsAndRestingHeartRateAndSkipsExternalWorkoutImport() = runTest {
        val gated = HealthRepository(source, FixedDateProvider(today)) { false }
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true, exercise = true)
        source.steps = listOf(HealthMetricBucket(today, 4_200))
        source.heart = listOf(HealthMetricBucket(today, 58))
        val start = today.atTime(8, 0).toInstant(ZoneOffset.UTC)
        source.exercise = listOf(
            HealthExerciseSession(start, start.plus(Duration.ofMinutes(40)), ZoneOffset.UTC, HealthExerciseKind.STRENGTH)
        )
        gated.refresh()
        assertTrue(gated.access.value.checked)
        assertEquals(HealthAvailability.Available, gated.access.value.availability)
        assertTrue(gated.access.value.stepsGranted)
        assertTrue(gated.access.value.restingHeartRateGranted)
        assertEquals(4_200L, gated.readings.value.steps.single().steps)
        assertEquals(58L, gated.readings.value.restingHeartRate.single().beatsPerMinute)
        assertTrue(gated.readings.value.exercise.isEmpty())
        assertEquals(0, source.exerciseReads)
        assertEquals(1, source.stepReads)
        assertEquals(1, source.heartReads)
    }

    @Test
    fun proImportsExternalWorkoutsAndALaterFreeRefreshKeepsThemWithoutReadingAgain() = runTest {
        var allowImport = true
        val gated = HealthRepository(source, FixedDateProvider(today)) { allowImport }
        source.availability = HealthAvailability.Available
        source.grants = HealthGrants(steps = true, restingHeartRate = true, exercise = true)
        source.steps = listOf(HealthMetricBucket(today, 1_000))
        source.heart = listOf(HealthMetricBucket(today, 60))
        val start = today.atTime(18, 0).toInstant(ZoneOffset.UTC)
        source.exercise = listOf(
            HealthExerciseSession(start, start.plus(Duration.ofMinutes(30)), ZoneOffset.UTC, HealthExerciseKind.CYCLING)
        )
        gated.refresh()
        assertEquals(1, source.exerciseReads)
        assertEquals(1, gated.readings.value.exercise.single().cycling.sessions)
        allowImport = false
        source.steps = listOf(HealthMetricBucket(today, 1_500))
        gated.refresh()
        assertEquals(1, source.exerciseReads)
        assertEquals(1, gated.readings.value.exercise.single().cycling.sessions)
        assertEquals(1_500L, gated.readings.value.steps.single().steps)
        assertEquals(60L, gated.readings.value.restingHeartRate.single().beatsPerMinute)
        val qualification = FounderProgramRules.Production.qualify(
            workouts = listOf(CompletedWorkout(today, WorkoutOrigin.HealthConnect)),
            feedbackRecorded = true,
            testerAnalyticsReportSubmitted = true
        )
        assertEquals(0, qualification.nativeCompletedWorkouts)
        assertEquals(0, qualification.distinctNativeWorkoutDays)
    }

    private class FakeHealthSource : HealthSource {
        var availability: HealthAvailability = HealthAvailability.Unavailable
        var grants = HealthGrants(steps = false, restingHeartRate = false)
        var steps: List<HealthMetricBucket> = emptyList()
        var heart: List<HealthMetricBucket> = emptyList()
        var exercise: List<HealthExerciseSession> = emptyList()
        var hrv: List<HealthHrvSample> = emptyList()
        var sleep: List<HealthSleepSpan> = emptyList()
        var failReads = false
        var failHeart = false
        var failExercise = false
        var failHrv = false
        var failSleep = false
        var permissionReads = 0
        var stepReads = 0
        var heartReads = 0
        var exerciseReads = 0
        var hrvReads = 0
        var sleepReads = 0
        var stepStart: LocalDateTime? = null
        var stepEnd: LocalDateTime? = null
        var heartStart: LocalDateTime? = null
        var heartEnd: LocalDateTime? = null

        override fun availability(): HealthAvailability = availability

        override suspend fun grantedPermissions(): HealthGrants {
            permissionReads += 1
            return grants
        }

        override suspend fun readSteps(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthMetricBucket> {
            stepReads += 1
            stepStart = startInclusive
            stepEnd = endExclusive
            if (failReads) throw IOException("health read failed")
            return steps
        }

        override suspend fun readRestingHeartRate(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthMetricBucket> {
            heartReads += 1
            heartStart = startInclusive
            heartEnd = endExclusive
            if (failReads || failHeart) throw IOException("health read failed")
            return heart
        }

        override suspend fun readExerciseSessions(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthExerciseSession> {
            exerciseReads += 1
            if (failReads || failExercise) throw IOException("health read failed")
            return exercise
        }

        override suspend fun readHrvSamples(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthHrvSample> {
            hrvReads += 1
            if (failReads || failHrv) throw IOException("health read failed")
            return hrv
        }

        override suspend fun readSleepSpans(
            startInclusive: LocalDateTime,
            endExclusive: LocalDateTime
        ): List<HealthSleepSpan> {
            sleepReads += 1
            if (failReads || failSleep) throw IOException("health read failed")
            return sleep
        }
    }
}
