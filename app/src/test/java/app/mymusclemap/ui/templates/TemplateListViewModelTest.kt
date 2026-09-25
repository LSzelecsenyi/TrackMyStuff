package app.mymusclemap.ui.templates

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
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
            listOf("Álló evezés", "Alma", "Zárógyakorlat"),
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
        assertEquals(listOf("Álló evezés", "Alma", "Záró"), all.visibleItems.map { it.template.name })
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
