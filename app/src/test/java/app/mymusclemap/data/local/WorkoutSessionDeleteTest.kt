package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.WeeklyOverviewLogic
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.AbandonWorkoutResult
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.DeleteWorkoutResult
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkoutSessionDeleteTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var weights: WeightRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-09-16")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
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
            database.workoutSessionDao()
        )
        weights = WeightRepository(database.weightMeasurementDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            weights,
            clock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completedWorkoutDeleteRemovesChildrenAndKeepsCatalog() = runTest {
        weights.save(today, 81.4)
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        val firstSet = sessions.getAggregate(started.sessionId)!!.exercises.single().sets.first()
        sessions.completeSet(
            firstSet.id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        val exerciseIds = database.workoutSessionDao().getExercises(started.sessionId).map { it.id }
        assertTrue(exerciseIds.isNotEmpty())
        val setCountBefore = exerciseIds.sumOf { database.workoutSessionDao().getSets(it).size }
        val muscleCountBefore = exerciseIds.sumOf { database.workoutSessionDao().getMuscles(it).size }
        assertTrue(setCountBefore >= 4)
        assertTrue(muscleCountBefore >= 1)

        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(started.sessionId))
        assertNull(sessions.getAggregate(started.sessionId))
        assertTrue(database.workoutSessionDao().getExercises(started.sessionId).isEmpty())
        assertTrue(database.workoutSessionDao().observeAllSets().first().isEmpty())
        assertTrue(database.workoutSessionDao().observeAllMuscles().first().isEmpty())
        assertNotNull(database.workoutTemplateDao().getById(templateId))
        assertNotNull(database.exerciseDao().getById(pull))
        assertEquals(81.4, weights.getByDate(today)!!.weightKg, 0.0)
        assertTrue(sessions.observeSummaries().first().isEmpty())
    }

    @Test
    fun deletingOneOfTwoSameDayWorkoutsLeavesTheOtherInJournalAndCalendar() = runTest {
        val pull = savePull()
        val firstTemplate = saveTemplate("Push A", listOf(pull to fourSets()))
        val secondTemplate = saveTemplate("Pull A", listOf(pull to fourSets()))
        val first = completeWorkout(firstTemplate)
        val second = completeWorkout(secondTemplate)
        val countsBefore = sessions.observeCompletedCounts(today, today).first()
        assertEquals(2, countsBefore[today])
        assertEquals(2, sessions.observeSummariesOnDate(today).first().size)

        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(first))
        val remaining = sessions.observeSummariesOnDate(today).first()
        assertEquals(listOf(second), remaining.map { it.session.id })
        val countsAfter = sessions.observeCompletedCounts(today, today).first()
        assertEquals(1, countsAfter[today])
        assertNotNull(sessions.getAggregate(second))
        assertNull(sessions.getAggregate(first))
    }

    @Test
    fun heatmapAndWeeklyOverviewRecountAfterDelete() = runTest {
        val pull = savePull()
        val firstTemplate = saveTemplate("Push A", listOf(pull to fourSets()))
        val secondTemplate = saveTemplate("Pull A", listOf(pull to fourSets()))
        val first = completeWorkout(firstTemplate)
        val second = completeWorkout(secondTemplate)
        val weekBefore = WeeklyOverviewLogic.assemble(
            today,
            sessions.observeSummariesBetween(WeeklyOverviewLogic.windowStart(today), today).first(),
            emptyList()
        )
        assertEquals(2, weekBefore.workoutCount)
        assertTrue(weekBefore.completedSetCount > 0)
        val heatmapBefore = sessions.observeHeatmapExercises().first()
        assertTrue(heatmapBefore.isNotEmpty())
        val latestBefore = sessions.observeLatestCompleted().first()
        assertNotNull(latestBefore)

        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(first))
        val weekAfter = WeeklyOverviewLogic.assemble(
            today,
            sessions.observeSummariesBetween(WeeklyOverviewLogic.windowStart(today), today).first(),
            emptyList()
        )
        assertEquals(1, weekAfter.workoutCount)
        assertTrue(weekAfter.completedSetCount < weekBefore.completedSetCount)
        val heatmapAfter = sessions.observeHeatmapExercises().first()
        assertTrue(heatmapAfter.size < heatmapBefore.size)
        assertEquals(second, sessions.observeLatestCompleted().first()!!.session.id)
        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(second))
        assertEquals(
            0,
            WeeklyOverviewLogic.assemble(
                today,
                sessions.observeSummariesBetween(WeeklyOverviewLogic.windowStart(today), today).first(),
                emptyList()
            ).workoutCount
        )
        assertTrue(sessions.observeHeatmapExercises().first().isEmpty())
        assertNull(sessions.observeLatestCompleted().first())
    }

    @Test
    fun discardActiveDeletesAggregateReleasesLockAndDoesNotCreateAbandoned() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        val firstSet = sessions.getAggregate(started.sessionId)!!.exercises.single().sets.first()
        sessions.completeSet(
            firstSet.id,
            ActualSetDraft(repsText = "7", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        assertEquals(AbandonWorkoutResult.Abandoned, sessions.abandon(started.sessionId))
        assertNull(sessions.getAggregate(started.sessionId))
        assertNull(sessions.observeInProgress().first())
        assertTrue(sessions.observeSummaries().first().none { it.session.status == SessionStatus.ABANDONED })
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        val restarted = sessions.start(templateId)
        assertTrue(restarted is StartWorkoutResult.Started)
    }

    @Test
    fun historicalDeleteDoesNotRemoveActiveSessionOrWrongId() = runTest {
        val pull = savePull()
        val firstTemplate = saveTemplate("Push A", listOf(pull to fourSets()))
        val secondTemplate = saveTemplate("Pull A", listOf(pull to fourSets()))
        val completed = completeWorkout(firstTemplate)
        val active = sessions.start(secondTemplate)
            as StartWorkoutResult.Started
        assertEquals(DeleteWorkoutResult.NotFound, sessions.deleteWorkout(999_999L))
        assertEquals(DeleteWorkoutResult.ActiveSession, sessions.deleteWorkout(active.sessionId))
        assertNotNull(sessions.getAggregate(completed))
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(active.sessionId)!!.session.status)
        assertEquals(AbandonWorkoutResult.AlreadyTerminal, sessions.abandon(completed))
        assertNotNull(sessions.getAggregate(completed))
    }

    @Test
    fun legacyAbandonedSessionCanBeDeletedWithoutChangingOthers() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val completed = completeWorkout(templateId)
        val abandonedId = database.workoutSessionDao().insertSession(
            WorkoutSessionEntity(
                templateId = templateId,
                templateName = "Legacy",
                status = SessionStatus.ABANDONED.name,
                workoutDate = today.toString(),
                startedAt = 2L,
                finishedAt = null,
                abandonedAt = 3L,
                notes = null,
                bodyWeightKg = null,
                bodyWeightSource = "UNKNOWN",
                bodyWeightSourceDate = null,
                createdAt = 2L,
                updatedAt = 3L,
                activeLock = null
            )
        )
        assertEquals(DeleteWorkoutResult.Deleted, sessions.deleteWorkout(abandonedId))
        assertNull(database.workoutSessionDao().getById(abandonedId))
        assertEquals(completed, sessions.getAggregate(completed)!!.session.id)
    }

    @Test
    fun concurrentDeletesOfTheSameSessionOnlyRemoveItOnce() = runTest {
        val pull = savePull()
        val templateId = saveTemplate("Push A", listOf(pull to fourSets()))
        val completed = completeWorkout(templateId)
        val first = async { sessions.deleteWorkout(completed) }
        val second = async { sessions.deleteWorkout(completed) }
        val results = setOf(first.await(), second.await())
        assertTrue(DeleteWorkoutResult.Deleted in results)
        assertTrue(DeleteWorkoutResult.NotFound in results || results == setOf(DeleteWorkoutResult.Deleted))
        assertNull(sessions.getAggregate(completed))
        assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
    }

    private suspend fun completeWorkout(templateId: Long): Long {
        val started = sessions.start(templateId)
            as StartWorkoutResult.Started
        val firstSet = sessions.getAggregate(started.sessionId)!!.exercises.single().sets.first()
        sessions.completeSet(
            firstSet.id,
            ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        )
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(started.sessionId, skipRemaining = true))
        return started.sessionId
    }

    private suspend fun savePull(): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Húzódzkodás",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PULL,
                measurementType = MeasurementType.REPETITIONS,
                resistanceBasis = ResistanceBasis.BODYWEIGHT,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.LATS,
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun fourSets(): List<PlannedSetDraft> {
        return List(4) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
    }

    private suspend fun saveTemplate(
        name: String,
        items: List<Pair<Long, List<PlannedSetDraft>>>
    ): Long {
        val draft = TemplateDraft(
            name = name,
            exercises = items.mapIndexed { index, item ->
                TemplateExerciseDraft(
                    localId = -(index + 1L),
                    exerciseId = item.first,
                    sets = item.second
                )
            }
        )
        return (templates.save(draft) as TemplateSaveResult.Created).id
    }
}
