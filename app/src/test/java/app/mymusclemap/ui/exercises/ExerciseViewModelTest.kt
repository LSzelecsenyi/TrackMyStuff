package app.mymusclemap.ui.exercises

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseFieldError
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
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
        val pending = viewModel.uiState.first { it.pendingDelete != null || it.pendingBlocked != null }
        assertEquals(stored.id, pending.pendingDelete?.id)
        viewModel.confirmDelete()
        val deleted = viewModel.uiState.first {
            it.message == CatalogMessage.Deleted && it.visibleExercises.isEmpty()
        }
        assertTrue(deleted.visibleExercises.isEmpty())
        assertNull(deleted.pendingDelete)
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun givenUnsortedRepositoryWhenCatalogAppearsThenNamesFollowHungarianAbc() = runTest {
        repository.save(draft("Zárógyakorlat"))
        repository.save(draft("Álló evezés"))
        repository.save(draft("Alma"))
        val viewModel = ExerciseListViewModel(repository)
        val state = viewModel.uiState.first { !it.loading && it.visibleExercises.size == 3 }
        assertEquals(
            listOf("Álló evezés", "Alma", "Zárógyakorlat"),
            state.visibleExercises.map { it.name }
        )
    }

    @Test
    fun givenRenamedExerciseWhenReturningToListThenItMovesToNewAbcPlace() = runTest {
        val first = repository.save(draft("Alma")) as ExerciseSaveResult.Created
        repository.save(draft("Béka"))
        val viewModel = ExerciseListViewModel(repository)
        viewModel.uiState.first { it.visibleExercises.map { exercise -> exercise.name } == listOf("Alma", "Béka") }
        val stored = repository.getById(first.id)!!
        repository.save(
            ExerciseDraft(
                id = stored.id,
                name = "őszibarack",
                category = stored.category,
                movementPattern = stored.movementPattern,
                measurementType = stored.measurementType,
                resistanceBasis = stored.resistanceBasis,
                weightInterpretation = stored.weightInterpretation,
                primaryMuscle = stored.primaryMuscle,
                secondaryMuscles = stored.secondaryMuscles
            )
        )
        val updated = viewModel.uiState.first { it.visibleExercises.any { exercise -> exercise.name == "Őszibarack" } }
        assertEquals(listOf("Béka", "Őszibarack"), updated.visibleExercises.map { it.name })
    }

    @Test
    fun givenArchiveFilterWhenUserSwitchesThenVisibleRowsStayAbcOrdered() = runTest {
        val activeLate = repository.save(draft("Záró")) as ExerciseSaveResult.Created
        val archived = repository.save(draft("Alma")) as ExerciseSaveResult.Created
        repository.save(draft("Álló evezés"))
        repository.archive(archived.id)
        val viewModel = ExerciseListViewModel(repository)
        val active = viewModel.uiState.first { !it.loading && it.visibleExercises.size == 2 }
        assertEquals(listOf("Álló evezés", "Záró"), active.visibleExercises.map { it.name })
        viewModel.onArchiveFilter(ArchiveFilter.ARCHIVED)
        val archivedState = viewModel.uiState.first { it.archiveFilter == ArchiveFilter.ARCHIVED && it.visibleExercises.size == 1 }
        assertEquals(listOf("Alma"), archivedState.visibleExercises.map { it.name })
        viewModel.onArchiveFilter(ArchiveFilter.ALL)
        val all = viewModel.uiState.first { it.archiveFilter == ArchiveFilter.ALL && it.visibleExercises.size == 3 }
        assertEquals(listOf("Álló evezés", "Alma", "Záró"), all.visibleExercises.map { it.name })
        viewModel.archive(activeLate.id)
        val afterArchive = viewModel.uiState.first {
            it.archiveFilter == ArchiveFilter.ALL &&
                it.visibleExercises.any { exercise -> exercise.id == activeLate.id && exercise.archived }
        }
        assertEquals(listOf("Álló evezés", "Alma", "Záró"), afterArchive.visibleExercises.map { it.name })
        viewModel.onArchiveFilter(ArchiveFilter.ACTIVE)
        val remainingActive = viewModel.uiState.first {
            it.archiveFilter == ArchiveFilter.ACTIVE && it.visibleExercises.size == 1
        }
        assertEquals(listOf("Álló evezés"), remainingActive.visibleExercises.map { it.name })
        viewModel.onQueryChange("nincsilyen")
        val miss = viewModel.uiState.first { it.emptyKind == CatalogEmptyKind.Search }
        assertEquals("nincsilyen", miss.query)
        assertTrue(miss.visibleExercises.isEmpty())
    }

    @Test
    fun editorRejectsDuplicateNameAgainstArchivedExercise() = runTest {
        repository.save(draft("Húzódzkodás"))
        val id = repository.observeAll().first().single().id
        repository.archive(id)
        val viewModel = editorViewModel()
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
        assertEquals(" Húzódzkodás ", viewModel.uiState.value.draft.name)
    }

    @Test
    fun editorCapitalizesFirstLetterOnly() {
        val viewModel = editorViewModel()
        viewModel.onNameChange("tolódzkodás")
        assertEquals("Tolódzkodás", viewModel.uiState.value.draft.name)
        viewModel.onNameChange("őrségi fekvőtámasz")
        assertEquals("Őrségi fekvőtámasz", viewModel.uiState.value.draft.name)
        viewModel.onNameChange("hammer curl")
        assertEquals("Hammer curl", viewModel.uiState.value.draft.name)
        viewModel.onNameChange("Biceps curl")
        assertEquals("Biceps curl", viewModel.uiState.value.draft.name)
    }

    @Test
    fun secondaryMusclesAreSortedByHungarianLabelAndPrimaryIsRemoved() = runTest {
        val viewModel = editorViewModel()
        viewModel.onNameChange("Evezés")
        viewModel.onPrimaryMuscleChange(MuscleGroup.LATS)
        viewModel.onToggleSecondary(MuscleGroup.TRICEPS)
        viewModel.onToggleSecondary(MuscleGroup.FOREARMS)
        viewModel.onToggleSecondary(MuscleGroup.BICEPS)
        assertEquals(
            listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS, MuscleGroup.TRICEPS),
            viewModel.uiState.value.draft.secondaryMuscles
        )
        viewModel.onPrimaryMuscleChange(MuscleGroup.BICEPS)
        assertEquals(MuscleGroup.BICEPS, viewModel.uiState.value.draft.primaryMuscle)
        assertFalse(MuscleGroup.BICEPS in viewModel.uiState.value.draft.secondaryMuscles)
        assertEquals(
            listOf(MuscleGroup.FOREARMS, MuscleGroup.TRICEPS),
            viewModel.uiState.value.draft.secondaryMuscles
        )
        viewModel.save()
        val created = viewModel.uiState.first { it.finished && it.saved }
        assertTrue(created.created)
        val stored = repository.observeAll().first().single()
        assertEquals("Evezés", stored.name)
        assertEquals(
            listOf(MuscleGroup.FOREARMS, MuscleGroup.TRICEPS),
            stored.secondaryMuscles
        )
    }

    @Test
    fun saveShowsFieldErrorAndIgnoresASecondSubmitAfterSuccess() = runTest {
        val viewModel = editorViewModel()
        viewModel.save()
        assertTrue(ExerciseFieldError.NameBlank in viewModel.uiState.value.fieldErrors)
        assertFalse(viewModel.uiState.value.finished)
        viewModel.onNameChange("Tolódzkodás")
        viewModel.onMeasurementChange(MeasurementType.REPETITIONS)
        viewModel.onResistanceChange(ResistanceBasis.BODYWEIGHT)
        viewModel.onPrimaryMuscleChange(MuscleGroup.LATS)
        viewModel.save()
        viewModel.uiState.first { it.finished && it.saved }
        viewModel.save()
        assertEquals(1, repository.observeAll().first().size)
        assertTrue(viewModel.uiState.value.finished)
        assertFalse(viewModel.uiState.value.saving)
    }

    private fun editorViewModel(
        handle: SavedStateHandle = SavedStateHandle()
    ): ExerciseEditorViewModel {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return ExerciseEditorViewModel(handle, repository) { group ->
            context.getString(group.labelRes())
        }
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
