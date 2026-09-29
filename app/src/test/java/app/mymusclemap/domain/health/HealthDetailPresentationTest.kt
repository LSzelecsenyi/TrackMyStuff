package app.mymusclemap.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class HealthDetailPresentationTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test
    fun stepsHeadlineIsTodayAndMissingDaysStayNullSlots() {
        val readings = HealthReadings(
            steps = listOf(
                DailyStepTotal(LocalDate.of(2026, 9, 23), 8_100),
                DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)
            )
        )
        val detail = connected(steps = true, readings = readings)
        assertNull(detail.steps.headline)
        assertFalse(detail.steps.headlineIsToday)
        assertEquals(7, detail.steps.slots.size)
        assertEquals(LocalDate.of(2026, 9, 23), detail.steps.slots.first().date)
        assertEquals(8_100.0, detail.steps.slots.first().amount!!, 0.0)
        assertNull(detail.steps.slots[1].amount)
        assertEquals(7_300.0, detail.steps.slots[5].amount!!, 0.0)
        assertNull(detail.steps.slots[6].amount)
        assertEquals(today, detail.steps.slots[6].date)
    }

    @Test
    fun restingHeartRateStaysTodayOnly() {
        val readings = HealthReadings(
            restingHeartRate = listOf(DailyRestingHeartRate(LocalDate.of(2026, 9, 28), 59))
        )
        val detail = connected(heart = true, readings = readings)
        assertNull(detail.heart.headline)
        assertEquals(59.0, detail.heart.slots[5].amount!!, 0.0)
        assertNull(detail.heart.slots[6].amount)
    }

    @Test
    fun hrvUsesTodayWhenPresentAndOtherwiseShowsTheLatestDate() {
        val todayOnly = connected(
            hrv = true,
            readings = HealthReadings(hrv = listOf(DailyHrv(today, 47.0), DailyHrv(today.minusDays(1), 55.0)))
        )
        assertEquals(47.0, todayOnly.hrv.headline!!, 0.0)
        assertTrue(todayOnly.hrv.headlineIsToday)
        assertEquals(today, todayOnly.hrv.headlineDate)

        val latest = connected(
            hrv = true,
            readings = HealthReadings(hrv = listOf(DailyHrv(LocalDate.of(2026, 9, 28), 47.0)))
        )
        assertEquals(47.0, latest.hrv.headline!!, 0.0)
        assertFalse(latest.hrv.headlineIsToday)
        assertEquals(LocalDate.of(2026, 9, 28), latest.hrv.headlineDate)
        assertNull(latest.hrv.slots[6].amount)
    }

    @Test
    fun sleepHeadlineIsTheLatestNightNotAnUnlabeledToday() {
        val detail = connected(
            sleep = true,
            readings = HealthReadings(
                sleep = listOf(DailySleep(LocalDate.of(2026, 9, 28), Duration.ofMinutes(452)))
            )
        )
        assertEquals(452.0, detail.sleep.headline!!, 0.0)
        assertFalse(detail.sleep.headlineIsToday)
        assertEquals(LocalDate.of(2026, 9, 28), detail.sleep.headlineDate)
        assertEquals(452.0, detail.sleep.slots[5].amount!!, 0.0)
        assertNull(detail.sleep.slots[6].amount)
        assertNull(detail.sleep.slots[4].amount)
    }

    @Test
    fun exerciseSummaryIsTheLastSevenDaysAndDoesNotInventToday() {
        val detail = connected(
            exercise = true,
            readings = HealthReadings(
                exercise = listOf(
                    DailyExercise(
                        date = LocalDate.of(2026, 9, 23),
                        strength = ExerciseTotal(1, Duration.ofMinutes(45))
                    ),
                    DailyExercise(
                        date = LocalDate.of(2026, 9, 28),
                        running = ExerciseTotal(1, Duration.ofMinutes(28)),
                        cycling = ExerciseTotal(1, Duration.ofMinutes(55))
                    )
                )
            )
        )
        assertEquals(1, detail.strength.recentSessions)
        assertEquals(Duration.ofMinutes(45), detail.strength.recentDuration)
        assertEquals(0, detail.strength.todaySessions)
        assertEquals(1, detail.running.recentSessions)
        assertEquals(0, detail.running.todaySessions)
        assertEquals(1, detail.cycling.recentSessions)
        assertEquals(HealthLineStatus.Off, detail.steps.status)
    }

    @Test
    fun aDeniedMetricStaysOffWhileAnotherStaysShown() {
        val detail = connected(
            steps = true,
            readings = HealthReadings(steps = listOf(DailyStepTotal(today, 1_000)))
        )
        assertEquals(HealthLineStatus.Shown, detail.steps.status)
        assertEquals(HealthLineStatus.Off, detail.hrv.status)
        assertEquals(HealthLineStatus.Off, detail.sleep.status)
        assertEquals(HealthLineStatus.Off, detail.strength.status)
    }

    @Test
    fun oneFailedMetricDoesNotFailTheOthers() {
        val detail = connected(
            steps = true,
            hrv = true,
            readings = HealthReadings(
                steps = listOf(DailyStepTotal(today, 5_100)),
                failed = setOf(HealthMetric.HRV)
            )
        )
        assertEquals(HealthLineStatus.Shown, detail.steps.status)
        assertEquals(5_100.0, detail.steps.headline!!, 0.0)
        assertEquals(HealthLineStatus.Failed, detail.hrv.status)
    }

    @Test
    fun noGrantsStayQuiet() {
        val state = HealthDetailPresentation.detail(
            HealthAccess(availability = HealthAvailability.Available, checked = true),
            HealthReadings(),
            today
        )
        assertTrue(state is HealthDetailState.Quiet)
    }

    private fun connected(
        steps: Boolean = false,
        heart: Boolean = false,
        exercise: Boolean = false,
        hrv: Boolean = false,
        sleep: Boolean = false,
        readings: HealthReadings
    ): HealthDetailState.Connected {
        val state = HealthDetailPresentation.detail(
            HealthAccess(
                availability = HealthAvailability.Available,
                stepsGranted = steps,
                restingHeartRateGranted = heart,
                checked = true,
                exerciseGranted = exercise,
                hrvGranted = hrv,
                sleepGranted = sleep
            ),
            readings,
            today
        )
        return state as HealthDetailState.Connected
    }
}
