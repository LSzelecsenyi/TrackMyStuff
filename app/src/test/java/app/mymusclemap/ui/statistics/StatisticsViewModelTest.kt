package app.mymusclemap.ui.statistics

import android.content.Context
import androidx.lifecycle.SavedStateHandle
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
import app.mymusclemap.domain.statistics.StatisticsRange
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
class StatisticsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 16)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private lateinit var scheduled: ScheduledWorkoutRepository

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
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(FakeWeightMeasurementDao(), clock),
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
    fun givenNoWorkoutsThenDashboardIsEmpty() = runTest {
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading }
        assertFalse(state.dashboard.hasCompletedWorkouts)
        assertEquals(0, state.dashboard.activity.workoutCount)
        assertEquals(StatisticsRange.Days30, state.range)
        assertNull(state.dashboard.adherence.percent)
    }

    @Test
    fun givenInProgressWorkoutThenStatisticsStayEmpty() = runTest {
        val templateId = saveTemplate()
        assertTrue(sessions.start(templateId) is StartWorkoutResult.Started)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(0, state.dashboard.activity.workoutCount)
        assertTrue(sessions.observeCompletedAggregates().first().isEmpty())
    }

    @Test
    fun givenCompletedWorkoutThenActivityUsesPersistedSession() = runTest {
        val templateId = saveTemplate()
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        val firstSet = aggregate.exercises.single().sets.first()
        sessions.completeSet(
            firstSet.id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.activity.workoutCount)
        assertEquals(1, state.dashboard.activity.completedSetCount)
        assertEquals(1, state.dashboard.activity.trainingDayCount)
        assertNull(state.dashboard.volume.totalKg)
        assertEquals(MuscleGroup.LATS, state.dashboard.muscleDistribution.single().muscle)
        assertEquals(1, state.dashboard.muscleDistribution.single().completedSetCount)
        assertTrue(state.dashboard.restBetweenSessions.isEmpty())
        assertEquals(1, sessions.observeCompletedAggregates().first().size)
        assertEquals("Pull-up", viewModel.exercise(state.dashboard.exercises.single().exerciseId)?.name)
    }

    @Test
    fun givenScheduledCompletionThenAdherenceCountsTheLinkedPlan() = runTest {
        val templateId = saveTemplate()
        val scheduleId = (scheduled.schedule(templateId, today) as ScheduleWorkoutResult.Scheduled).id
        val started = sessions.start(templateId, scheduleId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            aggregate.exercises.single().sets.first().id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.adherence.plannedCount)
        assertEquals(1, state.dashboard.adherence.completedCount)
        assertEquals(100, state.dashboard.adherence.percent)
    }

    @Test
    fun givenUnlinkedHubWorkoutThenAdherenceDoesNotInferCompletion() = runTest {
        val templateId = saveTemplate()
        scheduled.schedule(templateId, today)
        val started = sessions.start(templateId) as StartWorkoutResult.Started
        val aggregate = sessions.getAggregate(started.sessionId)!!
        sessions.completeSet(
            aggregate.exercises.single().sets.first().id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        sessions.finish(started.sessionId, skipRemaining = true)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.activity.workoutCount)
        assertEquals(1, state.dashboard.adherence.plannedCount)
        assertEquals(0, state.dashboard.adherence.completedCount)
        assertEquals(0, state.dashboard.adherence.percent)
    }

    @Test
    fun givenLateScheduledCompletionThenAdherenceCountsItAsCompleted() = runTest {
        val templateId = saveTemplate()
        val scheduleId = (scheduled.schedule(templateId, today.minusDays(2)) as ScheduleWorkoutResult.Scheduled).id
        completeScheduled(templateId, scheduleId)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.adherence.plannedCount)
        assertEquals(1, state.dashboard.adherence.completedCount)
        assertEquals(100, state.dashboard.adherence.percent)
        assertEquals(0, state.dashboard.adherence.missedCount)
        val occurrence = scheduled.getById(scheduleId)!!
        assertEquals(today.minusDays(2), occurrence.scheduledDate)
        assertEquals(today.minusDays(2), occurrence.originalScheduledDate)
    }

    @Test
    fun givenCancelledOccurrenceThenAdherenceIgnoresIt() = runTest {
        val templateId = saveTemplate()
        val kept = (scheduled.schedule(templateId, today.minusDays(1)) as ScheduleWorkoutResult.Scheduled).id
        completeScheduled(templateId, kept)
        val cancelledId = (
            scheduled.schedule(saveTemplate("Other"), today) as ScheduleWorkoutResult.Scheduled
        ).id
        scheduled.unschedule(cancelledId)
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading && it.dashboard.hasCompletedWorkouts }
        assertEquals(1, state.dashboard.adherence.plannedCount)
        assertEquals(1, state.dashboard.adherence.completedCount)
        assertEquals(100, state.dashboard.adherence.percent)
        assertTrue(scheduled.observeHistorical().first().any { it.id == cancelledId && it.isCancelled })
    }

    @Test
    fun givenDeletedTemplateThenPastOccurrenceStillCounts() = runTest {
        val templateId = saveTemplate()
        scheduled.schedule(templateId, today.minusDays(2))
        assertEquals(
            app.mymusclemap.domain.workout.TemplateDeleteResult.Deleted,
            templates.deletePermanently(templateId)
        )
        val viewModel = viewModel()
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(1, state.dashboard.adherence.plannedCount)
        assertEquals(0, state.dashboard.adherence.completedCount)
        assertEquals(0, state.dashboard.adherence.percent)
        val historical = scheduled.observeHistorical().first().single()
        assertNull(historical.templateId)
        assertNull(historical.cancelledAt)
    }

    @Test
    fun givenFreeEntitlementsWhenProRangeSelectedThenRangeStaysThirtyDays() = runTest {
        val viewModel = viewModel(SelectiveFeatureEntitlements(emptySet()))
        viewModel.uiState.first { !it.loading }
        listOf(
            StatisticsRange.Months3,
            StatisticsRange.Months6,
            StatisticsRange.Year1,
            StatisticsRange.All
        ).forEach { range ->
            viewModel.onRangeSelected(range)
            val state = viewModel.uiState.first { it.lockedFeature == AppFeature.AdvancedStatistics }
            assertEquals(StatisticsRange.Days30, state.range)
            viewModel.consumeLockedFeature()
            viewModel.uiState.first { it.lockedFeature == null }
        }
    }

    @Test
    fun givenThirtyDayRangeWhenSelectedThenNoProLockIsShown() = runTest {
        val viewModel = viewModel(SelectiveFeatureEntitlements(emptySet()))
        viewModel.uiState.first { !it.loading }
        viewModel.onRangeSelected(StatisticsRange.Days30)
        val state = viewModel.uiState.value
        assertEquals(StatisticsRange.Days30, state.range)
        assertNull(state.lockedFeature)
    }

    @Test
    fun givenOpenEntitlementsWhenProRangeSelectedThenDashboardUsesThatRange() = runTest {
        val viewModel = viewModel(OpenFeatureEntitlements)
        viewModel.uiState.first { !it.loading }
        listOf(
            StatisticsRange.Months3,
            StatisticsRange.Months6,
            StatisticsRange.Year1,
            StatisticsRange.All
        ).forEach { range ->
            viewModel.onRangeSelected(range)
            val state = viewModel.uiState.first { it.range == range }
            assertEquals(range, state.range)
            assertNull(state.lockedFeature)
        }
    }

    @Test
    fun givenLockedEntitlementsThenSavedProRangeIsCoercedToThirtyDays() = runTest {
        val viewModel = viewModel(
            entitlements = SelectiveFeatureEntitlements(emptySet()),
            savedStateHandle = SavedStateHandle(mapOf("statistics_range" to StatisticsRange.Year1.name))
        )
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(StatisticsRange.Days30, state.range)
    }

    private fun viewModel(
        entitlements: FeatureEntitlements = OpenFeatureEntitlements,
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): StatisticsViewModel {
        return StatisticsViewModel(
            sessions,
            scheduled,
            dateProvider,
            entitlements,
            savedStateHandle
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

    private suspend fun saveTemplate(name: String = "Pull"): Long {
        val exerciseId = (exercises.save(
            ExerciseDraft(
                name = if (name == "Pull") "Pull-up" else "Pull-up $name",
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
