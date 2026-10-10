package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionMutationResult
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.StartWorkoutResult
import app.mymusclemap.domain.workout.TemplateDraft
import app.mymusclemap.domain.workout.TemplateExerciseDraft
import app.mymusclemap.domain.workout.TemplateSaveResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A brand-new account database, the first saved plan, the first workout, and a
 * process restart. This is the data path behind a fresh install. It does not
 * replace the navigation test that covers the first plan screen.
 */
@RunWith(RobolectricTestRunner::class)
class FreshAccountFirstWorkoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"
    private val clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)
    private val date = FixedDateProvider(LocalDate.of(2026, 10, 9))
    private val openDatabases = mutableListOf<WeightDatabase>()

    @After
    fun tearDown() {
        openDatabases.forEach { database ->
            if (database.isOpen) database.close()
        }
        openDatabases.clear()
        deleteAccount(userA)
        deleteAccount(userB)
    }

    @Test
    fun firstPlanSurvivesStartCompletionAndReopen() = runTest {
        val first = stack(open(userA))
        val exerciseId = saveBench(first.exercises)
        val planId = savePlan(first.templates, exerciseId)
        val started = first.sessions.start(planId) as StartWorkoutResult.Started

        closeAll()
        val restarted = stack(open(userA))
        val inProgress = restarted.sessions.getAggregate(started.sessionId)!!
        assertEquals(SessionStatus.IN_PROGRESS, inProgress.session.status)
        assertEquals("Push A", inProgress.session.templateName)

        val setId = inProgress.exercises.single().sets.single().id
        assertEquals(
            SessionMutationResult.Updated,
            restarted.sessions.completeSet(
                setId,
                ActualSetDraft(
                    repsText = "8",
                    loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                    weightText = "10"
                )
            )
        )
        assertEquals(
            FinishWorkoutResult.Finished,
            restarted.sessions.finish(started.sessionId, skipRemaining = false)
        )

        closeAll()
        val returned = stack(open(userA))
        val completed = returned.sessions.getAggregate(started.sessionId)!!
        assertEquals(SessionStatus.COMPLETED, completed.session.status)
        assertEquals(8, completed.exercises.single().sets.single().actualReps)
        assertEquals("Push A", returned.database.workoutTemplateDao().getById(planId)?.name)
    }

    @Test
    fun aSecondAccountDoesNotSeeTheFirstWorkout() = runTest {
        val first = stack(open(userA))
        val exerciseId = saveBench(first.exercises)
        val planId = savePlan(first.templates, exerciseId)
        val started = first.sessions.start(planId) as StartWorkoutResult.Started
        val clientWorkoutId = first.sessions.getAggregate(started.sessionId)!!.session.clientWorkoutId

        val second = stack(open(userB))
        assertEquals(0, second.database.workoutTemplateDao().countAll())
        assertEquals(0, second.database.workoutSessionDao().countCompleted())
        assertNull(second.database.workoutSessionDao().getInProgress())
        assertNull(second.database.workoutSessionDao().getByClientWorkoutId(clientWorkoutId))
        assertNull(second.sessions.getAggregate(started.sessionId))
        assertEquals("Push A", first.sessions.getAggregate(started.sessionId)!!.session.templateName)
    }

    private fun open(userId: String): WeightDatabase {
        val database = Room.databaseBuilder(
            context,
            WeightDatabase::class.java,
            accountDatabaseName(userId)
        )
            .allowMainThreadQueries()
            .build()
        openDatabases += database
        return database
    }

    private fun closeAll() {
        openDatabases.forEach { database ->
            if (database.isOpen) database.close()
        }
        openDatabases.clear()
    }

    private fun deleteAccount(userId: String) {
        val path = context.getDatabasePath(accountDatabaseName(userId))
        path.delete()
        File("${path.path}-wal").delete()
        File("${path.path}-shm").delete()
    }

    private fun stack(database: WeightDatabase): AccountStack {
        val exercises = ExerciseRepository(
            database.exerciseDao(),
            clock,
            database.workoutTemplateDao(),
            database.workoutSessionDao()
        )
        val templates = WorkoutTemplateRepository(
            database.workoutTemplateDao(),
            database.exerciseDao(),
            clock,
            database.workoutSessionDao()
        )
        val sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            date
        )
        return AccountStack(database, exercises, templates, sessions)
    }

    private suspend fun saveBench(exercises: ExerciseRepository): Long {
        return (exercises.save(
            ExerciseDraft(
                name = "Bench",
                category = ExerciseCategory.STRENGTH,
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT,
                resistanceBasis = ResistanceBasis.EXTERNAL,
                weightInterpretation = WeightInterpretation.TOTAL,
                primaryMuscle = MuscleGroup.CHEST
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun savePlan(templates: WorkoutTemplateRepository, exerciseId: Long): Long {
        return (templates.save(
            TemplateDraft(
                name = "Push A",
                exercises = listOf(
                    TemplateExerciseDraft(
                        localId = -1L,
                        exerciseId = exerciseId,
                        sets = listOf(
                            PlannedSetDraft(
                                localId = -1L,
                                minRepsText = "8",
                                loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
                                weightText = "10"
                            )
                        )
                    )
                )
            )
        ) as TemplateSaveResult.Created).id
    }

    private data class AccountStack(
        val database: WeightDatabase,
        val exercises: ExerciseRepository,
        val templates: WorkoutTemplateRepository,
        val sessions: WorkoutSessionRepository
    )
}
