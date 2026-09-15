package hu.laca.weighttracker.ui.dashboard

import hu.laca.weighttracker.FakeWeightMeasurementDao
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.Greeting
import hu.laca.weighttracker.domain.model.ChartRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
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
        assertEquals(Greeting.Morning, viewModel.uiState.value.greeting)
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

    @Test
    fun futureDaySelectionIsRejected() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            dateProvider = dateProvider
        )
        viewModel.selectDay(today.plusDays(1))
        assertNull(viewModel.uiState.value.daySheet)
    }

    @Test
    fun daySheetWithoutMeasurement() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            dateProvider = dateProvider
        )
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.value.daySheet
        assertEquals(today, sheet?.date)
        assertFalse(sheet!!.hasMeasurement)
    }

    @Test
    fun daySheetWithMeasurement() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = DashboardViewModel(repository, dateProvider)
        repository.save(today.minusDays(3), 80.0)
        repository.save(today, 80.5)
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.value.daySheet
        assertTrue(sheet!!.hasMeasurement)
        assertEquals(0.5, sheet.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun decemberNavigatesToJanuary() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            dateProvider = dateProvider
        )
        assertEquals(YearMonth.of(2026, 3), viewModel.uiState.value.displayedMonth)
        repeat(9) { viewModel.onNextMonth() }
        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.value.displayedMonth)
        viewModel.onNextMonth()
        assertEquals(YearMonth.of(2027, 1), viewModel.uiState.value.displayedMonth)
    }
}
