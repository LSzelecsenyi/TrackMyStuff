package hu.laca.weighttracker.ui.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.FakeWeightMeasurementDao
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.Greeting
import hu.laca.weighttracker.domain.model.ChartRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyRepositoryProducesEmptyDashboardState() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            sessionRepository = sessions(),
            dateProvider = dateProvider
        )
        val state = viewModel.uiState.first { !it.snapshot.isEmpty || it.today == today }
        assertTrue(state.snapshot.isEmpty)
        assertTrue(state.snapshot.chartPoints.isEmpty())
        assertTrue(state.snapshot.recentItems.isEmpty())
        assertEquals(Greeting.Morning, state.greeting)
    }

    @Test
    fun savedMeasurementLeavesEmptyState() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = DashboardViewModel(repository, sessions(), dateProvider)
        repository.save(today, 80.4)
        val state = viewModel.uiState.first { it.snapshot.todayHasMeasurement }
        assertFalse(state.snapshot.isEmpty)
        viewModel.onChartRangeSelected(ChartRange.All)
        assertFalse(viewModel.uiState.value.snapshot.chartPoints.isEmpty())
    }

    @Test
    fun futureDaySelectionIsRejected() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            sessionRepository = sessions(),
            dateProvider = dateProvider
        )
        viewModel.selectDay(today.plusDays(1))
        assertNull(viewModel.uiState.value.daySheet)
    }

    @Test
    fun daySheetWithoutMeasurement() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            sessionRepository = sessions(),
            dateProvider = dateProvider
        )
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.first { it.daySheet != null }.daySheet
        assertEquals(today, sheet?.date)
        assertFalse(sheet!!.hasMeasurement)
        assertFalse(sheet.hasWorkouts)
    }

    @Test
    fun daySheetWithMeasurement() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = DashboardViewModel(repository, sessions(), dateProvider)
        repository.save(today.minusDays(3), 80.0)
        repository.save(today, 80.5)
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.first { it.daySheet?.hasMeasurement == true }.daySheet
        assertTrue(sheet!!.hasMeasurement)
        assertEquals(0.5, sheet.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun decemberNavigatesToJanuary() = runTest {
        val viewModel = DashboardViewModel(
            repository = WeightRepository(FakeWeightMeasurementDao(), clock),
            sessionRepository = sessions(),
            dateProvider = dateProvider
        )
        assertEquals(YearMonth.of(2026, 3), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2026, 3) }.displayedMonth)
        repeat(9) { viewModel.onNextMonth() }
        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2026, 12) }.displayedMonth)
        viewModel.onNextMonth()
        assertEquals(YearMonth.of(2027, 1), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2027, 1) }.displayedMonth)
    }

    private fun sessions(): WorkoutSessionRepository {
        return WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = WeightRepository(database.weightMeasurementDao(), clock),
            clock = clock,
            dateProvider = dateProvider
        )
    }
}
