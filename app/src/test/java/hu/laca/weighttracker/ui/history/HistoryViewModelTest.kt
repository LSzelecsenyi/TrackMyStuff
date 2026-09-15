package hu.laca.weighttracker.ui.history

import hu.laca.weighttracker.FakeWeightMeasurementDao
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun journalKeepsMeasurementHistoryStateAndActions() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        repository.save(today.minusDays(1), 81.0)
        repository.save(today, 81.4)
        val viewModel = HistoryViewModel(repository, dateProvider)
        val loaded = viewModel.uiState.first { !it.isEmpty }
        assertEquals(2, loaded.items.size)
        assertEquals(0.4, loaded.items.first().differenceFromPreviousKg!!, 0.001)

        viewModel.openEditor(today)
        assertNotNull(viewModel.uiState.value.editor)
        viewModel.dismissEditor()
        assertEquals(null, viewModel.uiState.value.editor)

        viewModel.openDelete(today.minusDays(1))
        assertTrue(viewModel.uiState.value.editor?.showDeleteConfirm == true)
        viewModel.confirmDelete()
        val remaining = viewModel.uiState.first { it.items.size == 1 }
        assertFalse(remaining.isEmpty)
        assertEquals(today, remaining.items.single().measurement.date)
    }
}
