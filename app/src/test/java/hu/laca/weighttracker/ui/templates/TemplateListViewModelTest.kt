package hu.laca.weighttracker.ui.templates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.MainDispatcherRule
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.repository.ExerciseRepository
import hu.laca.weighttracker.data.repository.WorkoutTemplateRepository
import hu.laca.weighttracker.domain.exercise.ArchiveFilter
import hu.laca.weighttracker.domain.exercise.ExerciseCategory
import hu.laca.weighttracker.domain.exercise.ExerciseDraft
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class TemplateListViewModelTest {
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
        exercises = ExerciseRepository(database.exerciseDao(), clock)
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenUnsortedRepositoryWhenListAppearsThenNamesFollowHungarianAbc() = runTest {
        val exerciseId = saveExercise()
        templates.save(draft("Zárógyakorlat", exerciseId))
        templates.save(draft("Álló evezés", exerciseId))
        templates.save(draft("Alma", exerciseId))
        val viewModel = TemplateListViewModel(templates)
        val state = viewModel.uiState.first { !it.loading && it.visibleItems.size == 3 }
        assertEquals(
            listOf("Alma", "Álló evezés", "Zárógyakorlat"),
            state.visibleItems.map { it.template.name }
        )
    }

    @Test
    fun givenQueryWhenChangedThenListFiltersImmediatelyIgnoringCase() = runTest {
        val exerciseId = saveExercise()
        templates.save(draft("Húzódzkodásos push", exerciseId))
        templates.save(draft("Kerékpár", exerciseId))
        val viewModel = TemplateListViewModel(templates)
        viewModel.uiState.first { it.visibleItems.size == 2 }
        viewModel.onQueryChange("  HÚZÓ  ")
        val filtered = viewModel.uiState.first { it.query == "  HÚZÓ  " && it.visibleItems.size == 1 }
        assertEquals("Húzódzkodásos push", filtered.visibleItems.single().template.name)
        viewModel.onQueryChange("nincsilyen")
        val miss = viewModel.uiState.first { it.emptyKind == TemplateEmptyKind.Search }
        assertTrue(miss.visibleItems.isEmpty())
    }

    @Test
    fun givenRenamedTemplateWhenReturningToListThenItMovesToNewAbcPlace() = runTest {
        val exerciseId = saveExercise()
        val first = templates.save(draft("Alma", exerciseId)) as TemplateSaveResult.Created
        templates.save(draft("Béka", exerciseId))
        val viewModel = TemplateListViewModel(templates)
        viewModel.uiState.first { it.visibleItems.map { item -> item.template.name } == listOf("Alma", "Béka") }
        templates.save(draft("Őszibarack", exerciseId, id = first.id))
        val updated = viewModel.uiState.first { it.visibleItems.any { item -> item.template.name == "Őszibarack" } }
        assertEquals(listOf("Béka", "Őszibarack"), updated.visibleItems.map { it.template.name })
    }

    @Test
    fun givenArchiveFilterWhenUserSwitchesThenVisibleRowsStayAbcOrdered() = runTest {
        val exerciseId = saveExercise()
        templates.save(draft("Záró", exerciseId))
        val archived = templates.save(draft("Alma", exerciseId)) as TemplateSaveResult.Created
        templates.save(draft("Álló evezés", exerciseId))
        templates.archive(archived.id)
        val viewModel = TemplateListViewModel(templates)
        val active = viewModel.uiState.first { !it.loading && it.visibleItems.size == 2 }
        assertEquals(listOf("Álló evezés", "Záró"), active.visibleItems.map { it.template.name })
        viewModel.onArchiveFilter(ArchiveFilter.ARCHIVED)
        val archivedState = viewModel.uiState.first {
            it.archiveFilter == ArchiveFilter.ARCHIVED && it.visibleItems.size == 1
        }
        assertEquals(listOf("Alma"), archivedState.visibleItems.map { it.template.name })
        viewModel.onArchiveFilter(ArchiveFilter.ALL)
        val all = viewModel.uiState.first { it.archiveFilter == ArchiveFilter.ALL && it.visibleItems.size == 3 }
        assertEquals(listOf("Alma", "Álló evezés", "Záró"), all.visibleItems.map { it.template.name })
    }

    private suspend fun saveExercise(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun draft(name: String, exerciseId: Long, id: Long? = null): TemplateDraft {
        return TemplateDraft(
            id = id,
            name = name,
            exercises = listOf(
                TemplateExerciseDraft(
                    localId = -1L,
                    exerciseId = exerciseId,
                    sets = listOf(
                        PlannedSetDraft(
                            localId = -1L,
                            minRepsText = "8",
                            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                        )
                    )
                )
            )
        )
    }
}
