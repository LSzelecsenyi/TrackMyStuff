package app.mymusclemap.ui.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.ui.navigation.AppNavigation
import app.mymusclemap.ui.navigation.AppRoutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import app.mymusclemap.data.repository.WeightRepository

@RunWith(RobolectricTestRunner::class)
class WeightDetailsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun emptyRepositoryKeepsNoMeasurementState() = runTest {
        val viewModel = WeightDetailsViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            dateProvider = dateProvider
        )
        val state = viewModel.uiState.first { it.today == today }
        assertTrue(state.snapshot.isEmpty)
        assertNull(state.snapshot.latest)
        assertTrue(state.snapshot.chartPoints.isEmpty())
        assertFalse(state.snapshot.todayHasMeasurement)
    }

    @Test
    fun savedMeasurementRetainsLatestWeeklyAndComparison() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today.minusDays(8), 81.0)
        repository.save(today, 82.0)
        val viewModel = WeightDetailsViewModel(repository, dateProvider)
        val state = viewModel.uiState.first { it.snapshot.todayHasMeasurement }
        assertEquals(82.0, state.snapshot.latest!!.weightKg, 0.0)
        assertEquals(today, state.snapshot.latest!!.date)
        assertTrue(state.snapshot.currentWeek != null)
        viewModel.onChartRangeSelected(ChartRange.All)
        assertFalse(viewModel.uiState.value.snapshot.chartPoints.isEmpty())
    }

    @Test
    fun dashboardSummaryOpensWeightDetailsAndBackIsOverview() {
        @Suppress("UNUSED_VARIABLE")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val navigation = AppNavigation.openWeightDetails(AppRoutes.OVERVIEW)
        assertEquals(AppRoutes.WEIGHT_DETAILS, navigation.targetRoute)
        assertEquals(AppRoutes.OVERVIEW, navigation.backTarget)
        assertFalse(AppNavigation.showsBottomBar(AppRoutes.WEIGHT_DETAILS))
    }
}
