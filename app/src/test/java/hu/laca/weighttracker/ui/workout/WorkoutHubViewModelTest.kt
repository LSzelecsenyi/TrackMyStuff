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
import hu.laca.weighttracker.domain.exercise.ExerciseSaveResult
import hu.laca.weighttracker.domain.exercise.MeasurementType
import hu.laca.weighttracker.domain.exercise.MovementPattern
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.exercise.ResistanceBasis
import hu.laca.weighttracker.domain.exercise.WeightInterpretation
import hu.laca.weighttracker.domain.workout.BodyWeightSource
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.PlannedSetDraft
import hu.laca.weighttracker.domain.workout.StartWorkoutResult
import hu.laca.weighttracker.domain.workout.TemplateDraft
import hu.laca.weighttracker.domain.workout.TemplateExerciseDraft
import hu.laca.weighttracker.domain.workout.TemplateSaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutHubViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: WeightDatabase
    private lateinit var repository: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var weights: WeightRepository
    private val today = LocalDate.parse("2026-09-15")

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
        weights = WeightRepository(database.weightMeasurementDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyCatalogProducesEmptyHubState() = runTest {
        val viewModel = hubViewModel()
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
        val viewModel = hubViewModel()
        val state = viewModel.uiState.first { !it.loading && it.activeCount == 1 }
        assertFalse(state.isEmpty)
        assertEquals(1, state.activeCount)
        assertEquals(1, state.archivedCount)
        assertEquals(0, state.activeTemplateCount)
    }

    @Test
    fun hubReportsRealTemplateAndExerciseCounts() = runTest {
        repository.save(draft("Plank"))
        val viewModel = hubViewModel()
        val emptyTemplates = viewModel.uiState.first { !it.loading && it.activeCount == 1 }
        assertEquals(0, emptyTemplates.activeTemplateCount)
        assertEquals(1, emptyTemplates.activeCount)
    }

    @Test
    fun givenNoActiveSessionWhenHubOpensThenActiveTemplatesAreListed() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        saveTemplate("Záró", exerciseId)
        val viewModel = hubViewModel()
        val state = viewModel.uiState.first { !it.loading && it.templates.size == 2 }
        assertNull(state.activeSession)
        assertEquals(listOf("Push – Kondipark", "Záró"), state.templates.map { it.template.name })
        assertTrue(state.templates.all { it.exerciseCount == 1 && it.setCount == 2 })
    }

    @Test
    fun givenActiveSessionWhenHubOpensThenInProgressSummaryIsPresent() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val templateId = saveTemplate("Push – Kondipark", exerciseId)
        val sessionRepository = sessions()
        val started = sessionRepository.start(templateId)
        assertTrue(started is StartWorkoutResult.Started)
        val viewModel = hubViewModel(sessionRepository)
        val state = viewModel.uiState.first { it.activeSession != null }
        assertEquals("Push – Kondipark", state.activeSession!!.session.templateName)
        assertEquals(0, state.activeSession!!.completedSets)
        assertEquals(2, state.activeSession!!.totalSets)
    }

    @Test
    fun givenSameDayWeightWhenTemplateChosenThenSessionUsesMeasuredSameDayWithoutWeightPrompt() = runTest {
        weights.save(today, 82.4)
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        val viewModel = hubViewModel()
        val item = viewModel.uiState.first { it.templates.size == 1 }.templates.single()
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.chooseTemplate(item)
        val started = viewModel.uiState.first { it.startedSessionId != null }
        assertNotNull(started.startedSessionId)
        assertFalse(started.pickerVisible)
        assertFalse(started.isStarting)
        val stored = sessions().getAggregate(started.startedSessionId!!)!!
        assertEquals(82.4, stored.session.bodyWeightKg!!, 0.0)
        assertEquals(BodyWeightSource.MEASURED_SAME_DAY, stored.session.bodyWeightSource)
    }

    @Test
    fun givenStartAlreadyInProgressWhenChosenAgainThenOnlyOneSessionIsCreated() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        val viewModel = hubViewModel()
        val item = viewModel.uiState.first { it.templates.size == 1 }.templates.single()
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.chooseTemplate(item)
        viewModel.chooseTemplate(item)
        val started = viewModel.uiState.first { it.startedSessionId != null }
        assertNotNull(started.startedSessionId)
        assertFalse(started.isStarting)
        val inProgress = sessions().observeInProgress().first()
        assertEquals(started.startedSessionId, inProgress?.session?.id)
    }

    @Test
    fun givenUserDismissesPickerWhenNoTemplateChosenThenNothingIsWritten() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        val viewModel = hubViewModel()
        viewModel.uiState.first { it.templates.size == 1 }
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.dismissPicker()
        val state = viewModel.uiState.first { !it.pickerVisible && !it.loading }
        assertFalse(state.pickerVisible)
        assertNull(state.startedSessionId)
        assertNull(sessions().observeInProgress().first())
    }

    @Test
    fun givenTwoStartRequestsWhenFirstStartIsRunningThenSecondIsIgnored() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        saveTemplate("Pull", exerciseId)
        val viewModel = hubViewModel()
        val items = viewModel.uiState.first { it.templates.size == 2 }.templates
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.chooseTemplate(items[0])
        viewModel.chooseTemplate(items[1])
        val started = viewModel.uiState.first { it.startedSessionId != null }
        val stored = sessions().getAggregate(started.startedSessionId!!)!!
        assertEquals(items[0].template.id, stored.session.templateId)
        assertEquals(1, listOfNotNull(sessions().observeInProgress().first()).size)
    }

    @Test
    fun givenNoActiveSessionWhenPrimaryActionThenPickerOpensWithHungarianAbc() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Záró", exerciseId)
        saveTemplate("Álló evezés", exerciseId)
        saveTemplate("Alma", exerciseId)
        val viewModel = hubViewModel()
        viewModel.uiState.first { !it.loading && it.templates.size == 3 }
        val first = viewModel.onPrimaryWorkoutAction()
        val second = viewModel.onPrimaryWorkoutAction()
        assertEquals(WorkoutPrimaryAction.ShowPicker, first)
        assertEquals(WorkoutPrimaryAction.Ignored, second)
        val state = viewModel.uiState.first { it.pickerVisible }
        assertEquals(listOf("Alma", "Álló evezés", "Záró"), state.templates.map { it.template.name })
        assertNull(state.activeSession)
        assertNull(state.startedSessionId)
    }

    @Test
    fun givenPickerWhenTemplateChosenThenStartRunsOnceWithoutWeightDialog() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push – Kondipark", exerciseId)
        val viewModel = hubViewModel()
        val item = viewModel.uiState.first { it.templates.size == 1 }.templates.single()
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.chooseTemplate(item)
        viewModel.chooseTemplate(item)
        val started = viewModel.uiState.first { it.startedSessionId != null }
        assertFalse(started.pickerVisible)
        assertEquals(item.template.id, sessions().getAggregate(started.startedSessionId!!)!!.session.templateId)
        assertEquals(started.startedSessionId, sessions().observeInProgress().first()?.session?.id)
    }

    @Test
    fun givenEmptyTemplateWhenChosenThenExistingErrorIsShownAndPickerStaysRecoverable() = runTest {
        templates.save(TemplateDraft(name = "Üres"))
        val viewModel = hubViewModel()
        val item = viewModel.uiState.first { it.templates.size == 1 }.templates.single()
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        viewModel.chooseTemplate(item)
        val failed = viewModel.uiState.first { it.message == WorkoutHubMessage.TemplateEmpty }
        assertEquals(WorkoutHubMessage.TemplateEmpty, failed.message)
        assertTrue(failed.pickerVisible)
        assertNull(failed.startedSessionId)
        assertNull(sessions().observeInProgress().first())
        assertEquals(WorkoutPrimaryAction.Ignored, viewModel.onPrimaryWorkoutAction())
        viewModel.dismissPicker()
        viewModel.uiState.first { !it.pickerVisible }
        assertEquals(WorkoutPrimaryAction.ShowPicker, viewModel.onPrimaryWorkoutAction())
    }

    @Test
    fun givenActiveSessionWhenPrimaryActionThenResumeWithoutPicker() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val templateId = saveTemplate("Push – Kondipark", exerciseId)
        val sessionRepository = sessions()
        val started = sessionRepository.start(templateId)
        assertTrue(started is StartWorkoutResult.Started)
        val viewModel = hubViewModel(sessionRepository)
        val state = viewModel.uiState.first { it.activeSession != null }
        val first = viewModel.onPrimaryWorkoutAction()
        val second = viewModel.onPrimaryWorkoutAction()
        assertEquals(WorkoutPrimaryAction.Resume(state.activeSession!!.session.id), first)
        assertEquals(WorkoutPrimaryAction.Resume(state.activeSession!!.session.id), second)
        assertFalse(viewModel.uiState.value.pickerVisible)
    }

    @Test
    fun givenNoTemplatesWhenPickerOpensThenEmptyListIsEmitted() = runTest {
        val viewModel = hubViewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onPrimaryWorkoutAction()
        val state = viewModel.uiState.first { it.pickerVisible }
        assertTrue(state.templates.isEmpty())
        assertEquals(0, state.activeTemplateCount)
    }

    @Test
    fun givenNewRepositoryEmissionWhenSessionAppearsThenPickerCloses() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val templateId = saveTemplate("Push – Kondipark", exerciseId)
        val viewModel = hubViewModel()
        viewModel.uiState.first { it.templates.size == 1 }
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible }
        val started = sessions().start(templateId)
        assertTrue(started is StartWorkoutResult.Started)
        val state = viewModel.uiState.first { it.activeSession != null }
        assertFalse(state.pickerVisible)
        assertEquals(WorkoutPrimaryAction.Resume(state.activeSession!!.session.id), viewModel.onPrimaryWorkoutAction())
    }

    private fun hubViewModel(
        sessionRepository: WorkoutSessionRepository = sessions()
    ): WorkoutHubViewModel {
        return WorkoutHubViewModel(
            repository,
            templates,
            sessionRepository
        )
    }

    private fun sessions(): WorkoutSessionRepository {
        val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        return WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weights,
            clock = clock,
            dateProvider = FixedDateProvider(today)
        )
    }

    private suspend fun saveExercise(name: String): Long {
        return (repository.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun saveTemplate(name: String, exerciseId: Long): Long {
        val draft = TemplateDraft(
            name = name,
            exercises = listOf(
                TemplateExerciseDraft(
                    localId = -1L,
                    exerciseId = exerciseId,
                    sets = List(2) { index ->
                        PlannedSetDraft(
                            localId = -(index + 1L),
                            minRepsText = "8",
                            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
                        )
                    }
                )
            )
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
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
