package app.mymusclemap.ui.reports

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.OpenFeatureEntitlements
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.reports.ReportHistoryCoverage
import app.mymusclemap.domain.reports.ReportKind
import app.mymusclemap.domain.workout.ActualSetDraft
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ReportsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 10, 1)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var scheduled: ScheduledWorkoutRepository
    private lateinit var weights: WeightRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
            database.workoutSessionDao(),
            database.scheduledWorkoutDao()
        )
        weights = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            dateProvider
        )
        scheduled = ScheduledWorkoutRepository(
            database.scheduledWorkoutDao(),
            database.workoutTemplateDao(),
            database.workoutSessionDao(),
            clock
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun givenNoHistoryThenEveryKindIsEmpty() = runTest {
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading }
        assertTrue(state.reports.isEmpty())
        assertTrue(state.summaries.isEmpty())
        ReportKind.entries.forEach { kind ->
            viewModel.onKindSelected(kind)
            assertTrue(viewModel.uiState.first { it.kind == kind }.reports.isEmpty())
        }
    }

    @Test
    fun givenOnlyTheOpenPeriodThenCatalogStaysEmpty() = runTest {
        val templateId = saveTemplate()
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            aggregate.exercises.single().sets.first().id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading }
        assertTrue(state.reports.isEmpty())
        assertNull(viewModel.summary(ReportKind.Monthly.name, "2026-10-01"))
    }

    @Test
    fun givenLateCompletionThenClosedMonthsStayNewestFirstAndOctoberIsHidden() = runTest {
        val templateId = saveTemplate()
        val cancelled = (
            scheduled.schedule(templateId, LocalDate.of(2026, 8, 20)) as ScheduleWorkoutResult.Scheduled
        ).id
        scheduled.unschedule(cancelled)
        val kept = (
            scheduled.schedule(templateId, LocalDate.of(2026, 8, 30)) as ScheduleWorkoutResult.Scheduled
        ).id
        completeScheduled(templateId, kept)

        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.reports.size == 2 }
        assertEquals(LocalDate.of(2026, 9, 1), state.reports[0].period.startInclusive)
        assertEquals(ReportHistoryCoverage.Complete, state.reports[0].coverage)
        assertEquals(LocalDate.of(2026, 8, 1), state.reports[1].period.startInclusive)
        assertEquals(ReportHistoryCoverage.Partial, state.reports[1].coverage)
        assertTrue(state.reports.none { it.period.startInclusive.monthValue == 10 })

        val august = viewModel.summary(ReportKind.Monthly.name, "2026-08-01")!!
        assertEquals(1, august.adherence.plannedCount)
        assertEquals(1, august.adherence.completedCount)
        assertEquals(0, august.activity.workoutCount)
        val september = viewModel.summary(ReportKind.Monthly.name, "2026-09-01")!!
        assertEquals(0, september.adherence.plannedCount)
        assertEquals(0, september.activity.workoutCount)
        assertNull(viewModel.summary(ReportKind.Monthly.name, "2026-10-01"))

        viewModel.onKindSelected(ReportKind.Quarterly)
        val quarter = viewModel.uiState.first { it.kind == ReportKind.Quarterly }
        assertEquals(LocalDate.of(2026, 7, 1), quarter.reports.single().period.startInclusive)
        assertEquals(ReportHistoryCoverage.Partial, quarter.reports.single().coverage)
        viewModel.onKindSelected(ReportKind.Yearly)
        assertTrue(viewModel.uiState.first { it.kind == ReportKind.Yearly }.reports.isEmpty())
    }

    @Test
    fun givenInPeriodWeightsThenSummaryUsesOnlyThoseMeasurements() = runTest {
        weights.save(LocalDate.of(2026, 8, 1), 80.0)
        weights.save(LocalDate.of(2026, 8, 20), 81.0)
        weights.save(LocalDate.of(2026, 10, 1), 90.0)
        val viewModel = viewModel()
        viewModel.uiState.first { !it.loading && it.reports.isNotEmpty() }
        val august = viewModel.summary(ReportKind.Monthly.name, "2026-08-01")!!
        val bodyWeight = august.bodyWeight!!
        assertEquals(ReportHistoryCoverage.Complete, august.coverage)
        assertEquals(80.0, bodyWeight.firstKg, 0.0)
        assertEquals(81.0, bodyWeight.lastKg!!, 0.0)
        assertEquals(1.0, bodyWeight.changeKg!!, 0.0)
        assertNull(viewModel.summary(ReportKind.Monthly.name, "2026-09-01")!!.bodyWeight)
    }

    @Test
    fun givenSavedKindThenLandingRestoresIt() = runTest {
        val viewModel = viewModel(savedStateHandle = SavedStateHandle(mapOf("reports_kind" to ReportKind.HalfYear.name)))
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(ReportKind.HalfYear, state.kind)
        assertTrue(state.lockedKinds.isEmpty())
    }

    @Test
    fun givenFreeEntitlementsWhenLongerKindSelectedThenMonthlyStaysAndProIsRequested() = runTest {
        weights.save(LocalDate.of(2026, 8, 1), 80.0)
        val viewModel = viewModel(SelectiveFeatureEntitlements(emptySet()))
        val monthly = viewModel.uiState.first { !it.loading && it.reports.isNotEmpty() }
        assertEquals(ReportKind.Monthly, monthly.kind)
        assertEquals(
            setOf(ReportKind.Quarterly, ReportKind.HalfYear, ReportKind.Yearly),
            monthly.lockedKinds
        )
        assertFalse(monthly.kindLocked)
        val august = viewModel.summary(ReportKind.Monthly.name, "2026-08-01")
        assertEquals(80.0, august!!.bodyWeight!!.firstKg, 0.0)

        listOf(ReportKind.Quarterly, ReportKind.HalfYear, ReportKind.Yearly).forEach { kind ->
            viewModel.onKindSelected(kind)
            val locked = viewModel.uiState.first { it.kind == kind && it.lockedFeature == AppFeature.AdvancedReports }
            assertTrue(locked.kindLocked)
            assertNull(viewModel.summary(kind.name, locked.reports.firstOrNull()?.period?.startInclusive?.toString()))
            viewModel.consumeLockedFeature()
            val dismissed = viewModel.uiState.first { it.lockedFeature == null && it.kind == kind }
            assertTrue(dismissed.kindLocked)
        }
        viewModel.onKindSelected(ReportKind.Quarterly)
        val visibleQuarter = viewModel.uiState.first { it.kind == ReportKind.Quarterly }
        assertEquals(LocalDate.of(2026, 7, 1), visibleQuarter.reports.single().period.startInclusive)
        viewModel.onKindSelected(ReportKind.Monthly)
        val restored = viewModel.uiState.first { it.kind == ReportKind.Monthly }
        assertNull(restored.lockedFeature)
        assertFalse(restored.kindLocked)
        assertEquals(80.0, viewModel.summary(ReportKind.Monthly.name, "2026-08-01")!!.bodyWeight!!.firstKg, 0.0)
    }

    @Test
    fun givenAdvancedStatisticsWithoutReportsThenLongerReportsStayLocked() = runTest {
        val viewModel = viewModel(SelectiveFeatureEntitlements(setOf(AppFeature.AdvancedStatistics)))
        viewModel.uiState.first { !it.loading }
        viewModel.onKindSelected(ReportKind.Quarterly)
        val state = viewModel.uiState.first { it.lockedFeature == AppFeature.AdvancedReports }
        assertEquals(ReportKind.Quarterly, state.kind)
        assertEquals(AppFeature.AdvancedReports, state.lockedFeature)
        assertNull(viewModel.summary(ReportKind.Quarterly.name, "2026-07-01"))
        var opened = false
        viewModel.openReport(
            app.mymusclemap.domain.reports.ReportPeriod(
                ReportKind.Quarterly,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 9, 30)
            )
        ) { opened = true }
        assertFalse(opened)
    }

    @Test
    fun givenAdvancedReportsThenQuarterlyOpensWithoutALock() = runTest {
        weights.save(LocalDate.of(2026, 8, 1), 80.0)
        val viewModel = viewModel(SelectiveFeatureEntitlements(setOf(AppFeature.AdvancedReports)))
        viewModel.uiState.first { !it.loading }
        viewModel.onKindSelected(ReportKind.Quarterly)
        val state = viewModel.uiState.first { it.kind == ReportKind.Quarterly }
        assertNull(state.lockedFeature)
        assertFalse(state.kindLocked)
        assertTrue(state.lockedKinds.isEmpty())
        assertEquals(LocalDate.of(2026, 7, 1), state.reports.single().period.startInclusive)
        var opened = false
        viewModel.openReport(state.reports.single().period) { opened = true }
        assertTrue(opened)
        assertNull(viewModel.denial(ReportKind.Quarterly.name))
    }

    @Test
    fun givenSavedProKindWithoutAccessThenLandingStaysOnMonthly() = runTest {
        val viewModel = viewModel(
            entitlements = SelectiveFeatureEntitlements(emptySet()),
            savedStateHandle = SavedStateHandle(mapOf("reports_kind" to ReportKind.Yearly.name))
        )
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(ReportKind.Monthly, state.kind)
        assertNull(state.lockedFeature)
        assertTrue(ReportKind.Yearly in state.lockedKinds)
    }

    @Test
    fun openingTheReportListDoesNotRecordAMonthlyReview() = runTest {
        weights.save(LocalDate.of(2026, 8, 10), 80.0)
        insertClosedMonthWorkout()
        var generated = 0
        val viewModel = viewModel(onMonthlyReportGenerated = { generated += 1 })
        val state = viewModel.uiState.first { !it.loading && it.reports.size >= 2 }
        assertEquals(0, generated)
        val august = state.reports.single { it.period.startInclusive == LocalDate.of(2026, 8, 1) }.period
        assertEquals(0, viewModel.summary(ReportKind.Monthly.name, "2026-08-01")!!.activity.workoutCount)
        viewModel.openReport(august) {}
        assertEquals(0, generated)
        val september = state.reports.single { it.period.startInclusive == LocalDate.of(2026, 9, 1) }.period
        assertTrue(viewModel.summary(ReportKind.Monthly.name, "2026-09-01")!!.activity.workoutCount > 0)
        viewModel.onKindSelected(ReportKind.Quarterly)
        val quarter = viewModel.uiState.first { it.kind == ReportKind.Quarterly }.reports.single().period
        viewModel.openReport(quarter) {}
        assertEquals(0, generated)
        viewModel.onKindSelected(ReportKind.Monthly)
        viewModel.openReport(september) {}
        assertEquals(1, generated)
    }

    private fun viewModel(
        entitlements: FeatureEntitlements = OpenFeatureEntitlements,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        onMonthlyReportGenerated: suspend () -> Unit = {}
    ): ReportsViewModel {
        return ReportsViewModel(
            sessions,
            scheduled,
            weights,
            dateProvider,
            entitlements,
            savedStateHandle,
            onMonthlyReportGenerated
        )
    }

    private suspend fun insertClosedMonthWorkout() {
        database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = null,
                templateName = "Push",
                status = "COMPLETED",
                workoutDate = "2026-09-15",
                startedAt = 1L,
                finishedAt = 2L,
                abandonedAt = null,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 1L,
                updatedAt = 1L,
                activeLock = null,
                clientWorkoutId = java.util.UUID.randomUUID().toString()
            )
        )
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

    private suspend fun saveTemplate(): Long {
        val exerciseId = (exercises.save(
            ExerciseDraft(
                name = "Pull-up",
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
                name = "Pull",
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
