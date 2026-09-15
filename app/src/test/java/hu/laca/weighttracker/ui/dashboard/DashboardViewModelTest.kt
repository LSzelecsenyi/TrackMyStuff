package hu.laca.weighttracker.ui.dashboard

import hu.laca.weighttracker.FakeWeightMeasurementDao
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.domain.model.ChartRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = DateProvider { today }
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun emptyRepositoryProducesEmptyDashboardState() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            dateProvider = dateProvider
        )
        assertTrue(viewModel.uiState.value.snapshot.isEmpty)
        assertTrue(viewModel.uiState.value.snapshot.chartPoints.isEmpty())
        assertTrue(viewModel.uiState.value.snapshot.recentItems.isEmpty())
    }

    @Test
    fun savedMeasurementLeavesEmptyState() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = DashboardViewModel(repository, dateProvider)
        repository.save(today, 80.4)
        assertFalse(viewModel.uiState.value.snapshot.isEmpty)
        assertTrue(viewModel.uiState.value.snapshot.todayHasMeasurement)
        viewModel.onChartRangeSelected(ChartRange.All)
        assertFalse(viewModel.uiState.value.snapshot.chartPoints.isEmpty())
    }
}
