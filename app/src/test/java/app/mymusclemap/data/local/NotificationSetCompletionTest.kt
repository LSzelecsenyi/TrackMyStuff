package app.mymusclemap.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.domain.FixedDateProvider
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
import app.mymusclemap.domain.workout.ActualSetLogic
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.NotificationSetCompletion
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionFocusLogic
import app.mymusclemap.domain.workout.SessionMutationResult
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class NotificationSetCompletionTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository

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
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(LocalDate.parse("2026-09-16"))
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun notificationCompletesOnlyTheCurrentPendingSetAndAdvances() = runTest {
        val sessionId = start(repsExercise("Dips") to twoSets())
        val sets = sessions.getAggregate(sessionId)!!.exercises.single().sets
        assertEquals(
            NotificationSetCompletion.Stale,
            sessions.completeCurrentPendingSetFromNotification(sessionId, sets[1].id)
        )
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises.single().sets[1].status)

        assertEquals(
            NotificationSetCompletion.Updated,
            sessions.completeCurrentPendingSetFromNotification(sessionId, sets[0].id)
        )
        val after = sessions.getAggregate(sessionId)!!
        assertEquals(8, after.exercises.single().sets[0].actualReps)
        assertEquals(SessionSetStatus.COMPLETED, after.exercises.single().sets[0].status)
        assertNull(after.exercises.single().sets[0].draft)
        assertEquals(sets[1].id, SessionFocusLogic.currentPendingSet(after)!!.id)
        assertEquals(SessionStatus.IN_PROGRESS, after.session.status)

        assertEquals(
            NotificationSetCompletion.Stale,
            sessions.completeCurrentPendingSetFromNotification(sessionId, sets[0].id)
        )
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises.single().sets[1].status)
        assertEquals(8, sessions.getAggregate(sessionId)!!.exercises.single().sets[0].actualReps)
    }

    @Test
    fun persistedDraftIsWhatGetsCompleted() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        val setId = sessions.getAggregate(sessionId)!!.exercises.single().sets.single().id
        assertEquals(
            SessionMutationResult.Updated,
            sessions.saveSetDraft(
                setId,
                ActualSetDraft(repsText = "11", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
            )
        )
        assertEquals(
            NotificationSetCompletion.Updated,
            sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        )
        val stored = sessions.getAggregate(sessionId)!!.exercises.single().sets.single()
        assertEquals(11, stored.actualReps)
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
    }

    @Test
    fun reconstructedPlanMatchesAnImmediateInAppCompletion() = runTest {
        val exerciseId = repsExercise("Dips")
        val notified = start(exerciseId to listOf(reps("8", "12")))
        val notifiedSet = sessions.getAggregate(notified)!!.exercises.single().sets.single()
        sessions.completeCurrentPendingSetFromNotification(notified, notifiedSet.id)
        val notifiedAggregate = sessions.getAggregate(notified)!!
        val fromNotification = notifiedAggregate.exercises.single().sets.single()
        assertEquals(SessionStatus.IN_PROGRESS, notifiedAggregate.session.status)

        sessions.abandon(notified)
        val inApp = start(exerciseId to listOf(reps("8", "12")))
        val inAppSet = sessions.getAggregate(inApp)!!.exercises.single().sets.single()
        sessions.completeSet(inAppSet.id, ActualSetLogic.draftFromSet(inAppSet))
        val fromScreen = sessions.getAggregate(inApp)!!.exercises.single().sets.single()

        assertEquals(8, fromNotification.actualReps)
        assertEquals(fromScreen.actualReps, fromNotification.actualReps)
        assertEquals(fromScreen.actualLoadKind, fromNotification.actualLoadKind)
        assertEquals(fromScreen.actualWeightKg, fromNotification.actualWeightKg)
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(inApp)!!.session.status)
    }

    @Test
    fun editingACompletedSetFromTheScreenStillWorks() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        val setId = sessions.getAggregate(sessionId)!!.exercises.single().sets.single().id
        sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        val edited = ActualSetDraft(repsText = "6", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        assertEquals(SessionMutationResult.Updated, sessions.completeSet(setId, edited))
        assertEquals(6, sessions.getAggregate(sessionId)!!.exercises.single().sets.single().actualReps)
    }

    @Test
    fun invalidDraftDoesNotComplete() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        val setId = sessions.getAggregate(sessionId)!!.exercises.single().sets.single().id
        sessions.saveSetDraft(setId, ActualSetDraft(repsText = "", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        assertEquals(
            NotificationSetCompletion.Invalid,
            sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        )
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises.single().sets.single().status)
    }

    @Test
    fun completionOnlySetCompletesWithoutInventedReps() = runTest {
        val exerciseId = saveExercise("Hold", MeasurementType.COMPLETION_ONLY, ResistanceBasis.NONE)
        val sessionId = start(exerciseId to listOf(PlannedSetDraft(-1L, loadKind = PlannedLoadKind.NONE)))
        val set = sessions.getAggregate(sessionId)!!.exercises.single().sets.single()
        assertEquals(
            NotificationSetCompletion.Updated,
            sessions.completeCurrentPendingSetFromNotification(sessionId, set.id)
        )
        val stored = sessions.getAggregate(sessionId)!!.exercises.single().sets.single()
        assertEquals(SessionSetStatus.COMPLETED, stored.status)
        assertNull(stored.actualReps)
        assertNull(stored.actualWeightKg)
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getAggregate(sessionId)!!.session.status)
    }

    @Test
    fun finalSetDoesNotFinishTheWorkout() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        val setId = sessions.getAggregate(sessionId)!!.exercises.single().sets.single().id
        sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        val stored = sessions.getAggregate(sessionId)!!
        assertEquals(SessionStatus.IN_PROGRESS, stored.session.status)
        assertNull(SessionFocusLogic.currentPendingSet(stored))
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(sessionId, skipRemaining = false))
    }

    @Test
    fun finishedOrDeletedSessionCannotBeMutatedOrRecreated() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        val setId = sessions.getAggregate(sessionId)!!.exercises.single().sets.single().id
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(sessionId, skipRemaining = true))
        assertEquals(
            NotificationSetCompletion.Stale,
            sessions.completeCurrentPendingSetFromNotification(sessionId, setId)
        )
        assertEquals(SessionStatus.COMPLETED, sessions.getAggregate(sessionId)!!.session.status)

        val other = start(repsExercise("Row") to listOf(reps("8")))
        val otherSet = sessions.getAggregate(other)!!.exercises.single().sets.single().id
        assertEquals(AbandonWorkoutResult.Abandoned, sessions.abandon(other))
        assertEquals(
            NotificationSetCompletion.NotFound,
            sessions.completeCurrentPendingSetFromNotification(other, otherSet)
        )
        assertNull(sessions.getAggregate(other))
        assertNull(sessions.activeAggregate())
    }

    @Test
    fun unknownIdsDoNotWrite() = runTest {
        val sessionId = start(repsExercise("Dips") to listOf(reps("8")))
        assertEquals(
            NotificationSetCompletion.NotFound,
            sessions.completeCurrentPendingSetFromNotification(999L, 1L)
        )
        assertEquals(
            NotificationSetCompletion.Stale,
            sessions.completeCurrentPendingSetFromNotification(sessionId, 999L)
        )
        assertEquals(SessionSetStatus.PENDING, sessions.getAggregate(sessionId)!!.exercises.single().sets.single().status)
    }

    private var templateSerial = 0

    private suspend fun start(item: Pair<Long, List<PlannedSetDraft>>): Long {
        templateSerial += 1
        val templateId = saveTemplate("Push $templateSerial", listOf(item))
        return (sessions.start(templateId) as StartWorkoutResult.Started).sessionId
    }

    private suspend fun repsExercise(name: String): Long {
        return saveExercise(name, MeasurementType.REPETITIONS, ResistanceBasis.BODYWEIGHT)
    }

    private suspend fun saveExercise(
        name: String,
        measurement: MeasurementType,
        resistance: ResistanceBasis
    ): Long {
        return (exercises.save(
            ExerciseDraft(
                name = name,
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.VERTICAL_PUSH,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE,
                primaryMuscle = MuscleGroup.TRICEPS
            )
        ) as ExerciseSaveResult.Created).id
    }

    private fun reps(min: String, max: String = ""): PlannedSetDraft {
        return PlannedSetDraft(
            localId = -1L,
            minRepsText = min,
            maxRepsText = max,
            loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
        )
    }

    private fun twoSets(): List<PlannedSetDraft> {
        return List(2) { index ->
            PlannedSetDraft(
                localId = -(index + 1L),
                minRepsText = "8",
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
            )
        }
    }

    private suspend fun saveTemplate(name: String, items: List<Pair<Long, List<PlannedSetDraft>>>): Long {
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
