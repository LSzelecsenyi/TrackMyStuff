package app.mymusclemap.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HealthPresentationTest {
    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun overviewQuietStatesStaySeparateFromReadings() {
        assertEquals(
            HealthCardState.Quiet(HealthQuietStatus.Unavailable),
            HealthPresentation.card(access(HealthAvailability.Unavailable), HealthReadings(), today)
        )
        assertEquals(
            HealthCardState.Quiet(HealthQuietStatus.UpdateRequired),
            HealthPresentation.card(
                access(HealthAvailability.ProviderUpdateRequired),
                HealthReadings(),
                today
            )
        )
        assertEquals(
            HealthCardState.Quiet(HealthQuietStatus.NotConnected),
            HealthPresentation.card(access(HealthAvailability.Available), HealthReadings(), today)
        )
        assertEquals(
            HealthCardState.Checking,
            HealthPresentation.card(HealthAccess(), HealthReadings(), today)
        )
    }

    @Test
    fun stepsOnlyShowsStepsAndLeavesHeartRateUnconnected() {
        val card = HealthPresentation.card(
            access(HealthAvailability.Available, steps = true),
            HealthReadings(steps = listOf(DailyStepTotal(today, 7_300), DailyStepTotal(today.minusDays(2), 6_500))),
            today
        ) as HealthCardState.Readings
        assertEquals(7_300L, card.todaySteps)
        assertNull(card.todayHeartRate)
        assertTrue(card.stepsGranted)
        assertFalse(card.heartRateGranted)
        assertEquals(7, card.recentSteps.size)
        assertEquals(6_500L, card.recentSteps.first { it.date == today.minusDays(2) }.steps)
        assertNull(card.recentSteps.first { it.date == today.minusDays(1) }.steps)
    }

    @Test
    fun heartRateOnlyOmitsTheStepsChart() {
        val card = HealthPresentation.card(
            access(HealthAvailability.Available, heart = true),
            HealthReadings(restingHeartRate = listOf(DailyRestingHeartRate(today, 59))),
            today
        ) as HealthCardState.Readings
        assertNull(card.todaySteps)
        assertEquals(59L, card.todayHeartRate)
        assertTrue(card.recentSteps.isEmpty())
    }

    @Test
    fun headlineIsTodayOnlyWhenTheLatestReadingIsYesterday() {
        val today = LocalDate.of(2026, 9, 29)
        val steps = listOf(
            DailyStepTotal(LocalDate.of(2026, 9, 23), 8_100),
            DailyStepTotal(LocalDate.of(2026, 9, 24), 6_500),
            DailyStepTotal(LocalDate.of(2026, 9, 25), 9_000),
            DailyStepTotal(LocalDate.of(2026, 9, 26), 5_100),
            DailyStepTotal(LocalDate.of(2026, 9, 27), 10_200),
            DailyStepTotal(LocalDate.of(2026, 9, 28), 7_300)
        )
        val card = HealthPresentation.card(
            access(HealthAvailability.Available, steps = true, heart = true),
            HealthReadings(
                steps = steps,
                restingHeartRate = listOf(DailyRestingHeartRate(LocalDate.of(2026, 9, 28), 59))
            ),
            today
        ) as HealthCardState.Readings
        assertNull(card.todaySteps)
        assertNull(card.todayHeartRate)
        assertEquals(7_300L, card.recentSteps.first { it.date == LocalDate.of(2026, 9, 28) }.steps)
        assertNull(card.recentSteps.first { it.date == today }.steps)
        assertEquals(6, card.recentSteps.count { it.steps != null })
        assertTrue(card.recentSteps.none { it.steps == 0L })
    }

    @Test
    fun futureOnlyDoesNotBecomeTodaysHeadlineOrAChartBar() {
        val today = LocalDate.of(2026, 9, 29)
        val card = HealthPresentation.card(
            access(HealthAvailability.Available, steps = true, heart = true),
            HealthReadings(
                steps = listOf(DailyStepTotal(today.plusDays(1), 100)),
                restingHeartRate = listOf(DailyRestingHeartRate(today.plusDays(1), 40))
            ),
            today
        ) as HealthCardState.Readings
        assertNull(card.todaySteps)
        assertNull(card.todayHeartRate)
        assertTrue(card.recentSteps.all { it.steps == null })
    }

    @Test
    fun connectedWithNoReadingsAndReadFailureStayOnTheCard() {
        val empty = HealthPresentation.card(
            access(HealthAvailability.Available, steps = true, heart = true),
            HealthReadings(),
            today
        ) as HealthCardState.Readings
        assertNull(empty.todaySteps)
        assertNull(empty.todayHeartRate)
        assertFalse(empty.readFailed)
        assertTrue(empty.recentSteps.all { it.steps == null })

        val failed = HealthPresentation.card(
            access(HealthAvailability.Available, steps = true, heart = true),
            HealthReadings(readFailed = true),
            today
        ) as HealthCardState.Readings
        assertTrue(failed.readFailed)
        assertNull(failed.todaySteps)
    }

    @Test
    fun settingsMapsAvailabilityAndPartialGrants() {
        assertFalse(HealthPresentation.settings(HealthAccess()).ready)
        assertEquals(
            HealthSettingsStatus.Unavailable,
            HealthPresentation.settings(access(HealthAvailability.Unavailable)).status
        )
        assertEquals(
            HealthSettingsAction.None,
            HealthPresentation.settings(access(HealthAvailability.Unavailable)).action
        )
        assertEquals(
            HealthSettingsAction.InstallOrUpdate,
            HealthPresentation.settings(access(HealthAvailability.ProviderUpdateRequired)).action
        )
        val denied = HealthPresentation.settings(access(HealthAvailability.Available))
        assertEquals(HealthSettingsStatus.NotConnected, denied.status)
        assertEquals(HealthSettingsAction.RequestPermissions, denied.action)

        val stepsOnly = HealthPresentation.settings(access(HealthAvailability.Available, steps = true))
        assertEquals(HealthSettingsStatus.Connected, stepsOnly.status)
        assertEquals(HealthSettingsAction.ManageAccess, stepsOnly.action)
        assertTrue(stepsOnly.stepsGranted)
        assertFalse(stepsOnly.restingHeartRateGranted)

        val heartOnly = HealthPresentation.settings(access(HealthAvailability.Available, heart = true))
        assertEquals(HealthSettingsStatus.Connected, heartOnly.status)
        assertEquals(HealthSettingsAction.ManageAccess, heartOnly.action)
        assertFalse(heartOnly.stepsGranted)
        assertTrue(heartOnly.restingHeartRateGranted)

        val both = HealthPresentation.settings(
            access(HealthAvailability.Available, steps = true, heart = true)
        )
        assertEquals(HealthSettingsAction.ManageAccess, both.action)
        assertTrue(both.stepsGranted && both.restingHeartRateGranted)
    }

    private fun access(
        availability: HealthAvailability,
        steps: Boolean = false,
        heart: Boolean = false
    ): HealthAccess {
        return HealthAccess(
            availability = availability,
            stepsGranted = steps,
            restingHeartRateGranted = heart,
            checked = true
        )
    }
}
