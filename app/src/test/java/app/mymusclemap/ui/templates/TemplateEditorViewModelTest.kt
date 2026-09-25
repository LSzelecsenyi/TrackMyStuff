package app.mymusclemap.ui.templates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.exercise.Exercise
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
class TemplateEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun successfulAddEmitsStableLocalIdOnce() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.scrollEvent != null && it.draft.exercises.isNotEmpty() }
        }
        val added = state.draft.exercises.single()
        assertEquals(added.localId, state.scrollEvent!!.exerciseLocalId)
        assertEquals(TemplateEditorPane.Form, state.pane)
        val generation = state.scrollEvent!!.generation
        viewModel.consumeScrollEvent()
        assertNull(viewModel.uiState.value.scrollEvent)
        viewModel.consumeScrollEvent()
        assertNull(viewModel.uiState.value.scrollEvent)
        assertEquals(generation, state.scrollEvent!!.generation)
    }

    @Test
    fun addingAnotherExerciseProducesANewTarget() = runTest {
        val pull = savePull()
        val dip = saveDip()
        val viewModel = editor()
        viewModel.selectExercise(catalogExercise(pull))
        val first = withTimeout(5_000) { viewModel.uiState.first { it.scrollEvent != null } }
        val firstId = first.scrollEvent!!.exerciseLocalId
        val firstGeneration = first.scrollEvent!!.generation
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalogExercise(dip))
        val second = withTimeout(5_000) {
            viewModel.uiState.first { it.scrollEvent != null && it.draft.exercises.size == 2 }
        }
        assertNotEquals(firstId, second.scrollEvent!!.exerciseLocalId)
        assertNotEquals(firstGeneration, second.scrollEvent!!.generation)
        assertEquals(second.draft.exercises.last().localId, second.scrollEvent!!.exerciseLocalId)
    }

    @Test
    fun rejectedDuplicateDoesNotEmitAFalseTarget() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        withTimeout(5_000) { viewModel.uiState.first { it.scrollEvent != null } }
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalog)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.pendingDuplicate != null } }
        assertNull(state.scrollEvent)
        assertEquals(1, state.draft.exercises.size)
    }

    @Test
    fun unrelatedEditDoesNotReplayConsumedScroll() = runTest {
        val pull = savePull()
        val viewModel = editor()
        viewModel.selectExercise(catalogExercise(pull))
        withTimeout(5_000) { viewModel.uiState.first { it.scrollEvent != null } }
        viewModel.consumeScrollEvent()
        viewModel.onNameChange("Push B")
        assertNull(viewModel.uiState.value.scrollEvent)
    }

    @Test
    fun confirmedDuplicateEmitsTheNewLocalId() = runTest {
        val pull = savePull()
        val viewModel = editor()
        val catalog = catalogExercise(pull)
        viewModel.selectExercise(catalog)
        withTimeout(5_000) { viewModel.uiState.first { it.scrollEvent != null } }
        viewModel.consumeScrollEvent()
        viewModel.selectExercise(catalog)
        withTimeout(5_000) { viewModel.uiState.first { it.pendingDuplicate != null } }
        viewModel.confirmDuplicate()
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.draft.exercises.size == 2 && it.scrollEvent != null }
        }
        assertEquals(state.draft.exercises.last().localId, state.scrollEvent!!.exerciseLocalId)
        assertTrue(state.draft.exercises.map { it.localId }.distinct().size == 2)
    }

    private fun editor(): TemplateEditorViewModel {
        return TemplateEditorViewModel(
            SavedStateHandle(),
            templates,
            exercises
        )
    }

    private suspend fun catalogExercise(id: Long): Exercise {
        return exercises.getById(id)!!
    }

    private suspend fun savePull(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun saveDip(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Tolódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created).id
    }
}
