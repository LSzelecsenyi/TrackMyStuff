package hu.laca.weighttracker.ui.exercises

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.domain.exercise.ArchiveFilter
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ExerciseViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var repository: ExerciseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun listArchiveRestoreAndPermanentDeleteFlow() = runTest {
        val created = repository.save(draft("Húzódzkodás")) as ExerciseSaveResult.Created
        val viewModel = ExerciseListViewModel(repository)
        viewModel.uiState.first { !it.loading && it.visibleExercises.isNotEmpty() }
        viewModel.archive(created.id)
        viewModel.uiState.first { it.message == CatalogMessage.Archived && it.visibleExercises.isEmpty() }
        viewModel.onArchiveFilter(ArchiveFilter.ARCHIVED)
        val archived = viewModel.uiState.first { it.visibleExercises.size == 1 }
        assertEquals("Húzódzkodás", archived.visibleExercises.single().name)
        viewModel.restore(created.id)
        viewModel.onArchiveFilter(ArchiveFilter.ACTIVE)
        viewModel.uiState.first { it.visibleExercises.size == 1 }
        val stored = repository.getById(created.id)!!
        viewModel.requestDelete(stored)
        assertEquals(stored.id, viewModel.uiState.value.pendingDelete?.id)
        viewModel.confirmDelete()
        val deleted = viewModel.uiState.first {
            it.message == CatalogMessage.Deleted && it.visibleExercises.isEmpty()
        }
        assertTrue(deleted.visibleExercises.isEmpty())
        assertNull(deleted.pendingDelete)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun editorRejectsDuplicateNameAgainstArchivedExercise() = runTest {
        repository.save(draft("Húzódzkodás"))
        val id = repository.observeAll().first().single().id
        repository.archive(id)
        val viewModel = ExerciseEditorViewModel(SavedStateHandle(), repository)
        viewModel.onNameChange(" húzódzkodás ")
        viewModel.onCategoryChange(ExerciseCategory.STRENGTH)
        viewModel.onMovementChange(MovementPattern.VERTICAL_PULL)
        viewModel.onMeasurementChange(MeasurementType.REPETITIONS)
        viewModel.onResistanceChange(ResistanceBasis.BODYWEIGHT)
        viewModel.onPrimaryMuscleChange(MuscleGroup.LATS)
        viewModel.save()
        val state = viewModel.uiState.first { it.duplicateName }
        assertTrue(state.duplicateName)
        assertTrue(!state.finished)
    }

    private fun draft(name: String): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.VERTICAL_PULL,
            measurementType = MeasurementType.REPETITIONS,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.LATS
        )
    }
}
