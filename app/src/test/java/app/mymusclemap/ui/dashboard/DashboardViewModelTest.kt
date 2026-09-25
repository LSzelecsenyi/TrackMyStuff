package app.mymusclemap.ui.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.MutableDateProvider
import app.mymusclemap.domain.WeeklyOverview
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.model.ChartRange
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.ScheduleWorkoutResult
import app.mymusclemap.domain.workout.ScheduledWorkoutStatus
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.ui.components.UserMessage
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
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-03-11T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var scheduled: ScheduledWorkoutRepository
    private lateinit var sessionRepository: WorkoutSessionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = ExerciseRepository(database.exerciseDao(), clock)
        templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao(),
            database.scheduledWorkoutDao()
        )
        scheduled = ScheduledWorkoutRepository(
            database.scheduledWorkoutDao(),
            database.workoutTemplateDao(),
            database.workoutSessionDao(),
            clock
        )
        sessionRepository = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = WeightRepository(database.weightMeasurementDao(), clock),
            clock = clock,
            dateProvider = dateProvider
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun emptyRepositoryProducesEmptyDashboardState() = runTest {
        val viewModel = dashboard()
        val state = viewModel.uiState.first { !it.snapshot.isEmpty || it.today == today }
        assertTrue(state.snapshot.isEmpty)
        assertTrue(state.snapshot.chartPoints.isEmpty())
        assertTrue(state.snapshot.recentItems.isEmpty())
        assertEquals(WeeklyOverview(), state.weeklyOverview)
    }

    @Test
    fun savedMeasurementLeavesEmptyState() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = dashboard(repository)
        repository.save(today, 80.4)
        val state = viewModel.uiState.first { it.snapshot.todayHasMeasurement }
        assertFalse(state.snapshot.isEmpty)
        viewModel.onChartRangeSelected(ChartRange.All)
        assertFalse(viewModel.uiState.value.snapshot.chartPoints.isEmpty())
    }

    @Test
    fun futureDaySelectionOpensSheetWithoutWeightRecording() = runTest {
        val viewModel = dashboard()
        viewModel.selectDay(today.plusDays(1))
        val sheet = viewModel.uiState.first { it.daySheet != null }.daySheet
        assertEquals(today.plusDays(1), sheet?.date)
        assertFalse(sheet!!.canRecordWeight)
        viewModel.recordSelectedDay()
        assertNull(viewModel.uiState.value.editor)
    }

    @Test
    fun daySheetWithoutMeasurement() = runTest {
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.first { it.daySheet != null }.daySheet
        assertEquals(today, sheet?.date)
        assertFalse(sheet!!.hasMeasurement)
        assertFalse(sheet.hasWorkouts)
    }

    @Test
    fun daySheetWithMeasurement() = runTest {
        val repository = WeightRepository(FakeWeightMeasurementDao(), clock)
        val viewModel = dashboard(repository)
        repository.save(today.minusDays(3), 80.0)
        repository.save(today, 80.5)
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.first { it.daySheet?.hasMeasurement == true }.daySheet
        assertTrue(sheet!!.hasMeasurement)
        assertEquals(0.5, sheet.differenceFromPreviousKg!!, 0.0001)
    }

    @Test
    fun decemberNavigatesToJanuary() = runTest {
        val viewModel = dashboard()
        assertEquals(YearMonth.of(2026, 3), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2026, 3) }.displayedMonth)
        repeat(9) { viewModel.onNextMonth() }
        assertEquals(YearMonth.of(2026, 12), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2026, 12) }.displayedMonth)
        viewModel.onNextMonth()
        assertEquals(YearMonth.of(2027, 1), viewModel.uiState.first { it.displayedMonth == YearMonth.of(2027, 1) }.displayedMonth)
    }

    @Test
    fun givenSchedulesWhenDaySelectedThenTheyAppearInCreatedOrder() = runTest {
        val pull = saveTemplate("Pull A")
        val push = saveTemplate("Push A")
        scheduled.schedule(push, today)
        scheduled.schedule(pull, today)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val sheet = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 2 }.daySheet!!
        assertEquals(listOf("Push A", "Pull A"), sheet.scheduledWorkouts.map { it.templateName })
        val cell = viewModel.uiState.value.monthGrid.cells.first { it.date == today }
        assertTrue(cell.hasPlannedWorkout)
        assertEquals(2, cell.plannedWorkoutCount)
    }

    @Test
    fun givenActiveTemplateWhenScheduledThenItAppearsOnTheDay() = runTest {
        val templateId = saveTemplate("Push A")
        val viewModel = dashboard()
        viewModel.selectDay(today.plusDays(2))
        viewModel.uiState.first { it.daySheet?.date == today.plusDays(2) }
        viewModel.openSchedulePicker()
        viewModel.uiState.first { it.schedulePickerVisible }
        assertEquals(listOf("Push A"), viewModel.uiState.value.availableTemplates.map { it.template.name })
        viewModel.scheduleTemplate(templateId)
        val sheet = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }.daySheet!!
        assertFalse(viewModel.uiState.value.schedulePickerVisible)
        assertEquals("Push A", sheet.scheduledWorkouts.single().templateName)
        assertEquals(ScheduledWorkoutStatus.PLANNED, sheet.scheduledWorkouts.single().status)
    }

    @Test
    fun givenAlreadyScheduledTemplateWhenPickerOpensThenItIsHidden() = runTest {
        val push = saveTemplate("Push A")
        saveTemplate("Pull A")
        scheduled.schedule(push, today)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
        viewModel.openSchedulePicker()
        val available = viewModel.uiState.first { it.schedulePickerVisible }.availableTemplates
        assertEquals(listOf("Pull A"), available.map { it.template.name })
    }

    @Test
    fun givenArchivedTemplateWhenPickerOpensThenItIsHidden() = runTest {
        val archivedId = saveTemplate("Régi")
        saveTemplate("Aktív")
        templates.archive(archivedId)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        viewModel.uiState.first { it.daySheet != null }
        viewModel.openSchedulePicker()
        val available = viewModel.uiState.first { it.schedulePickerVisible }.availableTemplates
        assertEquals(listOf("Aktív"), available.map { it.template.name })
    }

    @Test
    fun givenRescheduleWhenMovedThenBothDaysUpdate() = runTest {
        val templateId = saveTemplate("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
            .daySheet!!.scheduledWorkouts.single()
        assertEquals(scheduleId, item.id)
        viewModel.openReschedule(item)
        viewModel.confirmReschedule(today.plusDays(3))
        viewModel.uiState.first { it.daySheet?.scheduledWorkouts.isNullOrEmpty() && it.rescheduleTarget == null }
        assertTrue(viewModel.uiState.value.monthGrid.cells.first { it.date == today.plusDays(3) }.hasPlannedWorkout)
        assertFalse(viewModel.uiState.value.monthGrid.cells.first { it.date == today }.hasPlannedWorkout)
        viewModel.selectDay(today.plusDays(3))
        val moved = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }.daySheet!!
        assertEquals(today.plusDays(3), moved.scheduledWorkouts.single().scheduledDate)
    }

    @Test
    fun givenConflictingRescheduleWhenSavedThenErrorStaysOnPicker() = runTest {
        val push = saveTemplate("Push A")
        val other = saveTemplate("Pull A")
        val first = (scheduled.schedule(push, today) as ScheduleWorkoutResult.Scheduled).id
        scheduled.schedule(other, today.plusDays(1))
        scheduled.schedule(push, today.plusDays(1))
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.any { it.id == first } == true }
            .daySheet!!.scheduledWorkouts.single { it.id == first }
        viewModel.openReschedule(item)
        viewModel.confirmReschedule(today.plusDays(1))
        val state = viewModel.uiState.first { it.scheduleActionError != null }
        assertEquals(UserMessage.ScheduleDuplicate, state.scheduleActionError)
        assertNotNull(state.rescheduleTarget)
        assertEquals(today, scheduled.getById(first)!!.scheduledDate)
    }

    @Test
    fun givenFreeScheduleWhenRemovedThenListAndCalendarRefresh() = runTest {
        val templateId = saveTemplate("Push A")
        scheduled.schedule(templateId, today)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
            .daySheet!!.scheduledWorkouts.single()
        viewModel.openRemove(item)
        viewModel.confirmRemove()
        val state = viewModel.uiState.first {
            it.userMessage == UserMessage.ScheduleRemoved &&
                it.daySheet?.scheduledWorkouts.isNullOrEmpty()
        }
        assertTrue(state.daySheet!!.scheduledWorkouts.isEmpty())
        assertFalse(state.monthGrid.cells.first { it.date == today }.hasPlannedWorkout)
    }

    @Test
    fun givenTodayPlannedWhenStartedThenScheduledWorkoutIdIsPassedOnce() = runTest {
        val templateId = saveTemplate("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val viewModel = dashboard()
        viewModel.selectDay(today)
        viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
        viewModel.startScheduled(scheduleId)
        viewModel.startScheduled(scheduleId)
        val started = viewModel.uiState.first { it.startedSessionId != null }
        val stored = sessionRepository.getAggregate(started.startedSessionId!!)!!
        assertEquals(scheduleId, stored.session.scheduledWorkoutId)
        assertEquals(started.startedSessionId, sessionRepository.observeInProgress().first()?.session?.id)
    }

    @Test
    fun givenFutureOrPastPlannedWhenStartRequestedThenNothingStarts() = runTest {
        val futureId = (scheduled.schedule(saveTemplate("Jövő"), today.plusDays(2)) as ScheduleWorkoutResult.Scheduled).id
        val pastId = (scheduled.schedule(saveTemplate("Múlt"), today.minusDays(2)) as ScheduleWorkoutResult.Scheduled).id
        val viewModel = dashboard()
        viewModel.selectDay(today.plusDays(2))
        viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.any { it.id == futureId } == true }
        viewModel.startScheduled(futureId)
        assertNull(viewModel.uiState.value.startedSessionId)
        viewModel.selectDay(today.minusDays(2))
        viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.any { it.id == pastId } == true }
        viewModel.startScheduled(pastId)
        assertNull(viewModel.uiState.value.startedSessionId)
        assertNull(sessionRepository.observeInProgress().first())
    }

    @Test
    fun givenInProgressScheduleWhenContinuedThenActiveSessionIdIsEmitted() = runTest {
        val templateId = saveTemplate("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessionRepository.start(templateId, scheduleId)
        assertTrue(started is app.mymusclemap.domain.workout.StartWorkoutResult.Started)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first {
            it.daySheet?.scheduledWorkouts?.singleOrNull()?.status == ScheduledWorkoutStatus.IN_PROGRESS
        }.daySheet!!.scheduledWorkouts.single()
        viewModel.continueScheduled(item.id)
        assertEquals(
            (started as app.mymusclemap.domain.workout.StartWorkoutResult.Started).sessionId,
            viewModel.uiState.first { it.startedSessionId != null }.startedSessionId
        )
    }

    @Test
    fun givenCompletedScheduleWhenOpenedThenJournalSessionIdIsEmitted() = runTest {
        val templateId = saveTemplate("Push A")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessionRepository.start(templateId, scheduleId) as app.mymusclemap.domain.workout.StartWorkoutResult.Started
        sessionRepository.finish(started.sessionId, skipRemaining = true)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first {
            it.daySheet?.scheduledWorkouts?.singleOrNull()?.status == ScheduledWorkoutStatus.COMPLETED
        }.daySheet!!.scheduledWorkouts.single()
        viewModel.openScheduledJournal(item.id)
        assertEquals(started.sessionId, viewModel.uiState.first { it.journalSessionId != null }.journalSessionId)
        viewModel.startScheduled(item.id)
        assertNull(viewModel.uiState.value.startedSessionId)
    }

    @Test
    fun givenArchivedTemplateScheduleWhenShownThenItCannotStart() = runTest {
        val templateId = saveTemplate("Régi")
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        templates.archive(templateId)
        val viewModel = dashboard()
        viewModel.selectDay(today)
        val item = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
            .daySheet!!.scheduledWorkouts.single()
        assertEquals(scheduleId, item.id)
        assertTrue(item.templateArchived)
        viewModel.startScheduled(item.id)
        assertNull(viewModel.uiState.value.startedSessionId)
        viewModel.openRemove(item)
        viewModel.confirmRemove()
        viewModel.uiState.first {
            it.userMessage == UserMessage.ScheduleRemoved &&
                it.daySheet?.scheduledWorkouts.isNullOrEmpty()
        }
        assertTrue(viewModel.uiState.value.daySheet!!.scheduledWorkouts.isEmpty())
    }

    @Test
    fun givenDoubleScheduleTapWhenBusyThenOnlyOneRowIsCreated() = runTest {
        val templateId = saveTemplate("Push A")
        val viewModel = dashboard()
        viewModel.selectDay(today)
        viewModel.uiState.first { it.daySheet != null }
        viewModel.scheduleTemplate(templateId)
        viewModel.scheduleTemplate(templateId)
        val sheet = viewModel.uiState.first { it.daySheet?.scheduledWorkouts?.isNotEmpty() == true }.daySheet!!
        assertEquals(1, sheet.scheduledWorkouts.size)
    }

    @Test
    fun givenPlannedTodayWhenDateProviderMovesPastMidnightThenCalendarTodayMoves() = runTest {
        val dates = MutableDateProvider(today, LocalTime.of(23, 50))
        scheduled.schedule(saveTemplate("Push A"), today)
        val viewModel = dashboard(dates = dates)
        val before = viewModel.uiState.first {
            it.monthGrid.cells.any { cell -> cell.date == today && cell.hasPlannedWorkout && cell.isToday }
        }
        assertEquals(today, before.today)
        dates.setNow(today.plusDays(1), LocalTime.of(0, 5))
        val after = viewModel.uiState.first { it.today == today.plusDays(1) }
        assertFalse(after.monthGrid.cells.first { it.date == today }.isToday)
        assertTrue(after.monthGrid.cells.first { it.date == today.plusDays(1) }.isToday)
        assertTrue(after.monthGrid.cells.first { it.date == today }.hasPlannedWorkout)
        assertNull(after.daySheet)
    }

    @Test
    fun givenSelectedDayWhenNewDashboardViewModelThenSheetIsNotRestoredButScheduleRemains() = runTest {
        scheduled.schedule(saveTemplate("Push A"), today)
        val first = dashboard()
        first.selectDay(today)
        first.uiState.first { it.daySheet?.scheduledWorkouts?.size == 1 }
        val second = dashboard()
        val restored = second.uiState.first {
            it.monthGrid.cells.first { cell -> cell.date == today }.hasPlannedWorkout
        }
        assertNull(restored.daySheet)
        assertEquals(today, restored.today)
        assertTrue(restored.monthGrid.cells.first { it.date == today }.hasPlannedWorkout)
    }

    private fun dashboard(
        weights: WeightRepository = WeightRepository(FakeWeightMeasurementDao(), clock),
        dates: app.mymusclemap.domain.DateProvider = dateProvider
    ): DashboardViewModel {
        return DashboardViewModel(
            repository = weights,
            sessionRepository = sessionRepository,
            dateProvider = dates,
            scheduledWorkoutRepository = scheduled,
            templateRepository = templates
        )
    }

    private suspend fun saveTemplate(name: String): Long {
        val exerciseId = (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás $name",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS
            )
        ) as ExerciseSaveResult.Created).id
        return (templates.save(
            TemplateDraft(
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
        ) as TemplateSaveResult.Created).id
    }
}
