package hu.laca.weighttracker.ui.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WeightRepository
import hu.laca.weighttracker.data.repository.WorkoutSessionRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.FixedDateProvider
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutHubViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        repository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock
        )
        templates = WorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            clock = clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyCatalogProducesEmptyHubState() = runTest {
        val viewModel = WorkoutHubViewModel(
            repository,
            templates,
            sessions(database),
            FixedDateProvider(LocalDate.parse("2026-09-15"))
        )
        val state = viewModel.uiState.first { !it.loading }
        assertTrue(state.isEmpty)
        assertEquals(0, state.activeCount)
        assertEquals(0, state.archivedCount)
    }

    @Test
    fun hubShowsActiveAndArchivedCountsWithoutListingExercises() = runTest {
        repository.save(draft("Plank"))
        repository.save(draft("Kerékpározás"))
        val id = repository.observeAll().first().first { it.name == "Kerékpározás" }.id
        repository.archive(id)
        val viewModel = WorkoutHubViewModel(
            repository,
            templates,
            sessions(database),
            FixedDateProvider(LocalDate.parse("2026-09-15"))
        )
        val state = viewModel.uiState.first { !it.loading && it.activeCount == 1 }
        assertFalse(state.isEmpty)
        assertEquals(1, state.activeCount)
        assertEquals(1, state.archivedCount)
        assertEquals(0, state.activeTemplateCount)
    }

    @Test
    fun hubReportsRealTemplateAndExerciseCounts() = runTest {
        repository.save(draft("Plank"))
        val viewModel = WorkoutHubViewModel(
            repository,
            templates,
            sessions(database),
            FixedDateProvider(LocalDate.parse("2026-09-15"))
        )
        val emptyTemplates = viewModel.uiState.first { !it.loading && it.activeCount == 1 }
        assertEquals(0, emptyTemplates.activeTemplateCount)
        assertEquals(1, emptyTemplates.activeCount)
    }

    private fun sessions(database: WeightDatabase): WorkoutSessionRepository {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        return WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = WeightRepository(database.weightMeasurementDao(), clock),
            clock = clock,
            dateProvider = FixedDateProvider(LocalDate.parse("2026-09-15"))
        )
    }

    private fun draft(name: String): ExerciseDraft {
        return ExerciseDraft(
            name = name,
            category = ExerciseCategory.STRENGTH,
            movementPattern = MovementPattern.CORE,
            measurementType = MeasurementType.DURATION,
            resistanceBasis = ResistanceBasis.BODYWEIGHT,
            weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
            primaryMuscle = MuscleGroup.ABS
        )
    }
}
