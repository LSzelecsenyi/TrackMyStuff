package app.mymusclemap.ui.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.journal.JournalEmptyKind
import app.mymusclemap.domain.journal.JournalFilter
import app.mymusclemap.domain.journal.WeightJournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class HistoryViewModelTest {
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
    fun journalKeepsMeasurementHistoryStateAndActions() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today.minusDays(1), 81.0)
        repository.save(today, 81.4)
        val viewModel = HistoryViewModel(repository, sessions(), dateProvider, SavedStateHandle())
        val initial = viewModel.uiState.first { !it.loading }
        assertEquals(JournalFilter.WORKOUT, initial.filter)
        viewModel.onFilterSelected(JournalFilter.WEIGHT)
        val loaded = viewModel.uiState.first { !it.loading && it.filter == JournalFilter.WEIGHT && it.timeline.entries.isNotEmpty() }
        val weights = loaded.timeline.entries.filterIsInstance<WeightJournalEntry>()
        assertEquals(2, weights.size)
        assertEquals(0.4, weights.first().item.differenceFromPreviousKg!!, 0.001)

        viewModel.openEditor(today)
        assertNotNull(viewModel.uiState.value.editor)
        viewModel.dismissEditor()
        assertEquals(null, viewModel.uiState.value.editor)

        viewModel.openDelete(today.minusDays(1))
        assertTrue(viewModel.uiState.value.editor?.showDeleteConfirm == true)
        viewModel.confirmDelete()
        val remaining = viewModel.uiState.first {
            !it.loading && it.timeline.entries.filterIsInstance<WeightJournalEntry>().size == 1
        }
        assertFalse(remaining.isEmpty)
        assertEquals(today, remaining.timeline.entries.filterIsInstance<WeightJournalEntry>().single().date)
    }

    @Test
    fun filterStateSurvivesAndEmptyFilterIsDistinct() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today, 81.4)
        val handle = SavedStateHandle()
        val viewModel = HistoryViewModel(repository, sessions(), dateProvider, handle)
        val initial = viewModel.uiState.first { !it.loading }
        assertEquals(JournalFilter.WORKOUT, initial.filter)
        assertEquals(JournalEmptyKind.FilterEmpty, initial.emptyKind)
        viewModel.onFilterSelected(JournalFilter.WORKOUT)
        val filtered = viewModel.uiState.first { it.filter == JournalFilter.WORKOUT }
        assertEquals(JournalEmptyKind.FilterEmpty, filtered.emptyKind)
        assertEquals(JournalFilter.WORKOUT.name, handle.get<String>(HistoryViewModel.FILTER))
        viewModel.onFilterSelected(JournalFilter.WEIGHT)
        val weightsOnly = viewModel.uiState.first { it.filter == JournalFilter.WEIGHT }
        assertEquals(1, weightsOnly.timeline.entries.size)
        assertTrue(weightsOnly.timeline.entries.single() is WeightJournalEntry)
        viewModel.onFilterSelected(JournalFilter.ALL)
        val all = viewModel.uiState.first { it.filter == JournalFilter.ALL }
        assertEquals(1, all.timeline.entries.size)
        assertTrue(all.timeline.entries.single() is WeightJournalEntry)
    }

    @Test
    fun savedWeightFilterIsRestoredInsteadOfNewDefault() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today, 81.4)
        val handle = SavedStateHandle(mapOf(HistoryViewModel.FILTER to JournalFilter.WEIGHT.name))
        val viewModel = HistoryViewModel(repository, sessions(), dateProvider, handle)
        val restored = viewModel.uiState.first { !it.loading && it.filter == JournalFilter.WEIGHT }
        assertEquals(JournalFilter.WEIGHT, restored.filter)
        assertEquals(1, restored.timeline.entries.size)
        assertTrue(restored.timeline.entries.single() is WeightJournalEntry)
        assertEquals(JournalFilter.WEIGHT.name, handle.get<String>(HistoryViewModel.FILTER))
    }

    @Test
    fun savedAllFilterIsRestoredInsteadOfNewDefault() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today, 81.4)
        val handle = SavedStateHandle(mapOf(HistoryViewModel.FILTER to JournalFilter.ALL.name))
        val viewModel = HistoryViewModel(repository, sessions(), dateProvider, handle)
        val restored = viewModel.uiState.first { !it.loading && it.filter == JournalFilter.ALL }
        assertEquals(JournalFilter.ALL, restored.filter)
        assertEquals(1, restored.timeline.entries.size)
        assertTrue(restored.timeline.entries.single() is WeightJournalEntry)
        assertEquals(JournalFilter.ALL.name, handle.get<String>(HistoryViewModel.FILTER))
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
