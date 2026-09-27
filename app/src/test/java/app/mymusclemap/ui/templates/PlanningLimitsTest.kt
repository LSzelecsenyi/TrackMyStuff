package app.mymusclemap.ui.templates

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.reports.ReportInputs
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.reports.ReportLogic
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.ScheduleWorkoutResult
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import app.mymusclemap.ui.dashboard.DashboardViewModel
import app.mymusclemap.ui.statistics.StatisticsViewModel
import app.mymusclemap.ui.workout.WorkoutHubViewModel
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class PlanningLimitsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 11)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val free = SelectiveFeatureEntitlements(emptySet())
    private val unlimitedPlans = SelectiveFeatureEntitlements(setOf(AppFeature.UnlimitedWorkoutPlans))

    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var scheduled: ScheduledWorkoutRepository
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = Clock.fixed(today.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
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
        sessions = WorkoutSessionRepository(
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
    fun freeCreatesThreePlansAndTheFourthOpensThePlanLimit() = runTest {
        val exerciseId = saveExercise()
        val viewModel = TemplateListViewModel(templates, free)
        var created = 0
        repeat(3) { index ->
            viewModel.uiState.first { !it.loading && it.planCount == index && it.canCreatePlan }
            viewModel.requestCreate { created += 1 }
            templates.save(draft("Plan ${index + 1}", exerciseId))
        }
        assertEquals(3, created)
        val blocked = viewModel.uiState.first { it.planCount == 3 && !it.canCreatePlan }
        assertEquals(listOf("Plan 1", "Plan 2", "Plan 3"), blocked.visibleItems.map { it.template.name })
        viewModel.requestCreate { created += 1 }
        assertEquals(3, created)
        assertEquals(AppFeature.UnlimitedWorkoutPlans, viewModel.uiState.value.lockedFeature)
        assertEquals(3, templates.planCount())
    }

    @Test
    fun proCanCreateMoreThanThreePlans() = runTest {
        val exerciseId = saveExercise()
        repeat(3) { index -> templates.save(draft("Plan ${index + 1}", exerciseId)) }
        val viewModel = TemplateListViewModel(templates, unlimitedPlans)
        viewModel.uiState.first { it.planCount == 3 && it.canCreatePlan }
        var allowed = false
        viewModel.requestCreate { allowed = true }
        assertTrue(allowed)
        assertNull(viewModel.uiState.value.lockedFeature)
        templates.save(draft("Plan 4", exerciseId))
        val state = viewModel.uiState.first { it.planCount == 4 }
        assertEquals(4, state.visibleItems.size)
        assertTrue(state.canCreatePlan)
    }

    @Test
    fun formerProKeepsEveryPlanAndCanEditStartAndDeleteThem() = runTest {
        val exerciseId = saveExercise()
        val ids = (1..4).map { index ->
            (templates.save(draft("Plan $index", exerciseId)) as TemplateSaveResult.Created).id
        }
        val viewModel = TemplateListViewModel(templates, free)
        val retained = viewModel.uiState.first { it.planCount == 4 && it.visibleItems.size == 4 }
        assertFalse(retained.canCreatePlan)
        assertEquals(ids.toSet(), retained.visibleItems.map { it.template.id }.toSet())
        var allowed = false
        viewModel.requestCreate { allowed = true }
        assertFalse(allowed)

        val editor = TemplateEditorViewModel(
            SavedStateHandle(mapOf(TemplateEditorViewModel.TEMPLATE_ID_KEY to ids[3])),
            templates,
            exercises,
            free
        )
        editor.uiState.first { !it.loading && it.draft.id == ids[3] }
        editor.onNameChange("Plan 4 edited")
        editor.save()
        assertTrue(editor.uiState.first { it.saved }.finished)
        assertNull(editor.uiState.value.lockedFeature)
        assertEquals(4, templates.planCount())
        assertEquals("Plan 4 edited", templates.getAggregate(ids[3])!!.template.name)

        val hub = WorkoutHubViewModel(
            exercises,
            templates,
            sessions,
            scheduled,
            dateProvider,
            free
        )
        hub.uiState.first { !it.loading && it.activeTemplateCount == 4 }
        hub.onPrimaryWorkoutAction()
        hub.uiState.first { it.pickerVisible }
        hub.chooseTemplate(hub.uiState.value.templates.first { it.template.id == ids[0] })
        assertNotNull(hub.uiState.first { it.startedSessionId != null }.startedSessionId)
        assertNull(hub.uiState.value.lockedFeature)

        viewModel.onArchiveFilter(ArchiveFilter.ALL)
        val fourth = viewModel.uiState.first {
            it.visibleItems.any { item -> item.template.id == ids[3] }
        }.visibleItems.first { it.template.id == ids[3] }
        viewModel.requestDelete(fourth)
        viewModel.confirmDelete()
        viewModel.uiState.first { it.planCount == 3 && !it.canCreatePlan }
        viewModel.requestCreate { allowed = true }
        assertFalse(allowed)

        val third = viewModel.uiState.value.visibleItems.first { it.template.id == ids[2] }
        viewModel.requestDelete(third)
        viewModel.confirmDelete()
        viewModel.uiState.first { it.planCount == 2 && it.canCreatePlan }
        viewModel.requestCreate { allowed = true }
        assertTrue(allowed)
        assertEquals(2, templates.planCount())
        assertEquals(setOf(ids[0], ids[1]), templates.observeAll().first().map { it.template.id }.toSet())
    }

    @Test
    fun editorSaveIsTheBackstopWhenAFreeAccountAlreadyHasThreePlans() = runTest {
        val exerciseId = saveExercise()
        repeat(3) { index -> templates.save(draft("Plan ${index + 1}", exerciseId)) }
        val editor = TemplateEditorViewModel(SavedStateHandle(), templates, exercises, free)
        val exercise = exercises.getById(exerciseId)!!
        editor.uiState.first { it.catalog.containsKey(exerciseId) }
        editor.onNameChange("Plan 4")
        editor.selectExercise(exercise)
        assertTrue(editor.uiState.first { it.canSave }.canSave)
        editor.save()
        val locked = editor.uiState.first { it.lockedFeature == AppFeature.UnlimitedWorkoutPlans }
        assertFalse(locked.finished)
        assertFalse(locked.saved)
        assertEquals(3, templates.planCount())
    }

    @Test
    fun archivedPlansCountTowardTheFreeLimit() = runTest {
        val exerciseId = saveExercise()
        val ids = (1..3).map { index ->
            (templates.save(draft("Plan $index", exerciseId)) as TemplateSaveResult.Created).id
        }
        ids.forEach { templates.archive(it) }
        val viewModel = TemplateListViewModel(templates, free)
        val blocked = viewModel.uiState.first { it.planCount == 3 && !it.canCreatePlan }
        assertTrue(blocked.visibleItems.isEmpty())
        viewModel.onArchiveFilter(ArchiveFilter.ALL)
        val all = viewModel.uiState.first { it.archiveFilter == ArchiveFilter.ALL && it.visibleItems.size == 3 }
        assertFalse(all.canCreatePlan)
        assertEquals(ids.toSet(), all.visibleItems.map { it.template.id }.toSet())
    }

    @Test
    fun losingPlanningAccessKeepsScheduleHistoryAndAdherence() = runTest {
        val exerciseId = saveExercise()
        val templateId = (templates.save(draft("Push", exerciseId)) as TemplateSaveResult.Created).id
        val completedId = (
            scheduled.schedule(templateId, LocalDate.of(2026, 2, 10)) as ScheduleWorkoutResult.Scheduled
            ).id
        completeScheduled(templateId, completedId)
        val cancelledId = (
            scheduled.schedule(templateId, LocalDate.of(2026, 2, 12)) as ScheduleWorkoutResult.Scheduled
            ).id
        scheduled.unschedule(cancelledId)
        val futureId = (
            scheduled.schedule(templateId, LocalDate.of(2026, 4, 2)) as ScheduleWorkoutResult.Scheduled
            ).id
        val before = scheduled.observeHistorical().first().sortedBy { it.id }

        val dashboard = DashboardViewModel(
            repository = WeightRepository(database.weightMeasurementDao(), clock),
            sessionRepository = sessions,
            dateProvider = dateProvider,
            scheduledWorkoutRepository = scheduled,
            templateRepository = templates,
            entitlements = free
        )
        dashboard.selectDay(LocalDate.of(2026, 4, 2))
        val future = dashboard.uiState.first {
            it.daySheet?.scheduledWorkouts?.singleOrNull()?.id == futureId
        }.daySheet!!.scheduledWorkouts.single()
        assertFalse(future.isCancelled)
        dashboard.openSchedulePicker()
        assertEquals(AppFeature.AdvancedPlanning, dashboard.uiState.first { it.lockedFeature != null }.lockedFeature)
        assertEquals(before, scheduled.observeHistorical().first().sortedBy { it.id })

        val stats = StatisticsViewModel(sessions, scheduled, dateProvider, free)
        val adherence = stats.uiState.first {
            !it.loading && it.dashboard.adherence.completedCount == 1
        }.dashboard.adherence
        assertEquals(1, adherence.plannedCount)
        assertEquals(1, adherence.completedCount)

        val february = ReportPeriod.containing(ReportKind.Monthly, LocalDate.of(2026, 2, 10))
        val summary = ReportLogic.summarize(
            period = february,
            inputs = ReportInputs(scheduled = scheduled.observeHistorical().first()),
            today = today,
            historyStart = LocalDate.of(2026, 2, 10)
        )
        assertEquals(1, summary.adherence.plannedCount)
        assertEquals(1, summary.adherence.completedCount)
        val history = scheduled.observeHistorical().first()
        assertTrue(history.single { it.id == cancelledId }.isCancelled)
        assertEquals(completedId, history.single { it.sessionStatus != null }.id)
    }

    private suspend fun completeScheduled(templateId: Long, scheduleId: Long) {
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            aggregate.exercises.single().sets.first().id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
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

    private fun draft(name: String, exerciseId: Long): TemplateDraft {
        return TemplateDraft(
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
