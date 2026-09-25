package app.mymusclemap.ui.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.MutableDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.ScheduleWorkoutResult
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
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
        assertEquals(listOf("Álló evezés", "Alma", "Záró"), state.templates.map { it.template.name })
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

    @Test
    fun givenTodaySchedulesWhenPickerOpensThenTheySitAboveRemainingTemplates() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        saveTemplate("Záró", exerciseId)
        val scheduleId = (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        val viewModel = hubViewModel()
        viewModel.onPrimaryWorkoutAction()
        val state = viewModel.uiState.first { it.pickerVisible && it.todayPlanned.size == 1 }
        assertEquals(listOf(scheduleId), state.todayPlanned.map { it.id })
        assertEquals(listOf("Push A"), state.todayPlanned.map { it.templateName })
        assertEquals(listOf("Záró"), state.templates.map { it.template.name })
        assertTrue(state.todayInProgress.isEmpty())
    }

    @Test
    fun givenTodayPlannedWhenStartedFromHubThenScheduledWorkoutIdIsPassed() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val scheduleId = (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        val viewModel = hubViewModel()
        val planned = viewModel.uiState.first { it.todayPlanned.size == 1 }.todayPlanned.single()
        viewModel.onPrimaryWorkoutAction()
        viewModel.startScheduled(planned)
        viewModel.startScheduled(planned)
        val started = viewModel.uiState.first { it.startedSessionId != null }
        val stored = sessions().getAggregate(started.startedSessionId!!)!!
        assertEquals(scheduleId, stored.session.scheduledWorkoutId)
        assertEquals(started.startedSessionId, sessions().observeInProgress().first()?.session?.id)
    }

    @Test
    fun givenNoTodaySchedulesWhenPickerOpensThenTodaySectionStaysEmpty() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        saveTemplate("Push A", exerciseId)
        val viewModel = hubViewModel()
        viewModel.onPrimaryWorkoutAction()
        val state = viewModel.uiState.first { it.pickerVisible }
        assertTrue(state.todayPlanned.isEmpty())
        assertTrue(state.todayInProgress.isEmpty())
        assertEquals(listOf("Push A"), state.templates.map { it.template.name })
    }

    @Test
    fun givenTodayInProgressWhenContinuedThenActiveRouteIdIsEmitted() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val scheduleId = (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessions().start(push, scheduleId) as StartWorkoutResult.Started
        val viewModel = hubViewModel()
        val inProgress = viewModel.uiState.first { it.todayInProgress.size == 1 }.todayInProgress.single()
        viewModel.continueScheduled(inProgress)
        assertEquals(started.sessionId, viewModel.uiState.first { it.startedSessionId != null }.startedSessionId)
    }

    @Test
    fun givenTodayScheduleWhenLocalDatePassesMidnightThenHubDropsItFromTodaySection() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val dates = MutableDateProvider(today, java.time.LocalTime.of(23, 50))
        (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled)
        val viewModel = hubViewModel(dateProvider = dates)
        viewModel.onPrimaryWorkoutAction()
        viewModel.uiState.first { it.pickerVisible && it.todayPlanned.size == 1 }
        dates.setNow(today.plusDays(1), java.time.LocalTime.of(0, 5))
        val afterMidnight = viewModel.uiState.first { it.todayPlanned.isEmpty() && !it.loading }
        assertTrue(afterMidnight.todayPlanned.isEmpty())
        assertTrue(afterMidnight.todayInProgress.isEmpty())
        assertEquals(listOf("Push A"), afterMidnight.templates.map { it.template.name })
    }

    @Test
    fun givenActiveScheduledSessionWhenDayChangesThenResumeStillWorks() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val dates = MutableDateProvider(today)
        val scheduleId = (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        val sessionRepository = sessions(dates)
        val started = sessionRepository.start(push, scheduleId) as StartWorkoutResult.Started
        val viewModel = hubViewModel(sessionRepository, dates)
        viewModel.uiState.first { it.activeSession != null && it.todayInProgress.size == 1 }
        dates.setDate(today.plusDays(1))
        val afterMidnight = viewModel.uiState.first {
            it.activeSession != null && it.todayInProgress.isEmpty()
        }
        assertEquals(started.sessionId, afterMidnight.activeSession!!.session.id)
        assertEquals(WorkoutPrimaryAction.Resume(started.sessionId), viewModel.onPrimaryWorkoutAction())
    }

    @Test
    fun givenExistingScheduleWhenNewHubViewModelIsCreatedThenTodayPlannedIsRestored() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val scheduleId = (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        val first = hubViewModel()
        assertEquals(listOf(scheduleId), first.uiState.first { it.todayPlanned.size == 1 }.todayPlanned.map { it.id })
        val second = hubViewModel()
        assertEquals(listOf(scheduleId), second.uiState.first { it.todayPlanned.size == 1 }.todayPlanned.map { it.id })
        assertNull(second.uiState.value.startedSessionId)
        assertFalse(second.uiState.value.pickerVisible)
    }

    @Test
    fun givenStalePlannedItemWhenDayChangedThenStartIsIgnored() = runTest {
        val exerciseId = saveExercise("Húzódzkodás")
        val push = saveTemplate("Push A", exerciseId)
        val dates = MutableDateProvider(today)
        (scheduled().schedule(push, today) as ScheduleWorkoutResult.Scheduled)
        val sessionRepository = sessions(dates)
        val viewModel = hubViewModel(sessionRepository, dates)
        viewModel.onPrimaryWorkoutAction()
        val planned = viewModel.uiState.first { it.pickerVisible && it.todayPlanned.size == 1 }.todayPlanned.single()
        dates.setDate(today.plusDays(1))
        viewModel.uiState.first { it.todayPlanned.isEmpty() }
        viewModel.startScheduled(planned)
        assertNull(viewModel.uiState.value.startedSessionId)
        assertNull(sessionRepository.observeInProgress().first())
    }

    private fun hubViewModel(
        sessionRepository: WorkoutSessionRepository = sessions(),
        dateProvider: app.mymusclemap.domain.DateProvider = FixedDateProvider(today)
    ): WorkoutHubViewModel {
        return WorkoutHubViewModel(
            repository,
            templates,
            sessionRepository,
            ScheduledWorkoutRepository(
                database.scheduledWorkoutDao(),
                database.workoutTemplateDao(),
                database.workoutSessionDao(),
                Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
            ),
            dateProvider
        )
    }

    private fun scheduled(): ScheduledWorkoutRepository {
        return ScheduledWorkoutRepository(
            database.scheduledWorkoutDao(),
            database.workoutTemplateDao(),
            database.workoutSessionDao(),
            Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        )
    }

    private fun sessions(
        dateProvider: app.mymusclemap.domain.DateProvider = FixedDateProvider(today)
    ): WorkoutSessionRepository {
        val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        return WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weights,
            clock = clock,
            dateProvider = dateProvider
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
