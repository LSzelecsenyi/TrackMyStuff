package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.workout.ActualSetDraft
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.domain.workout.FinishWorkoutResult
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.PlannedSetDraft
import app.mymusclemap.domain.workout.SessionMutationResult
import app.mymusclemap.domain.workout.SessionProgressLogic
import app.mymusclemap.domain.workout.SessionSetStatus
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
class SetDraftPersistenceTest {
    private lateinit var database: WeightDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var templates: WorkoutTemplateRepository
    private lateinit var sessions: WorkoutSessionRepository
    private val today = LocalDate.parse("2026-10-02")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-10-02T12:00:00Z"), ZoneOffset.UTC)
        exercises = ExerciseRepository(database.exerciseDao(), clock)
        templates = WorkoutTemplateRepository(database.workoutTemplateDao(), database.exerciseDao(), clock)
        sessions = WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), clock),
            clock,
            FixedDateProvider(today)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun unfinishedDraftSurvivesANewRepositoryAndStaysIncomplete() = runTest {
        val setId = startOneSet(
            measurement = MeasurementType.REPETITIONS_AND_WEIGHT,
            resistance = ResistanceBasis.EXTERNAL,
            interpretation = WeightInterpretation.TOTAL,
            sets = listOf(weighted("5", "60"))
        )
        val draft = ActualSetDraft(
            repsText = "7",
            loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
            weightText = "82.5"
        )
        assertEquals(SessionMutationResult.Updated, sessions.saveSetDraft(setId, draft))

        val reloaded = reopened().getAggregate(sessionId())!!
        val set = reloaded.exercises.single().sets.single()
        assertEquals(SessionSetStatus.PENDING, set.status)
        assertEquals("7", set.draft!!.repsText)
        assertEquals("82.5", set.draft.weightText)
        assertEquals(0, SessionProgressLogic.fromAggregate(reloaded).completed)
        assertNull(WorkoutSetVolume.volumeKg(reloaded.exercises.single().exercise, set))
    }

    @Test
    fun completingADraftPersistsTheSetOnceAndRemovesTheDraft() = runTest {
        val setId = startOneSet(
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(reps("8"))
        )
        val draft = ActualSetDraft(repsText = "9", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY)
        sessions.saveSetDraft(setId, draft)
        assertEquals(SessionMutationResult.Updated, sessions.completeSet(setId, draft))
        assertEquals(SessionMutationResult.NotActive, sessions.saveSetDraft(setId, draft.copy(repsText = "11")))

        val set = sessions.getAggregate(sessionId())!!.exercises.single().sets.single()
        assertEquals(SessionSetStatus.COMPLETED, set.status)
        assertNull(set.draft)
        assertEquals(9, set.actualReps)
        assertNull(database.workoutSessionDao().getSet(setId)!!.draftPayload)
    }

    @Test
    fun removingAnExtraSetRemovesItsDraft() = runTest {
        val original = startOneSet(
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(reps("8"))
        )
        val exerciseId = sessions.getAggregate(sessionId())!!.exercises.single().exercise.id
        assertEquals(SessionMutationResult.Updated, sessions.addExtraSet(exerciseId))
        val extra = sessions.getAggregate(sessionId())!!.exercises.single().sets.single { it.id != original }
        sessions.saveSetDraft(extra.id, ActualSetDraft(repsText = "12", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        assertEquals(SessionMutationResult.Updated, sessions.removeExtraSet(extra.id))
        assertNull(database.workoutSessionDao().getSet(extra.id))
    }

    @Test
    fun abandoningTheWorkoutRemovesDraftRows() = runTest {
        val setId = startOneSet(
            measurement = MeasurementType.DURATION,
            resistance = ResistanceBasis.NONE,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(duration(1, 30))
        )
        sessions.saveSetDraft(
            setId,
            ActualSetDraft(loadKind = PlannedLoadKind.NONE, minutesText = "2", secondsText = "5")
        )
        assertTrue(sessions.abandon(sessionId()) is app.mymusclemap.domain.workout.AbandonWorkoutResult.Abandoned)
        assertNull(sessions.getAggregate(sessionId()))
        assertNull(database.workoutSessionDao().getSet(setId))
    }

    @Test
    fun finishingSkipsAnUnfinishedDraftInsteadOfCompletingIt() = runTest {
        val first = startOneSet(
            measurement = MeasurementType.REPETITIONS,
            resistance = ResistanceBasis.BODYWEIGHT,
            interpretation = WeightInterpretation.NOT_APPLICABLE,
            sets = listOf(reps("8", -1), reps("8", -2))
        )
        val sets = sessions.getAggregate(sessionId())!!.exercises.single().sets
        val second = sets.single { it.id != first }.id
        sessions.completeSet(first, ActualSetDraft(repsText = "8", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        sessions.saveSetDraft(second, ActualSetDraft(repsText = "3", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        assertEquals(FinishWorkoutResult.Finished, sessions.finish(sessionId(), skipRemaining = true))

        val stored = database.workoutSessionDao().getSets(
            sessions.getAggregate(sessionId())!!.exercises.single().exercise.id
        )
        val skipped = stored.single { it.id == second }
        assertEquals(SessionSetStatus.SKIPPED.name, skipped.status)
        assertNull(skipped.draftPayload)
        assertEquals(SessionSetStatus.COMPLETED.name, stored.single { it.id == first }.status)
    }

    @Test
    fun draftsForRepsWeightTimeAndDistanceRestoreOnTheCorrectRows() = runTest {
        val pull = saveExercise(
            "Pull",
            MeasurementType.REPETITIONS,
            ResistanceBasis.BODYWEIGHT,
            WeightInterpretation.NOT_APPLICABLE,
            MovementPattern.VERTICAL_PULL
        )
        val bench = saveExercise(
            "Bench",
            MeasurementType.REPETITIONS_AND_WEIGHT,
            ResistanceBasis.EXTERNAL,
            WeightInterpretation.TOTAL
        )
        val plank = saveExercise(
            "Plank",
            MeasurementType.DURATION,
            ResistanceBasis.NONE,
            WeightInterpretation.NOT_APPLICABLE,
            MovementPattern.CORE
        )
        val run = saveExercise(
            "Run",
            MeasurementType.DISTANCE_AND_DURATION,
            ResistanceBasis.NONE,
            WeightInterpretation.NOT_APPLICABLE,
            MovementPattern.CARDIO,
            ExerciseCategory.CARDIO
        )
        val templateId = (templates.save(
            TemplateDraft(
                name = "Mixed",
                exercises = listOf(
                    TemplateExerciseDraft(localId = -1, exerciseId = pull, sets = listOf(reps("8", -11), reps("8", -12))),
                    TemplateExerciseDraft(localId = -2, exerciseId = bench, sets = listOf(weighted("5", "60"))),
                    TemplateExerciseDraft(localId = -3, exerciseId = plank, sets = listOf(duration(0, 30))),
                    TemplateExerciseDraft(localId = -4, exerciseId = run, sets = listOf(distance("400", 2, 0)))
                )
            )
        ) as TemplateSaveResult.Created).id
        startedSession = (sessions.start(templateId) as StartWorkoutResult.Started).sessionId
        val stored = sessions.getAggregate(startedSession)!!.exercises
        val pullSets = stored[0].sets
        val benchSet = stored[1].sets.single().id
        val plankSet = stored[2].sets.single().id
        val runSet = stored[3].sets.single().id
        sessions.saveSetDraft(pullSets[0].id, ActualSetDraft(repsText = "6", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        sessions.saveSetDraft(pullSets[1].id, ActualSetDraft(repsText = "10", loadKind = PlannedLoadKind.BODYWEIGHT_ONLY))
        sessions.saveSetDraft(
            benchSet,
            ActualSetDraft(repsText = "7", loadKind = PlannedLoadKind.EXTERNAL_WEIGHT, weightText = "82.5")
        )
        sessions.saveSetDraft(
            benchSet,
            ActualSetDraft(repsText = "7", loadKind = PlannedLoadKind.EXTERNAL_WEIGHT, weightText = "85")
        )
        sessions.saveSetDraft(
            plankSet,
            ActualSetDraft(loadKind = PlannedLoadKind.NONE, minutesText = "1", secondsText = "15")
        )
        sessions.saveSetDraft(
            runSet,
            ActualSetDraft(
                loadKind = PlannedLoadKind.NONE,
                minutesText = "3",
                secondsText = "0",
                distanceText = "1.5",
                distanceUnit = DistanceUnit.KILOMETERS
            )
        )

        val byId = reopened().getAggregate(startedSession)!!.exercises.flatMap { it.sets }.associateBy { it.id }
        assertEquals("6", byId.getValue(pullSets[0].id).draft!!.repsText)
        assertEquals("10", byId.getValue(pullSets[1].id).draft!!.repsText)
        assertEquals("85", byId.getValue(benchSet).draft!!.weightText)
        assertEquals("1", byId.getValue(plankSet).draft!!.minutesText)
        assertEquals("15", byId.getValue(plankSet).draft!!.secondsText)
        assertEquals("1.5", byId.getValue(runSet).draft!!.distanceText)
        assertEquals(DistanceUnit.KILOMETERS, byId.getValue(runSet).draft!!.distanceUnit)
        assertTrue(byId.values.all { it.status == SessionSetStatus.PENDING })
    }

    private var startedSession = 0L

    private fun sessionId(): Long = startedSession

    private fun reopened(): WorkoutSessionRepository {
        return WorkoutSessionRepository(
            database.workoutSessionDao(),
            database.workoutTemplateDao(),
            database.exerciseDao(),
            WeightRepository(database.weightMeasurementDao(), Clock.systemUTC()),
            Clock.systemUTC(),
            FixedDateProvider(today)
        )
    }

    private suspend fun startOneSet(
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        sets: List<PlannedSetDraft>,
        name: String = "Exercise"
    ): Long {
        val exerciseId = saveExercise(name, measurement, resistance, interpretation)
        val templateId = saveTemplate(name, exerciseId, sets)
        startedSession = (sessions.start(templateId) as StartWorkoutResult.Started).sessionId
        return sessions.getAggregate(startedSession)!!.exercises.single().sets.first().id
    }

    private suspend fun saveExercise(
        name: String,
        measurement: MeasurementType,
        resistance: ResistanceBasis,
        interpretation: WeightInterpretation,
        movement: MovementPattern = MovementPattern.HORIZONTAL_PUSH,
        category: ExerciseCategory = ExerciseCategory.STRENGTH
    ): Long {
        return (exercises.save(
            ExerciseDraft(
                name = name,
                category = category,
                movementPattern = movement,
                measurementType = measurement,
                resistanceBasis = resistance,
                weightInterpretation = interpretation,
                primaryMuscle = MuscleGroup.CHEST
            )
        ) as ExerciseSaveResult.Created).id
    }

    private suspend fun saveTemplate(name: String, exerciseId: Long, sets: List<PlannedSetDraft>): Long {
        return (templates.save(
            TemplateDraft(
                name = name,
                exercises = listOf(
                    TemplateExerciseDraft(localId = -1L, exerciseId = exerciseId, sets = sets)
                )
            )
        ) as TemplateSaveResult.Created).id
    }

    private fun reps(value: String, id: Long = -1L) = PlannedSetDraft(
        localId = id,
        minRepsText = value,
        loadKind = PlannedLoadKind.BODYWEIGHT_ONLY
    )

    private fun weighted(reps: String, weight: String) = PlannedSetDraft(
        localId = -1L,
        minRepsText = reps,
        loadKind = PlannedLoadKind.EXTERNAL_WEIGHT,
        weightText = weight
    )

    private fun duration(minutes: Int, seconds: Int) = PlannedSetDraft(
        localId = -1L,
        loadKind = PlannedLoadKind.NONE,
        minutesText = minutes.toString(),
        secondsText = seconds.toString()
    )

    private fun distance(meters: String, minutes: Int, seconds: Int) = PlannedSetDraft(
        localId = -1L,
        loadKind = PlannedLoadKind.NONE,
        minutesText = minutes.toString(),
        secondsText = seconds.toString(),
        distanceText = meters,
        distanceUnit = DistanceUnit.METERS
    )
}
